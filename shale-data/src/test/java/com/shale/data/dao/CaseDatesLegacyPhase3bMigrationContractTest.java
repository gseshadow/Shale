package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseDatesLegacyPhase3bMigrationContractTest {
    private static final Path PREFLIGHT = resolve("docs/sql/2026-08-05_case_dates_legacy_phase3b_preflight.sql");
    private static final Path SEED_TYPES = resolve("docs/sql/2026-08-05_case_dates_legacy_phase3b_seed_types.sql");
    private static final Path BACKFILL = resolve("docs/sql/2026-08-05_case_dates_legacy_phase3b_backfill_case_dates.sql");
    private static final Path STATUS_BLOCKER = resolve("docs/sql/2026-08-05_case_dates_legacy_phase3b_status_history_blocker.sql");
    private static final Path VALIDATION = resolve("docs/sql/2026-08-05_case_dates_legacy_phase3b_post_validation.sql");

    private static final String[] LEGACY_FIELDS = {
            "CallerDate", "CallerTime", "DateOfMedicalNegligence", "DateMedicalNegligenceWasDiscovered",
            "DateOfInjury", "StatuteOfLimitations", "TortNoticeDeadline", "DiscoveryDeadline",
            "DateFeeAgreementSigned", "DateNonEngagementLetterSent", "AcceptedDate", "DeniedDate", "ClosedDate"
    };

    private static final String[] SYSTEM_KEYS = {
            "intake", "date_of_injury", "date_of_medical_negligence", "medical_negligence_discovered",
            "statute_of_limitations", "tort_notice_deadline", "discovery_deadline",
            "fee_agreement_signed", "non_engagement_letter_sent"
    };

    @Test void preflightAndValidationCoverEveryApprovedLegacyField() throws Exception {
        String preflight = Files.readString(PREFLIGHT);
        String validation = Files.readString(VALIDATION);
        for (String field : LEGACY_FIELDS) {
            assertTrue(preflight.contains(field), "preflight missing " + field);
            assertTrue(validation.contains(field), "validation missing " + field);
        }
        assertTrue(preflight.contains("ORPHAN_CALLER_TIME"));
        assertTrue(validation.contains("FINAL_VALIDATION_SUMMARY") || preflight.contains("PREFLIGHT_VALIDATION_SUMMARY"));
    }

    @Test void seedAndBackfillUseApprovedSystemKeysWithoutDuplicatingTypes() throws Exception {
        String seed = Files.readString(SEED_TYPES);
        String backfill = Files.readString(BACKFILL);
        for (String key : SYSTEM_KEYS) {
            assertTrue(seed.contains(key), "seed missing " + key);
            assertTrue(backfill.contains(key), "backfill missing " + key);
        }
        assertTrue(seed.contains("WHERE NOT EXISTS"));
        assertTrue(seed.contains("Conflicting global CaseDateType definition exists"));
        assertTrue(seed.contains("Duplicate global CaseDateType system key exists"));
    }

    @Test void backfillIsTenantAwareDuplicateGuardedAndHandlesIntakeTime() throws Exception {
        String sql = Files.readString(BACKFILL);
        assertTrue(sql.contains("ShaleClientId"));
        assertTrue(sql.contains("cd.ShaleClientId=l.ShaleClientId"));
        assertFalse(sql.contains("DATEADD(NANOSECOND"));
        assertTrue(sql.contains("CallerTime IS NOT NULL AND CallerDate IS NULL"));
        assertTrue(sql.contains("ActiveExactMatches=0"));
        assertTrue(sql.contains("ActiveExactMatches>1"));
        assertTrue(sql.contains("DATETIME2FROMPARTS"));
    }

    @Test void mutationScriptsHaveTransactionAndErrorHandlingProtections() throws Exception {
        for (Path path : new Path[] {SEED_TYPES, BACKFILL}) {
            String sql = Files.readString(path);
            assertTrue(sql.contains("SET NOCOUNT ON"), path + " missing NOCOUNT");
            assertTrue(sql.contains("SET XACT_ABORT ON"), path + " missing XACT_ABORT");
            assertTrue(sql.contains("BEGIN TRANSACTION"), path + " missing transaction");
            assertTrue(sql.contains("ROLLBACK TRANSACTION"), path + " missing rollback");
            assertTrue(sql.contains("THROW"), path + " missing clear failure path");
        }
    }

    @Test void preflightIsReadOnlyAndHasMachineReadableSummary() throws Exception {
        String sql = Files.readString(PREFLIGHT);
        assertFalse(sql.matches("(?is).*\bINSERT\b.*"));
        assertFalse(sql.matches("(?is).*\bUPDATE\b.*"));
        assertFalse(sql.matches("(?is).*\bDELETE\b.*"));
        assertFalse(sql.matches("(?is).*\bMERGE\b.*"));
        assertFalse(sql.matches("(?is).*\bALTER\b.*"));
        assertFalse(sql.matches("(?is).*\bDROP\b.*"));
        assertFalse(sql.matches("(?is).*\bTRUNCATE\b.*"));
        assertFalse(sql.matches("(?is).*\bEXEC(UTE)?\b.*"));
        assertTrue(sql.contains("PREFLIGHT_VALIDATION_SUMMARY"));
        assertTrue(sql.contains("READY_FOR_SEED_REVIEW"));
        assertTrue(sql.contains("BLOCKED_FOR_BACKFILL"));
    }

    @Test void packageMaterializesResolvedRowsAndDoesNotReuseCteAcrossStatements() throws Exception {
        String sql = Files.readString(BACKFILL);
        assertTrue(sql.contains("INTO #ResolvedBackfill"));
        assertTrue(sql.contains("CREATE UNIQUE CLUSTERED INDEX IX_ResolvedBackfill_Row"));
        assertFalse(sql.contains("WITH L AS"), "backfill should use materialized source tables instead of chained single-use CTEs");
        assertFalse(sql.contains("FROM C WHERE"), "backfill must not reuse a CTE named C across statements");
    }

    @Test void packageUsesActualRemovalColumnAndRejectsIsRemoved() throws Exception {
        String combined = Files.readString(PREFLIGHT) + Files.readString(BACKFILL) + Files.readString(VALIDATION);
        assertTrue(combined.contains("COL_LENGTH(N'dbo.CaseDates', N'IsDeleted')"));
        assertTrue(combined.contains("ISNULL(cd.IsDeleted,0)"));
        assertFalse(combined.contains("cd.IsRemoved"));
    }

    @Test void intakeExpressionIsConsistentAcrossSqlFilesAndAvoidsDateaddOverflow() throws Exception {
        String expression = "DATETIME2FROMPARTS(DATEPART(year, c.CallerDate)";
        assertTrue(Files.readString(PREFLIGHT).contains(expression));
        assertTrue(Files.readString(BACKFILL).contains(expression));
        assertTrue(Files.readString(VALIDATION).contains(expression));
        assertFalse(Files.readString(BACKFILL).contains("DATEADD(NANOSECOND"));
    }

    @Test void actorAndTypeResolutionAreTenantAware() throws Exception {
        String sql = Files.readString(BACKFILL);
        assertTrue(sql.contains("DECLARE @MigrationActors TABLE"));
        assertTrue(sql.contains("u.ShaleClientId=r.ShaleClientId"));
        assertFalse(sql.contains("SELECT TOP(1) @MigrationActorUserId"));
        assertTrue(sql.contains("#EffectiveCaseDateTypes"));
        assertTrue(sql.contains("t.ShaleClientId = tenantScope.ShaleClientId OR t.ShaleClientId IS NULL"));
        assertFalse(sql.contains("t.ShaleClientId IS NULL AND t.SystemKey=l.SystemKey"));
    }

    @Test void validationHasMachineReadableSummaryAndThrowsOnBlockers() throws Exception {
        String sql = Files.readString(VALIDATION);
        assertTrue(sql.contains("FINAL_VALIDATION_SUMMARY"));
        assertTrue(sql.contains("@BlockerCount"));
        assertTrue(sql.contains("IF @BlockerCount <> 0 THROW"));
    }

    @Test void packageDoesNotClearDropCalendarWriteOrChangeRuntimeStatusHistory() throws Exception {
        String combined = Files.readString(PREFLIGHT) + Files.readString(SEED_TYPES) + Files.readString(BACKFILL)
                + Files.readString(STATUS_BLOCKER) + Files.readString(VALIDATION);
        assertFalse(combined.matches("(?is).*DROP\\s+COLUMN.*"));
        assertFalse(combined.matches("(?is).*UPDATE\\s+dbo\\.Cases\\s+SET.*=\\s*NULL.*"));
        assertFalse(combined.matches("(?is).*INSERT\\s+dbo\\.CalendarEvents.*"));
        assertFalse(combined.matches("(?is).*UPDATE\\s+dbo\\.CalendarEvents.*"));
        assertFalse(combined.matches("(?is).*DELETE\\s+FROM\\s+dbo\\.CalendarEvents.*"));
        assertFalse(Files.readString(STATUS_BLOCKER).matches("(?is).*INSERT\\s+INTO\\s+dbo\\.CaseStatuses.*"));
        assertTrue(Files.readString(STATUS_BLOCKER).contains("BLOCKER REPORT"));
    }

    private static Path resolve(String path) {
        Path fromModule = Path.of("..", path);
        return Files.exists(fromModule) ? fromModule : Path.of(path);
    }
}
