package com.shale.data.security.ai;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiCaseCatalogBatchTest {
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,InvocationHandler calls){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},calls);}
    private static String body(String name){return "PRIVATE_DEFINITION_"+name;}
    private static Properties definitions(int count) {
        var values=new Properties();values.setProperty("Source","not-a-module");
        for(int i=0;i<count;i++)put(values,"module_"+i);return values;
    }
    private static void put(Properties values,String module){values.setProperty(module,HexFormat.of().formatHex(AiSql.hash(body(module).getBytes(StandardCharsets.UTF_16LE))));}
    private static final class Lease {
        String sql;int timeout;boolean closed,rowsClosed;final TreeMap<Integer,Object> bindings=new TreeMap<>();final List<String> calendars=new ArrayList<>();
    }
    private static final class Jdbc {
        final List<Lease> leases=new ArrayList<>();ZoneId zone=ZoneId.of("America/Denver");
        Function<List<Object[]>,List<Object[]>> fault=Function.identity();int failQuery=-1;
        final SQLException failure=new SQLException("PRIVATE_DRIVER_SENTINEL",null,208);
        final Connection connection=proxy(Connection.class,(p,m,a)->{
            if(!m.getName().equals("prepareStatement"))throw new UnsupportedOperationException(m.getName());
            var lease=new Lease();lease.sql=(String)a[0];leases.add(lease);
            return proxy(PreparedStatement.class,(sp,sm,sa)->{
                switch(sm.getName()) {
                    case "setQueryTimeout":lease.timeout=(Integer)sa[0];return null;
                    case "setString":lease.bindings.put((Integer)sa[0],sa[1]);return null;
                    case "setTimestamp":lease.bindings.put((Integer)sa[0],sa[1]);lease.calendars.add(((Calendar)sa[2]).getTimeZone().getID());return null;
                    case "close":lease.closed=true;return null;
                    case "executeQuery":
                        if(leases.size()==failQuery)throw failure;
                        var values=new ArrayList<Object[]>();
                        if(lease.sql.contains("ModuleOrdinal")) {
                            for(int i=0;i<lease.bindings.size()/2;i++){String name=(String)lease.bindings.get(i+1);assertEquals(name,lease.bindings.get(lease.bindings.size()/2+i+1));values.add(new Object[]{i,body(name),name.equals("AiDataLocateAccessOwnerV1")?-2:null,1});}
                        }else {
                            for(int i=0;i<lease.bindings.size()-1;i++){var sample=(Timestamp)lease.bindings.get(i+2);values.add(new Object[]{i,Timestamp.valueOf(LocalDateTime.ofInstant(sample.toInstant(),zone))});}
                        }
                        var rows=fault.apply(values);int[] index={-1};boolean[] wasNull={false};
                        return proxy(ResultSet.class,(rp,rm,ra)->{
                            if(rm.getName().equals("next"))return ++index[0]<rows.size();
                            if(rm.getName().equals("close")){lease.rowsClosed=true;return null;}
                            if(rm.getName().equals("wasNull"))return wasNull[0];
                            Object value=rows.get(index[0])[(Integer)ra[0]-1];wasNull[0]=value==null;
                            return switch(rm.getName()){case "getInt"->value==null?0:((Number)value).intValue();case "getString"->(String)value;case "getTimestamp"->(Timestamp)value;default->throw new UnsupportedOperationException(rm.getName());};
                        });
                    default:throw new UnsupportedOperationException(sm.getName());
                }
            });
        });
        void closed(){assertTrue(leases.stream().allMatch(l->l.closed));}
        void allClosed(){closed();assertTrue(leases.stream().allMatch(l->l.rowsClosed));}
    }
    @Test void allModuleNamesStayBoundAndEveryBodyIsCheckedAcrossBatches()throws Exception {
        var expected=definitions(79);put(expected,"odd' ] Unicode \u03a9");put(expected,"AiDataLocateAccessOwnerV1");
        var jdbc=new Jdbc();jdbc.fault=rows->{Collections.reverse(rows);return rows;};
        AiCaseDataAttestation.verifyModules(jdbc.connection,expected);
        assertEquals(List.of(64,17),jdbc.leases.stream().map(l->l.bindings.size()/2).toList());
        var found=new HashSet<Object>();jdbc.leases.forEach(l->found.addAll(l.bindings.values()));
        var names=new HashSet<>(expected.stringPropertyNames());names.remove("Source");assertEquals(names,found);
        for(var lease:jdbc.leases){assertEquals(3,lease.timeout);assertFalse(lease.sql.contains("odd' ]"));assertTrue(lease.sql.contains("WHERE s.name='dbo' AND o.name IN("));assertTrue(lease.bindings.size()<=128);}
        jdbc.allClosed();
    }
    @Test void hashOwnershipAndCallerExecutionFailuresAreRejectedAtBatchBoundaries() {
        for(int position:List.of(0,63,64,79))for(int field:List.of(1,2,3)) {
            var jdbc=new Jdbc();int[] offset={0};jdbc.fault=rows->{int local=position-offset[0];if(local>=0&&local<rows.size())rows.get(local)[field]=switch(field){case 1->"ALTERED_BODY";case 2->-2;default->2;};offset[0]+=rows.size();return rows;};
            assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.verifyModules(jdbc.connection,definitions(80)));jdbc.allClosed();
        }
    }
    @Test void locatorMustRetainOwnerExecutionInsteadOfCallerOrAnotherIdentity() {
        for(Integer execution:Arrays.asList(null,0,1,-1)) {
            var jdbc=new Jdbc();jdbc.fault=rows->{rows.getFirst()[2]=execution;return rows;};var expected=definitions(0);put(expected,"AiDataLocateAccessOwnerV1");
            assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.verifyModules(jdbc.connection,expected));jdbc.allClosed();
        }
    }
    @Test void missingExtraDuplicatedOrMalformedModuleRowsNeverSatisfyTheManifest() {
        for(String fault:List.of("missing","duplicate","extra","nullOrdinal","negative","nullBody","nullOwner")) {
            var jdbc=new Jdbc();jdbc.fault=rows->{switch(fault){case "missing"->rows.removeLast();case "duplicate"->rows.add(rows.getFirst());case "extra"->rows.add(new Object[]{999,"PRIVATE_BODY",null,1});case "nullOrdinal"->rows.getFirst()[0]=null;case "negative"->rows.getFirst()[0]=-1;case "nullBody"->rows.getFirst()[1]=null;case "nullOwner"->rows.getFirst()[3]=null;}return rows;};
            var refused=assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.verifyModules(jdbc.connection,definitions(3)),fault);assertFalse(refused.toString().contains("PRIVATE_"));jdbc.allClosed();
        }
    }
    @Test void moduleSqlFailureClosesTheFailingBatchAndDoesNotFallback()throws Exception {
        var jdbc=new Jdbc();jdbc.failQuery=2;
        assertSame(jdbc.failure,assertThrows(SQLException.class,()->AiCaseDataAttestation.verifyModules(jdbc.connection,definitions(80))));
        assertEquals(2,jdbc.leases.size());jdbc.closed();assertTrue(jdbc.leases.getFirst().rowsClosed);
        var empty=new Jdbc();AiCaseDataAttestation.verifyModules(empty.connection,definitions(0));assertTrue(empty.leases.isEmpty());
    }
    @Test void everyOriginalTimezoneSampleUsesUtcBindingAndAUniqueIndex()throws Exception {
        var jdbc=new Jdbc();jdbc.fault=rows->{Collections.reverse(rows);return rows;};
        AiCaseDataAttestation.verifyTimeZones(jdbc.connection,jdbc.zone,"Mountain Standard Time");
        assertEquals(1,jdbc.leases.size());var lease=jdbc.leases.getFirst();assertEquals(29,lease.bindings.size());assertEquals("Mountain Standard Time",lease.bindings.get(1));
        var dates=lease.bindings.tailMap(2).values().stream().map(t->((Timestamp)t).toInstant().atOffset(ZoneOffset.UTC).toLocalDate()).toList();
        assertEquals(28,new HashSet<>(dates).size());assertEquals(LocalDate.of(2025,1,15),dates.getFirst());assertEquals(LocalDate.of(2028,12,15),dates.getLast());
        assertEquals(Set.of(2025,2026,2027,2028),dates.stream().map(LocalDate::getYear).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(1,3,4,7,10,11,12),dates.stream().map(LocalDate::getMonthValue).collect(java.util.stream.Collectors.toSet()));
        assertTrue(dates.stream().allMatch(d->d.getDayOfMonth()==15));assertEquals(Collections.nCopies(28,"UTC"),lease.calendars);
        assertEquals(3,lease.timeout);assertFalse(lease.sql.contains("Mountain Standard Time"));jdbc.allClosed();
    }
    @Test void timezoneMappingMismatchAtFirstMiddleAndLastSampleFailsClosed() {
        for(int index:List.of(0,13,27)) {
            var jdbc=new Jdbc();jdbc.fault=rows->{var old=(Timestamp)rows.get(index)[1];rows.get(index)[1]=Timestamp.valueOf(old.toLocalDateTime().plusNanos(100));return rows;};
            assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.verifyTimeZones(jdbc.connection,jdbc.zone,"Mountain Standard Time"));jdbc.allClosed();
        }
    }
    @Test void missingExtraDuplicateAndNullTimezoneResultsFailClosed() {
        for(String fault:List.of("missing","duplicate","extra","nullOrdinal","nullTime")) {
            var jdbc=new Jdbc();jdbc.fault=rows->{switch(fault){case "missing"->rows.removeLast();case "duplicate"->rows.add(rows.getFirst());case "extra"->rows.add(new Object[]{28,rows.getFirst()[1]});case "nullOrdinal"->rows.getFirst()[0]=null;case "nullTime"->rows.getFirst()[1]=null;}return rows;};
            assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.verifyTimeZones(jdbc.connection,jdbc.zone,"Mountain Standard Time"),fault);jdbc.allClosed();
        }
    }
    @Test void timezoneSqlFailureIsPropagatedAndStatementClosed() {
        var jdbc=new Jdbc();jdbc.failQuery=1;
        assertSame(jdbc.failure,assertThrows(SQLException.class,()->AiCaseDataAttestation.verifyTimeZones(jdbc.connection,jdbc.zone,"Mountain Standard Time")));jdbc.closed();
    }
}
