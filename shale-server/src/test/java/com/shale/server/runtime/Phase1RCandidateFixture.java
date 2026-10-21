package com.shale.server.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Executing SYNTHETIC candidate tests. These function bodies are NOT authoritative schema evidence. */
final class Phase1RCandidateFixture {
    static final String STRICT="CREATE FUNCTION sec.fn_FilterByTenant(@Tenant int) RETURNS TABLE WITH SCHEMABINDING AS RETURN SELECT 1 AS AccessResult WHERE USER_NAME()=N'dbo' OR IS_MEMBER(N'ShaleAdmins')=1 OR @Tenant=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))";
    static final String OVERLAY="CREATE FUNCTION sec.fn_FilterByTenantOrGlobal(@Tenant int) RETURNS TABLE WITH SCHEMABINDING AS RETURN SELECT 1 AS AccessResult WHERE USER_NAME()=N'dbo' OR IS_MEMBER(N'ShaleAdmins')=1 OR @Tenant IS NULL OR @Tenant=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))";
    private final Connection c;
    private final Map<String,String> review=new HashMap<>();
    private final String engine;
    Phase1RCandidateFixture(Connection c,String db)throws Exception {
        this.c=c;
        engine=Files.readString(Path.of("..","docs","sql","phase1r","engine.sql"));
        review.put("ExpectedDatabase",db);
        review.put("ReviewedCollation","SQL_Latin1_General_CP1_CI_AS");
        review.put("ReviewedStrictHash",hash(STRICT));review.put("ReviewedOverlayHash",hash(OVERLAY));
        // Inventory is only the fixture's known session predicates and separate synthetic unrelated policy.
        review.put("ReviewedOtherPredicatesHash",scalar("SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT p.* FROM sys.security_predicates p WHERE p.target_object_id NOT IN(OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),OBJECT_ID(N'dbo.Statuses')) ORDER BY p.object_id,p.security_predicate_id FOR JSON PATH)),2)"));
    }
    private static String hash(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_16LE))).toUpperCase(Locale.ROOT);}
    private void sql(String text)throws SQLException{try(var q=c.createStatement()){q.setQueryTimeout(15);q.execute(text);}}
    private String scalar(String text)throws SQLException{try(var q=c.createStatement();var r=q.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private String snapshot()throws SQLException{return scalar("SELECT (SELECT p.* FROM sys.security_predicates p ORDER BY p.object_id,p.security_predicate_id FOR JSON PATH)");}
    private String metadata()throws SQLException{return scalar("SELECT (SELECT * FROM sys.database_permissions ORDER BY class,major_id,minor_id,grantee_principal_id,grantor_principal_id FOR JSON PATH)")+scalar("SELECT (SELECT * FROM sys.sql_modules ORDER BY object_id FOR JSON PATH)");}
    private String rows()throws SQLException{return scalar("SELECT (SELECT * FROM dbo.auditLog ORDER BY Id FOR JSON PATH)")+scalar("SELECT (SELECT * FROM dbo.Statuses ORDER BY Id FOR JSON PATH)");}
    private void setReceipt(String value)throws SQLException {
        try(var q=c.prepareStatement("EXEC sys.sp_updateextendedproperty @name=N'Shale.Phase1R.v1',@value=?,@level0type=N'SCHEMA',@level0name=N'sec'")){q.setNString(1,value);q.execute();}
    }
    private String receipt()throws SQLException{return scalar("SELECT COALESCE((SELECT CONVERT(nvarchar(4000),value) FROM sys.extended_properties WHERE class=3 AND major_id=SCHEMA_ID('sec') AND name=N'Shale.Phase1R.v1'),N'ABSENT')");}
    private void run(String mode)throws Exception{run(mode,engine);}
    private void run(String mode,String body)throws Exception{
        String text="DECLARE @Phase1RMode varchar(10)='"+mode+"';\n"+body;
        for(var entry:review.entrySet())text=text.replace("$("+entry.getKey()+")",entry.getValue());
        assertFalse(text.contains("$("));sql(text);
    }
    private void rejected(String mode,int code)throws Exception{
        String before=snapshot(),acl=metadata(),owner=receipt();
        var failure=assertThrows(SQLException.class,()->run(mode));assertEquals(code,failure.getErrorCode(),"must fail at intended guard");
        assertEquals(before,snapshot());assertEquals(acl,metadata());assertEquals(owner,receipt());
        assertEquals("0",scalar("SELECT @@TRANCOUNT"));
    }
    private void catalogSpelling()throws Exception {
        // SQL Server 2022 emits one outer pair. The old Case harness equality rejected this exact FILTER.
        assertEquals("([sec].[fn_FilterByTenant]([ShaleClientId]))",scalar("SELECT predicate_definition FROM sys.security_predicates WHERE target_object_id=OBJECT_ID('dbo.Users')"));
        String query="SELECT COUNT(*) FROM sys.security_predicates p JOIN sys.security_policies s ON s.object_id=p.object_id WHERE p.target_object_id=OBJECT_ID('dbo.Users') AND p.predicate_type_desc='FILTER' AND s.name='TenantFilter' AND s.is_enabled=1 AND LOWER(REPLACE(REPLACE(REPLACE(p.predicate_definition,'[',''),']',''),' ','')) IN (?,?)";
        for(String expected:List.of("sec.fn_filterbytenant(shaleclientid)","sec.fn_filterbytenantorglobal(shaleclientid)","sec.fn_filterbytenant(id)")) {
            try(var q=c.prepareStatement(query)){q.setString(1,expected);q.setString(2,"("+expected+")");try(var r=q.executeQuery()){assertTrue(r.next());assertEquals(expected.equals("sec.fn_filterbytenant(shaleclientid)")?1:0,r.getInt(1));}}
        }
    }
    void verify()throws Exception{
        String before=snapshot(),acl=metadata(),rows=rows();
        run("PREFLIGHT");assertEquals(before,snapshot());assertEquals("ABSENT",receipt());
        rejected("POSTCHECK",58514);
        run("ROLLBACK");assertEquals(before,snapshot()); // no receipt cannot claim any predicate
        sql("ALTER SECURITY POLICY sec.TenantFilter WITH(STATE=OFF)");rejected("APPLY",58504);
        sql("ALTER SECURITY POLICY sec.TenantFilter WITH(STATE=ON)");
        String h=review.put("ReviewedStrictHash","0".repeat(64));rejected("APPLY",58506);review.put("ReviewedStrictHash",h);
        sql("ALTER AUTHORIZATION ON SCHEMA::sec TO shale_app");rejected("APPLY",58505);sql("ALTER AUTHORIZATION ON SCHEMA::sec TO dbo");
        sql("ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.Users");
        rejected("APPLY",58508);rejected("ROLLBACK",58508);
        sql("ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.Users");
        sql("ALTER SECURITY POLICY sec.TenantFilter ADD BLOCK PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Users AFTER INSERT");
        rejected("APPLY",58508);sql("ALTER SECURITY POLICY sec.TenantFilter DROP BLOCK PREDICATE ON dbo.Users AFTER INSERT");
        sql("CREATE SECURITY POLICY sec.UnreviewedPolicy ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Users WITH(STATE=ON)");
        rejected("APPLY",58508);sql("DROP SECURITY POLICY sec.UnreviewedPolicy");
        sql("EXEC sys.sp_rename N'dbo.auditLog',N'AuditLog'");rejected("APPLY",58507);sql("EXEC sys.sp_rename N'dbo.AuditLog',N'auditLog'");
        // Failure after the first real addition must atomically undo it and leave no receipt.
        String injected=engine.replace("IF EXISTS(SELECT 1 FROM @Targets WHERE Name=N'auditLog' AND Added=1)","THROW 58599,N'Injected partial apply failure',1;\n  IF EXISTS(SELECT 1 FROM @Targets WHERE Name=N'auditLog' AND Added=1)");
        assertEquals(58599,assertThrows(SQLException.class,()->run("APPLY",injected)).getErrorCode());
        assertEquals(before,snapshot());assertEquals("ABSENT",receipt());
        run("APPLY");catalogSpelling();String applied=snapshot(),owned=receipt();assertNotEquals("ABSENT",owned);
        assertEquals("3",scalar("SELECT COUNT(*) FROM sys.security_predicates WHERE target_object_id IN(OBJECT_ID('dbo.Users'),OBJECT_ID('dbo.auditLog'),OBJECT_ID('dbo.Statuses'))"));
        run("APPLY");run("POSTCHECK");assertEquals(applied,snapshot());assertEquals(owned,receipt());
        setReceipt("{");rejected("APPLY",58510);rejected("ROLLBACK",58510);setReceipt(owned);
        setReceipt("{\"added\":[]}");rejected("APPLY",58511);rejected("ROLLBACK",58511);setReceipt(owned);
        sql("ALTER SECURITY POLICY sec.TenantFilter WITH(STATE=OFF)");rejected("ROLLBACK",58504);sql("ALTER SECURITY POLICY sec.TenantFilter WITH(STATE=ON)");
        String other=review.put("ReviewedOtherPredicatesHash","0".repeat(64));rejected("APPLY",58509);rejected("ROLLBACK",58509);review.put("ReviewedOtherPredicatesHash",other);
        String partialRollback=engine.replace("EXEC sys.sp_executesql @Sql;","EXEC sys.sp_executesql @Sql; THROW 58598,N'Injected partial rollback failure',1;");
        assertEquals(58598,assertThrows(SQLException.class,()->run("ROLLBACK",partialRollback)).getErrorCode());
        assertEquals(applied,snapshot());assertEquals(owned,receipt());
        // Replacement with the same semantics still changes predicate identity: never adopt it.
        sql("ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.Users");sql("ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Users");
        rejected("APPLY",58511);rejected("ROLLBACK",58511);
        // Discard only the exact synthetic additions/receipt after the drift probe.
        // This fixture reset is not a production drift-recovery procedure.
        sql("ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.Users, DROP FILTER PREDICATE ON dbo.auditLog, DROP FILTER PREDICATE ON dbo.Statuses");
        sql("EXEC sys.sp_dropextendedproperty @name=N'Shale.Phase1R.v1',@level0type=N'SCHEMA',@level0name=N'sec'");
        assertEquals(before,snapshot());run("APPLY");run("ROLLBACK");run("ROLLBACK");assertEquals(before,snapshot());
        // One pre-existing matching FILTER is preserved by exact rollback.
        sql("ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Users");
        String preExisting=snapshot();run("APPLY");run("APPLY");run("ROLLBACK");assertEquals(preExisting,snapshot());
        // All three pre-existing: no receipt and rollback is a no-op.
        sql("ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.auditLog, ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.Statuses");
        String all=snapshot();run("APPLY");run("ROLLBACK");assertEquals(all,snapshot());assertEquals("ABSENT",receipt());
        assertEquals(acl,metadata(),"candidate must preserve all function bodies and grants");
        assertEquals(rows,rows(),"candidate must preserve existing audit history and lookup rows");
        // Finish with actual clean candidate application and receipt for real application/session calls.
        sql("ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.Users, DROP FILTER PREDICATE ON dbo.auditLog, DROP FILTER PREDICATE ON dbo.Statuses");
        run("APPLY");run("POSTCHECK");assertNotEquals("ABSENT",receipt());
    }
}
