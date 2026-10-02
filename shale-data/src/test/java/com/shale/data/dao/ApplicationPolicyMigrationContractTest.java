package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class ApplicationPolicyMigrationContractTest {
    private static final String MIGRATION =
            "docs/sql/2026-09-28_application_policy_foundation_phase1b.sql";
    private static final String VERIFICATION =
            "docs/sql/verification/2026-09-28_application_policy_foundation_phase1b_verification.sql";
    private static final Pattern GO = Pattern.compile("(?im)^\\s*GO\\s*$");

    private static Path repo(String path) {
        Path direct = Path.of(path);
        return Files.exists(direct) ? direct : Path.of("..").resolve(path);
    }

    private static String read(String path) throws Exception {
        return Files.readString(repo(path)).replace("\r\n", "\n").replace('\r', '\n');
    }

    @Test
    void policyIsGlobalRevisionedHistoryWithOneCurrentRowPerChannel() throws Exception {
        String sql = read(MIGRATION);
        String table = sql.substring(sql.indexOf("CREATE TABLE dbo.ApplicationPolicy("),
                sql.indexOf("/* Closed rerun contract"));

        assertTrue(table.contains("RevisionNumber bigint NOT NULL"));
        assertTrue(table.contains("IsCurrent bit NOT NULL"));
        assertTrue(table.contains("PublishedAt datetime2(7) NOT NULL"));
        assertTrue(table.contains("SupersededAt datetime2(7) NULL"));
        assertTrue(table.contains("RowVer rowversion NOT NULL"));
        assertTrue(sql.contains("UX_ApplicationPolicy_Channel_Revision"));
        assertTrue(sql.contains("UX_ApplicationPolicy_Channel_Current"));
        assertTrue(sql.contains("WHERE IsCurrent=1"));
        assertFalse(table.contains("ShaleClientId"));
        assertFalse(sql.contains("CREATE SECURITY POLICY"));
        assertFalse(sql.contains("ALTER SECURITY POLICY"));
        assertTrue(sql.contains("sys.security_predicates"));
    }

    @Test
    void policyVocabularyDeadlineAndLifecycleAreConstrained() throws Exception {
        String sql = read(MIGRATION);

        assertTrue(sql.contains("ReleaseChannel IN('PRODUCTION','PILOT','DEVELOPMENT')"));
        assertTrue(sql.contains("AccessMode IN('NORMAL','READ_ONLY','MAINTENANCE','BLOCKED')"));
        assertTrue(sql.contains("RequiredUpdateDeadline datetime2(7) NULL"));
        assertTrue(sql.contains("RevisionNumber>0"));
        assertTrue(sql.contains("IsCurrent=1 AND SupersededAt IS NULL AND SupersededByUserId IS NULL"));
        assertTrue(sql.contains("IsCurrent=0 AND SupersededAt IS NOT NULL"));
        assertTrue(sql.contains("SupersededAt>=PublishedAt"));
    }

    @Test
    void releaseReferencesAreNullableValidAndNonCascading() throws Exception {
        String sql = read(MIGRATION);
        String architecture = read("docs/architecture/application-release-session-management.md");

        for (String role : new String[]{"Latest", "MinimumRecommended", "MinimumAllowed"}) {
            assertTrue(sql.contains("FK_ApplicationPolicy_" + role + "Release"));
            assertTrue(sql.contains("FOREIGN KEY(" + role + "ReleaseId) REFERENCES dbo.ApplicationReleases(Id) ON DELETE NO ACTION"));
            assertTrue(sql.contains("IX_ApplicationPolicy_" + role + "Release"));
        }
        assertFalse(sql.contains("ON DELETE CASCADE"));
        assertFalse(sql.contains("CREATE TRIGGER"), "cross-table version/channel rules belong to the future mutation service");
        assertTrue(architecture.contains("Cross-table channel equality, published-release eligibility,"));
        assertTrue(architecture.contains("minimumAllowed <= minimumRecommended <= latest"));
        assertTrue(architecture.contains("future mutation service must enforce them transactionally"));
    }

    @Test
    void migrationIsTransactionalRerunnableClosedAndEmpty() throws Exception {
        String sql = read(MIGRATION);

        assertTrue(sql.contains("SET XACT_ABORT ON;"));
        assertTrue(sql.contains("BEGIN TRY") && sql.contains("BEGIN TRANSACTION;")
                && sql.contains("ROLLBACK TRANSACTION") && sql.contains("THROW;"));
        assertTrue(sql.contains("IF OBJECT_ID(N'dbo.ApplicationPolicy',N'U') IS NULL"));
        assertTrue(sql.contains("IF NOT EXISTS(SELECT 1 FROM sys.indexes"));
        assertTrue(sql.contains("@ExpectedColumns"));
        assertTrue(sql.contains("@RequiredObjects"));
        assertTrue(sql.contains("@RequiredIndexes"));
        assertTrue(sql.contains("ApplicationPolicy contains unexpected columns."));
        assertFalse(sql.contains("INSERT dbo.ApplicationPolicy"), "foundation must not seed policy");
        assertEquals(1, GO.matcher(sql).results().count());
    }

    @Test
    void verificationIsReadOnlyAndCoversPolicyAndPhase1aRegressionContracts() throws Exception {
        String sql = read(VERIFICATION);

        for (String finding : new String[]{"missing ApplicationPolicy table", "policy rows unexpectedly seeded",
                "missing or incompatible policy columns", "unexpected ShaleClientId policy column",
                "unexpected ApplicationPolicy RLS security predicate", "disabled, untrusted, or cascading policy foreign keys",
                "missing policy release-channel vocabulary", "missing reserved policy access-mode vocabulary",
                "missing current and superseded lifecycle rejection rules", "missing one-current-policy-per-channel filter",
                "Phase 1A release tables missing", "Phase 1A release columns changed", "Phase 1A named objects changed",
                "Phase 1A unexpected RLS predicates"}) {
            assertTrue(sql.contains(finding), finding);
        }
        assertTrue(sql.contains("IF EXISTS(SELECT 1 FROM @Findings WHERE FindingCount<>0)"));
        assertFalse(sql.contains("CREATE TABLE"));
        assertFalse(sql.contains("ALTER TABLE"));
        assertFalse(sql.contains("ALTER SECURITY POLICY"));
        assertFalse(sql.contains("INSERT dbo."));
        assertFalse(GO.matcher(sql).find());
    }

    @Test
    void migrationExcludesRuntimeAuditAndLaterPhaseSurfaces() throws Exception {
        String sql = read(MIGRATION);
        for (String forbidden : new String[]{"ApplicationInstances", "UserSessions", "UserReleaseState",
                "ApplicationUpdateAttempts", "EntityActionAuditLog(", "TenantFilter", "CREATE TRIGGER"}) {
            assertFalse(sql.contains(forbidden), forbidden);
        }
    }
}
