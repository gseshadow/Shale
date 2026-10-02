package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class ApplicationReleaseCatalogMigrationContractTest {
    private static final Pattern GO = Pattern.compile("(?im)^\\s*GO\\s*$");

    private static Path repo(String path) {
        Path direct = Path.of(path);
        return Files.exists(direct) ? direct : Path.of("..").resolve(path);
    }

    private static String read(String path) throws Exception {
        return Files.readString(repo(path)).replace("\r\n", "\n").replace('\r', '\n');
    }

    @Test
    void migrationCreatesOnlyTheGlobalReleaseCatalogWithNumericVersionIdentity() throws Exception {
        String sql = read("docs/sql/2026-09-28_application_release_catalog_foundation_phase1a.sql");

        assertTrue(sql.contains("CREATE TABLE dbo.ApplicationReleases("));
        assertTrue(sql.contains("CREATE TABLE dbo.ApplicationReleaseItems("));
        assertTrue(sql.contains("MajorVersion int NOT NULL"));
        assertTrue(sql.contains("MinorVersion int NOT NULL"));
        assertTrue(sql.contains("BuildVersion int NOT NULL"));
        assertTrue(sql.contains("ApplicationVersion AS (") && sql.contains("PERSISTED"));
        assertTrue(sql.contains("CK_ApplicationReleases_VersionComponents CHECK(MajorVersion>=0 AND MinorVersion>=0 AND BuildVersion>=0)"));
        assertTrue(sql.contains("UX_ApplicationReleases_Channel_Version"));
        assertFalse(sql.contains("CREATE TABLE dbo.ApplicationPolicy"));
        assertFalse(sql.contains("INSERT dbo.ApplicationReleases"), "foundation must not seed a release");
    }

    @Test
    void releaseAndItemVocabularyLifecycleOrderingAndHistoryAreConstrained() throws Exception {
        String sql = read("docs/sql/2026-09-28_application_release_catalog_foundation_phase1a.sql");

        assertTrue(sql.contains("ReleaseChannel IN('PRODUCTION','PILOT','DEVELOPMENT')"));
        assertTrue(sql.contains("PublicationStatus IN('DRAFT','PUBLISHED')"));
        assertTrue(sql.contains("PublicationStatus='DRAFT' AND PublishedAt IS NULL AND PublishedByUserId IS NULL"));
        assertTrue(sql.contains("PublicationStatus='PUBLISHED' AND PublishedAt IS NOT NULL"));
        assertTrue(sql.contains("ItemType IN('FEATURE','FIX','IMPROVEMENT','IMPORTANT','LINK','VIDEO')"));
        assertTrue(sql.contains("UX_ApplicationReleaseItems_Release_SortOrder"));
        assertTrue(sql.contains("FOREIGN KEY(ApplicationReleaseId) REFERENCES dbo.ApplicationReleases(Id) ON DELETE NO ACTION"));
        assertFalse(sql.contains("ON DELETE CASCADE"));
        assertEquals(2, Pattern.compile("RowVer rowversion NOT NULL").matcher(sql).results().count());
    }

    @Test
    void migrationIsTransactionalRerunnableAndRejectsIncompatiblePartialObjects() throws Exception {
        String sql = read("docs/sql/2026-09-28_application_release_catalog_foundation_phase1a.sql");

        assertTrue(sql.contains("SET XACT_ABORT ON;"));
        assertTrue(sql.contains("BEGIN TRY") && sql.contains("BEGIN TRANSACTION;")
                && sql.contains("ROLLBACK TRANSACTION") && sql.contains("THROW;"));
        assertTrue(sql.contains("IF OBJECT_ID(N'dbo.ApplicationReleases',N'U') IS NULL"));
        assertTrue(sql.contains("IF OBJECT_ID(N'dbo.ApplicationReleaseItems',N'U') IS NULL"));
        assertTrue(sql.contains("IF NOT EXISTS(SELECT 1 FROM sys.indexes"));
        assertTrue(sql.contains("@ExpectedColumns"));
        assertTrue(sql.contains("@RequiredObjects"));
        assertTrue(sql.contains("@RequiredIndexes"));
        assertTrue(sql.contains("A required release-catalog column is missing or incompatible."));
        assertEquals(1, GO.matcher(sql).results().count());
    }

    @Test
    void globalTablesHaveNoTenantColumnOrSecurityPolicyMutation() throws Exception {
        String sql = read("docs/sql/2026-09-28_application_release_catalog_foundation_phase1a.sql");
        String createRegion = sql.substring(sql.indexOf("CREATE TABLE dbo.ApplicationReleases("),
                sql.indexOf("/* Closed rerun contract"));

        assertFalse(createRegion.contains("ShaleClientId"));
        assertFalse(sql.contains("ALTER SECURITY POLICY"));
        assertFalse(sql.contains("CREATE SECURITY POLICY"));
        assertFalse(sql.contains("sec.fn_FilterByTenant("));
        assertFalse(sql.contains("sec.fn_FilterByTenantOrGlobal("));
        assertTrue(sql.contains("sys.security_predicates"));
        assertTrue(sql.contains("Global release-catalog tables must not have an RLS security predicate."));
    }

    @Test
    void verificationIsReadOnlyAndCoversSchemaRerunAndNoRlsContracts() throws Exception {
        String sql = read("docs/sql/verification/2026-09-28_application_release_catalog_foundation_phase1a_verification.sql");

        for (String finding : new String[]{"missing ApplicationReleases table", "missing ApplicationReleaseItems table",
                "missing or incompatible required columns", "unexpected ShaleClientId columns",
                "unexpected RLS security predicates", "missing required keys, foreign keys, or CHECK constraints",
                "missing, duplicated, or incompatible required indexes", "missing numeric nonnegative version enforcement",
                "missing release channel vocabulary", "missing publication lifecycle enforcement",
                "missing release item type vocabulary", "incompatible release-item parent foreign key"}) {
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
    void migrationDoesNotTouchRuntimeOrLaterPhaseSurfaces() throws Exception {
        String sql = read("docs/sql/2026-09-28_application_release_catalog_foundation_phase1a.sql");
        for (String forbidden : new String[]{"ApplicationInstances", "UserSessions", "UserReleaseState",
                "ApplicationUpdateAttempts", "EntityActionAuditLog(", "shale-stable.json", "TenantFilter"}) {
            assertFalse(sql.contains(forbidden), forbidden);
        }
    }
}
