package com.shale.data.security.ai;

import com.shale.core.dto.*;
import com.shale.core.security.ai.AiScope;
import com.shale.core.service.*;
import javax.sql.DataSource;
import java.io.*;
import java.sql.*;
import java.util.*;
import static com.shale.data.security.ai.IntegrationAccessAudit.*;

/** Internal only: no bean, route, transport, token parser, servlet or ordinary-user fallback.
 * One existing shared operation; required audits commit and lease closes before encoding.
 * SQL finalizes authorization before exactly one completed immutable buffer is returned. */
public final class IntegrationCaseReadService {
    @FunctionalInterface interface Encoder {
        void encode(Object minimizedResult, UUID correlationId, OutputStream destination) throws IOException;
    }
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(IntegrationCaseReadService.class);
    private static final java.util.concurrent.Semaphore PROCESS_READS=new java.util.concurrent.Semaphore(5);
    private final DataSource runtime;
    private final CaseServicePort cases;
    private final Encoder encoder;
    private final boolean enabled;
    private final IntegrationAccessAudit audit=new IntegrationAccessAudit();

    public IntegrationCaseReadService(DataSource runtime, CaseServicePort cases) {
        this(runtime,cases,false);
    }
    public IntegrationCaseReadService(DataSource runtime, CaseServicePort cases, boolean enabled) {
        this(runtime,cases,new MinimizedCaseEncoder(),enabled);
    }
    IntegrationCaseReadService(DataSource runtime, CaseServicePort cases, Encoder encoder) {
        this(runtime,cases,encoder,false);
    }
    IntegrationCaseReadService(DataSource runtime, CaseServicePort cases, Encoder encoder, boolean enabled) {
        this.runtime=Objects.requireNonNull(runtime); this.cases=Objects.requireNonNull(cases);
        this.encoder=Objects.requireNonNull(encoder); this.enabled=enabled;
    }
    public DisclosureBuffer search(VerifiedIntegrationContext verified, String query, int page) {
        return execute(IntegrationInvocation.create(verified,IntegrationInvocation.Operation.SEARCH),query,page,null);
    }
    public DisclosureBuffer overview(VerifiedIntegrationContext verified, long caseId) {
        return execute(IntegrationInvocation.create(verified,IntegrationInvocation.Operation.OVERVIEW),null,null,caseId);
    }
    private DisclosureBuffer execute(IntegrationInvocation i, String query, Integer page, Long id) {
        Integer safePage=page!=null && page>=0 && page<=9 ? page : null;
        Integer requested=id!=null && id>0 && id<=Integer.MAX_VALUE ? id.intValue() : null;
        boolean reserved=false, process=false, committed=false, commitAttempted=false;
        Reason phase=Reason.AUTHORITY;
        try {
            i.check();
            if (!enabled || !i.authority().effectiveScopes().contains(i.operation().scope())) throw failure(Outcome.DENIED,Reason.AUTHORITY);
            if(i.operation()==IntegrationInvocation.Operation.SEARCH) {
                if(query==null || query.length()>100 || query.strip().length()<3 || query.strip().codePoints().filter(Character::isLetterOrDigit).count()<3 || safePage==null)
                    throw failure(Outcome.INVALID_INPUT,Reason.INPUT);
                query=query.strip();
            } else if(requested==null) throw failure(Outcome.INVALID_INPUT,Reason.INPUT);
            process=PROCESS_READS.tryAcquire();
            if(!process) throw failure(Outcome.THROTTLED,Reason.QUOTA);
            // Independent durable admission: a later read rollback never refunds consumed credit.
            phase=Reason.QUOTA;
            try(Connection c=lease(i)) {
                reserved=AiSql.one(AiSql.call(c,"AiReserveQuotaV1",r->r.getBoolean("Admitted"),
                        i.authority().delegatedSessionId(),i.operation().scope().bit(),i.reservationId()));
            }
            if(!reserved) throw failure(Outcome.THROTTLED,Reason.QUOTA);
            i.check();
            Object result;
            try(Connection c=lease(i)) {
                begin(c,i);
                try {
                    phase=Reason.AUTHORITY; gate(c,i);
                    i.check(); phase=Reason.READ;
                    List<Integer> returned;
                    if(i.operation()==IntegrationInvocation.Operation.SEARCH) {
                        MinimizedCasePage value=cases.searchMinimizedCases(c,query,i.authority().shaleClientId(),i.authority().actingUserId(),page,10);
                        if(value.page()!=page || value.size()!=10) throw failure(Outcome.UNAVAILABLE,Reason.READ);
                        result=value; returned=value.items().stream().map(v->Math.toIntExact(v.caseId())).toList();
                    } else {
                        phase=Reason.AUDIT;
                        long before=phiCount(c,requested);
                        phase=Reason.READ;
                        result=cases.readMinimizedCaseOverview(c,id,i.authority().shaleClientId(),i.authority().actingUserId())
                                .orElseThrow(()->failure(Outcome.UNAVAILABLE,Reason.CASE_UNAVAILABLE));
                        if(((MinimizedCaseOverview)result).caseId()!=id) throw failure(Outcome.UNAVAILABLE,Reason.READ);
                        phase=Reason.AUDIT;
                        if(phiCount(c,requested)!=Math.addExact(before,1)) throw failure(Outcome.UNAVAILABLE,Reason.AUDIT);
                        returned=List.of(requested);
                    }
                    i.check(); phase=Reason.AUDIT;
                    audit.append(c,i,Stage.READ,Outcome.ALLOWED_READ,Reason.NONE,safePage,returned,null);
                    phase=Reason.AUTHORITY; gate(c,i); i.check();
                    phase=Reason.COMMIT; commitAttempted=true; c.commit(); committed=true; c.setAutoCommit(true);
                } catch(RuntimeException | SQLException failure) {
                    try { c.rollback(); } catch(SQLException ignored) { /* Result remains withheld. */ }
                    throw failure;
                }
                phase=Reason.CLOSE;
            }
            i.check(); phase=Reason.SERIALIZATION;
            var out=new BoundedOutput(i.operation().maximumBytes());
            encoder.encode(result,i.correlationId(),out);
            i.check();
            var buffer=new DisclosureBuffer(out.toByteArray(),i);
            if(buffer.size()==0) throw failure(Outcome.UNAVAILABLE,Reason.SERIALIZATION);
            phase=Reason.AUTHORITY;
            // Short final gate: DB UTC after all lock waits. Commit/close before any internal handoff.
            GatePermit permit;
            try(Connection c=lease(i)) {
                begin(c,i);
                try { permit=gate(c,i,true); i.check(); c.commit(); c.setAutoCommit(true); }
                catch(RuntimeException | SQLException failure) { try { c.rollback(); } catch(SQLException ignored) {} throw failure; }
            }
            reserved=false;
            i.check(); permit.check();
            return buffer;
        } catch(Exception fault) {
            IntegrationReadException safe=classify(fault,phase);
            appendFailure(i,committed||commitAttempted?Stage.TERMINAL:Stage.READ,safePage,requested,safe);
            throw safe;
        } finally {
            if(reserved) release(i);
            if(process) PROCESS_READS.release();
        }
    }
    private Connection lease(IntegrationInvocation i)throws SQLException {
        i.check();
        Connection c=IntegrationRuntimeLease.open(runtime,i.authority().shaleClientId(),i.authority().actingUserId(),i.timeoutSeconds());
        try { i.check(); if(!c.getAutoCommit()) throw new SQLException("Fresh integration lease required."); return c; }
        catch(RuntimeException | SQLException failure) { try { c.close(); } catch(SQLException ignored) {} throw failure; }
    }
    private static void begin(Connection c,IntegrationInvocation i)throws SQLException {
        c.setAutoCommit(false);
        try(var s=c.createStatement()) {
            s.setQueryTimeout(i.timeoutSeconds());
            s.execute("SET IMPLICIT_TRANSACTIONS OFF; IF @@TRANCOUNT<>0 THROW 59002,'Fresh transaction required.',1; BEGIN TRANSACTION;");
        }
    }
    private static long phiCount(Connection c,int id)throws SQLException {
        return AiSql.one(AiSql.call(c,"AiOverviewPhiCountV1",r->r.getLong("AuditCount"),id));
    }
    private static void gate(Connection c,IntegrationInvocation i)throws SQLException { gate(c,i,false); }
    private static GatePermit gate(Connection c,IntegrationInvocation i,boolean handoff)throws SQLException {
        var a=i.authority();
        long started=System.nanoTime();
        long remaining=AiSql.one(AiSql.call(c,"AiGateCaseReadV1",r->{
            if((r.getInt("ScopeMask") & i.operation().scope().bit())==0)throw failure(Outcome.DENIED,Reason.AUTHORITY);
            return r.getLong("RemainingMillis");
        },
                a.credentialId(),a.integrationId(),a.grantId(),a.delegatedSessionId(),a.oauthClientId(),
                AiSql.nullable(a.authenticatingKeyId(),Types.VARCHAR),a.issuer(),a.resource(),AiScope.mask(a.effectiveScopes()),
                i.operation().scope().bit(),a.capabilityRevision(),a.policyRevision(),a.integrationRevision(),a.grantRevision(),i.reservationId(),i.deadline(),handoff));
        if(remaining<=0)throw failure(Outcome.UNAVAILABLE,Reason.DEADLINE);
        return new GatePermit(started,Math.min(10_000,remaining)*1_000_000L);
    }
    /** Conservative DB-time receipt, specific to this gate only. Commit/close time cannot extend it. */
    private record GatePermit(long started,long remainingNanos) {
        void check() { if(System.nanoTime()-started>=remainingNanos)throw failure(Outcome.UNAVAILABLE,Reason.DEADLINE); }
    }
    private void appendFailure(IntegrationInvocation i,Stage stage,Integer page,Integer requested,IntegrationReadException fault) {
        // Deliberately independent of cancellation/disabled state and the expired invocation deadline.
        boolean interrupted=Thread.interrupted();
        try(Connection c=auditLease(i)) {
            c.setAutoCommit(false);
            try {
                try(var s=c.createStatement()) { s.setQueryTimeout(3); s.execute("SET IMPLICIT_TRANSACTIONS OFF; BEGIN TRANSACTION;"); }
                var a=i.authority();
                AiSql.call(c,"AiAccountCaseAttemptV1",r->true,i.invocationId(),a.integrationId(),a.grantId(),a.delegatedSessionId(),a.oauthClientId(),i.operation().scope().bit());
                audit.append(c,i,stage,fault.outcome(),fault.reason(),page,List.of(),requested);
                c.commit();
            } catch(Exception failure) { try { c.rollback(); } catch(SQLException ignored) {} throw failure; }
        } catch(Exception outage) { LOG.error("INTEGRATION_ACCESS_AUDIT_OUTAGE"); }
        finally { if(interrupted)Thread.currentThread().interrupt(); }
    }
    private Connection auditLease(IntegrationInvocation i)throws SQLException {
        return IntegrationRuntimeLease.open(runtime,i.authority().shaleClientId(),i.authority().actingUserId(),3);
    }
    private void release(IntegrationInvocation i) {
        boolean interrupted=Thread.interrupted();
        try(Connection c=auditLease(i)) {
            c.setAutoCommit(false);
            try {
                try(var s=c.createStatement()) { s.setQueryTimeout(3); s.execute("SET IMPLICIT_TRANSACTIONS OFF; BEGIN TRANSACTION;"); }
                AiSql.call(c,"AiFinishCaseQuotaV1",r->true,i.reservationId()); c.commit();
            } catch(Exception failure) { try { c.rollback(); } catch(SQLException ignored) {} throw failure; }
        } catch(Exception unavailable) { LOG.warn("INTEGRATION_QUOTA_RELEASE_UNAVAILABLE"); }
        finally { if(interrupted)Thread.currentThread().interrupt(); }
    }
    private static IntegrationReadException classify(Exception fault,Reason phase) {
        if(fault instanceof IntegrationReadException safe) return safe;
        if(Thread.currentThread().isInterrupted()) return failure(Outcome.CANCELLED,Reason.CANCELLED);
        if(fault instanceof CaseReadException read) {
            if(read.kind()==CaseReadException.Kind.DENIED) return failure(Outcome.DENIED,Reason.AUTHORITY);
            if(read.kind()==CaseReadException.Kind.AUDIT_UNAVAILABLE) return failure(Outcome.UNAVAILABLE,Reason.AUDIT);
        }
        if(fault instanceof SQLException sql && Set.of(58901,58403,58404,58408).contains(sql.getErrorCode())) return failure(Outcome.DENIED,Reason.AUTHORITY);
        return failure(Outcome.UNAVAILABLE,phase);
    }
    private static IntegrationReadException failure(Outcome outcome,Reason reason) { return new IntegrationReadException(outcome,reason); }
    private static final class BoundedOutput extends ByteArrayOutputStream {
        private final int maximum;
        BoundedOutput(int maximum) { super(Math.min(maximum,1024)); this.maximum=maximum; }
        private void capacity(int n) { if(n<0 || count>maximum-n) throw failure(Outcome.UNAVAILABLE,Reason.OVERSIZED); }
        @Override public synchronized void write(int b) { capacity(1); super.write(b); }
        @Override public synchronized void write(byte[] bytes,int offset,int length) { capacity(length); super.write(bytes,offset,length); }
    }
}
