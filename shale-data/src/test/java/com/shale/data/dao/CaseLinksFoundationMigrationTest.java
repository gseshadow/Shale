package com.shale.data.dao;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseLinksFoundationMigrationTest {

    private static final Path MIGRATION = resolveMigrationPath();

    @Test
    void migrationCreatesAllRequiredTablesAndTenantColumns() throws IOException {
        String sql = readMigration();

        assertContains(sql, "CREATE TABLE dbo.LinkTypes");
        assertContains(sql, "CREATE TABLE dbo.ExternalLinks");
        assertContains(sql, "CREATE TABLE dbo.CaseLinks");
        assertContains(sql, "ShaleClientId int NULL");
        assertContains(sql, "ShaleClientId int NOT NULL");
        assertContains(sql, "LinkTypeId int NOT NULL");
        assertContains(sql, "CaseId int NOT NULL");
        assertContains(sql, "ExternalLinkId int NOT NULL");
    }

    @Test
    void migrationClassifiesLinkTypesAsOverlayAndOwnedTablesAsStrictRls() throws IOException {
        String sql = readMigration();

        assertContains(sql, "ADD FILTER PREDICATE sec.' + QUOTENAME(@OverlayPredicate) + N'(ShaleClientId) ON dbo.LinkTypes");
        assertContains(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.ExternalLinks");
        assertContains(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseLinks");
        assertContains(sql, "Required security policy TenantFilter is missing");
        assertEquals(0, countOccurrences(sql, "CREATE SECURITY POLICY"));
    }

    @Test
    void migrationHasFilteredUniqueIndexesForSystemKeysAndActiveCaseLinkRules() throws IOException {
        String sql = readMigration();

        assertContains(sql, "UX_LinkTypes_ShaleClientId_SystemKey_NonNull");
        assertContains(sql, "WHERE SystemKey IS NOT NULL");
        assertContains(sql, "UX_CaseLinks_CaseId_ExternalLinkId_Active");
        assertContains(sql, "ON dbo.CaseLinks (ShaleClientId, CaseId, ExternalLinkId) WHERE IsDeleted = 0");
        assertContains(sql, "UX_CaseLinks_CaseId_Primary_Active");
        assertContains(sql, "ON dbo.CaseLinks (ShaleClientId, CaseId) WHERE IsDeleted = 0 AND IsPrimary = 1");
    }

    @Test
    void migrationSeedsConservativeGlobalLinkTypes() throws IOException {
        String sql = readMigration();

        for (String key : new String[] {
                "court_docket",
                "claims_portal",
                "medical_records_portal",
                "insurance_portal",
                "document_repository",
                "government_record",
                "research",
                "other"
        }) {
            assertContains(sql, "N'" + key + "'");
        }
        assertContains(sql, "SELECT NULL, v.SystemKey, v.Name, v.Color, 1, 0");
    }

    @Test
    void migrationDoesNotCreateCompetingRlsArchitecture() throws IOException {
        String sql = readMigration();

        assertContains(sql, "Required predicate sec.fn_FilterByTenant is missing");
        assertContains(sql, "Required security policy TenantFilter is missing");
        assertEquals(0, countOccurrences(sql, "CREATE SECURITY POLICY"));
        assertEquals(0, countOccurrences(sql, "CREATE SCHEMA security"));
        assertEquals(0, countOccurrences(sql, "CREATE SCHEMA rls"));
    }

    private static String readMigration() throws IOException {
        return Files.readString(MIGRATION);
    }

    private static Path resolveMigrationPath() {
        Path fromModule = Path.of("../docs/sql/2026-07-16_case_links_foundation_phase1.sql");
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        return Path.of("docs/sql/2026-07-16_case_links_foundation_phase1.sql");
    }

    private static void assertContains(String haystack, String needle) {
        assertTrue(haystack.contains(needle), () -> "Expected migration to contain: " + needle);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
