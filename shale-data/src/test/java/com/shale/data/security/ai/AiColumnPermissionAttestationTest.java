package com.shale.data.security.ai;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiColumnPermissionAttestationTest {
    private record Row(String name,Integer select,Integer update) {}
    private static List<String> names(int count){return IntStream.range(0,count).mapToObj(i->"column_"+i).toList();}
    @SuppressWarnings("unchecked")
    private static <T>T proxy(Class<T> type,InvocationHandler calls){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},calls);}
    private static ResultSet inventory(List<String> names) {
        int[] index={-1};
        return proxy(ResultSet.class,(p,m,a)->switch(m.getName()){
            case "next" -> ++index[0]<names.size(); case "getString" -> names.get(index[0]);
            case "close" -> null; default -> throw new UnsupportedOperationException(m.getName());
        });
    }
    private static final class Lease {
        String sql;int timeout;boolean closed,rowsClosed;final TreeMap<Integer,String> bindings=new TreeMap<>();
    }
    private static final class Jdbc {
        final List<List<String>> batches=new ArrayList<>();final List<Lease> leases=new ArrayList<>();
        SQLException failure;
        final Connection caller;
        Jdbc(Function<List<String>,List<Row>> results) {
            caller=proxy(Connection.class,(p,m,a)->{
                if(!m.getName().equals("prepareStatement"))throw new UnsupportedOperationException(m.getName());
                var lease=new Lease();lease.sql=(String)a[0];leases.add(lease);
                return proxy(PreparedStatement.class,(qp,qm,qa)->{
                    switch(qm.getName()) {
                        case "setString":lease.bindings.put((Integer)qa[0],(String)qa[1]);return null;
                        case "setQueryTimeout":lease.timeout=(Integer)qa[0];return null;
                        case "close":lease.closed=true;return null;
                        case "executeQuery":
                            if(failure!=null)throw failure;
                            assertEquals(lease.bindings.get(1),lease.bindings.get(2));
                            var columns=List.copyOf(lease.bindings.tailMap(3).values());batches.add(columns);
                            var values=results.apply(columns);int[] index={-1};
                            return proxy(ResultSet.class,(rp,rm,ra)->switch(rm.getName()){
                                case "next" -> ++index[0]<values.size();
                                case "getString" -> values.get(index[0]).name();
                                case "getInt" -> {
                                    Row row=values.get(index[0]);Integer value=(Integer)ra[0]==2?row.select():row.update();yield value==null?0:value;
                                }
                                case "close" -> {lease.rowsClosed=true;yield null;}
                                default -> throw new UnsupportedOperationException(rm.getName());
                            });
                        default:throw new UnsupportedOperationException(qm.getName());
                    }
                });
            });
        }
    }
    private static List<Row> denied(List<String> names){return names.stream().map(n->new Row(n,0,null)).toList();}
    @Test void everyIndependentColumnIsBoundInThreeQueriesIncludingQuotedUnicodeNames()throws Exception {
        var columns=new ArrayList<>(names(599));columns.add("odd' ] Unicode \u03a9");
        var jdbc=new Jdbc(n->{var values=new ArrayList<>(denied(n));Collections.reverse(values);return values;});
        AiColumnPermissionAttestation.verify(jdbc.caller,"[dbo].[quoted]]object]",inventory(columns));
        assertEquals(List.of(256,256,88),jdbc.batches.stream().map(List::size).toList());
        assertEquals(columns,jdbc.batches.stream().flatMap(List::stream).toList(),"No inventoried column may be skipped");
        for(var lease:jdbc.leases) {
            assertTrue(lease.sql.contains("HAS_PERMS_BY_NAME")&&lease.sql.contains(" FROM (VALUES "));
            assertFalse(lease.sql.contains("sys.columns"),"Never use caller-filtered column inventory");
            assertFalse(lease.sql.contains("quoted]]object")||lease.sql.contains("odd'"),"Names must remain bound values");
            assertEquals("[dbo].[quoted]]object]",lease.bindings.get(1),"SELECT must target the independently inventoried object");
            assertEquals("[dbo].[quoted]]object]",lease.bindings.get(2),"UPDATE must target that same object");
            assertEquals(3,lease.timeout);assertTrue(lease.closed&&lease.rowsClosed);
            assertTrue(lease.bindings.size()<=258,"Stay well below SQL Server's parameter limit");
        }
    }
    @Test void eitherGrantFailsAtEveryBatchBoundary()throws Exception {
        var columns=names(600);
        for(int position:List.of(0,255,256,599))for(boolean select:List.of(false,true)) {
            String granted=columns.get(position);
            var jdbc=new Jdbc(n->n.stream().map(c->new Row(c,c.equals(granted)&&select?1:0,c.equals(granted)&&!select?1:0)).toList());
            assertThrows(IllegalStateException.class,()->AiColumnPermissionAttestation.verify(jdbc.caller,"object",inventory(columns)));
            assertTrue(jdbc.leases.stream().allMatch(l->l.closed&&l.rowsClosed));
        }
    }
    @Test void missingExtraDuplicateUnknownAndNullResultsFailClosed() {
        for(String fault:List.of("missing","extra","duplicate","unknown","null")) {
            var jdbc=new Jdbc(n->{
                var values=new ArrayList<>(denied(n));
                switch(fault) {
                    case "missing" -> values.removeLast(); case "extra" -> values.add(new Row("unrequested",0,0));
                    case "duplicate" -> values.add(values.getFirst()); case "unknown" -> values.set(0,new Row("unrequested",0,0));
                    case "null" -> values.set(0,new Row(null,0,0));
                }
                return values;
            });
            assertThrows(IllegalStateException.class,()->AiColumnPermissionAttestation.verify(jdbc.caller,"object",inventory(names(3))),fault);
        }
    }
    @Test void malformedInventoryFailsAndEmptyInventoryOpensNoQuery()throws Exception {
        var jdbc=new Jdbc(AiColumnPermissionAttestationTest::denied);
        AiColumnPermissionAttestation.verify(jdbc.caller,"object",inventory(List.of()));assertTrue(jdbc.leases.isEmpty());
        assertThrows(IllegalStateException.class,()->AiColumnPermissionAttestation.verify(jdbc.caller,"object",inventory(List.of("a","a"))));
        assertThrows(IllegalStateException.class,()->AiColumnPermissionAttestation.verify(jdbc.caller,"object",inventory(Arrays.asList("a",null))));
        var acrossBatches=new ArrayList<>(names(256));acrossBatches.add(acrossBatches.getFirst());
        assertThrows(IllegalStateException.class,()->AiColumnPermissionAttestation.verify(jdbc.caller,"object",inventory(acrossBatches)),
                "A duplicate must not become acceptable merely because the first batch was consumed");
    }
    @Test void queryFailurePropagatesAndClosesTheStatement() {
        var jdbc=new Jdbc(AiColumnPermissionAttestationTest::denied);jdbc.failure=new SQLException("PRIVATE_DRIVER_TEXT");
        assertSame(jdbc.failure,assertThrows(SQLException.class,()->AiColumnPermissionAttestation.verify(jdbc.caller,"object",inventory(names(1)))));
        assertTrue(jdbc.leases.getFirst().closed);
    }
}
