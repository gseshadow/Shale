package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class DesktopRememberCredentialMigrationContractTest {
    private static final Path MIGRATION=Path.of("..","docs","sql","2026-10-05_desktop_remember_credentials.sql");
    private static final Path VERIFY=Path.of("..","docs","sql","verification","2026-10-05_desktop_remember_credentials_verification.sql");
    private static final Path PERMISSIONS=Path.of("..","docs","sql","2026-10-06_desktop_remember_credentials_permissions.sql");
    private static final Path PERMISSIONS_VERIFY=Path.of("..","docs","sql","verification","2026-10-06_desktop_remember_credentials_permissions_verification.sql");

    @Test void storeColumnsTypesAndBindingsMatchDeployedSchema()throws Exception{
        String migration=Files.readString(MIGRATION);
        String store=Files.readString(Path.of("..","shale-server","src","main","java","com","shale","server","runtime","SqlRememberCredentialStore.java"));
        for(String column:new String[]{"ShaleClientId","UserId","SessionId","InstallationId","CredentialHash","AbsoluteExpiresAt"}){
            assertTrue(migration.contains(column),"migration must define "+column);
            assertTrue(store.contains(column),"store SQL must bind "+column);
        }
        assertTrue(migration.contains("CredentialHash binary(32) NOT NULL"),"SHA-256 hashes require exactly binary(32)");
        assertTrue(migration.contains("InstallationId uniqueidentifier NOT NULL"),"installation identity must remain a UUID");
        assertTrue(migration.contains("AbsoluteExpiresAt datetime2(7) NOT NULL"),"the absolute deadline must remain datetime2(7)");
        assertTrue(store.contains("q.setBytes(5,hash)"),"credential hashes must be bound as bytes, never text");
        assertTrue(store.contains("q.setTimestamp(6,Timestamp.from(expires))"),"the deadline must be bound as a timestamp");
    }

    @Test void verifierIsReadOnlyAndCoversConstraintsPredicatesAndApiPrincipalPermissions()throws Exception{
        String sql=Files.readString(VERIFY);
        for(String required:new String[]{"sys.columns","sys.key_constraints","sys.foreign_keys","sys.security_predicates","HAS_PERMS_BY_NAME","SHALE_APP_DB_USER","InspectionPrincipal"})
            assertTrue(sql.contains(required),"verification must include "+required);
        assertTrue(sql.contains("SHALE_APP_DB_USER',N'dbo.ResolveDesktopRememberCredential',N'EXECUTE'"),"the app principal must be checked for narrow lookup execution");
        assertTrue(sql.contains("SHALE_RT_DB_USER',N'dbo.DesktopRememberCredentials',N'INSERT'"),"the runtime principal must be checked for credential creation");
        assertTrue(sql.contains("SHALE_RT_DB_USER',N'dbo.UserSessions',N'UPDATE'"),"the runtime principal must be checked for rotation access");
        assertFalse(sql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|ALTER|CREATE|DROP)\\s+(dbo|sec)\\..*"),
                "verification SQL must remain read-only");
    }
    @Test void additivePermissionCorrectionUsesNarrowModuleAndObjectGrants()throws Exception{
        String migration=Files.readString(PERMISSIONS),verify=Files.readString(PERMISSIONS_VERIFY);
        assertTrue(migration.contains("CREATE OR ALTER PROCEDURE dbo.ResolveDesktopRememberCredential"),"pre-auth lookup must use one narrowly scoped module");
        assertTrue(migration.contains("GRANT EXECUTE ON OBJECT::dbo.ResolveDesktopRememberCredential TO [shale_app]"),"authentication principal receives module execution only");
        assertFalse(migration.contains("GRANT SELECT ON OBJECT::dbo.DesktopRememberCredentials TO [shale_app]"),"authentication principal must not receive base credential-table read access");
        assertTrue(migration.contains("GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.DesktopRememberCredentials TO [shale_runtime]"),"runtime receives only complete credential-table transaction permissions");
        assertFalse(migration.matches("(?is).*(db_owner|db_datareader|db_datawriter).*"),"correction must not grant broad role membership");
        for(String token:new String[]{"USER_NAME()","N'shale_app'","N'shale_runtime'","HAS_PERMS_BY_NAME","dbo.UserSessions","dbo.Users","FindingCount"})
            assertTrue(verify.contains(token),"actual-principal verifier must include "+token);
        assertFalse(verify.matches("(?is).*\\b(INSERT|UPDATE|DELETE|ALTER|CREATE|DROP)\\s+(dbo|sec)\\..*"),"permission verifier must remain read-only");
    }

}
