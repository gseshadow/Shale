package com.shale.data.verification;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class CaseReadVerificationSafetyTest {
    @Test void destinationCannotBeAConnectionStringRemoteHostOrExistingDatabase() {
        assertTrue(CaseReadVerificationSafety.url(14333, "master").startsWith("jdbc:sqlserver://127.0.0.1:14333;"));
        for (String value : new String[]{"Shale", "Shale_Copy", "master;serverName=example.com", "azure.database.windows.net", "../test"})
            assertThrows(IllegalStateException.class, () -> CaseReadVerificationSafety.url(14333, value));
        assertThrows(IllegalStateException.class, () -> CaseReadVerificationSafety.url(0, "master"));
    }

    @Test void productionConfigurationAndMissingOptInAreRejected() {
        Map<String, String> env = new HashMap<>(Map.of("SHALE_CASE_VERIFY_OPT_IN", CaseReadVerificationSafety.OPT_IN,
                "SHALE_CASE_VERIFY_PORT", "14333", "SHALE_CASE_VERIFY_ADMIN_USER", "synthetic",
                "SHALE_CASE_VERIFY_ADMIN_PASSWORD", "synthetic", "SHALE_CASE_VERIFY_SCHEMA_SOURCE", "offline snapshot"));
        assertEquals(14333, CaseReadVerificationSafety.configuration(env, Map.of()));
        assertThrows(IllegalStateException.class, () -> CaseReadVerificationSafety.configuration(env, Map.of("SHALE_RT_DB_URL", "synthetic")));
        for (String key : new String[]{"SHALE_APP_PASS", "SHALE_RT_DB_URL", "SHALE_AUTH_TOKEN_SECRET", "JAVA_TOOL_OPTIONS"}) {
            env.put(key, "synthetic");
            assertThrows(IllegalStateException.class, () -> CaseReadVerificationSafety.configuration(env, Map.of()));
            env.remove(key);
        }
        env.remove("SHALE_CASE_VERIFY_OPT_IN");
        assertThrows(IllegalStateException.class, () -> CaseReadVerificationSafety.configuration(env, Map.of()));
    }

    @Test void importerAcceptsSchemaDefinitionsButCannotRunMigrationDataOrServerActions() {
        assertEquals(3, CaseReadVerificationSafety.ddl("CREATE SCHEMA sec;\nGO\nCREATE TABLE dbo.Example(Id int);\nGO\n"
                + "CREATE FUNCTION sec.f(@tenant int) RETURNS TABLE WITH SCHEMABINDING AS RETURN (SELECT 1 Visible WHERE @tenant=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')));\nGO").size());
        for (String sql : new String[]{"USE master", "CREATE DATABASE Shale", "CREATE LOGIN attacker WITH PASSWORD='x'",
                "DROP TABLE dbo.Cases", "INSERT dbo.Cases VALUES(1)", "EXEC('DROP DATABASE Shale')",
                "CREATE TABLE dbo.Example(Id int); DROP DATABASE Shale", "ALTER TABLE dbo.Cases NOCHECK CONSTRAINT ALL",
                "CREATE TABLE dbo.Example(Id int REFERENCES other.dbo.Cases(Id))", "CREATE SECURITY POLICY dbo.p WITH (STATE=OFF)",
                "CREATE FUNCTION sec.f() RETURNS TABLE WITH SCHEMABINDING AS RETURN(SELECT * FROM OPENQUERY(server,'x'));",
                ":connect remote", "GO 100", "/* unterminated", "CREATE TABLE dbo.Example(Id int DEFAULT 'unterminated)"})
            assertThrows(IllegalStateException.class, () -> CaseReadVerificationSafety.ddl(sql));
    }

    @Test void commentsAndQuotedGoCannotCreateExecutableBatches() {
        assertEquals(1, CaseReadVerificationSafety.ddl("/* GO\n /* nested */ */\nCREATE TABLE dbo.Example(Name nvarchar(10) DEFAULT 'GO\nGO');\n-- GO").size());
        assertThrows(IllegalStateException.class, () -> CaseReadVerificationSafety.ddl("CREATE TABLE dbo.X(Id int)\n/* harmless */\nDROP DATABASE Shale"));
    }

    @Test void ssmsWhitespaceAndComputedIndexSettingsPreserveDefinitionsWithoutOpeningScalarExecution() {
        String function = "CREATE   FUNCTION sec.f(@tenant int) RETURNS TABLE WITH SCHEMABINDING AS RETURN (SELECT 1 Visible WHERE @tenant=CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')));";
        assertEquals(function, CaseReadVerificationSafety.ddl(function).getFirst());
        for (String setting : new String[]{"ANSI_PADDING", "ANSI_WARNINGS", "ARITHABORT", "CONCAT_NULL_YIELDS_NULL"})
            assertEquals(1, CaseReadVerificationSafety.ddl("SET " + setting + " ON").size());
        assertEquals(1, CaseReadVerificationSafety.ddl("SET NUMERIC_ROUNDABORT OFF").size());
        String foreignKey = "ALTER TABLE dbo.Child WITH CHECK ADD CONSTRAINT FK_Child FOREIGN KEY(ParentId) REFERENCES dbo.Parent(Id) ON DELETE CASCADE ON UPDATE NO ACTION";
        assertEquals(foreignKey, CaseReadVerificationSafety.ddl(foreignKey).getFirst(), "Authoritative FK actions must be retained");
        assertThrows(IllegalStateException.class, () -> CaseReadVerificationSafety.ddl(foreignKey + "; DELETE FROM dbo.Parent"));
        for (String sql : new String[]{"SET ANSI_PADDING OFF", "SET ARITHABORT ON; EXEC('SELECT 1')",
                "CREATE FUNCTION dbo.ToBitNV(@v nvarchar(255)) RETURNS bit AS BEGIN RETURN 1; END",
                "CREATE FUNCTION dbo.other() RETURNS bit AS BEGIN RETURN 1; END"})
            assertThrows(IllegalStateException.class, () -> CaseReadVerificationSafety.ddl(sql), "Unreviewed scalar bodies and extra statements must remain refused");
    }

    @Test void testOnlyLostAcknowledgementDelegatesCommitButPreCommitFaultDoesNot() throws Exception {
        for (var fault : CaseReadLocalSqlServerTest.Fault.values()) {
            int[] actualCommits = {0}, actualRollbacks = {0}, wrapperCommits = {0}, wrapperRollbacks = {0};
            Connection fakeTransport = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("commit")) actualCommits[0]++;
                        if (method.getName().equals("rollback")) actualRollbacks[0]++;
                        return null;
                    });
            Connection wrapper = CaseReadLocalSqlServerTest.faultConnection(fakeTransport, fault, wrapperCommits, wrapperRollbacks);
            assertThrows(SQLException.class, wrapper::commit);
            wrapper.rollback();
            assertEquals(fault == CaseReadLocalSqlServerTest.Fault.AFTER_COMMIT ? 1 : 0, actualCommits[0]);
            assertEquals(1, wrapperCommits[0]);
            assertEquals(1, actualRollbacks[0]);
            assertEquals(1, wrapperRollbacks[0]);
        }
    }
}
