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
    private record AuthorityCheck(String condition,String securable,String permission,Integer value,String outcome) {}
    private final List<AuthorityCheck> authorityChecks=new ArrayList<>();
    private final List<Map<String,String>> legacyEvidence=new ArrayList<>();
    private void execute(String sql)throws SQLException {
        try(var q=connection.createStatement()) {
            q.setQueryTimeout(15);
            boolean results=q.execute(sql);
            while(results || q.getUpdateCount()!=-1) {
                if(results)try(var r=q.getResultSet()) {
                    if(r.getMetaData().getColumnCount()==5 && r.getMetaData().getColumnLabel(1).equals("ConditionName"))while(r.next())
                        authorityChecks.add(new AuthorityCheck(r.getString("ConditionName"),r.getString("Securable"),
                                r.getString("PermissionName"),(Integer)r.getObject("EffectiveValue"),r.getString("Outcome")));
                    else if(r.getMetaData().getColumnLabel(1).equals("EvidenceKind"))while(r.next()) {
                        if(r.getString(1).startsWith("LegacyLogin")) {
                            var row=new HashMap<String,String>();
                            for(int i=1;i<=r.getMetaData().getColumnCount();i++)
                                row.put(r.getMetaData().getColumnLabel(i),r.getString(i));
                            legacyEvidence.add(row);
                        }
                    }
                }
                results=q.getMoreResults();
            }
        }
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
        authorityChecks.clear();
        legacyEvidence.clear();
        // Only compile-time fixed repo script paths/known safe fixture substitutions; no user SQL inputs.
        String sql=expand(root.resolve("docs/sql/auth/permission-cutover/"+name+".sql"));
        for(var e:variables.entrySet()) sql=sql.replace("$("+e.getKey()+")",e.getValue());
        assertFalse(sql.contains("$("),"all candidate variables must be explicit");
        try {for(String batch:sql.split("(?im)^GO\\s*$"))if(!batch.isBlank())execute(batch);}
        catch(Exception failure){execute("IF @@TRANCOUNT>0 ROLLBACK");throw failure;}
        finally {
            // SQLCMD exits its connection on the first error. Reused JDBC test connections need temp cleanup.
            for(String table:List.of("LegacyLoginAuthority","LegacyLoginPermissions","LegacyLoginSchemas","LegacyLoginObjects","LegacyLoginDependencies","LegacyLoginModules","PriorPermissions","PriorModules","PriorOwners","PriorMemberships","PriorPredicates","AuthAuthorityChecks","CutoverOwnership","CutoverGrantOptions","CutoverVisibility","CutoverPrincipals","CutoverObjects","CutoverColumns","ElevatedModules","AuthColumns","CutoverDelta"))
                execute("DROP TABLE IF EXISTS #"+table);
        }
    }
    /** Prevents name-based adoption of unreviewed deployed code. These bodies are deliberately
     * synthetic authority probes, never a reconstruction of Azure login/lockout behavior. */
    private void legacyLoginEvidence()throws Exception {
        var originalPermissions=catalog("database_permissions");
        var originalMemberships=catalog("database_role_members");
        var originalPredicates=catalog("security_predicates");
        rejected("inspect-legacy-login",58331);
        assertEquals(3,legacyEvidence.stream().filter(r->"MISSING".equals(r.get("DefinitionStatus"))).count());
        var names=List.of("sp_login_get_hash","sp_login_mark_failure","sp_login_mark_success");
        try {
            for(String name:names) {
                execute("CREATE PROCEDURE dbo."+name+" @FixtureId int AS SELECT Id FROM dbo.Users WHERE Id=@FixtureId AND 1=0");
                execute("GRANT EXECUTE ON dbo."+name+" TO shale_app AS dbo");
                as("shale_app","EXEC dbo."+name+" @FixtureId=-1");
            }
            for(String entry:List.of("diagnose-auth","preflight","install")) {
                rejected(entry,58307);
                assertEquals(3,authorityChecks.stream().filter(c->Integer.valueOf(1).equals(c.value())).count(),
                        "the reported profile must produce exactly three grants");
                assertTrue(authorityChecks.stream().noneMatch(c->c.value()==null),
                        "metadata DENY must not turn these known capabilities into UNKNOWN evidence");
                for(String name:names)assertTrue(authorityChecks.stream().anyMatch(c->
                        c.securable().equals("[dbo].["+name+"]") && c.permission().equals("EXECUTE")
                        && Integer.valueOf(1).equals(c.value()) && c.outcome().equals("GRANTED")),
                        "each exact reported module must remain blocked: "+entry+"/"+name);
                assertEquals("0",scalar("SELECT COUNT(*) FROM sys.objects WHERE name IN (N'LookupAuthenticationCredentialV1',N'ResolveDesktopRememberCredentialV2')"),
                        "blocked Stage A must not install either module");
            }
            var permissions=catalog("database_permissions");
            script("inspect-legacy-login");
            var modules=legacyEvidence.stream().filter(r->"LegacyLoginModule".equals(r.get("EvidenceKind"))).toList();
            assertEquals(3,modules.size());
            assertTrue(modules.stream().allMatch(r->"AVAILABLE".equals(r.get("DefinitionStatus"))
                    && "CALLER".equals(r.get("ExecutionContext")) && "dbo".equals(r.get("EffectiveOwner"))
                    && r.get("definition").contains("AND 1=0")),"dbo evidence must retain full synthetic bodies/authority under auth metadata DENY");
            assertEquals(3,legacyEvidence.stream().filter(r->"LegacyLoginParameter".equals(r.get("EvidenceKind"))
                    && "@FixtureId".equals(r.get("name"))).count());
            assertEquals(3,legacyEvidence.stream().filter(r->"LegacyLoginPermission".equals(r.get("EvidenceKind"))
                    && "shale_app".equals(r.get("Grantee")) && "dbo".equals(r.get("Grantor"))
                    && "EXECUTE".equals(r.get("permission_name")) && "G".equals(r.get("state"))
                    && r.get("Securable").startsWith("[dbo].[sp_login_")).count());
            String oldHash=modules.stream().filter(r->"sp_login_mark_failure".equals(r.get("ModuleName"))).findFirst().orElseThrow().get("DefinitionHash");
            execute("ALTER PROCEDURE dbo.sp_login_mark_failure @FixtureId int WITH EXECUTE AS OWNER AS SELECT Id FROM dbo.Users WHERE Id=@FixtureId AND 1=0");
            script("inspect-legacy-login");
            assertTrue(legacyEvidence.stream().anyMatch(r->"sp_login_mark_failure".equals(r.get("ModuleName"))
                    && "OWNER".equals(r.get("ExecutionContext")) && !oldHash.equals(r.get("DefinitionHash"))),
                    "definition and execution-authority drift must be visible, never adopted by name");
            execute("CREATE CERTIFICATE LegacyLoginFixtureCert ENCRYPTION BY PASSWORD='Synthetic-fixture-only!29' WITH SUBJECT='Disposable metadata probe'");
            execute("CREATE USER LegacyLoginFixtureSigner FROM CERTIFICATE LegacyLoginFixtureCert");
            execute("GRANT UPDATE ON dbo.Statuses TO LegacyLoginFixtureSigner AS dbo");
            execute("ADD SIGNATURE TO dbo.sp_login_mark_success BY CERTIFICATE LegacyLoginFixtureCert WITH PASSWORD='Synthetic-fixture-only!29'");
            script("inspect-legacy-login");
            assertTrue(legacyEvidence.stream().anyMatch(r->"LegacyLoginSignature".equals(r.get("EvidenceKind"))
                    && "LegacyLoginFixtureSigner".equals(r.get("SignerPrincipal"))),"signer identity must be visible without exporting keys/signature bytes");
            assertTrue(legacyEvidence.stream().anyMatch(r->"LegacyLoginPermission".equals(r.get("EvidenceKind"))
                    && "LegacyLoginFixtureSigner".equals(r.get("Grantee")) && "UPDATE".equals(r.get("permission_name"))),
                    "signer authority beyond a module's direct dependencies must be reported");
            rejected("preflight",58307);
            execute("DROP SIGNATURE FROM dbo.sp_login_mark_success BY CERTIFICATE LegacyLoginFixtureCert");
            execute("DROP USER LegacyLoginFixtureSigner; DROP CERTIFICATE LegacyLoginFixtureCert");
            assertEquals(permissions,catalog("database_permissions"),"inspection/rejection must preserve exact grantors/states");
            execute("ALTER PROCEDURE dbo.sp_login_get_hash AS /*"+"x".repeat(524289)+"*/ SELECT 1 AS SyntheticOnly");
            rejected("inspect-legacy-login",58330);
            assertTrue(legacyEvidence.isEmpty(),"over-limit collection must stop before emitting partial evidence");
            execute("ALTER PROCEDURE dbo.sp_login_get_hash WITH ENCRYPTION AS SELECT 1 AS SyntheticOnly");
            rejected("inspect-legacy-login",58331);
            assertTrue(legacyEvidence.stream().anyMatch(r->"sp_login_get_hash".equals(r.get("ModuleName"))
                    && "UNAVAILABLE".equals(r.get("DefinitionStatus"))),"encrypted code must remain incomplete evidence");
        } finally {
            for(String name:names)execute("DROP PROCEDURE IF EXISTS dbo."+name);
            execute("DROP USER IF EXISTS LegacyLoginFixtureSigner; IF EXISTS(SELECT 1 FROM sys.certificates WHERE name=N'LegacyLoginFixtureCert') DROP CERTIFICATE LegacyLoginFixtureCert");
        }
        assertEquals(originalPermissions,catalog("database_permissions"));
        assertEquals(originalMemberships,catalog("database_role_members"));
        assertEquals(originalPredicates,catalog("security_predicates"));
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
    private void authFinding(String injection,String cleanup,String condition,String permission)throws Exception {
        execute(injection);
        try {
            rejected("preflight",58307);
            assertTrue(authorityChecks.stream().anyMatch(c->c.condition().equals(condition)
                    && c.permission().equals(permission) && Integer.valueOf(1).equals(c.value())
                    && c.outcome().equals("GRANTED")),"must name the actual granted authority: "+condition+"/"+permission);
        } finally { execute(cleanup); }
    }
    private void authAuthorityDiagnostics()throws Exception {
        var originalPermissions=catalog("database_permissions");
        var originalMemberships=catalog("database_role_members");
        authFinding("GRANT EXECUTE TO shale_app", "REVOKE EXECUTE FROM shale_app", "database", "EXECUTE");
        authFinding("GRANT EXECUTE ON SCHEMA::dbo TO shale_app", "REVOKE EXECUTE ON SCHEMA::dbo FROM shale_app", "dbo-schema", "EXECUTE");
        authFinding("ALTER ROLE db_datareader ADD MEMBER shale_app", "ALTER ROLE db_datareader DROP MEMBER shale_app", "fixed-role", "MEMBER");
        authFinding("CREATE ROLE ShaleAdmins; ALTER ROLE ShaleAdmins ADD MEMBER shale_app",
                "ALTER ROLE ShaleAdmins DROP MEMBER shale_app; DROP ROLE ShaleAdmins", "role", "MEMBER");
        authFinding("GRANT UPDATE ON dbo.Users(email) TO shale_app AS dbo",
                "REVOKE UPDATE ON dbo.Users FROM shale_app AS dbo","column","UPDATE");
        authFinding("GRANT SELECT ON dbo.Statuses(Id) TO shale_app AS dbo",
                "REVOKE SELECT ON dbo.Statuses FROM shale_app AS dbo","column","SELECT");
        authFinding("CREATE ROLE AuthProbeInner; CREATE ROLE AuthProbeOuter; ALTER ROLE AuthProbeInner ADD MEMBER shale_app; "
                        +"ALTER ROLE AuthProbeOuter ADD MEMBER AuthProbeInner; GRANT UPDATE ON dbo.Users(email) TO AuthProbeOuter",
                "ALTER ROLE AuthProbeOuter DROP MEMBER AuthProbeInner; ALTER ROLE AuthProbeInner DROP MEMBER shale_app; "
                        +"DROP ROLE AuthProbeInner; DROP ROLE AuthProbeOuter","column","UPDATE");
        authFinding("GRANT UPDATE ON dbo.Users(email) TO public",
                "REVOKE UPDATE ON dbo.Users FROM public AS dbo","column","UPDATE");
        authFinding("GRANT SELECT ON dbo.Users TO shale_app WITH GRANT OPTION AS dbo",
                "REVOKE SELECT ON dbo.Users FROM shale_app CASCADE AS dbo","grant-option","SELECT");
        authFinding("CREATE SCHEMA AuthProbeOwned AUTHORIZATION shale_app",
                "DROP SCHEMA AuthProbeOwned","ownership","OWNER");
        authFinding("GRANT IMPERSONATE ON USER::PriorGrantor TO shale_app",
                "REVOKE IMPERSONATE ON USER::PriorGrantor FROM shale_app","impersonate-user","IMPERSONATE");
        execute("CREATE PROCEDURE dbo.AuthProbeModule WITH EXECUTE AS OWNER AS SELECT 1 AS FixtureOnly");
        try { authFinding("GRANT EXECUTE ON dbo.AuthProbeModule TO shale_app",
                "REVOKE EXECUTE ON dbo.AuthProbeModule FROM shale_app","object","EXECUTE"); }
        finally { execute("DROP PROCEDURE dbo.AuthProbeModule"); }
        // A deliberately unsupported permission probe returns SQL NULL: preserve it and stop.
        String snapshot=expand(root.resolve("docs/sql/auth/permission-cutover/auth-authority-snapshot.sql"));
        for(String batch:snapshot.split("(?im)^GO\\s*$"))if(!batch.isBlank())execute(batch);
        String diagnostics=expand(root.resolve("docs/sql/auth/permission-cutover/auth-authority-diagnostics.sql"))
                .replace("v.PermissionName,c.ColumnName", "N'NOT_A_PERMISSION',c.ColumnName");
        for(String batch:diagnostics.split("(?im)^GO\\s*$"))if(!batch.isBlank())execute(batch);
        assertTrue(authorityChecks.stream().anyMatch(c->c.value()==null && c.outcome().equals("UNKNOWN")
                && c.condition().equals("column")),"unknown column permission must not be called GRANTED or safe");
        rejected("auth-authority-check",58307);
        assertEquals(originalPermissions,catalog("database_permissions"),"diagnostic probes must preserve the original ACL states and grantors");
        assertEquals(originalMemberships,catalog("database_role_members"),"diagnostic probes must preserve original memberships");
    }
    void verifyAndInstall()throws Exception {
        // Reproduce the operator's complete auth ACL profile, including denied metadata.
        as("shale_app","IF COALESCE(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION'),-1)<>0 THROW 58400,N'Fixture must reproduce metadata DENY',1");
        script("diagnose-auth");
        script("preflight");
        assertTrue(authorityChecks.stream().noneMatch(c->c.value()==null || c.value()==1),
                "reported grants plus VIEW DEFINITION DENY alone must pass without unknown authority");
        authAuthorityDiagnostics();
        legacyLoginEvidence();
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
