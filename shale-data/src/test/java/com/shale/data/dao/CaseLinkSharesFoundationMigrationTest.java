package com.shale.data.dao;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseLinkSharesFoundationMigrationTest {

    private static final Path MIGRATION = resolveMigrationPath();

    @Test
    void migrationCreatesCaseLinkSharesWithRequiredColumns() throws IOException {
        String sql = readMigration();

        assertContains(sql, "CREATE TABLE dbo.CaseLinkShares");
        for (String column : new String[] {
                "Id int IDENTITY(1,1) NOT NULL",
                "ShaleClientId int NOT NULL",
                "CaseLinkId int NOT NULL",
                "ContactId int NOT NULL",
                "SharedAt datetime2 NOT NULL",
                "Notes nvarchar(1000) NULL",
                "IsDeleted bit NOT NULL",
                "DeletedAt datetime2 NULL",
                "DeletedByUserId int NULL",
                "CreatedByUserId int NOT NULL",
                "UpdatedByUserId int NULL",
                "CreatedAt datetime2 NOT NULL",
                "UpdatedAt datetime2 NULL",
                "RowVer rowversion NOT NULL"
        }) {
            assertContains(sql, column);
        }
        assertContains(sql, "DF_CaseLinkShares_SharedAt DEFAULT (SYSUTCDATETIME())");
        assertContains(sql, "DF_CaseLinkShares_IsDeleted DEFAULT (0)");
        assertContains(sql, "DF_CaseLinkShares_CreatedAt DEFAULT (SYSUTCDATETIME())");
    }

    @Test
    void migrationUsesVerifiedSingleColumnForeignKeysWithoutCascadeDelete() throws IOException {
        String sql = readMigration();

        assertContains(sql, "FK_CaseLinkShares_ShaleClientId_ShaleClients FOREIGN KEY (ShaleClientId) REFERENCES dbo.ShaleClients (Id)");
        assertContains(sql, "FK_CaseLinkShares_CaseLinkId_CaseLinks FOREIGN KEY (CaseLinkId) REFERENCES dbo.CaseLinks (Id)");
        assertContains(sql, "FK_CaseLinkShares_ContactId_Contacts FOREIGN KEY (ContactId) REFERENCES dbo.Contacts (Id)");
        assertContains(sql, "FK_CaseLinkShares_CreatedByUserId_Users FOREIGN KEY (CreatedByUserId) REFERENCES dbo.Users (Id)");
        assertContains(sql, "FK_CaseLinkShares_UpdatedByUserId_Users FOREIGN KEY (UpdatedByUserId) REFERENCES dbo.Users (Id)");
        assertContains(sql, "FK_CaseLinkShares_DeletedByUserId_Users FOREIGN KEY (DeletedByUserId) REFERENCES dbo.Users (Id)");
        assertFalse(sql.toUpperCase().contains("ON DELETE CASCADE"));
        assertContains(sql, "Required base table primary-key contract is missing or incompatible");
    }

    @Test
    void migrationCreatesFilteredActiveDuplicateAndContactLookupIndexes() throws IOException {
        String sql = readMigration();

        assertContains(sql, "UX_CaseLinkShares_CaseLinkId_ContactId_Active");
        assertContains(sql, "ON dbo.CaseLinkShares (ShaleClientId, CaseLinkId, ContactId) WHERE IsDeleted = 0");
        assertContains(sql, "IX_CaseLinkShares_ShaleClientId_ContactId_Active");
        assertContains(sql, "ON dbo.CaseLinkShares (ShaleClientId, ContactId) INCLUDE (CaseLinkId, SharedAt) WHERE IsDeleted = 0");
    }

    @Test
    void migrationAddsStrictTenantFilterPredicateOnly() throws IOException {
        String sql = readMigration();

        assertContains(sql, "Required strict predicate sec.fn_FilterByTenant is missing");
        assertContains(sql, "Required security policy TenantFilter is missing");
        assertContains(sql, "Multiple security policies named TenantFilter exist");
        assertContains(sql, "Security policy TenantFilter is disabled");
        assertContains(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseLinkShares");
        assertFalse(sql.contains("sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.CaseLinkShares"));
        assertEquals(0, countOccurrences(sql, "CREATE SECURITY POLICY"));
        assertFalse(sql.contains("CREATE FUNCTION sec.fn_FilterByTenant"));
    }

    @Test
    void migrationValidatesPreflightAndExistingTableContract() throws IOException {
        String sql = readMigration();

        assertContains(sql, "READ-ONLY PREFLIGHT");
        assertContains(sql, "dbo.CaseLinkShares");
        assertContains(sql, "STRING_AGG(c.name, N',')");
        assertContains(sql, "Existing dbo.CaseLinkShares table is missing required columns or has incompatible column definitions");
        assertContains(sql, "Required default DF_CaseLinkShares_SharedAt is missing");
        assertContains(sql, "Required default DF_CaseLinkShares_IsDeleted is missing");
        assertContains(sql, "Required default DF_CaseLinkShares_CreatedAt is missing");
        assertContains(sql, "TenantFilterEnabled");
    }

    @Test
    void migrationIncludesTenantIsolationVerificationThatRollsBack() throws IOException {
        String sql = readMigration();

        assertContains(sql, "OPTIONAL TENANT-ISOLATION VERIFICATION");
        assertContains(sql, "not previously marked read_only");
        assertContains(sql, "@Tenant7 int = 7, @Tenant8 int = 8");
        assertContains(sql, "Phase 5.2 verification tenant 7");
        assertContains(sql, "Phase 5.2 verification tenant 8");
        assertContains(sql, "Tenant 7 can see tenant 8 CaseLinkShares verification row");
        assertContains(sql, "Tenant 8 can see tenant 7 CaseLinkShares verification row");
        assertContains(sql, "Duplicate active CaseLinkShares insert unexpectedly succeeded");
        assertContains(sql, "ROLLBACK TRANSACTION");
        assertContains(sql, "CaseLinkShares verification rows persisted after rollback");
    }

    @Test
    void migrationDocumentsPhase53AndExcludesApplicationImplementation() throws IOException {
        String sql = readMigration();

        assertContains(sql, "Phase 5.3 Case Link deletion must soft-delete active CaseLinkShares");
        assertContains(sql, "Contact soft deletion must not cascade-delete shares");
        assertContains(sql, "SharedAt is the user-asserted sharing time");
        assertContains(sql, "Single-column foreign keys are used");
        assertFalse(sql.contains("CaseServicePort"));
        assertFalse(sql.contains("CaseLinkCardFactory"));
        assertFalse(sql.contains("JavaFX"));
        assertFalse(sql.contains("REST routes"));
    }

    private static String readMigration() throws IOException {
        return Files.readString(MIGRATION);
    }

    private static Path resolveMigrationPath() {
        Path fromModule = Path.of("../docs/sql/2026-07-16_case_link_shares_foundation_phase52.sql");
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        return Path.of("docs/sql/2026-07-16_case_link_shares_foundation_phase52.sql");
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
