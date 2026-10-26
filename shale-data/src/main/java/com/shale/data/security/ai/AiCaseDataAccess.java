package com.shale.data.security.ai;

import com.shale.core.dto.MinimizedCaseOverview;
import com.shale.core.dto.MinimizedCasePage;
import com.shale.core.security.ai.AiScope;
import com.shale.data.util.UtcJdbcTimestamp;
import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.ToIntFunction;
import static com.shale.data.security.ai.IntegrationAccessAudit.*;

/** Confined SQL boundary: no Case DAO/port or ordinary runtime identity is available here.
 * SQL owns all transactions; Java drains completion and closes before encoding/handoff. */
public final class AiCaseDataAccess implements IntegrationCaseReader {
    private static final Semaphore ACTIVE = new Semaphore(5);
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AiCaseDataAccess.class);
    private final DataSource data;
    private final java.util.function.LongFunction<DataSource> cleanup;
    private final AiCaseDataRequest request;
    private final java.util.function.LongSupplier monotonic;
    public AiCaseDataAccess(DataSource data, DataSource cleanup, AiCaseDataRequest request) {
        this(data,until->cleanup,request,System::nanoTime);
    }
    public AiCaseDataAccess(DataSource data,java.util.function.LongFunction<DataSource> cleanup,AiCaseDataRequest request) {
        this(data,cleanup,request,System::nanoTime);
    }
    AiCaseDataAccess(DataSource data,DataSource cleanup,AiCaseDataRequest request,java.util.function.LongSupplier monotonic) {
        this(data,until->cleanup,request,monotonic);
    }
    private AiCaseDataAccess(DataSource data,java.util.function.LongFunction<DataSource> cleanup,AiCaseDataRequest request,java.util.function.LongSupplier monotonic) {
        this.data=Objects.requireNonNull(data);this.cleanup=Objects.requireNonNull(cleanup);this.request=Objects.requireNonNull(request);this.monotonic=Objects.requireNonNull(monotonic);
    }

    public static AiCaseDataRequest authenticate(DataSource data, String token, String issuer, String resource, long admissionDeadline) {
        byte[] digest;
        try { digest=AiOAuthCredentialStore.digest(token); } catch(RuntimeException invalid) { throw denied(); }
        ProofDigest proof=new ProofDigest(digest);
        Arrays.fill(digest,(byte)0);
        boolean retained=false;
        try {
            long remaining=admissionDeadline-System.nanoTime();
            if(remaining<=0 || Thread.currentThread().isInterrupted()) throw denied();
            UUID invocation=UUID.randomUUID(),correlation=UUID.randomUUID();
            var contexts=new ArrayList<VerifiedIntegrationContext>();
            rpc(data,proof,invocation,correlation,"AiDataAuthenticateV1",5,(index,rows)-> {
                while(rows.next()) {
                    if(!invocation.equals(AiSql.uuid(rows,"InvocationId")) || !correlation.equals(AiSql.uuid(rows,"CorrelationId"))
                            || !issuer.equals(rows.getString("Issuer")) || !resource.equals(rows.getString("Resource"))) throw denied();
                    contexts.add(new VerifiedIntegrationContext(rows.getInt("ShaleClientId"),rows.getInt("UserId"),AiSql.uuid(rows,"IntegrationId"),AiSql.uuid(rows,"GrantId"),AiSql.uuid(rows,"SessionId"),
                            AiScope.fromMask(rows.getInt("ScopeMask")),null,rows.getString("ClientId"),nullableUuid(rows,"KeyId"),rows.getLong("AccessCredentialId"),issuer,resource,
                            rows.getLong("CapabilityRevision"),rows.getLong("PolicyRevision"),rows.getLong("IntegrationRevision"),rows.getLong("GrantRevision"),AiSql.instant(rows,"Deadline"),correlation,invocation));
                }
            },Instant.now().plusNanos(Math.min(10_000_000_000L,remaining)));
            if(contexts.size()!=1 || System.nanoTime()>=admissionDeadline || Thread.currentThread().isInterrupted()) throw denied();
            var result=new AiCaseDataRequest(contexts.getFirst(),proof);retained=true;return result;
        } catch(Exception unavailable) { throw denied(); }
        finally { if(!retained)proof.close(); }
    }
    @Override public DisclosureBuffer search(IntegrationInvocation i,String query,Integer page,ToIntFunction<DisclosureBuffer> bytes) {
        return execute(i,query,page,null,bytes);
    }
    @Override public DisclosureBuffer overview(IntegrationInvocation i,Long id,ToIntFunction<DisclosureBuffer> bytes) {
        return execute(i,null,null,id,bytes);
    }
    private DisclosureBuffer execute(IntegrationInvocation i,String query,Integer page,Long id,ToIntFunction<DisclosureBuffer> envelope) {
        try(var use=request.retain()) { return executeRetained(i,query,page,id,envelope); }
    }
    private DisclosureBuffer executeRetained(IntegrationInvocation i,String query,Integer page,Long id,ToIntFunction<DisclosureBuffer> envelope) {
        requireBinding(i);
        boolean active=false;
        Reason phase=Reason.AUTHORITY;
        Integer safePage=page!=null && page>=0 && page<=9?page:null;
        Integer requested=id!=null && id>0 && id<=Integer.MAX_VALUE?id.intValue():null;
        try {
            i.check();
            if(!i.authority().effectiveScopes().contains(i.operation().scope())) throw failure(Outcome.DENIED,Reason.AUTHORITY);
            if(i.operation()==IntegrationInvocation.Operation.SEARCH) {
                if(query==null || query.length()>200 || query.codePointCount(0,query.length())>100 || query.strip().length()<3 || query.strip().length()>100
                        || query.codePoints().anyMatch(cp->cp>=0xd800&&cp<=0xdfff) || query.strip().codePoints().filter(Character::isLetterOrDigit).count()<3 || safePage==null)
                    throw failure(Outcome.INVALID_INPUT,Reason.INPUT);
                query=query.strip();
            } else if(requested==null) throw failure(Outcome.INVALID_INPUT,Reason.INPUT);
            active=ACTIVE.tryAcquire();if(!active)throw failure(Outcome.THROTTLED,Reason.QUOTA);
            var rows=new ArrayList<MinimizedCaseOverview>();
            var receipts=new ArrayList<Receipt>();
            phase=Reason.READ;
            rpc(data,request.proof(),i.invocationId(),i.correlationId(),i.operation()==IntegrationInvocation.Operation.SEARCH?"AiDataSearchCasesV1":"AiDataGetCaseOverviewV1",i.timeoutSeconds(),(index,r)-> {
                if(index==0) while(r.next()) { if(rows.size()>=10)throw failure(Outcome.UNAVAILABLE,Reason.READ);rows.add(map(r)); }
                else if(index==1) while(r.next()) receipts.add(receipt(i,r));
                else throw failure(Outcome.UNAVAILABLE,Reason.READ);
            },i.operation()==IntegrationInvocation.Operation.SEARCH?new Object[]{query,page}:new Object[]{id});
            if(receipts.size()!=1 || i.operation()==IntegrationInvocation.Operation.OVERVIEW && (rows.size()!=1 || rows.getFirst().caseId()!=id)) throw failure(Outcome.UNAVAILABLE,Reason.READ);
            Receipt receipt=receipts.getFirst();
            if(i.operation()==IntegrationInvocation.Operation.SEARCH && (receipt.page()!=page || receipt.size()!=10 || receipt.windowExhausted()!=(page==9&&receipt.hasMore()))) throw failure(Outcome.UNAVAILABLE,Reason.READ);
            i.check(); phase=Reason.SERIALIZATION;
            Object value=i.operation()==IntegrationInvocation.Operation.SEARCH?new MinimizedCasePage(rows,page,10,receipt.hasMore()):rows.getFirst();
            var output=new LimitedOutput(i.operation().maximumBytes());new MinimizedCaseEncoder().encode(value,i.correlationId(),output);
            var buffer=new DisclosureBuffer(output.toByteArray(),i);
            if(buffer.size()==0)throw failure(Outcome.UNAVAILABLE,Reason.SERIALIZATION);
            int full=envelope.applyAsInt(buffer);
            if(full<0 || full>(i.operation()==IntegrationInvocation.Operation.SEARCH?65536:32768))throw failure(Outcome.UNAVAILABLE,Reason.OVERSIZED);
            i.check();phase=Reason.AUTHORITY;
            long started=monotonic.getAsLong();var permits=new ArrayList<Long>();
            rpc(data,request.proof(),i.invocationId(),i.correlationId(),"AiDataAuthorizeCaseHandoffV1",i.timeoutSeconds(),(index,r)-> {
                if(index!=0)throw failure(Outcome.DENIED,Reason.AUTHORITY);
                while(r.next()) {
                    verifyIds(i,r);
                    if((r.getInt("ScopeMask")&i.operation().scope().bit())==0)throw failure(Outcome.DENIED,Reason.AUTHORITY);
                    permits.add(r.getLong("RemainingMillis"));
                }
            });
            // Query, COMMIT acknowledgment, drainage and successful close all consume this permit.
            if(permits.size()!=1 || permits.getFirst()<=0 || permits.getFirst()>10_000
                    || monotonic.getAsLong()-started>=permits.getFirst()*1_000_000L)throw failure(Outcome.UNAVAILABLE,Reason.DEADLINE);
            i.check();return buffer;
        } catch(Exception fault) {
            var safe=classify(fault,phase);
            cleanup(i,safePage,requested,safe);throw safe;
        } finally {
            // Successful handoff already released quota atomically; no SQL may consume its permit here.
            if(active)ACTIVE.release();
        }
    }
    private void cleanup(IntegrationInvocation i,Integer page,Integer requested,IntegrationReadException fault) {
        boolean interrupted=Thread.interrupted();long until=System.nanoTime()+3_000_000_000L;
        try {
            DataSource bounded=cleanup.apply(until);
            try { rpc(bounded,request.proof(),i.invocationId(),i.correlationId(),"AiDataRecordCaseFailureV1",3,(n,r)->{while(r.next()){}},
                    i.operation().tool(),fault.outcome().name(),fault.reason().name(),nullable(page,Types.INTEGER),nullable(requested,Types.BIGINT)); }
            catch(Exception unavailable) { LOG.error("INTEGRATION_ACCESS_AUDIT_OUTAGE"); }
            if(System.nanoTime()<until) try { rpc(bounded,request.proof(),i.invocationId(),i.correlationId(),"AiDataFinishCaseQuotaV1",Math.max(1,(int)((until-System.nanoTime())/1_000_000_000L)),(n,r)->{while(r.next()){} }); }
            catch(Exception unavailable) { LOG.warn("INTEGRATION_QUOTA_RELEASE_UNAVAILABLE"); }
        } finally { if(interrupted)Thread.currentThread().interrupt(); }
    }
    private void requireBinding(IntegrationInvocation i) {
        if(i.authority()!=request.authority() || !Objects.equals(i.invocationId(),request.authority().invocationId()) || !Objects.equals(i.correlationId(),request.authority().correlationId())) throw denied();
    }
    private record Receipt(int page,int size,boolean hasMore,boolean windowExhausted) {}
    private static Receipt receipt(IntegrationInvocation i,ResultSet r)throws SQLException { verifyIds(i,r);if(r.getLong("ReadEventId")<=0)throw denied();return new Receipt(r.getInt("Page"),r.getInt("Size"),r.getBoolean("HasMore"),r.getBoolean("WindowExhausted")); }
    private static void verifyIds(IntegrationInvocation i,ResultSet r)throws SQLException { if(!i.invocationId().equals(AiSql.uuid(r,"InvocationId")) || !i.correlationId().equals(AiSql.uuid(r,"CorrelationId")))throw denied(); }
    private static UUID nullableUuid(ResultSet r,String key)throws SQLException {String s=r.getString(key);return s==null?null:UUID.fromString(s);}
    private static Integer integer(ResultSet r,String key)throws SQLException {int n=r.getInt(key);return r.wasNull()?null:n;}
    private static MinimizedCaseOverview map(ResultSet r)throws SQLException {
        Integer status=integer(r,"StatusId"),area=integer(r,"PracticeAreaId"),attorney=integer(r,"ResponsibleAttorneyId"),assistant=integer(r,"PrimaryLegalAssistantId");
        String color=r.getString("StatusColor");if(color!=null&&!color.matches("#[0-9a-fA-F]{6}"))color=null;
        Timestamp updated=r.getTimestamp("UpdatedAt");
        return new MinimizedCaseOverview(r.getLong("Id"),r.getString("CaseNumber"),r.getString("Name"),
                status==null?null:new MinimizedCaseOverview.CaseReadStatus(status,r.getString("StatusName"),color),
                area==null?null:new MinimizedCaseOverview.CaseReadPracticeArea(area,r.getString("PracticeAreaName")),
                attorney==null?null:new MinimizedCaseOverview.CaseReadUser(attorney,r.getString("ResponsibleAttorneyName")),
                assistant==null?null:new MinimizedCaseOverview.CaseReadUser(assistant,r.getString("PrimaryLegalAssistantName")),updated==null?null:updated.toLocalDateTime().toString());
    }
    @FunctionalInterface private interface Rows { void read(int index,ResultSet result)throws SQLException; }
    private static void rpc(DataSource source,ProofDigest proof,UUID invocation,UUID correlation,String procedure,int timeout,Rows reader,Object... args)throws SQLException {
        Connection c=source.getConnection();
        Throwable primary=null;
        try {
                if(!c.getAutoCommit())throw new SQLException("Fresh AI lease required.");
                String markers=String.join(",",Collections.nCopies(args.length+3,"?"));
                try(CallableStatement p=c.prepareCall("{call dbo."+procedure+"("+markers+")}")) {
                    p.setQueryTimeout(Math.max(1,Math.min(5,timeout)));p.setObject(2,invocation);p.setObject(3,correlation);
                    for(int n=0;n<args.length;n++) {
                        Object value=args[n];int index=n+4;
                        if(value instanceof AiSql.TypedNull nil)p.setNull(index,nil.sqlType());
                        else if(value instanceof Instant instant)UtcJdbcTimestamp.set(p,index,instant);
                        else p.setObject(index,value);
                    }
                    proof.withBinding(p,1,()-> {
                        boolean result=p.execute();int index=0;
                        while(true) {
                            if(result)try(ResultSet r=p.getResultSet()){reader.read(index++,r);}
                            else if(p.getUpdateCount()==-1)break;
                            result=p.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
                        }
                        return null;
                    });
                }
        } catch(SQLException|RuntimeException fault) {
            primary=fault;
            // Failed restoration, uncertain completion and poisoned context never reenter the pool.
            try { c.abort(Runnable::run); } catch(SQLException discard) { fault.addSuppressed(discard); }
            if(fault instanceof SQLException sql && sql.getSQLState()!=null && sql.getSQLState().startsWith("08"))
                throw failure(Outcome.UNAVAILABLE,Reason.COMMIT);
            throw fault;
        } finally {
            try { c.close(); }
            catch(SQLException close) {
                try { c.abort(Runnable::run); } catch(SQLException discard) { close.addSuppressed(discard); }
                if(primary!=null)primary.addSuppressed(close);else throw failure(Outcome.UNAVAILABLE,Reason.CLOSE);
            }
        }
    }
    private static Object nullable(Object value,int type){return AiSql.nullable(value,type);}
    private static SecurityException denied(){return new SecurityException("Integration authentication failed.");}
    private static IntegrationReadException failure(Outcome o,Reason r){return new IntegrationReadException(o,r);}
    private static IntegrationReadException classify(Exception e,Reason phase){
        if(e instanceof IntegrationReadException safe)return safe;
        if(Thread.currentThread().isInterrupted())return failure(Outcome.CANCELLED,Reason.CANCELLED);
        if(e instanceof SQLTimeoutException)return failure(Outcome.UNAVAILABLE,Reason.DEADLINE);
        if(e instanceof SQLException sql)return switch(sql.getErrorCode()){
            case 59101 -> failure(Outcome.DENIED,Reason.AUTHORITY);case 59102 -> failure(Outcome.INVALID_INPUT,Reason.INPUT);
            case 59103 -> failure(Outcome.UNAVAILABLE,Reason.AUDIT);case 59104 -> failure(Outcome.UNAVAILABLE,Reason.CASE_UNAVAILABLE);
            case 59105 -> failure(Outcome.THROTTLED,Reason.QUOTA);case 59106 -> failure(Outcome.UNAVAILABLE,Reason.OVERSIZED);
            case 59107 -> failure(Outcome.UNAVAILABLE,Reason.READ);case 59108 -> failure(Outcome.UNAVAILABLE,Reason.COMMIT);case 59109 -> failure(Outcome.UNAVAILABLE,Reason.CLOSE);
            case 1222 -> failure(Outcome.UNAVAILABLE,Reason.DEADLINE);
            default -> failure(Outcome.UNAVAILABLE,phase);
        };
        return failure(Outcome.UNAVAILABLE,phase);
    }
    private static final class LimitedOutput extends ByteArrayOutputStream {
        private final int maximum;LimitedOutput(int maximum){this.maximum=maximum;}
        private void check(int n){if(n<0||count>maximum-n)throw failure(Outcome.UNAVAILABLE,Reason.OVERSIZED);}
        @Override public synchronized void write(int b){check(1);super.write(b);}
        @Override public synchronized void write(byte[] b,int off,int len){check(len);super.write(b,off,len);}
    }
}
