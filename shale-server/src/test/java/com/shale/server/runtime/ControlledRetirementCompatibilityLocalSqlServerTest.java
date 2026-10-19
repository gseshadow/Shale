package com.shale.server.runtime;

import static org.junit.jupiter.api.Assertions.*;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.shale.data.auth.BCryptPasswordVerifier;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Dedicated synthetic pre-Stage-A test, not an installed 1.0.136 or serving API certification.
 * Exercises real non-dbo database users and the actual old resolver with nonempty data,
 * before/during/after the unmodified controlled SQL delta. Never accepts a remote target. */
class ControlledRetirementCompatibilityLocalSqlServerTest {
    private final String database = "ShaleRetirementCompatibility_" + UUID.randomUUID().toString().replace("-", "");
    private final String nonce = UUID.randomUUID().toString();
    private int port;

    @Test @Timeout(120)
    void preservedPasswordAndOldResolverRemainUsableAcrossControlledRetirementAndRecovery() throws Exception {
        require("LOCAL_DISPOSABLE_AUTH".equals(System.getenv("SHALE_AUTH_VERIFY_OPT_IN")));
        for (var entry : System.getenv().entrySet())
            require(!entry.getKey().matches("(?i)(SHALE_(APP|RT|AUTH(?!_VERIFY)|SERVER|SUGGESTION).*|SPRING_DATASOURCE.*|SQLCONNSTR_.*|SQLAZURECONNSTR_.*|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|MAVEN_ARGS|MAVEN_OPTS)") || entry.getValue().isBlank());
        port = Integer.parseInt(System.getenv("SHALE_AUTH_VERIFY_PORT"));
        require(port >= 1024 && port <= 65535);
        try (var c = open("master")) {
            try (var q = c.createStatement(); var r = q.executeQuery("SELECT CONVERT(int,SERVERPROPERTY('EngineEdition')),DB_ID(N'" + database + "')")) {
                require(r.next()); require(Set.of(2,3,4).contains(r.getInt(1))); require(r.getObject(2) == null);
            }
            execute(c, "CREATE DATABASE [" + database + "] COLLATE SQL_Latin1_General_CP1_CI_AS");
        }
        // Failed tests retain this exact named resource; no forced disconnect or wildcard cleanup.
        System.out.println("RETIREMENT_COMPATIBILITY LOCAL resource=" + database);
        try (var c = open(database)) {
            execute(c, "EXEC sys.sp_addextendedproperty @name=N'RetirementCompatibilityNonce',@value=N'" + nonce + "'");
            execute(c, "SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON; "
                    + "CREATE TABLE dbo.Users(Id int PRIMARY KEY,ShaleClientId int,email nvarchar(255),password_hash nvarchar(255),is_deleted bit,IsRemoved bit,failed_attempts int,lock_until datetime2,last_login datetime2);"
                    + "CREATE TABLE dbo.AuthConfig(id int,max_attempts int,lock_minutes int);"
                    + "CREATE TABLE dbo.UserSessions(SessionId uniqueidentifier PRIMARY KEY,ShaleClientId int,UserId int,RevokedAt datetime2,ExpiresAt datetime2);"
                    + "CREATE TABLE dbo.DesktopRememberCredentials(ShaleClientId int,UserId int,SessionId uniqueidentifier,InstallationId uniqueidentifier,CredentialHash binary(32),ConsumedAt datetime2,AbsoluteExpiresAt datetime2);"
                    + "CREATE USER shale_app WITHOUT LOGIN; CREATE USER shale_runtime WITHOUT LOGIN; CREATE ROLE shale_runtime_role AUTHORIZATION dbo;"
                    + "ALTER ROLE shale_runtime_role ADD MEMBER shale_runtime; GRANT SELECT ON dbo.Users TO shale_app AS dbo; DENY VIEW DEFINITION TO shale_app AS dbo;"
                    + "GRANT SELECT,UPDATE ON SCHEMA::dbo TO shale_runtime_role AS dbo; GRANT EXECUTE TO shale_runtime AS dbo; GRANT EXECUTE ON SCHEMA::dbo TO shale_runtime_role AS dbo;");
            for (String batch : Files.readString(Path.of("..", "docs/sql/2026-10-06_desktop_remember_credentials_permissions.sql")).split("(?im)^GO\\s*$"))
                if (!batch.isBlank()) execute(c, batch);
            String hash = org.mindrot.jbcrypt.BCrypt.hashpw("synthetic-password", org.mindrot.jbcrypt.BCrypt.gensalt(4));
            try (var q = c.prepareStatement("INSERT dbo.Users VALUES(1,11,N'synthetic@test',?,0,0,0,NULL,NULL)")) {
                q.setString(1, hash); q.executeUpdate();
            }
            new AuthPermissionCutoverFixture(c, database).controlledCompatibility(retired -> compatibility(c, retired));
            execute(c, "IF NOT EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=0 AND name=N'RetirementCompatibilityNonce' AND CONVERT(nvarchar(128),value)=N'" + nonce + "') THROW 58500,N'Owner mismatch',1");
        }
        try (var c = open("master")) {
            execute(c, "IF NOT EXISTS(SELECT 1 FROM [" + database + "].sys.extended_properties WHERE class=0 AND name=N'RetirementCompatibilityNonce' AND CONVERT(nvarchar(128),value)=N'" + nonce + "') THROW 58500,N'Owner mismatch',1; DROP DATABASE [" + database + "]");
        }
        System.out.println("RETIREMENT_COMPATIBILITY PASS preserved_password_old_resolver_exact_recovery_cleanup");
    }

    private void compatibility(Connection c, boolean retired) throws Exception {
        execute(c, "EXECUTE AS USER=N'shale_app'");
        try {
            try (var q = c.createStatement(); var r = q.executeQuery("SELECT password_hash FROM dbo.Users WHERE email=N'synthetic@test' AND is_deleted=0")) {
                assertTrue(r.next(), "preserved direct Users password path must return a real row");
                var verifier = new BCryptPasswordVerifier();
                assertTrue(verifier.verify("synthetic-password", r.getString(1)));
                assertFalse(verifier.verify("wrong-synthetic-password", r.getString(1)));
                assertFalse(r.next());
            }
            for (String name : List.of("sp_login_get_hash", "sp_login_mark_failure", "sp_login_mark_success")) {
                try (var q = c.createStatement(); var r = q.executeQuery("SELECT HAS_PERMS_BY_NAME(N'dbo." + name + "','OBJECT','EXECUTE')")) {
                    assertTrue(r.next()); assertEquals(retired ? 0 : 1, r.getInt(1)); assertFalse(r.wasNull());
                }
            }
        } finally { execute(c, "REVERT"); }
        UUID session = UUID.randomUUID(), installation = UUID.randomUUID();
        execute(c, "INSERT dbo.UserSessions VALUES('" + session + "',11,1,NULL,DATEADD(HOUR,1,SYSUTCDATETIME()));"
                + "INSERT dbo.DesktopRememberCredentials VALUES(11,1,'" + session + "','" + installation + "',0x01,NULL,DATEADD(HOUR,1,SYSUTCDATETIME()))");
        try {
            assertEquals(1, resolve(c, installation, "01"), "old resolver must return the designated tenant/user/session");
            assertEquals(0, resolve(c, UUID.randomUUID(), "01"), "foreign installation must not resolve");
            execute(c, "EXECUTE AS USER=N'shale_runtime'");
            try {
                execute(c, "EXEC sys.sp_set_session_context N'ShaleClientId',11; EXEC sys.sp_set_session_context N'PrincipalUserId',1");
                try (var q = c.createStatement(); var r = q.executeQuery("SELECT Id,email FROM dbo.Users WHERE ShaleClientId=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')) AND Id=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId'))")) {
                    assertTrue(r.next(), "tenant-qualified ordinary User projection must remain usable"); assertEquals(1,r.getInt(1)); assertFalse(r.next());
                }
                execute(c, "UPDATE dbo.DesktopRememberCredentials SET CredentialHash=0x02 WHERE SessionId='" + session + "' AND ShaleClientId=11 AND UserId=1");
            } finally { execute(c, "REVERT; EXEC sys.sp_set_session_context N'ShaleClientId',NULL; EXEC sys.sp_set_session_context N'PrincipalUserId',NULL"); }
            assertEquals(0, resolve(c, installation, "01"), "consumed old hash must not resolve after rotation");
            assertEquals(1, resolve(c, installation, "02"), "replacement hash must resolve");
            execute(c, "UPDATE dbo.Users SET is_deleted=1 WHERE Id=1");
            assertEquals(0, resolve(c, installation, "02"), "disabled identity must not resolve");
            execute(c, "UPDATE dbo.Users SET is_deleted=0,IsRemoved=1 WHERE Id=1");
            assertEquals(0, resolve(c, installation, "02"), "removed identity must not resolve");
            execute(c, "UPDATE dbo.Users SET IsRemoved=0 WHERE Id=1; UPDATE dbo.DesktopRememberCredentials SET AbsoluteExpiresAt=DATEADD(SECOND,-1,SYSUTCDATETIME())");
            assertEquals(0, resolve(c, installation, "02"), "expired remembered credential must not resolve");
        } finally {
            execute(c, "UPDATE dbo.Users SET is_deleted=0,IsRemoved=0 WHERE Id=1; DELETE dbo.DesktopRememberCredentials; DELETE dbo.UserSessions");
        }
    }

    private int resolve(Connection c, UUID installation, String hash) throws Exception {
        execute(c, "EXECUTE AS USER=N'shale_app'");
        try (var q = c.prepareCall("{call dbo.ResolveDesktopRememberCredential(?,?,?)}")) {
            byte[] bytes = new byte[32]; bytes[0] = (byte) Integer.parseInt(hash,16);
            q.setBytes(1,bytes); q.setObject(2,installation);
            q.setTimestamp(3,Timestamp.from(java.time.Instant.now()),Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            int count=0;
            try (var r=q.executeQuery()) { while(r.next()) { count++; assertEquals(11,r.getInt(1)); assertEquals(1,r.getInt(2)); assertNotNull(r.getString(3)); } }
            return count;
        } finally { execute(c,"REVERT"); }
    }
    private Connection open(String db) throws SQLException {
        require(db.equals("master") || db.equals(database));
        var source = new SQLServerDataSource();
        source.setURL("jdbc:sqlserver://127.0.0.1:"+port+";databaseName="+db+";encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=15000");
        source.setUser(System.getenv("SHALE_AUTH_VERIFY_ADMIN_USER"));
        source.setPassword(System.getenv("SHALE_AUTH_VERIFY_ADMIN_PASSWORD"));
        return source.getConnection();
    }
    private static void execute(Connection c,String sql) throws SQLException { try(var q=c.createStatement()){q.setQueryTimeout(10);q.execute(sql);} }
    private static void require(boolean value) { if(!value) throw new IllegalStateException("Dedicated LOCAL_DISPOSABLE_AUTH loopback configuration required"); }
}
