package com.shale.data.security.ai;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AzureOwnerV2InstallTest {
    @Test @org.junit.jupiter.api.Timeout(15) void failedCancelClosesPhysicalConnectionAfterFiveSecondAllowance()throws Exception {
        var released=new java.util.concurrent.CountDownLatch(1);var closed=new java.util.concurrent.atomic.AtomicBoolean();
        Connection c=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("close")){closed.set(true);released.countDown();return null;}
            if(closed.get())throw new SQLException("private closed transport");
            assertEquals("createStatement",m.getName());
            return Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class<?>[]{Statement.class},(sp,sm,sa)->switch(sm.getName()) {
                case "setQueryTimeout","close"->null;
                case "execute"->{assertTrue(released.await(10,java.util.concurrent.TimeUnit.SECONDS),"Physical-close fallback must bound a failed cancel");throw new SQLException("private driver failure");}
                case "cancel"->throw new SQLException("private cancel failure");
                case "getWarnings"->null;
                default->throw new AssertionError("Unexpected deadline operation");
            });
        });
        var failure=assertThrows(SQLException.class,()->AzureOwnerV2Install.execute(c,new AzureOwnerV2Install.Plan("synthetic",List.of()),1));
        assertTrue(closed.get());assertTrue(failure.getMessage().contains("rollback=UNCONFIRMED"));
        assertNull(failure.getCause());assertEquals(0,failure.getSuppressed().length);assertFalse(failure.toString().contains("private"));
    }
    @Test void diagnosticsOnlyInsertFixedWarningsIntoTheExactReviewedSource()throws Exception {
        byte[] raw=Files.readAllBytes(Path.of("../docs/sql/ai-integration/phase3b8-confinement.sql"));
        var plan=AzureOwnerV2Install.diagnosticPlan(raw);
        String recovered=plan.sql();
        for(int i=1;i<=plan.stages().size();i++)recovered=recovered.replace(AzureOwnerV2Install.checkpoint(i),"");
        assertArrayEquals(raw,recovered.getBytes(StandardCharsets.UTF_8),"Only independent diagnostic statements may be inserted; receipt/module/catalog input text must remain exact");
        assertTrue(plan.stages().containsAll(List.of("CASE_MODULES","CASE_CATALOG","AI_CATALOG","DDL","UNICODE","MODULE","RLS_AND_GRANTS","RECEIPTS","COMMIT")));
        assertEquals(13,Collections.frequency(plan.stages(),"MODULE"));
        assertEquals(17,Collections.frequency(plan.stages(),"UNICODE"));
        assertThrows(IllegalStateException.class,()->AzureOwnerV2Install.diagnosticPlan(plan.sql().getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalStateException.class,()->AzureOwnerV2Install.diagnosticPlan(new String(raw,StandardCharsets.UTF_8).replace("\n","\r\n").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void onlyExactAllowlistedCheckpointsBecomeEvidence() {
        var first=new SQLWarning("private SQL SID target token");
        first.setNextWarning(new SQLWarning("AZURE_V2_CHECKPOINT_2"));
        first.setNextWarning(new SQLWarning("AZURE_V2_CHECKPOINT_999"));
        first.setNextWarning(new SQLWarning("AZURE_V2_CHECKPOINT_3 private"));
        assertEquals(2,AzureOwnerV2Install.lastCheckpoint(first,3));
        assertEquals(0,AzureOwnerV2Install.lastCheckpoint(null,3));
    }
    @Test void timeoutRollsBackExplicitSqlEvenInJdbcAutocommitAndSanitizesSurefire()throws Exception {
        for(boolean verificationFails:List.of(false,true)) {
            var queries=new ArrayList<String>();var limits=new ArrayList<Integer>();
            Connection c=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                assertEquals("createStatement",m.getName(),"Never call JDBC rollback in autocommit or change transaction mode");
                return Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class<?>[]{Statement.class},(sp,sm,sa)->switch(sm.getName()) {
                    case "setQueryTimeout"->{limits.add((int)sa[0]);yield null;}
                    case "execute"->{String sql=(String)sa[0];queries.add(sql);if(sql.equals("synthetic"))throw new SQLTimeoutException("private sql target token","HYT00");yield false;}
                    case "getWarnings"->new SQLWarning("AZURE_V2_CHECKPOINT_1");
                    case "executeQuery"->{queries.add((String)sa[0]);var read=new boolean[1];yield Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class<?>[]{ResultSet.class},(rp,rm,ra)->switch(rm.getName()) {
                        case "next"->{boolean first=!read[0];read[0]=true;yield first;}
                        case "getInt"->verificationFails?1:0;
                        case "close"->null;
                        default->throw new AssertionError("Unexpected result operation");
                    });}
                    case "close"->null;
                    default->throw new AssertionError("Unexpected statement operation");
                });
            });
            var bytes=new java.io.ByteArrayOutputStream();var prior=System.err;
            try(var output=new java.io.PrintStream(bytes)) {
                System.setErr(output);
                var failure=assertThrows(SQLException.class,()->AzureOwnerV2Install.execute(c,new AzureOwnerV2Install.Plan("synthetic",List.of("SESSION")),20));
                failure.printStackTrace(output);
                assertNull(failure.getCause());assertEquals(0,failure.getSuppressed().length);
            }finally{System.setErr(prior);}
            String log=bytes.toString(StandardCharsets.UTF_8);
            assertFalse(log.contains("private"));assertTrue(log.contains("lastObserved=1 category=SESSION"));
            assertTrue(log.contains("failure=QUERY_TIMEOUT sqlState=HYT00"));
            assertTrue(log.contains("rollback="+(verificationFails?"UNCONFIRMED":"CONFIRMED")));
            assertEquals("IF XACT_STATE()<>0 ROLLBACK TRANSACTION",queries.get(1));
            assertTrue(queries.get(2).contains("@@TRANCOUNT,XACT_STATE()"));
            assertTrue(limits.stream().allMatch(n->n==20),"Ordinary, install and rollback limits remain bounded at 20 seconds");
        }
    }
}
