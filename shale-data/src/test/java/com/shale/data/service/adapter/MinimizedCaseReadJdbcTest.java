package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import com.shale.core.dto.MinimizedCaseOverview;
import com.shale.core.service.CaseReadException;
import com.shale.data.dao.CaseDao;

/** Synthetic JDBC execution of the public production adapter/gateway/DAO, not live SQL/RLS proof. */
class MinimizedCaseReadJdbcTest {
    @Test void searchFiltersEntireEligibleDatasetBeforePagingIncludingBeyondFirst25() {
        Jdbc db = new Jdbc();
        IntStream.rangeClosed(1, 35).forEach(id -> db.rows.add(row(id, "A unfiltered " + id)));
        IntStream.rangeClosed(36, 90).forEach(id -> db.rows.add(row(id, "Z match")));
        db.rows.add(with(row(91, "Z match"), "ShaleClientId", 42));
        db.rows.add(with(row(92, "Z match"), "IsDeleted", true));
        var page = db.adapter().searchMinimizedCases(" MATCH ", 41, 31, 0, 25);
        assertEquals(36L, page.items().getFirst().caseId(), "A match after 35 unfiltered Cases must be returned");
        assertEquals(25, page.items().size()); assertTrue(page.hasMore());
        assertEquals(26, db.returnedRows); assertEquals(0, db.bindings.get(5)); assertEquals(26, db.bindings.get(6));
        var second = db.adapter().searchMinimizedCases("match", 41, 31, 1, 25);
        assertEquals(61L, second.items().getFirst().caseId()); assertTrue(second.hasMore());
        var last = db.adapter().searchMinimizedCases("match", 41, 31, 2, 25);
        assertEquals(List.of(86L,87L,88L,89L,90L), last.items().stream().map(MinimizedCaseOverview::caseId).toList());
        assertFalse(last.hasMore());
        assertTrue(db.adapter().searchMinimizedCases("match", 41, 31, 3, 25).items().isEmpty());
        assertTrue(db.sql.contains("ORDER BY c.Name ASC,c.Id ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
        assertEquals(0, db.audits, "Search pages have the explicit first-slice audit exemption");
    }

    @Test void assignmentFiltersBeforePagingButDoesNotRestrictSearchOrOverview() {
        Jdbc db = new Jdbc();
        IntStream.rangeClosed(1, 35).forEach(id -> db.rows.add(row(id, "Match")));
        IntStream.rangeClosed(36, 66).forEach(id -> db.rows.add(with(row(id, "Match"), "Assigned", true)));
        var assigned = db.adapter().listMinimizedAssignedCases(41,31,0,25);
        assertEquals(36L,assigned.items().getFirst().caseId()); assertTrue(assigned.hasMore());
        assertEquals(31, db.bindings.get(4)); assertEquals(26,db.bindings.get(6));
        assertTrue(db.sql.contains("scope.ShaleClientId=c.ShaleClientId"));
        assertTrue(db.sql.contains("dates.IntakeDate DESC,c.Id DESC"));
        assertFalse(db.adapter().listMinimizedAssignedCases(41,31,1,25).hasMore());
        assertEquals(1L,db.adapter().searchMinimizedCases("match",41,31,0,25).items().getFirst().caseId());
        assertEquals(1L,db.adapter().readMinimizedCaseOverview(1,41,31).orElseThrow().caseId());
        assertEquals(1,db.audits);
    }

    @Test void authorityMismatchRemovedActorAndForeignTenantNeverReadCasesOrAudit() {
        for (int scenario = 0; scenario < 4; scenario++) {
            Jdbc db = new Jdbc(); db.rows.add(row(1,"Secret"));
            if(scenario==0)db.sessionActor=32;
            if(scenario==1)db.sessionTenant=42;
            if(scenario==2)db.removed=true;
            if(scenario==3)db.sessionActor=null;
            var ex=assertThrows(CaseReadException.class,()->db.adapter().readMinimizedCaseOverview(1,41,31));
            assertEquals(CaseReadException.Kind.DENIED,ex.kind());
            assertEquals(List.of("begin","authority","rollback","close"),db.events);
            assertEquals(0,db.audits);
            assertThrows(CaseReadException.class,()->db.adapter().searchMinimizedCases("",41,31,0,25), "Blank query must not bypass actor eligibility");
        }
    }

    @Test void unavailableIdsAllReturnEmptyWithoutSuccessAudit() {
        for(Map<Object,Object> fixture:List.of(with(row(1,"Secret"),"ShaleClientId",42),with(row(1,"Secret"),"IsDeleted",true))) {
            Jdbc db=new Jdbc();db.rows.add(fixture);
            assertTrue(db.adapter().readMinimizedCaseOverview(1,41,31).isEmpty());
            assertEquals(0,db.audits);assertFalse(db.events.contains("commit"));
        }
        Jdbc db=new Jdbc();assertTrue(db.adapter().readMinimizedCaseOverview(999,41,31).isEmpty());assertEquals(0,db.audits);
    }

    @Test void overviewUsesExistingRepresentationOnReadConnectionAndCommitsBeforeReturn() {
        Jdbc db=new Jdbc();db.rows.add(row(1,"Secret name"));
        var result=db.adapter().readMinimizedCaseOverview(1,41,31).orElseThrow();
        assertEquals("Secret name",result.caseName());
        assertEquals(List.of("begin","authority","cases","audit-tenant","audit","commit","close"),db.events);
        assertEquals(1,db.connections,"Audit must use the authoritative read connection");
        assertEquals(41,db.auditBindings.get(1));assertEquals(31,db.auditBindings.get(2));assertEquals(1,db.auditBindings.get(3));
        assertEquals(1L,db.auditBindings.get(4));assertEquals("Case.Overview.Read",db.auditBindings.get(5));
        assertEquals(4,db.auditBindings.get(6));assertEquals("action=READ;screen=Case.Overview",db.auditBindings.get(7));
        assertNull(db.auditBindings.get(8));
        assertFalse(db.auditBindings.values().contains("Secret name"));
        assertNull(result.status());assertNull(result.practiceArea());assertNull(result.responsibleAttorney());
    }

    @Test void requiredAppendOrCommitFailureRollsBackAndReturnsNoResult() {
        for(boolean commitFailure:List.of(false,true)) {
            Jdbc db=new Jdbc();db.rows.add(row(1,"Secret"));db.failCommit=commitFailure;db.failAudit=!commitFailure;
            var failure=assertThrows(CaseReadException.class,()->db.adapter().readMinimizedCaseOverview(1,41,31));
            assertEquals(CaseReadException.Kind.AUDIT_UNAVAILABLE,failure.kind());
            assertTrue(db.events.contains("rollback"));assertEquals("close",db.events.getLast());
        }
    }

    @Test void literalEscapesBoundsTimeoutAndOversizedSourceFailClosed() {
        Jdbc db=new Jdbc(); db.adapter().searchMinimizedCases(" ÉLAN_%[東京 ",41,31,100,25);
        assertEquals("%élan[_][%][[]東京%",db.bindings.get(4));assertEquals(2500,db.bindings.get(5));assertEquals(26,db.bindings.get(6));
        assertThrows(IllegalArgumentException.class,()->db.adapter().searchMinimizedCases("x",41,31,101,25));
        assertThrows(IllegalArgumentException.class,()->db.adapter().listMinimizedAssignedCases(41,31,0,26));
        assertThrows(IllegalArgumentException.class,()->db.adapter().searchMinimizedCases("x".repeat(101),41,31,0,25));
        db.timeout=true;
        assertEquals(CaseReadException.Kind.TIMEOUT,assertThrows(CaseReadException.class,()->db.adapter().searchMinimizedCases("x",41,31,0,25)).kind());
        db.timeout=false; db.rows.add(with(row(1,"unused"),"NameOversized",true));
        assertEquals(CaseReadException.Kind.OVERSIZED,assertThrows(CaseReadException.class,()->db.adapter().readMinimizedCaseOverview(1,41,31)).kind());
        assertEquals(0,db.audits);assertFalse(db.events.contains("commit"));
    }

    @Test void projectionTransfersOnlyApprovedFieldsWithoutChildHydrationAndPreservesRelationships() {
        Jdbc db=new Jdbc();Map<Object,Object> item=row(1,"Name");
        item.putAll(Map.of("StatusId",10,"StatusName","Current","StatusColor","bad css", "PracticeAreaId",12,"PracticeAreaName","Area",
                "ResponsibleAttorneyId",7,"ResponsibleAttorneyName","A","PrimaryLegalAssistantId",8,"PrimaryLegalAssistantName","B"));
        item.put("UpdatedAt",Timestamp.valueOf("2026-10-09 12:30:00"));db.rows.add(item);
        var result=db.adapter().readMinimizedCaseOverview(1,41,31).orElseThrow();
        assertEquals("Current",result.status().name());assertNull(result.status().color());
        assertEquals(7,result.responsibleAttorney().userId());assertEquals("2026-10-09T12:30",result.updatedAt());
        for(String excluded:List.of("Description","Summary","dbo.Contacts","dbo.CaseParties","RowVer","dbo.Tasks","dbo.CaseDates"))
            assertFalse(db.sql.contains(excluded),"Overview must not load " + excluded);
        assertTrue(db.sql.contains("cu.ShaleClientId=c.ShaleClientId"));assertTrue(db.sql.contains("u.ShaleClientId=c.ShaleClientId"));
        assertTrue(db.sql.contains("DATALENGTH(COALESCE(c.Name,''))<=510"));assertFalse(db.sql.contains("LEFT("));
        assertEquals(1,db.connections);
    }

    static Map<Object,Object> row(long id,String name) {var row=new HashMap<Object,Object>();row.put("Id",id);row.put("Name",name);row.put("ShaleClientId",41);return row;}
    static Map<Object,Object> with(Map<Object,Object> row,String key,Object value){row.put(key,value);return row;}

    private static final class Jdbc {
        List<Map<Object,Object>> rows=new ArrayList<>(); List<String> events=new ArrayList<>();
        Integer sessionActor=31,sessionTenant=41;boolean removed,failAudit,failCommit,timeout;
        int connections,audits,returnedRows;String sql;Map<Integer,Object> bindings,auditBindings;
        CaseServiceAdapter adapter(){return new CaseServiceAdapter(new CaseDao(()->{
            connections++;return proxy(Connection.class,(p,m,a)->switch(m.getName()){
                case "prepareStatement"->statement((String)a[0]);
                case "setAutoCommit"->{assertEquals(false,a[0]);events.add("begin");yield null;}
                case "commit"->{events.add("commit");if(failCommit)throw new SQLException("private failure");yield null;}
                case "rollback"->{events.add("rollback");yield null;}
                case "close"->{events.add("close");yield null;}
                case "getCatalog"->"catalog";
                case "getMetaData"->proxy(DatabaseMetaData.class,(pp,mm,aa)->{assertEquals("getColumns",mm.getName());return resultSet(List.of(Map.of("DATA_TYPE",Types.INTEGER)));});
                default->throw new AssertionError(m.getName());
            });}));}
        PreparedStatement statement(String text){Map<Integer,Object> values=new HashMap<>();int[] timeoutSet={0};
            return proxy(PreparedStatement.class,(p,m,a)->switch(m.getName()){
                case "setQueryTimeout"->{assertEquals(5,a[0]);timeoutSet[0]=5;yield null;}
                case "setInt","setLong","setString","setTimestamp"->{values.put((Integer)a[0],a[1]);yield null;}
                case "setNull"->{values.put((Integer)a[0],null);yield null;}
                case "close"->null;
                case "executeUpdate"->{assertEquals(5,timeoutSet[0]);assertTrue(text.contains("INSERT INTO dbo.AuditLog"));events.add("audit");auditBindings=new HashMap<>(values);if(failAudit)throw new SQLException("Secret source text");audits++;yield 1;}
                case "executeQuery"->{assertEquals(5,timeoutSet[0],"Every selected SQL statement must have the five-second timeout");
                    assertEquals(text.chars().filter(c->c=='?').count(),values.size(),"Exact SQL placeholder/binding contract");
                    if(text.startsWith("SELECT 1 FROM dbo.Users")){events.add("authority");assertTrue(text.contains("IsRemoved"));
                        yield resultSet(!removed&&Objects.equals(sessionActor,values.get(4))&&Objects.equals(sessionTenant,values.get(3))?List.of(Map.of(1,1)):List.of());}
                    if(text.contains("SESSION_CONTEXT")){events.add("audit-tenant");yield resultSet(List.of(Map.of(1,sessionTenant)));}
                    events.add("cases");sql=text.replaceAll("\\s+"," ");bindings=new HashMap<>(values);if(timeout)throw new SQLTimeoutException("Secret timeout");
                    int where=sql.indexOf("WHERE c.ShaleClientId=?"),order=sql.indexOf("ORDER BY c.Name ASC,c.Id ASC OFFSET");
                    assertTrue(where>=0&&sql.contains("ISNULL(c.IsDeleted,0)=0"));
                    boolean assigned=sql.contains("scope.UserId=?"),detail=sql.contains("AND c.Id=?");
                    boolean search=sql.contains("LOWER(COALESCE(c.Name,'')) LIKE ?");
                    if(search)assertTrue(sql.indexOf("LOWER(COALESCE(c.Name,'')) LIKE ?")<order,"Search must precede ordering/paging");
                    int count=values.size(),offset=(int)values.get(count-1),limit=(int)values.get(count);
                    assertTrue(limit<=26);
                    String query=search?values.get(4).toString().replaceAll("^%|%$",""):null;
                    var selected=rows.stream().filter(r->Objects.equals(r.get("ShaleClientId"),values.get(3))&&!Boolean.TRUE.equals(r.get("IsDeleted")))
                        .filter(r->!assigned||Boolean.TRUE.equals(r.get("Assigned"))).filter(r->!detail||Objects.equals(r.get("Id"),values.get(4)))
                        .filter(r->!search||r.get("Name").toString().toLowerCase(Locale.ROOT).contains(query))
                        .sorted(Comparator.<Map<Object,Object>,String>comparing(r->r.get("Name").toString()).thenComparingLong(r->(Long)r.get("Id")))
                        .skip(offset).limit(limit).toList();returnedRows=selected.size();yield resultSet(selected);
                }
                default->throw new AssertionError(m.getName());
            });}
    }
    static ResultSet resultSet(List<? extends Map<?,?>> rows){int[] cursor={-1};boolean[] nil={false};return proxy(ResultSet.class,(p,m,a)->{
        if(m.getName().equals("next"))return ++cursor[0]<rows.size();if(m.getName().equals("close"))return null;if(m.getName().equals("wasNull"))return nil[0];
        Object value=rows.get(cursor[0]).get(a[0]);nil[0]=value==null;
        return switch(m.getName()){case "getObject","getTimestamp"->value;case "getString"->value==null?null:value.toString();case "getInt"->value==null?0:((Number)value).intValue();case "getLong"->value==null?0L:((Number)value).longValue();case "getBoolean"->value!=null&&(Boolean)value;default->throw new AssertionError(m.getName());};});}
    static <T>T proxy(Class<T> type,InvocationHandler handler){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));}
}
