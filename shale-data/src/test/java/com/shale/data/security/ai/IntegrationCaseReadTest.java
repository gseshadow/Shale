package com.shale.data.security.ai;

import com.shale.core.dto.*;
import com.shale.core.security.ai.AiScope;
import com.shale.core.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic JDBC fault injection, not SQL Server/RLS evidence. Real persistence has separate opt-in tests. */
class IntegrationCaseReadTest {
    static VerifiedIntegrationContext context(int tenant,int user) {
        return new VerifiedIntegrationContext(tenant,user,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),AiScope.fromMask(3),null,
                "client",null,1L,"https://shale.test/oauth/mcp","https://shale.test/mcp",1,1,1,1,
                Instant.now().plusSeconds(60),UUID.randomUUID(),null);
    }
    static MinimizedCaseOverview item(long id) { return new MinimizedCaseOverview(id,"N","SECRET_CASE",null,null,null,null,null); }

    @Test void successfulReadsCommitAndCloseBeforeEncodingAndOneHandoff() {
        for(boolean overview:List.of(false,true)) {
            Fake f=new Fake(""); var ctx=context(7,72);
            var service=f.service((value,correlation,out)->{
                assertTrue(f.events.contains("2:commit")); assertTrue(f.events.contains("2:close"));
                assertEquals(1,f.durableAllowed); assertEquals(overview?1:0,f.durablePhi);
                new MinimizedCaseEncoder().encode(value,correlation,out);
            });
            DisclosureBuffer buffer=overview?service.overview(ctx,72):service.search(ctx,"secret query",0);
            assertEquals(1,f.reads); assertEquals(1,f.durableAllowed); assertEquals(3,f.gates);
            assertTrue(buffer.size()>0); assertTrue(buffer.bytes().isReadOnly());
            assertEquals("DisclosureBuffer[redacted]",buffer.toString());
            assertThrows(java.nio.ReadOnlyBufferException.class,()->buffer.bytes().put(0,(byte)1));
            assertTrue(f.events.indexOf("3:close")<f.events.size());
            assertEquals(overview?1:10,f.cost); assertEquals(1,f.releases);
        }
    }
    @Test void zeroSearchIsAuditedAndProbeIsNeverAnAuditResource() {
        Fake f=new Fake("EMPTY"); f.service(new MinimizedCaseEncoder()).search(context(7,72),"literal",9);
        var args=f.auditCalls.getFirst(); assertEquals(0,args.get(11));
        for(int n=14;n<=23;n++)assertNull(args.get(n));
        assertEquals(1,f.durableAllowed);
    }
    @Test void semanticUnicodeAndUtf16BoundsCannotBeBypassedByTheAdapter() {
        for(String invalid:List.of("  ab  ","  %_*  ","a💼💼","\ud800abc","x".repeat(101))) {
            Fake f=new Fake("");
            assertThrows(IntegrationReadException.class,()->f.service(new MinimizedCaseEncoder()).search(context(7,72),invalid,0));
            assertEquals(0,f.reads);assertEquals("INVALID_INPUT",f.auditCalls.getFirst().get(9));
        }
        Fake f=new Fake("");assertTrue(f.service(new MinimizedCaseEncoder()).search(context(7,72),"  É漢𐐀  ",1).size()>0);assertEquals(1,f.reads);
        Fake stripped=new Fake("");assertTrue(stripped.service(new MinimizedCaseEncoder()).search(context(7,72)," "+"x".repeat(97)+"𐐀 ",0).size()>0);
    }
    @Test void originalAdmissionDeadlineAndEnvelopeFailureUseExistingFailureAudit() {
        Fake expired=new Fake("");var invocation=IntegrationInvocation.search(context(7,72),System.nanoTime()-1);
        var timeout=assertThrows(IntegrationReadException.class,()->expired.service(new MinimizedCaseEncoder()).search(invocation,"literal",0,b->0));
        assertEquals(IntegrationAccessAudit.Reason.DEADLINE,timeout.reason());assertEquals(0,expired.reads);
        assertEquals("UNAVAILABLE",expired.auditCalls.getFirst().get(9));
        Fake large=new Fake("");
        var failure=assertThrows(IntegrationReadException.class,()->large.service(new MinimizedCaseEncoder()).search(
                IntegrationInvocation.search(context(7,72),System.nanoTime()+9_000_000_000L),"literal",9,b->65537));
        assertEquals(IntegrationAccessAudit.Reason.OVERSIZED,failure.reason());assertEquals(1,large.reads);assertEquals(1,large.durableAllowed);
        assertEquals(2,large.gates,"No final handoff gate for an overlarge envelope");
        assertTrue(large.auditCalls.stream().anyMatch(a->"TERMINAL".equals(a.get(8))));
    }
    @ParameterizedTest @ValueSource(strings={"READ","AUDIT_ZERO","PARENT","CHILD","PHI","PRECOMMIT","COMMIT","LOSTACK","CLOSE","SERIALIZE","OVERSIZE","GATE","HANDOFF","CANCEL_READ","CANCEL_ENCODER","QUOTA"})
    void everyPreHandoffFaultWithholdsAllBytesAndNeverReplays(String fault) {
        Fake f=new Fake(fault);
        var service=f.service((value,correlation,out)->{
            if(fault.equals("SERIALIZE"))throw new java.io.IOException("private exception SQL URL token");
            if(fault.equals("OVERSIZE")) { out.write(new byte[32769]); return; }
            if(fault.equals("CANCEL_ENCODER"))Thread.currentThread().interrupt();
            new MinimizedCaseEncoder().encode(value,correlation,out);
        });
        try {
            var error=assertThrows(IntegrationReadException.class,()->{
                if(fault.equals("PHI"))service.overview(context(7,72),72); else service.search(context(7,72),"SECRET_QUERY",0);
            },fault);
            assertNull(error.getCause()); assertEquals("Integration read unavailable.",error.getMessage());
            assertTrue(f.reads<=1,"No automatic sensitive read replay");
            if(Set.of("LOSTACK","CLOSE","SERIALIZE","OVERSIZE","HANDOFF","CANCEL_ENCODER").contains(fault))
                assertEquals(1,f.durableAllowed,"Committed server-access history survives later failure");
            else assertEquals(0,f.durableAllowed);
            assertTrue(f.auditCalls.stream().anyMatch(a->!"ALLOWED_READ".equals(a.get(9))),"A verified failure attempts a separate bounded outcome");
            for(var args:f.auditCalls)for(Object value:args.values())if(value instanceof String text)
                for(String privateText:List.of("SECRET_CASE","SECRET_QUERY","private exception","http://","access_token","email@"))assertFalse(text.contains(privateText));
        } finally { Thread.interrupted(); }
    }
    @Test void cancellationBeforeReadAndInvalidInputHaveNoProtectedRead() {
        for(String invalid:List.of("","ab","%%%","x".repeat(101))) {
            Fake f=new Fake(""); assertThrows(IntegrationReadException.class,()->f.service(new MinimizedCaseEncoder()).search(context(7,72),invalid,0));
            assertEquals(0,f.reads);assertEquals("INVALID_INPUT",f.auditCalls.getFirst().get(9));
        }
        Fake f=new Fake(""); Thread.currentThread().interrupt();
        try { assertThrows(IntegrationReadException.class,()->f.service(new MinimizedCaseEncoder()).overview(context(7,72),72)); assertEquals(0,f.reads); }
        finally { Thread.interrupted(); }
    }
    @Test void failureAuditOutageDoesNotCreateResultOrLeakError() {
        Fake f=new Fake("FAILURE_AUDIT");
        var error=assertThrows(IntegrationReadException.class,()->f.service(new MinimizedCaseEncoder()).search(context(7,72),"invalid",-1));
        assertEquals(IntegrationAccessAudit.Outcome.INVALID_INPUT,error.outcome());assertEquals(0,f.reads);
    }
    @Test void requestedIdIsOnlySyntacticAndMalformedIdHasNoChild() {
        for(long id:List.of(-1L,0L,2147483648L,123L)) {
            Fake f=new Fake("MISSING"); assertThrows(IntegrationReadException.class,()->f.service(new MinimizedCaseEncoder()).overview(context(7,72),id));
            assertEquals(id==123?123:null,f.auditCalls.getFirst().get(13)); assertEquals(0,f.durableAllowed);
        }
    }
    @Test void explicitOwnersInterleaveWithoutAmbientAuthority()throws Exception {
        Fake f=new Fake(""); var service=f.service(new MinimizedCaseEncoder());
        var owners=List.of(context(7,71),context(7,72),context(8,81),context(7,71));
        for(var owner:owners)assertTrue(service.search(owner,"literal",0).size()>0);
        try(var executor=Executors.newFixedThreadPool(3)) {
            var jobs=new ArrayList<Future<DisclosureBuffer>>();
            for(var owner:owners.subList(0,3))jobs.add(executor.submit(()->service.search(owner,"literal",0)));
            for(var job:jobs)assertTrue(job.get(5,TimeUnit.SECONDS).size()>0);
        }
        assertEquals(7,f.reads);assertEquals(7,f.durableAllowed);
    }
    @Test void invocationCannotUseTrustedSessionControlContextAndIsImmutable() {
        var unverified=new VerifiedIntegrationContext(7,72,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),AiScope.fromMask(3),AiScope.CASES_SEARCH);
        assertThrows(SecurityException.class,()->IntegrationInvocation.create(unverified,IntegrationInvocation.Operation.SEARCH));
        var invocation=IntegrationInvocation.create(context(7,72),IntegrationInvocation.Operation.SEARCH);
        assertEquals(invocation.invocationId(),invocation.reservationId());assertEquals("IntegrationInvocation[redacted]",invocation.toString());
        for(var field:IntegrationInvocation.class.getDeclaredFields())assertTrue(Modifier.isFinal(field.getModifiers()));
    }
    @Test void encoderRejectsUnrepresentableTextAndLocalTimeWithoutPartialHandoff()throws Exception {
        var encoder=new MinimizedCaseEncoder();
        for(var value:List.of(new MinimizedCaseOverview(1,null,"bad\ud800",null,null,null,null,null),
                new MinimizedCaseOverview(1,null,"name",null,null,null,null,"2026-10-23T10:00Z")))
            assertThrows(java.io.IOException.class,()->encoder.encode(value,UUID.randomUUID(),new java.io.ByteArrayOutputStream()));
    }

    @Test void timedOutAcquisitionClosesLateLeaseAndNeverRunsBusinessSql()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var closed=new CountDownLatch(1);
        DataSource source=proxy(DataSource.class,(p,m,a)->{
            if(!m.getName().equals("getConnection"))return zero(m);
            entered.countDown();
            // Model a driver that ignores interruption during acquisition.
            boolean interrupted=false;
            while(release.getCount()>0)try { release.await(); }catch(InterruptedException cancelled){interrupted=true;}
            if(interrupted)Thread.currentThread().interrupt();
            return proxy(Connection.class,(cp,cm,ca)->switch(cm.getName()) {
                case "close"->{closed.countDown();yield null;}
                case "prepareStatement"->{
                    assertTrue(((String)ca[0]).contains("sp_set_session_context"),"Late borrower may only stamp, never read data");
                    yield proxy(PreparedStatement.class,(sp,sm,sa)->zero(sm));
                }
                default->zero(cm);
            });
        });
        try(var executor=Executors.newSingleThreadExecutor()) {
            var task=executor.submit(()->assertThrows(SQLException.class,()->IntegrationRuntimeLease.open(source,7,72,1)));
            assertTrue(entered.await(1,TimeUnit.SECONDS));task.get(2,TimeUnit.SECONDS);
            release.countDown();assertTrue(closed.await(2,TimeUnit.SECONDS),"A late initialized lease must be closed");
        } finally { release.countDown(); }
    }

    static final class Fake {
        final String fault;
        final List<String> events=Collections.synchronizedList(new ArrayList<>());
        final List<Map<Integer,Object>> auditCalls=Collections.synchronizedList(new ArrayList<>());
        int leases,reads,gates,durableAllowed,durablePhi,cost,releases;
        Fake(String fault) { this.fault=fault; }
        synchronized int nextLease() { return ++leases; }
        IntegrationCaseReadService service(IntegrationCaseReadService.Encoder encoder) {
            DataSource ds=proxy(DataSource.class,(p,m,a)->m.getName().equals("getConnection")?connection(nextLease()):zero(m));
            CaseServicePort port=proxy(CaseServicePort.class,(p,m,a)->{
                assertTrue(a[0] instanceof Connection,"Integration must use the explicit transaction overload");
                Connection c=(Connection)a[0]; Lease state=(Lease)Proxy.getInvocationHandler(c);
                assertEquals(state.tenant,a[2]);assertEquals(state.user,a[3]);
                synchronized(this){reads++;} events.add(state.id+":read");
                if(fault.equals("READ"))throw new SQLException("private read exception");
                if(fault.equals("CANCEL_READ"))Thread.currentThread().interrupt();
                if(m.getName().equals("searchMinimizedCases"))return new MinimizedCasePage(fault.equals("EMPTY")?List.of():List.of(item(state.user)),(int)a[4],10,false);
                if(fault.equals("MISSING"))return Optional.empty();
                if(fault.equals("PHI"))throw new CaseReadException(CaseReadException.Kind.AUDIT_UNAVAILABLE);
                state.phi++;return Optional.of(item((long)a[1]));
            });
            return new IntegrationCaseReadService(ds,port,encoder,true);
        }
        Connection connection(int id) { return proxy(Connection.class,new Lease(id)); }
        final class Lease implements InvocationHandler {
            final int id; int tenant,user,allowed,phi,gateCalls;boolean auto=true;
            Lease(int id){this.id=id;}
            public Object invoke(Object p,Method m,Object[] a)throws Throwable {
                return switch(m.getName()) {
                    case "getAutoCommit"->auto;
                    case "setAutoCommit"->{auto=(boolean)a[0];yield null;}
                    case "prepareStatement","prepareCall"->statement((String)a[0]);
                    case "createStatement"->statement("begin");
                    case "commit"->{
                        events.add(id+":commit");
                        if(id==2&&fault.equals("COMMIT"))throw new SQLException("commit unavailable");
                        synchronized(Fake.this){durableAllowed+=allowed;durablePhi+=phi;}allowed=0;phi=0;
                        if(id==2&&fault.equals("LOSTACK"))throw new SQLException("lost acknowledgment");yield null;
                    }
                    case "rollback"->{allowed=0;phi=0;events.add(id+":rollback");yield null;}
                    case "close"->{events.add(id+":close");if(id==2&&fault.equals("CLOSE"))throw new SQLException("close unavailable");yield null;}
                    default->zero(m);
                };
            }
            Object statement(String sql) {
                Map<Integer,Object> args=new HashMap<>(); Map<String,Object> row=new HashMap<>();boolean[] done={false};
                return proxy(CallableStatement.class,(p,m,a)->{
                    String name=m.getName();
                    if(name.startsWith("set")&&a!=null&&a.length>=2&&a[0] instanceof Integer index){args.put(index,name.equals("setNull")?null:a[1]);return null;}
                    return switch(name) {
                        case "execute"->{
                            if(sql.contains("sp_set_session_context")){if(sql.contains("ShaleClientId"))tenant=(int)args.get(1);else user=(int)args.get(1);yield false;}
                            if(sql.contains("AiOverviewPhiCountV1")){row.put("AuditCount",(long)phi);}
                            if(sql.contains("AiReserveQuotaV1")){row.put("Admitted",!fault.equals("QUOTA"));cost=((Number)args.get(2)).intValue()==1?10:1;}
                            if(sql.contains("AiGateCaseReadV1")){
                                synchronized(Fake.this){gates++;}gateCalls++;
                                if(fault.equals("GATE")||(fault.equals("PRECOMMIT")&&gateCalls==2)||(fault.equals("HANDOFF")&&id==3))throw new SQLException("live denial","",58901);
                                if(Boolean.TRUE.equals(args.get(17))) {synchronized(Fake.this){releases++;}}
                                row.put("ScopeMask",3); row.put("RemainingMillis",10_000L);
                            }
                            if(sql.contains("AiAppendCaseAccessV1")){
                                auditCalls.add(new HashMap<>(args));boolean success="ALLOWED_READ".equals(args.get(9));
                                if((success&&Set.of("PARENT","CHILD").contains(fault))||(!success&&fault.equals("FAILURE_AUDIT")))throw new SQLException("private audit outage");
                                row.put("AppendedCount",success&&fault.equals("AUDIT_ZERO")?0:1);
                                if(success&&!fault.equals("AUDIT_ZERO"))allowed++;
                            }
                            if(sql.contains("AiFinishCaseQuotaV1")){synchronized(Fake.this){releases++;}}
                            yield !row.isEmpty();
                        }
                        case "getUpdateCount"->-1;
                        case "getMoreResults"->false;
                        case "getResultSet"->proxy(ResultSet.class,(rp,rm,ra)->switch(rm.getName()) {
                            case "next"->{boolean next=!done[0];done[0]=true;yield next;}
                            case "getInt"->((Number)row.get(ra[0])).intValue();
                            case "getLong"->((Number)row.get(ra[0])).longValue();
                            case "getBoolean"->row.get(ra[0]);
                            default->zero(rm);
                        });
                        default->zero(m);
                    };
                });
            }
        }
    }
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,InvocationHandler h){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h);}
    static Object zero(Method m){if(!m.getReturnType().isPrimitive()||m.getReturnType()==void.class)return null;if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==long.class)return 0L;return 0;}
}
