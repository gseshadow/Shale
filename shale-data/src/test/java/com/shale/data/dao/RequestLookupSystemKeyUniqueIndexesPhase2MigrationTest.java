package com.shale.data.dao;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

final class RequestLookupSystemKeyUniqueIndexesPhase2MigrationTest {
    private static final Path MIGRATION = resolve("docs/sql/2026-07-22_request_lookup_systemkey_unique_indexes_phase2.sql");

    @Test
    void migrationDefinesAllFourExactFilteredUniqueIndexes() throws Exception {
        String sql = read(MIGRATION);

        assertContains(sql, "CREATE UNIQUE NONCLUSTERED INDEX UX_RequestMethods_Global_SystemKey\n        ON dbo.RequestMethods (SystemKey)\n        WHERE ShaleClientId IS NULL AND SystemKey IS NOT NULL");
        assertContains(sql, "CREATE UNIQUE NONCLUSTERED INDEX UX_RequestMethods_Tenant_SystemKey\n        ON dbo.RequestMethods (ShaleClientId, SystemKey)\n        WHERE ShaleClientId IS NOT NULL AND SystemKey IS NOT NULL");
        assertContains(sql, "CREATE UNIQUE NONCLUSTERED INDEX UX_RequestStatuses_Global_SystemKey\n        ON dbo.RequestStatuses (SystemKey)\n        WHERE ShaleClientId IS NULL AND SystemKey IS NOT NULL");
        assertContains(sql, "CREATE UNIQUE NONCLUSTERED INDEX UX_RequestStatuses_Tenant_SystemKey\n        ON dbo.RequestStatuses (ShaleClientId, SystemKey)\n        WHERE ShaleClientId IS NOT NULL AND SystemKey IS NOT NULL");
    }

    @Test
    void migrationChecksIdempotencyIndependentlyAndValidatesExistingDefinitions() throws Exception {
        String sql = read(MIGRATION);

        for (String index : new String[]{
                "UX_RequestMethods_Global_SystemKey",
                "UX_RequestMethods_Tenant_SystemKey",
                "UX_RequestStatuses_Global_SystemKey",
                "UX_RequestStatuses_Tenant_SystemKey"}) {
            assertEquals(5, countOccurrences(sql, index), "Expected independent create, verify, throw, and post-deploy references for " + index);
            assertContains(sql, "name = N'" + index + "'");
            assertContains(sql, "Index " + index + " exists with a different definition. Stop for manual review.");
        }

        assertTrue(countOccurrences(sql, "i.is_unique = 1") >= 4);
        assertTrue(countOccurrences(sql, "ic.is_included_column = 1") >= 4);
        assertTrue(countOccurrences(sql, "ic.key_ordinal = 1") >= 4);
        assertTrue(countOccurrences(sql, "c.name = N'SystemKey'") >= 4);
        assertTrue(countOccurrences(sql, "c.name = N'ShaleClientId'") >= 2);
        assertContains(sql, "i.filter_definition = N'([ShaleClientId] IS NULL AND [SystemKey] IS NOT NULL)'");
        assertContains(sql, "i.filter_definition = N'([ShaleClientId] IS NOT NULL AND [SystemKey] IS NOT NULL)'");
    }

    @Test
    void migrationUsesSingleTransactionAndLetsCreateIndexValidatePhysicalRows() throws Exception {
        String sql = read(MIGRATION);

        assertContains(sql, "SET NOCOUNT ON;");
        assertContains(sql, "SET XACT_ABORT ON;");
        assertEquals(1, countOccurrences(sql, "BEGIN TRANSACTION;"));
        assertEquals(1, countOccurrences(sql, "COMMIT TRANSACTION;"));
        assertContains(sql, "BEGIN TRY");
        assertContains(sql, "BEGIN CATCH");
        assertContains(sql, "IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;");
        assertContains(sql, "including rows that row-level security may hide");
        assertFalse(sql.contains("GROUP BY ShaleClientId, SystemKey"), "Migration should not pre-audit only visible rows for duplicates.");
    }

    @Test
    void migrationDoesNotDropIndexesMutateDataOrTouchUnrelatedSchema() throws Exception {
        String upper = read(MIGRATION).toUpperCase(Locale.ROOT);

        for (String token : new String[]{"DROP INDEX", "INSERT ", "UPDATE ", "MERGE ", "DELETE ", "ALTER SECURITY POLICY", "CREATE SECURITY POLICY", "CREATE TABLE", "ALTER TABLE", "FOREIGN KEY", "REQUESTMETHODID", "REQUESTSTATUSID"}) {
            assertFalse(upper.contains(token), () -> "Unexpected token: " + token);
        }
    }

    @Test
    void verificationQueryReportsExpectedCatalogShapeForBothTables() throws Exception {
        String sql = read(MIGRATION);

        for (String projection : new String[]{"TableName", "IndexName", "IsUnique", "FilterDefinition", "KeyOrdinal", "ColumnName", "IsIncludedColumn"}) assertContains(sql, projection);
        assertContains(sql, "FROM sys.indexes AS i");
        assertContains(sql, "JOIN sys.index_columns AS ic");
        assertContains(sql, "JOIN sys.columns AS c");
        assertContains(sql, "OBJECT_ID(N'dbo.RequestMethods')");
        assertContains(sql, "OBJECT_ID(N'dbo.RequestStatuses')");
    }

    private static String read(Path p) throws Exception { return Files.readString(p); }
    private static Path resolve(String repoRelative) { Path p = Path.of("..", repoRelative); return Files.exists(p) ? p : Path.of(repoRelative); }
    private static void assertContains(String s, String n) { assertTrue(s.contains(n), () -> "Expected to contain: " + n); }
    private static int countOccurrences(String s, String n) { int count = 0, index = 0; while ((index = s.indexOf(n, index)) >= 0) { count++; index += n.length(); } return count; }
}
