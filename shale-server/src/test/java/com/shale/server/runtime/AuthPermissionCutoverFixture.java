package com.shale.server.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Executes the actual candidate scripts only inside the existing random, nonce-owned local fixture.
 * Prevents inherited EXECUTE bypass, compatibility regressions and lossy grantor/state rollback. */
final class AuthPermissionCutoverFixture {
    private final Connection connection;
    private final Map<String,String> variables=new HashMap<>();
    private final Path root=Path.of("..").toAbsolutePath().normalize();
    AuthPermissionCutoverFixture(Connection connection,String database) {
        this.connection=connection;
        variables.put("ExpectedDatabase",database);
        variables.put("ReviewedIdentityMapping","CONFIRMED");
        variables.put("SecurityOwnerApproval","APPROVED");
        variables.put("CompatibleFleetDrained","CONFIRMED");
        variables.put("CallerInventoryApproved","APPROVED");
        variables.put("RollbackCompatibilityReviewed","CONFIRMED");
        variables.put("NewModuleConsumersDrained","CONFIRMED");
    }
    private void execute(String sql)throws SQLException {
        try(var q=connection.createStatement()){q.setQueryTimeout(15);q.execute(sql);}
    }
    private String expand(Path path)throws Exception {
        var result=new StringBuilder();
        for(String line:Files.readAllLines(path)) {
            if(line.startsWith(":r ")) result.append(expand(root.resolve(line.substring(3).trim())));
            else if(!line.equals(":ON ERROR EXIT")) result.append(line).append('\n');
        }
        return result.toString();
    }
    private void script(String name)throws Exception {
        // Only compile-time fixed repo script paths/known safe fixture substitutions; no user SQL inputs.
        String sql=expand(root.resolve("docs/sql/auth/permission-cutover/"+name+".sql"));
        for(var e:variables.entrySet()) sql=sql.replace("$("+e.getKey()+")",e.getValue());
        assertFalse(sql.contains("$("),"all candidate variables must be explicit");
        try {for(String batch:sql.split("(?im)^GO\\s*$"))if(!batch.isBlank())execute(batch);}
        catch(Exception failure){execute("IF @@TRANCOUNT>0 ROLLBACK");throw failure;}
        finally {
            // SQLCMD exits its connection on the first error. Reused JDBC test connections need temp cleanup.
            for(String table:List.of("PriorPermissions","PriorModules","PriorOwners","PriorMemberships","PriorPredicates","CutoverPrincipals","CutoverObjects","CutoverColumns","ElevatedModules","AuthColumns","CutoverDelta"))
                execute("DROP TABLE IF EXISTS #"+table);
        }
    }
    private String scalar(String sql)throws SQLException {
        try(var q=connection.createStatement();var r=q.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}
    }
    private Set<String> catalog(String table)throws SQLException {
        var result=new TreeSet<String>();
        try(var q=connection.createStatement();var r=q.executeQuery("SELECT * FROM sys."+table)) {
            while(r.next()){var row=new StringBuilder();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.append(r.getString(i)).append('|');result.add(row.toString());}
        }
        return result;
    }
    private void as(String principal,String sql)throws SQLException {
        execute("EXECUTE AS USER=N'"+principal+"'");
        try{execute(sql);}finally{execute("REVERT");}
    }
    private void denied(String principal,String sql)throws SQLException {
        var failure=assertThrows(SQLException.class,()->as(principal,sql),"SQL must deny the actual operation");
        assertTrue(failure.getErrorCode()==229 || (sql.startsWith("SELECT") && failure.getErrorCode()==230),"denial must be object/column permission error, not empty rows or syntax");
    }
    private void rejected(String name,int code)throws Exception {
        var failure=assertThrows(SQLException.class,()->script(name),"candidate must stop on prerequisite/drift");
        assertEquals(code,failure.getErrorCode(),"must reject the intended guard");
        assertEquals("dbo",scalar("SELECT USER_NAME()"),"failed preflight/postcheck must restore operator");
        assertEquals("0",scalar("SELECT @@TRANCOUNT"),"failed stage must not leave an open transaction");
    }
    void verifyAndInstall()throws Exception {
        // Establish the dangerous baseline with the same broad database/schema grants reported by operator.
        execute("BEGIN TRANSACTION");
        String installer=Files.readString(root.resolve("docs/sql/2026-10-14_pretenant_authentication_modules.sql"));
        for(String batch:installer.split("(?im)^GO\\s*$"))if(!batch.isBlank())execute(batch);
        as("shale_runtime","EXEC dbo.LookupAuthenticationCredentialV1 @Email=N'missing@test'");
        as("shale_runtime","EXEC dbo.ResolveDesktopRememberCredentialV2 0x00,NULL,NULL");
        execute("REVOKE EXECUTE ON OBJECT::dbo.LookupAuthenticationCredentialV1 FROM shale_runtime");
        as("shale_runtime","EXEC dbo.LookupAuthenticationCredentialV1 @Email=N'missing@test'");
        execute("ROLLBACK"); // original installer alone / REVOKE alone really are insufficient.
        var permissions=catalog("database_permissions");var members=catalog("database_role_members");
        var predicates=catalog("security_predicates");
        assertEquals("4",scalar("SELECT COUNT(DISTINCT state) FROM sys.database_permissions WHERE state IN ('G','W','D','R')"),
                "fixture must exercise original grant, grant option, denial and column REVOKE states");
        variables.put("ReviewedIdentityMapping","UNCONFIRMED");rejected("install",58301);
        variables.put("ReviewedIdentityMapping","CONFIRMED");
        assertEquals(permissions,catalog("database_permissions"));
        // Fail after module creation: neither privileged module may escape the outer transaction.
        execute("DENY EXECUTE ON SCHEMA::dbo TO shale_app AS dbo");
        rejected("install",58313);
        assertEquals("0",scalar("SELECT COUNT(*) FROM sys.objects WHERE name IN (N'LookupAuthenticationCredentialV1',N'ResolveDesktopRememberCredentialV2')"));
        execute("REVOKE EXECUTE ON SCHEMA::dbo FROM shale_app AS dbo");
        assertEquals(permissions,catalog("database_permissions"));
        script("install");
        var installed=catalog("database_permissions");
        assertTrue(installed.containsAll(permissions),"original grantors/G/W/D/R states must remain intact");
        assertEquals(members,catalog("database_role_members"));assertEquals(predicates,catalog("security_predicates"));
        rejected("install",58310);
        variables.put("LookupDefinitionHash",scalar("SELECT CONVERT(varchar(66),HASHBYTES('SHA2_256',definition),1) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'dbo.LookupAuthenticationCredentialV1')"));
        variables.put("RememberDefinitionHash",scalar("SELECT CONVERT(varchar(66),HASHBYTES('SHA2_256',definition),1) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'dbo.ResolveDesktopRememberCredentialV2')"));
        denied("shale_runtime","EXEC dbo.LookupAuthenticationCredentialV1 @Email=N'missing@test'");
        denied("shale_runtime","EXEC dbo.ResolveDesktopRememberCredentialV2 0x00,NULL,NULL");
        execute("SET XACT_ABORT OFF; BEGIN TRANSACTION; GRANT EXECUTE TO public");
        denied("shale_runtime","EXEC dbo.LookupAuthenticationCredentialV1 @Email=N'missing@test'");
        denied("shale_runtime","EXEC dbo.ResolveDesktopRememberCredentialV2 0x00,NULL,NULL");
        execute("ROLLBACK; SET XACT_ABORT ON");
        // Caller-executed same-owner wrappers skip nested target permission checks.
        execute("BEGIN TRANSACTION");
        execute("CREATE PROCEDURE dbo.AuthFenceWrapper AS EXEC dbo.LookupAuthenticationCredentialV1 @Email=N'missing@test'");
        as("shale_runtime","EXEC dbo.AuthFenceWrapper");
        rejected("module-postcheck",58328); // rolls back the injected wrapper, not a production permission.
        // A future role member inherits the role's fence, even with schema EXECUTE.
        execute("CREATE USER FutureRuntime WITHOUT LOGIN; ALTER ROLE shale_runtime_role ADD MEMBER FutureRuntime");
        denied("FutureRuntime","EXEC dbo.LookupAuthenticationCredentialV1 @Email=N'missing@test'");
        execute("ALTER ROLE shale_runtime_role DROP MEMBER FutureRuntime; DROP USER FutureRuntime");
        // Stage A preserves old desktops and all direct read/write permissions.
        as("shale_app","SELECT TOP(0) id,password_hash FROM dbo.Users");
        as("shale_app","EXEC dbo.ResolveDesktopRememberCredential 0x00,NULL,NULL");
        variables.put("CompatibleFleetDrained","UNCONFIRMED");rejected("restrict-reads",58314);
        variables.put("CompatibleFleetDrained","CONFIRMED");
        // Loss of required password writes must abort and remove BOTH new fence roles.
        execute("DENY UPDATE ON OBJECT::dbo.Users(password_hash) TO shale_runtime AS dbo");
        rejected("restrict-reads",58317);
        assertEquals("0",scalar("SELECT COUNT(*) FROM sys.database_principals WHERE name IN (N'ShaleAuthTableFenceV1',N'ShaleRuntimeHashFenceV1')"));
        execute("REVOKE UPDATE ON OBJECT::dbo.Users FROM shale_runtime AS dbo");
        assertEquals(installed,catalog("database_permissions"));
        // A direct column grant remains intact yet cannot defeat this role-based table fence.
        execute("GRANT SELECT ON OBJECT::dbo.Users(email) TO shale_app AS dbo");
        var beforeReadFence=catalog("database_permissions");
        script("restrict-reads");
        denied("shale_app","SELECT TOP(0) email FROM dbo.Users");
        // A same-principal column GRANT does override table DENY: detect it rather than falsely pass.
        execute("BEGIN TRANSACTION; GRANT SELECT ON OBJECT::dbo.Users(email) TO ShaleAuthTableFenceV1 AS dbo");
        rejected("read-postcheck",58316); // runner rolls back only that test injection.
        variables.put("AuthFenceSid",scalar("SELECT CONVERT(varchar(172),sid,1) FROM sys.database_principals WHERE name=N'ShaleAuthTableFenceV1'"));
        variables.put("RuntimeFenceSid",scalar("SELECT CONVERT(varchar(172),sid,1) FROM sys.database_principals WHERE name=N'ShaleRuntimeHashFenceV1'"));
        denied("shale_app","SELECT TOP(0) id FROM dbo.Users");
        denied("shale_app","SELECT TOP(0) password_hash FROM dbo.Users");
        denied("shale_app","SELECT TOP(0) CredentialHash FROM dbo.DesktopRememberCredentials");
        denied("shale_app","EXEC dbo.ResolveDesktopRememberCredential 0x00,NULL,NULL");
        denied("shale_runtime","SELECT TOP(0) password_hash FROM dbo.Users");
        // Public nested inheritance and direct column grant cannot undo the column DENY.
        execute("GRANT SELECT ON OBJECT::dbo.Users(password_hash) TO shale_runtime AS dbo");
        denied("shale_runtime","SELECT TOP(0) password_hash FROM dbo.Users");
        execute("REVOKE SELECT ON OBJECT::dbo.Users FROM shale_runtime AS dbo");
        as("shale_runtime","SELECT TOP(0) id,name_first,name_last,email,color,is_admin,is_attorney,ShaleClientId FROM dbo.Users");
        as("shale_runtime","UPDATE dbo.Users SET password_hash=N'synthetic-not-a-credential',password_alg='bcrypt' WHERE 1=0");
        as("shale_runtime","SELECT TOP(0) * FROM dbo.auditLog"); // explicitly still broad, not append-only.
        as("shale_runtime","UPDATE dbo.auditLog SET EntryCode=EntryCode WHERE 1=0; DELETE dbo.auditLog WHERE 1=0");
        String sid=variables.put("AuthFenceSid","0x00");rejected("rollback-reads",58319);variables.put("AuthFenceSid",sid);
        execute("GRANT VIEW DEFINITION ON OBJECT::dbo.Statuses TO ShaleAuthTableFenceV1 AS dbo");
        rejected("rollback-reads",58321);
        execute("REVOKE VIEW DEFINITION ON OBJECT::dbo.Statuses FROM ShaleAuthTableFenceV1 AS dbo");
        script("rollback-reads");
        assertEquals(beforeReadFence,catalog("database_permissions"),"rollback must restore original grantors/states, including W and column R");
        execute("REVOKE SELECT ON OBJECT::dbo.Users FROM shale_app AS dbo");
        assertEquals(installed,catalog("database_permissions"));
        assertEquals(members,catalog("database_role_members"));
        String hash=variables.put("LookupDefinitionHash","0x00");rejected("rollback-install",58323);variables.put("LookupDefinitionHash",hash);
        script("rollback-install");
        assertEquals(permissions,catalog("database_permissions"));assertEquals(members,catalog("database_role_members"));
        assertEquals(predicates,catalog("security_predicates"));
        script("install");script("restrict-reads"); // leave real application scenarios on candidate restrictions.
    }
}
