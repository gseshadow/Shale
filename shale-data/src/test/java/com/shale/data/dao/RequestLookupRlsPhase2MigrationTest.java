package com.shale.data.dao;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class RequestLookupRlsPhase2MigrationTest {
    private static final Path MIGRATION = resolve("docs/sql/2026-07-22_request_lookup_rls_phase2.sql");

    @Test
    void migrationAddsOnlyRequestLookupOverlayRlsPredicates() throws Exception {
        String sql = read(MIGRATION);

        assertContains(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.RequestMethods");
        assertContains(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.RequestStatuses");
        assertEquals(1, countOccurrences(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.RequestMethods"));
        assertEquals(1, countOccurrences(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.RequestStatuses"));

        assertFalse(sql.contains("ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.LinkTypes"));
        assertFalse(sql.contains("ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.MaterialTypes"));
        assertFalse(sql.contains("CREATE SECURITY POLICY"));
        assertFalse(sql.contains("CREATE FUNCTION sec.fn_FilterByTenantOrGlobal"));
    }

    @Test
    void migrationChecksExactPredicateCatalogStateIndependentlyForEachTarget() throws Exception {
        String sql = read(MIGRATION);
        String methods = block(sql, "/* Protect global-plus-current-tenant RequestMethods lookup definitions. */", "/* Protect global-plus-current-tenant RequestStatuses lookup definitions. */");
        String statuses = block(sql, "/* Protect global-plus-current-tenant RequestStatuses lookup definitions. */", "SET @Sql = N'ALTER SECURITY POLICY '");

        for (String block : new String[]{methods, statuses}) {
            assertContains(block, "FROM sys.security_predicates AS p");
            assertContains(block, "p.object_id = @PolicyObjectId");
            assertContains(block, "p.predicate_type_desc = N'FILTER'");
            assertContains(block, "p.predicate_definition = N'[sec].[fn_FilterByTenantOrGlobal]([ShaleClientId])'");
        }
        assertContains(methods, "p.target_object_id = OBJECT_ID(N'dbo.RequestMethods')");
        assertContains(statuses, "p.target_object_id = OBJECT_ID(N'dbo.RequestStatuses')");
        assertContains(methods, "dbo.RequestMethods already has a non-matching FILTER predicate");
        assertContains(statuses, "dbo.RequestStatuses already has a non-matching FILTER predicate");
    }

    @Test
    void migrationDoesNotContainUnrelatedSchemaIndexDataOrApplicationChanges() throws Exception {
        String sql = read(MIGRATION).toUpperCase(java.util.Locale.ROOT);

        for (String token : new String[]{"CREATE TABLE", "ALTER TABLE", "CREATE INDEX", "CREATE UNIQUE", "INSERT ", "UPDATE ", "MERGE ", "DELETE ", "BACKFILL", "NORMALIZE", "REQUESTMETHODID", "REQUESTSTATUSID"}) {
            assertFalse(sql.contains(token), () -> "Unexpected token: " + token);
        }
        assertContains(sql, "ALTER SECURITY POLICY");
        assertContains(sql, "WITH (STATE = ON)");
    }

    @Test
    void verificationQueryCoversAllMatureLookupTablesAndRequiredColumns() throws Exception {
        String sql = read(MIGRATION);

        for (String table : new String[]{"dbo.LinkTypes", "dbo.MaterialTypes", "dbo.RequestMethods", "dbo.RequestStatuses"}) assertContains(sql, "OBJECT_ID(N'" + table + "')");
        for (String projection : new String[]{"TargetTable", "SecurityPolicy", "PredicateType", "PredicateDefinition", "PolicyEnabled"}) assertContains(sql, projection);
        assertContains(sql, "JOIN sys.security_policies AS sp");
    }

    private static String read(Path p) throws Exception { return Files.readString(p); }
    private static Path resolve(String repoRelative) { Path p = Path.of("..", repoRelative); return Files.exists(p) ? p : Path.of(repoRelative); }
    private static void assertContains(String s, String n) { assertTrue(s.contains(n), () -> "Expected to contain: " + n); }
    private static int countOccurrences(String s, String n) { int count = 0, index = 0; while ((index = s.indexOf(n, index)) >= 0) { count++; index += n.length(); } return count; }
    private static String block(String s, String start, String end) { int i = s.indexOf(start); assertTrue(i >= 0, () -> "Missing start: " + start); int j = s.indexOf(end, i + start.length()); assertTrue(j >= 0, () -> "Missing end: " + end); return s.substring(i, j); }
}
