package com.shale.data.security.ai;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiObjectAuthorityAttestationTest {
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,InvocationHandler calls) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},calls);
    }
    private static String object(int i){return "[dbo].[object_"+i+"]";}
    private static final class Lease {
        String sql;boolean reviewer,closed;int timeout;
        final TreeMap<Integer,String> bindings=new TreeMap<>();
    }
    private static final class Jdbc {
        final List<Object[]> inventory=new ArrayList<>();final List<Lease> leases=new ArrayList<>();
        final Set<String> roots=new HashSet<>(Set.of("object_0"));
        final Set<String> invisible=new HashSet<>();
        Function<List<List<Object[]>>,List<List<Object[]>>> permissionFault=Function.identity();
        Function<List<Object[]>,List<Object[]>> columnFault=Function.identity();
        String callerGrant,reviewerGrant,columnGrant;SQLException queryFailure;int inventories,resultSets,closedResultSets;
        final Connection caller=connection(false),review=connection(true);
        Jdbc(int objects,int columns) {
            for(int i=0;i<objects;i++) {
                if(columns==0)inventory.add(new Object[]{"dbo","object_"+i,"P",null});
                else for(int c=0;c<columns;c++)inventory.add(new Object[]{"dbo","object_"+i,"U","column_"+c});
            }
        }
        Connection connection(boolean reviewer) {
            return proxy(Connection.class,(p,m,a)->{
                if(!Set.of("createStatement","prepareStatement").contains(m.getName()))throw new UnsupportedOperationException(m.getName());
                var lease=new Lease();lease.reviewer=reviewer;lease.sql=a==null?null:(String)a[0];leases.add(lease);
                int[] result={0};List<List<Object[]>>[] sets=new List[]{null};
                Class<?> api=lease.sql==null?Statement.class:PreparedStatement.class;
                return Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(sp,sm,sa)->{
                    switch(sm.getName()) {
                        case "setString":lease.bindings.put((Integer)sa[0],(String)sa[1]);return null;
                        case "setQueryTimeout":lease.timeout=(Integer)sa[0];return null;
                        case "close":lease.closed=true;return null;
                        case "execute":
                            if(queryFailure!=null)throw queryFailure;
                            assertTrue(lease.sql.contains("fn_my_permissions"));
                            var batches=new ArrayList<List<Object[]>>();
                            for(int i=0;i<lease.bindings.size();i++) {
                                String name=lease.bindings.get(i+1);var rows=new ArrayList<Object[]>();
                                if(!reviewer&&invisible.contains(name))rows.add(new Object[]{i,null,1});
                                else if(name.equals(reviewer?reviewerGrant:callerGrant))rows.add(new Object[]{i,"CONTROL",0});
                                else if(reviewer){rows.add(new Object[]{i,"VIEW DEFINITION",0});rows.add(new Object[]{i,"VIEW SECURITY DEFINITION",0});rows.add(new Object[]{i,"VIEW PERFORMANCE DEFINITION",0});}
                                else if(roots.stream().anyMatch(r->name.equals("[dbo].["+r.replace("]","]]")+"]")))rows.add(new Object[]{i,"EXECUTE",0});
                                else rows.add(new Object[]{i,null,0});
                                batches.add(rows);
                            }
                            sets[0]=permissionFault.apply(batches);result[0]=0;return !sets[0].isEmpty();
                        case "getResultSet":return rows(sets[0].get(result[0]));
                        case "getMoreResults":return ++result[0]<sets[0].size();
                        case "getUpdateCount":return -1;
                        case "executeQuery":
                            if(lease.sql==null) {
                                assertTrue(reviewer,"Only the independent verifier can enumerate columns");inventories++;lease.sql=(String)sa[0];return rows(inventory);
                            }
                            assertFalse(reviewer,"Column authority must use the caller identity");
                            var values=new ArrayList<Object[]>();
                            for(int i=1;i<=lease.bindings.size();i+=2) {
                                String name=lease.bindings.get(i),column=lease.bindings.get(i+1);
                                values.add(new Object[]{name,column,(name+":"+column).equals(columnGrant)?1:0,null});
                            }
                            return rows(columnFault.apply(values));
                        default:throw new UnsupportedOperationException(sm.getName());
                    }
                });
            });
        }
        ResultSet rows(List<Object[]> values) {
            resultSets++;int[] cursor={-1};boolean[] wasNull={false};
            return proxy(ResultSet.class,(p,m,a)->{
                if(m.getName().equals("next"))return ++cursor[0]<values.size();
                if(m.getName().equals("close")){closedResultSets++;return null;}
                if(m.getName().equals("wasNull"))return wasNull[0];
                Object value=values.get(cursor[0])[(Integer)a[0]-1];wasNull[0]=value==null;
                return switch(m.getName()){case "getString"->(String)value;case "getInt"->value==null?0:((Number)value).intValue();default->throw new UnsupportedOperationException(m.getName());};
            });
        }
        void verify()throws SQLException{AiObjectAuthorityAttestation.verify(caller,review,roots,true);}
        void allClosed(){assertTrue(leases.stream().allMatch(l->l.closed));assertEquals(resultSets,closedResultSets);}
    }

    @Test void allObjectsAndColumnsRemainCheckedAcrossBoundedBatches()throws Exception {
        var jdbc=new Jdbc(300,2);jdbc.inventory.add(new Object[]{"odd]schema","quote' Unicode \u03a9","U","odd' ]column"});
        jdbc.verify();assertEquals(1,jdbc.inventories);
        var permissions=jdbc.leases.stream().filter(l->l.sql.contains("fn_my_permissions")).toList();
        assertEquals(10,permissions.size());
        for(boolean reviewer:List.of(false,true))assertEquals(301,permissions.stream().filter(l->l.reviewer==reviewer).mapToInt(l->l.bindings.size()).sum());
        assertEquals(3,jdbc.leases.stream().filter(l->l.sql.contains("AS v(ObjectName,ColumnName)")).count());
        for(var lease:jdbc.leases) {
            assertEquals(3,lease.timeout);assertTrue(lease.bindings.size()<=512);
            assertFalse(lease.sql.contains("quote' Unicode"));assertFalse(lease.sql.contains("odd' ]column"));
            if(lease.sql.contains("fn_my_permissions")){assertTrue(lease.bindings.size()<=64);assertTrue(lease.sql.contains("ERROR_NUMBER()<>15151 THROW"));}
        }
        jdbc.allClosed();
    }
    @Test void grantsAtEveryObjectBatchBoundaryFailUnderEitherIdentity() {
        for(int position:List.of(0,63,64,129))for(boolean reviewer:List.of(false,true)) {
            var jdbc=new Jdbc(130,1);if(reviewer)jdbc.reviewerGrant=object(position);else jdbc.callerGrant=object(position);
            assertThrows(IllegalStateException.class,jdbc::verify);jdbc.allClosed();
        }
    }
    @Test void invisibleObjectCannotHideAnotherObjectsGrantOrRequiredExecute()throws Exception {
        var safe=new Jdbc(70,0);safe.invisible.add(object(1));safe.verify();safe.allClosed();
        var forbidden=new Jdbc(70,0);forbidden.invisible.add(object(1));forbidden.callerGrant=object(64);
        assertThrows(IllegalStateException.class,forbidden::verify);forbidden.allClosed();
        var root=new Jdbc(2,0);root.invisible.add(object(0));assertThrows(IllegalStateException.class,root::verify);root.allClosed();
    }
    @Test void missingRequiredRootAndUnexpectedExecutableFailClosed() {
        var missing=new Jdbc(1,0);missing.roots.add("absent");assertThrows(IllegalStateException.class,missing::verify);
        var extra=new Jdbc(2,0);extra.permissionFault=sets->{sets.get(1).set(0,new Object[]{1,"EXECUTE",0});return sets;};
        assertThrows(IllegalStateException.class,extra::verify);extra.allClosed();
    }
    @Test void malformedPermissionBatchesNeverBecomeEmptyAuthority() {
        for(String fault:List.of("missingSet","extraSet","emptySet","ordinal","nullOrdinal","nullFlag","badFlag","mixedSentinel","invisibleGrant")) {
            var jdbc=new Jdbc(2,0);jdbc.permissionFault=sets->{
                switch(fault) {
                    case "missingSet"->sets.removeLast();case "extraSet"->sets.add(sets.getLast());case "emptySet"->sets.get(1).clear();
                    case "ordinal"->sets.get(1).getFirst()[0]=0;case "nullOrdinal"->sets.get(1).getFirst()[0]=null;
                    case "nullFlag"->sets.get(1).getFirst()[2]=null;case "badFlag"->sets.get(1).getFirst()[2]=2;
                    case "mixedSentinel"->sets.get(1).add(new Object[]{1,"EXECUTE",0});
                    case "invisibleGrant"->{sets.get(0).getFirst()[2]=1;}
                }return sets;
            };
            assertThrows(IllegalStateException.class,jdbc::verify,fault);jdbc.allClosed();
        }
    }
    @Test void allowedDuplicatePermissionNamesRetainOldSetSemantics()throws Exception {
        var jdbc=new Jdbc(1,0);jdbc.permissionFault=sets->{sets.getFirst().add(sets.getFirst().getFirst().clone());return sets;};
        jdbc.verify();jdbc.allClosed();
    }
    @Test void columnGrantsAndMalformedRowsFailAtCrossObjectBoundaries() {
        for(int position:List.of(0,255,256,599)) {
            var jdbc=new Jdbc(300,2);jdbc.columnGrant=object(position/2)+":column_"+(position%2);
            assertThrows(IllegalStateException.class,jdbc::verify);jdbc.allClosed();
        }
        for(String fault:List.of("missing","duplicate","otherObject","otherColumn","nullColumn","updateGrant")) {
            var jdbc=new Jdbc(2,2);jdbc.columnFault=rows->{switch(fault){case "missing"->rows.removeLast();case "duplicate"->rows.add(rows.getFirst());case "otherObject"->rows.getFirst()[0]="other";case "otherColumn"->rows.getFirst()[1]="other";case "nullColumn"->rows.getFirst()[1]=null;case "updateGrant"->rows.getFirst()[3]=1;}return rows;};
            assertThrows(IllegalStateException.class,jdbc::verify,fault);jdbc.allClosed();
        }
    }
    @Test void malformedIndependentInventoryIsRejectedBeforeCallerQueries() {
        for(String fault:List.of("duplicate","nullMixed","emptyDuplicate","type","nullName")) {
            var jdbc=new Jdbc(1,1);
            switch(fault){case "duplicate"->jdbc.inventory.add(jdbc.inventory.getFirst().clone());case "nullMixed"->jdbc.inventory.add(new Object[]{"dbo","object_0","U",null});case "emptyDuplicate"->{jdbc.inventory.getFirst()[3]=null;jdbc.inventory.add(jdbc.inventory.getFirst().clone());}case "type"->jdbc.inventory.getFirst()[2]="unknown";case "nullName"->jdbc.inventory.getFirst()[1]=null;}
            assertThrows(IllegalStateException.class,jdbc::verify,fault);assertEquals(1,jdbc.leases.size());jdbc.allClosed();
        }
    }
    @Test void laterAdmissionsSeeChangedAuthorityAndSqlErrorsNeverFallback()throws Exception {
        var jdbc=new Jdbc(2,1);jdbc.verify();jdbc.callerGrant=object(1);
        assertThrows(IllegalStateException.class,jdbc::verify);assertEquals(2,jdbc.inventories);jdbc.allClosed();
        for(int code:List.of(15151,208,1222)) {
            var broken=new Jdbc(1,0);broken.queryFailure=new SQLException("PRIVATE_DRIVER_SENTINEL",null,code);
            assertSame(broken.queryFailure,assertThrows(SQLException.class,broken::verify));broken.allClosed();
        }
    }
}
