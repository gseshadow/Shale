package com.shale.data.security.ai;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;

/** JDBC fault evidence supplements, and never substitutes for, the restricted-login SQL proofs. */
class AiCaseDataAccessTest {
    @Test void commitDrainAndCloseConsumeHandoffLifetimeAndNoCleanupRunsAfterSuccess()throws Exception {
        var f=new Fixture();
        var result=f.run();assertTrue(result.size()>0);assertEquals(List.of("read","read-close","encode","gate","gate-close"),f.events);
        assertEquals(0,f.cleanups.get());
        f=new Fixture();f.elapsedOnGateClose=5_000_000L;f.remaining=5;
        assertEquals(IntegrationAccessAudit.Reason.DEADLINE,assertThrows(IntegrationReadException.class,f::run).reason());
        assertEquals(2,f.cleanups.get());
    }
    @Test void closeFailureAndUncertainCommitWithholdAllOutputAndRetainProofForCleanup()throws Exception {
        for(String fault:List.of("close","uncertain","cancel")) {
            var f=new Fixture();f.fault=fault;
            var failure=assertThrows(IntegrationReadException.class,f::run);
            assertEquals(switch(fault){case "close"->IntegrationAccessAudit.Reason.CLOSE;case "uncertain"->IntegrationAccessAudit.Reason.COMMIT;default->IntegrationAccessAudit.Reason.CANCELLED;},failure.reason());
            assertEquals(2,f.cleanups.get(),"Both bounded failure and quota release retain exact proof");
            if(fault.equals("cancel")){assertTrue(Thread.interrupted());}
            assertFalse(f.events.contains("encode"));
        }
    }
    private static final class Fixture {
        final DataSource data,cleanup;
        final AtomicLong clock=new AtomicLong();final AtomicInteger cleanups=new AtomicInteger();
        final List<String> events=new ArrayList<>();final AiCaseDataRequest request;
        String fault="";long remaining=500,elapsedOnGateClose=0;
        Fixture() {
            var c=IntegrationCaseReadTest.context(7,72);
            var authority=new VerifiedIntegrationContext(7,72,c.integrationId(),c.grantId(),c.delegatedSessionId(),c.effectiveScopes(),null,c.oauthClientId(),null,c.credentialId(),c.issuer(),c.resource(),1,1,1,1,Instant.now().plusSeconds(30),UUID.randomUUID(),UUID.randomUUID());
            byte[] proof=new byte[32];Arrays.fill(proof,(byte)19);request=new AiCaseDataRequest(authority,new ProofDigest(proof));
            AtomicInteger sequence=new AtomicInteger();
            data=proxy(DataSource.class,(p,m,a)->m.getName().equals("getConnection")?lease(sequence.getAndIncrement()==0?"read":"gate"):zero(m));
            cleanup=proxy(DataSource.class,(p,m,a)->m.getName().equals("getConnection")?lease("cleanup"):zero(m));
        }
        DisclosureBuffer run(){return new AiCaseDataAccess(data,cleanup,request,clock::get).overview(IntegrationInvocation.overview(request.authority(),System.nanoTime()+8_000_000_000L),100L,b->{events.add("encode");return b.size();});}
        Connection lease(String kind) {
            AtomicInteger result=new AtomicInteger();
            CallableStatement statement=proxy(CallableStatement.class,(p,m,a)->switch(m.getName()) {
                case "setBytes"->{assertEquals(19,((byte[])a[1])[0],"Cleanup retains exact proof");yield null;}
                case "execute"->{
                    if(kind.equals("cleanup")){cleanups.incrementAndGet();yield false;}
                    events.add(kind);
                    if(kind.equals("read")&&fault.equals("uncertain"))throw new SQLException("synthetic","08006");
                    if(kind.equals("read")&&fault.equals("cancel")){request.close();Thread.currentThread().interrupt();}
                    yield true;
                }
                case "getUpdateCount"->-1;
                case "getMoreResults"->kind.equals("read")&&result.incrementAndGet()==1;
                case "getResultSet"->row(kind,result.get());
                default->zero(m);
            });
            return proxy(Connection.class,(p,m,a)->switch(m.getName()) {
                case "getAutoCommit"->true;
                case "prepareCall"->statement;
                case "close"->{if(!kind.equals("cleanup"))events.add(kind+"-close");if(kind.equals("gate"))clock.set(elapsedOnGateClose);if(kind.equals("read")&&fault.equals("close"))throw new SQLException("synthetic close");yield null;}
                default->zero(m);
            });
        }
        ResultSet row(String kind,int result) {
            AtomicBoolean next=new AtomicBoolean();
            return proxy(ResultSet.class,(p,m,a)->switch(m.getName()) {
                case "next"->!next.getAndSet(true);
                case "wasNull"->true;
                case "getString"->switch(a[0].toString()){case "InvocationId"->request.authority().invocationId().toString();case "CorrelationId"->request.authority().correlationId().toString();case "Name"->"Synthetic Case";default->null;};
                case "getLong"->switch(a[0].toString()){case "Id"->100L;case "ReadEventId"->1L;case "RemainingMillis"->remaining;default->0L;};
                case "getInt"->switch(a[0].toString()){case "Size"->10;case "ScopeMask"->3;default->0;};
                default->zero(m);
            });
        }
    }
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
    private static Object zero(Method m){if(!m.getReturnType().isPrimitive()||m.getReturnType()==void.class)return null;if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==long.class)return 0L;return 0;}
}
