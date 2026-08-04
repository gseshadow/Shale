package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseDatesFoundationMigrationTest {
    private static final Path MIGRATION = resolve("docs/sql/2026-08-04_case_dates_foundation_phase1a.sql");
    private static final Path VERIFY = resolve("docs/sql/verification/case_dates_phase1a_schema_and_tenant_rls.sql");
    private static final Path ARCH = resolve("architecture/case-dates.md");

    @Test void migrationCreatesOnlyCaseDateFoundationTables() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("CREATE TABLE dbo.CaseDateTypes"));
        assertTrue(sql.contains("CREATE TABLE dbo.CaseDates"));
        assertFalse(sql.contains("CREATE TABLE dbo.CalendarEvents"));
        assertFalse(sql.contains("ALTER TABLE dbo.Cases ADD"));
        assertFalse(sql.contains("INSERT dbo.CalendarEvents"));
        assertTrue(sql.contains("CalendarEvents remains for manually created calendar events"));
    }

    @Test void migrationUsesOverlayAndStrictTenantRls() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.CaseDateTypes"));
        assertTrue(sql.contains("ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseDates"));
        assertTrue(sql.contains("FK_CaseDates_Case_Tenant FOREIGN KEY (ShaleClientId, CaseId) REFERENCES dbo.Cases(ShaleClientId, Id)"));
        assertTrue(sql.contains("Phase 1C service-layer validation still required"));
    }

    @Test void migrationDefinesConstraintsIndexesAndConservativeSeeds() throws Exception {
        String sql = Files.readString(MIGRATION);
        for (String key : new String[] {"statute_of_limitations", "tort_notice_deadline", "discovery_deadline", "date_of_injury", "date_of_medical_negligence", "date_medical_negligence_discovered", "trial", "hearing", "mediation", "deposition"}) {
            assertTrue(sql.contains(key), "missing seed " + key);
        }
        assertTrue(sql.contains("CK_CaseDateTypes_Category"));
        assertTrue(sql.contains("CK_CaseDateTypes_Color"));
        assertTrue(sql.contains("CK_CaseDates_Range"));
        assertTrue(sql.contains("UX_CaseDateTypes_Global_SystemKey"));
        assertTrue(sql.contains("UX_CaseDateTypes_Tenant_SystemKey"));
        assertTrue(sql.contains("IX_CaseDates_Case_Active"));
        assertTrue(sql.contains("IX_CaseDates_CalendarRange_Active"));
        assertTrue(sql.contains("IX_CaseDates_Type_Usage"));
        assertTrue(sql.contains("IX_CaseDates_SoftDeletion"));
        assertFalse(sql.contains("UNIQUE INDEX UX_CaseDates"));
    }

    @Test void verificationAndArchitectureDocumentOwnershipBoundary() throws Exception {
        String verification = Files.readString(VERIFY);
        String architecture = Files.readString(ARCH);
        assertTrue(verification.contains("Seeded global types" ) || verification.contains("FROM dbo.CaseDateTypes"));
        assertTrue(verification.contains("Cross-tenant") || verification.contains("c.ShaleClientId <> cd.ShaleClientId"));
        assertTrue(architecture.contains("The unified calendar is a projection hub, not the owner of dates."));
        assertTrue(architecture.contains("Other domains must not copy their dates into `CalendarEvents`"));
        assertTrue(architecture.contains("Existing fixed legal/factual `Cases` columns remain authoritative temporarily"));
        assertTrue(architecture.contains("Workflow/lifecycle dates"));
    }

    private static Path resolve(String path) {
        Path fromModule = Path.of("..", path);
        return Files.exists(fromModule) ? fromModule : Path.of(path);
    }
}
