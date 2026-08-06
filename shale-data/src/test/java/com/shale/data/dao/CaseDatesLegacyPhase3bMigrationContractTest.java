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
        assertFalse(sql.matches("(?is).*\\bINSERT\\b.*"));
        assertFalse(sql.matches("(?is).*\\bUPDATE\\b.*"));
        assertFalse(sql.matches("(?is).*\\bDELETE\\b.*"));
        assertFalse(sql.matches("(?is).*\\bMERGE\\b.*"));
        assertFalse(sql.matches("(?is).*\\bALTER\\b.*"));
        assertFalse(sql.matches("(?is).*\\bDROP\\b.*"));
        assertFalse(sql.matches("(?is).*\\bTRUNCATE\\b.*"));
        assertFalse(sql.matches("(?is).*\\bEXEC(UTE)?\\b.*"));
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
        assertTrue(combined.contains("COL_LENGTH(N'dbo.CaseDates',N'IsDeleted')"));
        assertTrue(combined.contains("ISNULL(cd.IsDeleted,0)"));
        assertFalse(combined.contains("cd.IsRemoved"));
    }

    @Test void intakeExpressionIsConsistentAcrossSqlFilesAndAvoidsDateaddOverflow() throws Exception {
        String expression = "DATETIME2FROMPARTS(DATEPART(year,c.CallerDate)";
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
        assertTrue(sql.contains("tw.ShaleClientId,gw.ShaleClientId"));
        assertTrue(sql.contains("ShaleClientId=pt.ShaleClientId AND SystemKey=e.SystemKey"));
        assertFalse(sql.contains("t.ShaleClientId IS NULL AND t.SystemKey=l.SystemKey"));
    }

    @Test void validationHasMachineReadableSummaryAndThrowsOnBlockers() throws Exception {
        String sql = Files.readString(VALIDATION);
        assertTrue(sql.contains("FINAL_VALIDATION_SUMMARY"));
        assertTrue(sql.contains("@BlockerCount"));
        assertTrue(sql.contains("IF @BlockerCount<>0 THROW"));
    }

    @Test void missingTypesCannotDisappearThroughAnInnerJoin() throws Exception {
        String backfill = Files.readString(BACKFILL);
        String validation = Files.readString(VALIDATION);
        assertTrue(backfill.contains("@SourceRowCount<>@ResolvedRowCount"));
        assertTrue(backfill.contains("Source rows were not resolved exactly once"));
        assertTrue(backfill.contains("@InsertedOrExactExistingCount<>@SourceRowCount"));
        assertTrue(validation.contains("LEFT JOIN #EffectiveCaseDateTypes"));
        assertTrue(validation.contains("UnresolvedTypeSourceCount"));
        assertTrue(validation.contains("@UnresolvedTypeCount+@DestinationMismatchCount"));
    }

    @Test void semanticOccurrenceChecksIncludeGlobalAndTenantTypeVariants() throws Exception {
        for (Path path : new Path[] {PREFLIGHT, BACKFILL, VALIDATION}) {
            String sql = Files.readString(path);
            assertTrue(sql.contains("variants.SystemKey=l.SystemKey"), path.toString());
            assertTrue(sql.contains("variants.ShaleClientId=l.ShaleClientId OR variants.ShaleClientId IS NULL"), path.toString());
            assertTrue(sql.contains("ActiveSameKeyDifferentValue"), path.toString());
            assertTrue(sql.contains("RemovedExactMatches"), path.toString());
        }
    }

    @Test void overlayResolutionCoversOverrideResetDuplicatesAndMissingGlobal() throws Exception {
        for (Path path : new Path[] {PREFLIGHT, BACKFILL, VALIDATION}) {
            String sql = Files.readString(path);
            assertTrue(sql.contains("NonDeletedTenantCount"), path.toString());
            assertTrue(sql.contains("DeletedTenantCount"), path.toString());
            assertTrue(sql.contains("GlobalCount"), path.toString());
            assertTrue(sql.contains("GLOBAL_OR_RESET_FALLBACK"), path.toString());
            assertTrue(sql.contains("ISNULL(IsDeleted,0)=0 ORDER BY Id"), path.toString());
            assertTrue(sql.contains("NonDeletedTenantCount,0)>1"), path.toString());
            assertTrue(sql.contains("GlobalCount<>1"), path.toString());
            assertTrue(sql.contains("IsActive,0)<>1"), path.toString());
        }
    }

    @Test void preflightReadinessMirrorsEverySeedRejection() throws Exception {
        String sql = Files.readString(PREFLIGHT);
        assertTrue(sql.contains("01_GLOBAL_SEED_COMPATIBILITY"));
        assertTrue(sql.contains("ExactNameConflictCount"));
        assertTrue(sql.contains("CategoryConflictCount"));
        assertTrue(sql.contains("SupportsTimeConflictCount"));
        assertTrue(sql.contains("InactiveCount"));
        assertTrue(sql.contains("DeletedCount"));
        assertTrue(sql.contains("GlobalDefinitionCount>1"));
        assertTrue(sql.contains("@SeedBlockers=0"));
    }

    @Test void casesSafeIdProjectionsUseAuthoritativeIdColumn() throws Exception {
        String sql = Files.readString(PREFLIGHT);
        assertTrue(sql.contains("'06_ORPHAN_CALLER_TIME' SectionName,ShaleClientId,Id AS CaseId FROM dbo.Cases"));
        assertTrue(sql.contains("'12_WORKFLOW_FLAG_DATE_MISMATCH' SectionName,ShaleClientId,Id CaseId FROM dbo.Cases"));
        assertFalse(sql.matches("(?is).*SELECT\\s+'06_ORPHAN_CALLER_TIME'.*?ShaleClientId\\s*,\\s*CaseId\\s+FROM\\s+dbo\\.Cases.*"));
    }

    @Test void zeroOrTenantScopedVisibilityCanNeverReportReady() throws Exception {
        for (Path path : new Path[] {PREFLIGHT, VALIDATION}) {
            String sql = Files.readString(path);
            assertTrue(sql.contains("SESSION_CONTEXT(N'ShaleClientId')"), path.toString());
            assertTrue(sql.contains("@SessionTenantId IS NULL"), path.toString());
            assertTrue(sql.contains("@IsAdministrativePrincipal=1"), path.toString());
            assertTrue(sql.contains("@ParticipatingTenantCount>0"), path.toString());
            assertTrue(sql.contains("@EligibleSourceRowCount>0"), path.toString());
            assertTrue(sql.contains("OPERATOR_VERIFICATION_REQUIRED"), path.toString());
            assertTrue(sql.contains("@OperatorVerifiedAllTenantVisibility=1"), path.toString());
        }
        String preflight = Files.readString(PREFLIGHT);
        assertTrue(preflight.contains("@VisibilityConfirmed=1 AND @SeedBlockers=0"));
        assertTrue(preflight.contains("CASE WHEN @BackfillBlockerCount=0 THEN 'READY_FOR_BACKFILL_REVIEW'"));
    }

    @Test void backfillIndependentlyRejectsEveryMutableDataBlocker() throws Exception {
        String sql = Files.readString(BACKFILL);
        assertTrue(sql.contains("CallerTime IS NOT NULL AND CallerDate IS NULL"));
        assertTrue(sql.contains("Workflow flag/date mismatches block backfill"));
        assertTrue(sql.contains("Broken or cross-tenant CaseDate case/type relationships block backfill"));
        assertTrue(sql.contains("Missing or cross-tenant CaseDate creator relationships block backfill"));
        assertTrue(sql.contains("c.Id IS NULL OR t.Id IS NULL"));
        assertTrue(sql.contains("u.Id=cd.CreatedByUserId AND u.ShaleClientId=cd.ShaleClientId"));
        assertTrue(sql.contains("Same-SystemKey CaseDates conflict"));
        assertTrue(sql.contains("Every source key must resolve to one valid effective type"));
    }

    @Test void summariesExposeVisibilityAndConsistentBlockerTotals() throws Exception {
        String preflight = Files.readString(PREFLIGHT);
        for (String column : new String[] {"ParticipatingTenantCount", "EligibleSourceRowCount",
                "VisibilityReadiness", "BackfillBlockerCount"}) {
            assertTrue(preflight.contains(column), "preflight summary missing " + column);
        }
        assertTrue(preflight.contains("(CASE WHEN @VisibilityConfirmed=1 THEN 0 ELSE 1 END)+"));
        assertTrue(preflight.contains("@BackfillBlockerCount BackfillBlockerCount"));
        String validation = Files.readString(VALIDATION);
        assertTrue(validation.contains("c.Id IS NULL OR t.Id IS NULL"));
        assertTrue(validation.contains("@VisibilityConfirmed=1 THEN 0 ELSE 1"));
        assertTrue(validation.contains("CrossTenantOrBrokenRelationshipCount"));
    }

    @Test void preflightDoesNotEmitDatesWithRowLevelSafeIds() throws Exception {
        String sql = Files.readString(PREFLIGHT);
        assertFalse(sql.contains("'08_SAME_SYSTEMKEY_OCCURRENCE_EVIDENCE' SectionName,*"));
        assertTrue(sql.contains("'08_SAME_SYSTEMKEY_OCCURRENCE_EVIDENCE' SectionName,ShaleClientId,SystemKey,COUNT_BIG(*)"));
        String conflictResult = sql.substring(sql.indexOf("SELECT '09_OCCURRENCE_CONFLICT_SAFE_IDS'"),
                sql.indexOf("SELECT '10_CROSS_TENANT_CASE_TYPE'"));
        assertTrue(conflictResult.contains("ShaleClientId,CaseId,FieldName,SystemKey"));
        assertFalse(conflictResult.contains("ExpectedStartsAt"));
        assertFalse(conflictResult.contains("ExpectedAllDay"));
        assertFalse(conflictResult.matches("(?is).*(CallerDate|LegacyDate|StartsAt|EndsAt|CallerTime).*"));
    }

    @Test void everyOutwardPreflightProjectionOmitsLegacyDateTimes() throws Exception {
        String sql = Files.readString(PREFLIGHT);
        assertTrue(sql.contains("'03_ELIGIBLE_COUNTS_BY_FIELD' SectionName,ShaleClientId,FieldName,COUNT_BIG(*) EligibleRows FROM"));
        int[] outwardSelects = {0};
        sql.lines().map(String::trim).filter(line -> line.startsWith("SELECT '")).forEach(line -> {
            outwardSelects[0]++;
            int from = line.indexOf(" FROM ");
            String projection = from < 0 ? line : line.substring(0, from);
            assertFalse(projection.matches("(?is).*\\b(ExpectedStartsAt|LegacyDate|CallerDate|CallerTime|StartsAt|EndsAt|EffectiveDate|AcceptedDate|DeniedDate|ClosedDate)\\b.*"),
                    "outward preflight projection discloses a date/time: " + projection);
        });
        assertTrue(outwardSelects[0] >= 16, "expected every named preflight result and summary to be scanned");
    }

    @Test void globalSeedRequiresAdministrativeNullTenantContext() throws Exception {
        String sql = Files.readString(SEED_TYPES);
        int guard = sql.indexOf("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL");
        int globalInsert = sql.indexOf("INSERT dbo.CaseDateTypes");
        assertTrue(guard >= 0 && guard < globalInsert, "visibility guard must precede global mutation");
        assertTrue(sql.contains("IS_SRVROLEMEMBER(N'sysadmin')"));
        assertTrue(sql.contains("IS_MEMBER(N'db_owner')"));
        assertTrue(sql.contains("THROW 56104"));
    }

    @Test void standaloneStatusReportIsExplicitlySessionScoped() throws Exception {
        String sql = Files.readString(STATUS_BLOCKER);
        assertTrue(sql.contains("SESSION-SCOPED, NON-AUTHORITATIVE BLOCKER REPORT"));
        assertTrue(sql.contains("@TenantId = NULL does not prove all-tenant visibility"));
        assertTrue(sql.contains("SESSION_SCOPED_NON_AUTHORITATIVE"));
        assertTrue(sql.contains("SESSION_CONTEXT(N'ShaleClientId')"));
    }

    @Test void statusBlockerUsesTenantOrGlobalStatusDefinition() throws Exception {
        String sql = Files.readString(STATUS_BLOCKER);
        assertTrue(sql.contains("s.ShaleClientId=c.ShaleClientId OR s.ShaleClientId IS NULL"));
        assertTrue(sql.contains("cs.CaseId=c.Id"));
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
