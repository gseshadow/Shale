package com.shale.data.dao;

import static com.shale.core.service.OrganizationServicePort.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** SQL Server-style RowVer progression coverage for the Phase 2B aggregate writer. */
final class OrganizationAggregateRowVersionTest {
    @Test void primaryAndScalarChangeUseOpeningTokenOnceAndReturnTheGeneratedToken() {
        UpdateDb db=new UpdateDb();
        byte[] result=new OrganizationTypeMutationDao(db).updateAggregate(command(db,new OrganizationFields("Renamed",null,null,null,null,null,null,null,null,null,null,null),List.of(existing(1,12,false,0,db.rv(1)),existing(2,13,true,1,db.rv(2)))));

        assertArrayEquals(db.organizationRowVer,result,"the consolidated UPDATE must return SQL Server's inserted RowVer");
        assertFalse(Arrays.equals(db.rv(3),result),"the final RowVer must differ from the opening token");
        assertEquals(1,db.organizationUpdates,"primary and scalar changes must share one Organization UPDATE");
        assertEquals(1,db.openingTokenPredicates,"the opening token must be consumed exactly once");
        assertEquals("Renamed",db.name);assertEquals(13,db.compatibilityType);
        assertTrue(db.assignments.get(2L).primary);assertFalse(db.assignments.get(1L).primary);
        assertTrue(db.events.contains("commit"));assertEquals(2,db.committedAudits,"the primary and Organization update audits must commit with the aggregate");
    }

    @Test void assignmentOnlyReconciliationDoesNotSelfInvalidateOrganizationConcurrency() {
        for(String scenario:List.of("add","remove","reorder","replace-remove")){
            UpdateDb db=new UpdateDb();List<StagedOrganizationTypeAssignment> desired=switch(scenario){
                case "add"->List.of(existing(1,12,true,0,db.rv(1)),new StagedOrganizationTypeAssignment(null,14,false,1,null));
                case "remove"->List.of(existing(1,12,true,0,db.rv(1)));
                case "reorder"->List.of(existing(2,13,false,0,db.rv(2)),existing(1,12,true,1,db.rv(1)));
                default->List.of(existing(2,13,true,0,db.rv(2)));
            };
            assertDoesNotThrow(()->new OrganizationTypeMutationDao(db).updateAggregate(command(db,fields(),desired)),scenario);
            assertEquals(1,db.organizationUpdates,scenario+" must perform one consolidated Organization UPDATE");
            assertEquals(1,db.openingTokenPredicates,scenario+" must use the opening RowVer once");
            assertTrue(db.events.contains("commit"),scenario);
            assertEquals(desired.stream().filter(StagedOrganizationTypeAssignment::primary).findFirst().orElseThrow().organizationTypeId(),db.compatibilityType,scenario);
        }
    }

    @Test void genuineOrganizationAndAssignmentStalenessStillRollBackWithoutAudit() {
        UpdateDb staleOrganization=new UpdateDb();staleOrganization.submittedOrganizationRowVer=staleOrganization.rv(99);
        assertThrows(IllegalStateException.class,()->new OrganizationTypeMutationDao(staleOrganization).updateAggregate(command(staleOrganization,fields(),staleOrganization.openingDesired())));
        assertRollbackOnly(staleOrganization);assertEquals(0,staleOrganization.organizationUpdates);

        UpdateDb staleAssignment=new UpdateDb();
        var desired=List.of(existing(1,12,true,0,staleAssignment.rv(99)),existing(2,13,false,1,staleAssignment.rv(2)));
        assertThrows(IllegalStateException.class,()->new OrganizationTypeMutationDao(staleAssignment).updateAggregate(command(staleAssignment,fields(),desired)));
        assertRollbackOnly(staleAssignment);assertEquals(0,staleAssignment.organizationUpdates);
    }

    @Test void postOrganizationAuditFailureRollsBackFieldsCompatibilityAssignmentsAndAudit() {
        UpdateDb db=new UpdateDb();db.failPrimaryAudit=true;
        var desired=List.of(existing(1,12,false,0,db.rv(1)),existing(2,13,true,1,db.rv(2)));
        assertThrows(IllegalStateException.class,()->new OrganizationTypeMutationDao(db).updateAggregate(command(db,new OrganizationFields("Renamed",null,null,null,null,null,null,null,null,null,null,null),desired)));
        assertRollbackOnly(db);assertEquals("Org",db.name);assertEquals(12,db.compatibilityType);
        assertTrue(db.assignments.get(1L).primary);assertFalse(db.assignments.get(2L).primary);
    }

	@Test void legacyBackfilledPhoneDoesNotBreakNameNotesOrPhoneEdits() {
		for (String edit : List.of("name", "notes", "phone")) {
			UpdateDb db = new UpdateDb("505 123 4567");
			OrganizationFields changed = new OrganizationFields("name".equals(edit) ? "Renamed" : "Org",
					"phone".equals(edit) ? "505 765 4321" : "505 123 4567", null, null, "example.test",
					null, null, null, null, null, null, "notes".equals(edit) ? "Changed notes" : null);
			assertDoesNotThrow(() -> new OrganizationTypeMutationDao(db).updateAggregate(command(db, changed, db.openingDesired())), edit);
			assertEquals(1, db.organizationUpdates, edit);
			assertEquals("phone".equals(edit) ? 1 : 0, db.structuredUpdates,
					"unchanged Phase 3A rows, including nullable CreatedByUserId provenance, must not be rewritten");
			assertEquals("phone".equals(edit) ? "505 765 4321" : "505 123 4567", db.phone, edit);
			assertTrue(db.events.contains("commit"), edit);
		}
	}

    private static void assertRollbackOnly(UpdateDb db){assertTrue(db.events.contains("rollback"));assertFalse(db.events.contains("commit"));assertEquals(0,db.committedAudits);}
    private static StagedOrganizationTypeAssignment existing(long id,int type,boolean primary,int order,byte[]rv){return new StagedOrganizationTypeAssignment(id,type,primary,order,rv);}
    private static OrganizationFields fields(){return new OrganizationFields("Org",null,null,null,null,null,null,null,null,null,null,null);}
    private static UpdateOrganizationAggregateCommand command(UpdateDb db,OrganizationFields fields,List<StagedOrganizationTypeAssignment> assignments){return new UpdateOrganizationAggregateCommand(42,7,9,db.submittedOrganizationRowVer,fields,assignments);}

    private static final class UpdateDb implements com.shale.core.runtime.DbSessionProvider {
        final List<String> events=new ArrayList<>();final Map<Long,A> assignments=new LinkedHashMap<>();
	        byte[] organizationRowVer=rv(3),submittedOrganizationRowVer=rv(3);int compatibilityType=12;String name="Org",phone,website;long nextId=3;int nextVersion=4;
	        int organizationUpdates,openingTokenPredicates,committedAudits,structuredUpdates;boolean failPrimaryAudit;List<String> pendingAudits=new ArrayList<>();Snapshot snapshot;
	        UpdateDb(){this(null);}UpdateDb(String phone){this.phone=phone;this.website=phone==null?null:"example.test";assignments.put(1L,new A(1,12,true,0,false,rv(1)));assignments.put(2L,new A(2,13,false,1,false,rv(2)));}
        List<StagedOrganizationTypeAssignment> openingDesired(){return List.of(existing(1,12,true,0,rv(1)),existing(2,13,false,1,rv(2)));}
        byte[] rv(int value){return new byte[]{0,0,0,0,0,0,0,(byte)value};}
        public Connection requireConnection(){return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
            case "getAutoCommit"->true;
	            case "setAutoCommit"->{if(!(boolean)a[0]){events.add("begin");snapshot=new Snapshot(name,phone,website,compatibilityType,organizationRowVer.clone(),copyAssignments());}else events.add("restore-auto");yield null;}
            case "prepareStatement"->statement((String)a[0]);
            case "commit"->{events.add("commit");committedAudits+=pendingAudits.size();pendingAudits.clear();yield null;}
            case "rollback"->{events.add("rollback");restore();pendingAudits.clear();yield null;}
            case "close"->{events.add("close");yield null;}case "isClosed"->false;default->value(m.getReturnType());});}
        private PreparedStatement statement(String sql){Map<Integer,Object>b=new HashMap<>();return(PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->switch(m.getName()){
	            case "setInt","setLong","setString","setBoolean","setBytes"->{b.put((Integer)a[0],a[1]);yield null;}
			case "setNull"->{b.put((Integer)a[0],null);yield null;}
            case "executeQuery"->query(sql,b);case "executeUpdate"->update(sql,b);case "close"->null;default->value(m.getReturnType());});}
        private ResultSet query(String sql,Map<Integer,Object>b)throws SQLException{
            if(sql.contains("SESSION_CONTEXT(N'ShaleClientId')"))return rows(new Object[][]{{7}});
            if(sql.contains("FROM dbo.Users"))return rows(new Object[][]{{1}});
            if(sql.startsWith("SELECT o.OrganizationTypeId")){long primaries=assignments.values().stream().filter(a->!a.deleted&&a.primary).count();Integer primary=assignments.values().stream().filter(a->!a.deleted&&a.primary).map(a->a.type).findFirst().orElse(null);return rows(new Object[][]{{compatibilityType,organizationRowVer.clone(),primaries,primary}});}
            if(sql.contains("FROM dbo.OrganizationTypes WHERE OrganizationTypeId"))return rows(new Object[][]{{b.get(1),7,"type_"+b.get(1),true,false,rv(1)}});
			if(sql.contains("FROM dbo.OrganizationPhoneNumbers"))return phone==null?rows(new Object[0][]):rows(new Object[][]{{71L,"WORK",phone,null,null,true,0,false,rv(7)}});
			if(sql.contains("FROM dbo.OrganizationWebsites"))return website==null?rows(new Object[0][]):rows(new Object[][]{{81L,"MAIN",website,true,0,false,rv(8)}});
			if(sql.contains("FROM dbo.OrganizationEmailAddresses")||sql.contains("FROM dbo.OrganizationAddresses"))return rows(new Object[0][]);
            if(sql.contains("WITH(UPDLOCK,HOLDLOCK)")&&sql.contains("FROM dbo.OrganizationOrganizationTypes"))return assignmentRows(assignments.values().stream().filter(a->!a.deleted).sorted(Comparator.comparingInt(a->a.order)).toList());
            if(sql.contains("IsDeleted=1 ORDER BY")){int type=(Integer)b.get(3);return assignmentRows(assignments.values().stream().filter(a->a.deleted&&a.type==type).limit(1).toList());}
            if(sql.contains("OUTPUT INSERTED.Id")&&sql.contains("OrganizationOrganizationTypes")){long id=nextId++;A row=new A(id,(Integer)b.get(3),false,(Integer)b.get(4),false,rv(nextVersion++));assignments.put(id,row);return rows(new Object[][]{{id}});}
            if(sql.startsWith("SELECT Id,OrganizationId,OrganizationTypeId")){A a=assignments.get(((Number)b.get(1)).longValue());return a==null?rows(new Object[0][]):rows(new Object[][]{{a.id,42,a.type,a.primary,a.order,a.deleted,a.rowVer.clone()}});}
	            if(sql.startsWith("UPDATE dbo.Organizations SET")){organizationUpdates++;openingTokenPredicates++;byte[] expected=(byte[])b.get(16);if(!Arrays.equals(expected,organizationRowVer))return rows(new Object[0][]);compatibilityType=(Integer)b.get(1);name=(String)b.get(2);phone=(String)b.get(3);website=(String)b.get(6);organizationRowVer=rv(nextVersion++);return rows(new Object[][]{{organizationRowVer.clone()}});}
            throw new SQLException("Unexpected query: "+sql);
        }
        private int update(String sql,Map<Integer,Object>b)throws SQLException{
	            if(sql.contains("EntityActionAuditLog")){String action=String.valueOf(b.values().stream().filter(v->v instanceof String).reduce((a,z)->z).orElse("audit"));pendingAudits.add(action);if(failPrimaryAudit&&sql.contains("INSERT")&&pendingAudits.size()>0&&compatibilityType==13)throw new SQLException("audit failed");return 1;}
			if(sql.startsWith("UPDATE dbo.OrganizationPhoneNumbers")){if(!Arrays.equals(rv(7),(byte[])b.get(12)))return 0;phone=(String)b.get(2);structuredUpdates++;return 1;}
            if(sql.startsWith("UPDATE dbo.OrganizationOrganizationTypes SET IsDeleted=1")){A a=assignments.get(((Number)b.get(3)).longValue());if(!Arrays.equals(a.rowVer,(byte[])b.get(6)))return 0;a.deleted=true;a.primary=false;a.rowVer=rv(nextVersion++);return 1;}
            if(sql.startsWith("UPDATE dbo.OrganizationOrganizationTypes SET IsDeleted=0")){A a=assignments.get(((Number)b.get(2)).longValue());if(!Arrays.equals(a.rowVer,(byte[])b.get(5)))return 0;a.deleted=false;a.primary=false;a.rowVer=rv(nextVersion++);return 1;}
            if(sql.contains("SET IsPrimary=0")&&sql.contains("IsDeleted=0")){assignments.values().stream().filter(a->!a.deleted).forEach(a->{a.primary=false;a.rowVer=rv(nextVersion++);});return 1;}
            if(sql.contains("SET IsPrimary=?,SortOrder=?")){A a=assignments.get(((Number)b.get(4)).longValue());a.primary=(Boolean)b.get(1);a.order=(Integer)b.get(2);a.rowVer=rv(nextVersion++);return 1;}
            throw new SQLException("Unexpected update: "+sql);
        }
        private ResultSet assignmentRows(List<A> values){Object[][] out=values.stream().map(a->new Object[]{a.id,a.type,a.primary,a.order,a.deleted,a.rowVer.clone()}).toArray(Object[][]::new);return rows(out);}
        private Map<Long,A> copyAssignments(){Map<Long,A> copy=new LinkedHashMap<>();assignments.forEach((id,a)->copy.put(id,a.copy()));return copy;}
	        private void restore(){if(snapshot==null)return;name=snapshot.name;phone=snapshot.phone;website=snapshot.website;compatibilityType=snapshot.type;organizationRowVer=snapshot.rowVer;assignments.clear();snapshot.assignments.forEach((id,a)->assignments.put(id,a.copy()));}
	        private record Snapshot(String name,String phone,String website,int type,byte[]rowVer,Map<Long,A>assignments){}
    }
    private static final class A {final long id;final int type;boolean primary;int order;boolean deleted;byte[]rowVer;A(long id,int type,boolean primary,int order,boolean deleted,byte[]rowVer){this.id=id;this.type=type;this.primary=primary;this.order=order;this.deleted=deleted;this.rowVer=rowVer;}A copy(){return new A(id,type,primary,order,deleted,rowVer.clone());}}
	    private static ResultSet rows(Object[][]data){int[]i={-1};return(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},(p,m,a)->switch(m.getName()){case"next"->++i[0]<data.length;case"getObject"->data[i[0]][((Integer)a[0])-1];case"getInt"->((Number)data[i[0]][((Integer)a[0])-1]).intValue();case"getLong"->((Number)data[i[0]][((Integer)a[0])-1]).longValue();case"getString"->{Object value=data[i[0]][((Integer)a[0])-1];yield value==null?null:value.toString();}case"getBoolean"->(Boolean)data[i[0]][((Integer)a[0])-1];case"getBytes"->(byte[])data[i[0]][((Integer)a[0])-1];case"close"->null;default->value(m.getReturnType());});}
    private static Object value(Class<?>c){if(!c.isPrimitive())return null;if(c==boolean.class)return false;if(c==int.class)return 0;if(c==long.class)return 0L;return null;}
}
