package com.shale.data.dao;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void migrationUsesExplicitOverlayPredicateForLinkTypesAndStrictPredicateForOwnedTables() throws IOException {
        String sql = readMigration();

        assertContains(sql, "CREATE FUNCTION sec.fn_FilterByTenantOrGlobal(@ShaleClientId int)");
        assertContains(sql, "WHERE @ShaleClientId IS NULL");
        assertContains(sql, "OR @ShaleClientId = TRY_CONVERT(int, SESSION_CONTEXT");
        assertContains(sql, "FROM sys.parameters");
        assertContains(sql, "@OverlayParameterName = prm.name");
        assertContains(sql, "@OverlayParameterSqlType = typ.name");
        assertContains(sql, "@OverlayParameterSqlType <> N'int'");
        assertContains(sql, "@OverlayDefinitionCompact NOT LIKE N'%' + @OverlayParameterCompact + N'isnull%'");
        assertContains(sql, "@OverlayParameterCompact + N'=try_convert(int,session_context(n''shaleclientid''))");
        assertContains(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.LinkTypes");
        assertContains(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.ExternalLinks");
        assertContains(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseLinks");
        assertFalse(sql.contains("@BasePredicateAllowsNull"), "LinkTypes overlay must not depend on brittle base-predicate text detection");
        assertFalse(sql.contains("LIKE N'%@shaleclientid is null%'"), "Existing overlay validation must use discovered parameter name, not literal @ShaleClientId");
        assertFalse(sql.contains("%is null%"), "Migration must not use generic %is null% predicate sniffing");
    }


    @Test
    void preflightDisplaysOverlayFunctionParameterAndDefinition() throws IOException {
        String sql = readMigration();

        assertContains(sql, "FunctionSchema = OBJECT_SCHEMA_NAME(o.object_id)");
        assertContains(sql, "FunctionName = o.name");
        assertContains(sql, "FunctionType = o.type_desc");
        assertContains(sql, "ParameterName = prm.name");
        assertContains(sql, "ParameterSqlType = typ.name");
        assertContains(sql, "FunctionDefinition = sm.definition");
    }

    @Test
    void migrationFailsFastForPolicyAmbiguityAndDisabledPolicy() throws IOException {
        String sql = readMigration();

        assertContains(sql, "SELECT @PolicyCount = COUNT(*)");
        assertContains(sql, "Multiple security policies named TenantFilter exist");
        assertContains(sql, "@PolicyEnabled = is_enabled");
        assertContains(sql, "Security policy TenantFilter is disabled");
        assertContains(sql, "SET @PolicyQualified = QUOTENAME(@PolicySchemaName) + N'.' + QUOTENAME(@PolicyName)");
        assertEquals(0, countOccurrences(sql, "CREATE SECURITY POLICY"));
    }

    @Test
    void migrationValidatesExistingTableContractsBeforeContinuing() throws IOException {
        String sql = readMigration();

        assertContains(sql, "DECLARE @RequiredColumns TABLE");
        assertContains(sql, "Existing Case Links foundation table is missing required columns or has incompatible column definitions");
        assertContains(sql, "ExpectedType = rc.TypeName");
        assertContains(sql, "ActualNullable = c.is_nullable");
        assertContains(sql, "CreatedByUserId");
        assertContains(sql, "UpdatedByUserId");
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
        assertContains(sql, "SELECT NULL, v.SystemKey, v.Name, v.Color, 1, 0, NULL, NULL");
    }

    @Test
    void migrationIncludesExplicitCrossTenantVerificationAndSessionCleanup() throws IOException {
        String sql = readMigration();

        assertContains(sql, "Tenant 7 should see zero tenant 8 LinkTypes");
        assertContains(sql, "Tenant 7 should see zero tenant 8 ExternalLinks");
        assertContains(sql, "Tenant 7 should see zero tenant 8 CaseLinks");
        assertContains(sql, "Tenant 8 should see zero tenant 7 LinkTypes");
        assertContains(sql, "Tenant 8 should see zero tenant 7 ExternalLinks");
        assertContains(sql, "Tenant 8 should see zero tenant 7 CaseLinks");
        assertContains(sql, "Tenant 7 should see global LinkTypes");
        assertContains(sql, "Tenant 8 should see global LinkTypes");
        assertContains(sql, "Tenant 7 should see own custom LinkTypes");
        assertContains(sql, "Tenant 8 should see own custom LinkTypes");
        assertContains(sql, "read_only");
        assertContains(sql, "@value = NULL");
    }

    @Test
    void migrationDoesNotCreateCompetingRlsArchitecture() throws IOException {
        String sql = readMigration();

        assertContains(sql, "Required strict predicate sec.fn_FilterByTenant is missing");
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
