package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class OrganizationTypesFoundationMigrationContractTest {
    private static final Pattern GO = Pattern.compile("(?im)^\\s*GO\\s*$");
    private static Path repo(String path) {
        Path direct = Path.of(path);
        return Files.exists(direct) ? direct : Path.of("..").resolve(path);
    }
    private static String read(String path) throws Exception {
        return Files.readString(repo(path)).replace("\r\n", "\n").replace('\r', '\n');
    }

    @Test void migrationPreservesBaselineAndBuildsDefinitionContract() throws Exception {
        String s = read("docs/sql/2026-09-08_organization_types_foundation_phase1a.sql");
        assertTrue(s.contains("COL_LENGTH(N'dbo.OrganizationTypes',N'SystemKey') IS NULL AND ((SELECT COUNT_BIG(*) FROM dbo.OrganizationTypes)<>7"));
        assertTrue(s.contains("COL_LENGTH(N'dbo.OrganizationTypes',N'SystemKey') IS NOT NULL"), "reruns tolerate later definitions while preserving the seven baseline identities");
        for (String row : new String[]{"(1,N'Provider',7)", "(7,N'Other',7)", "N'provider',N'#0F766E',0", "N'other',N'#6B7280',6"}) assertTrue(s.contains(row), row);
        assertTrue(s.contains("sys.default_constraints dc JOIN sys.columns c"));
        assertTrue(s.contains("ALTER COLUMN ShaleClientId int NULL"));
        for (String column : new String[]{"SystemKey", "Description", "Color", "SortOrder", "IsActive", "IsDeleted", "CreatedAt", "CreatedByUserId", "UpdatedAt", "UpdatedByUserId", "DeletedAt", "DeletedByUserId", "RowVer"}) assertTrue(s.contains("N'" + column + "'") || s.contains("ADD " + column), column);
        assertTrue(s.contains("UX_OrganizationTypes_Global_SystemKey"));
        assertTrue(s.contains("UX_OrganizationTypes_Tenant_SystemKey"));
        assertTrue(s.contains("Color=UPPER(Color)"));
        assertFalse(s.contains("INSERT dbo.OrganizationTypes"));
    }

    @Test void assignmentBackfillOwnershipHistoryAndCompatibilityAreEnforced() throws Exception {
        String s = read("docs/sql/2026-09-08_organization_types_foundation_phase1a.sql");
        assertTrue(s.contains("CREATE TABLE dbo.OrganizationOrganizationTypes"));
        assertTrue(s.contains("FOREIGN KEY(ShaleClientId,OrganizationId) REFERENCES dbo.Organizations(ShaleClientId,Id)"));
        assertTrue(s.contains("FOREIGN KEY(OrganizationTypeId) REFERENCES dbo.OrganizationTypes(OrganizationTypeId)"));
        assertTrue(s.contains("IsDeleted=1 AND IsPrimary=0"));
        assertTrue(s.contains("UX_OrganizationOrganizationTypes_ActivePrimary"));
        assertTrue(s.contains("WHERE IsDeleted=0 AND IsPrimary=1"));
        assertTrue(s.contains("INSERT dbo.OrganizationOrganizationTypes"));
        assertTrue(s.contains("NOT EXISTS(SELECT 1 FROM dbo.OrganizationOrganizationTypes"));
        assertTrue(s.contains("#OrganizationBaseline"));
        assertFalse(s.contains("UPDATE dbo.Organizations"));
        assertFalse(s.contains("DELETE FROM"));
    }

    @Test void migrationUsesOneGuardedTransactionAndEstablishedRls() throws Exception {
        String s = read("docs/sql/2026-09-08_organization_types_foundation_phase1a.sql");
        assertTrue(s.contains("SET XACT_ABORT ON;"));
        assertTrue(s.indexOf("@OperatorVerifiedAllTenantVisibility bit=0") < s.indexOf("BEGIN TRANSACTION;"));
        assertTrue(s.contains("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL"));
        assertTrue(s.contains("USER_NAME() IN(N'shale_app',N'shale_runtime')"));
        assertTrue(s.contains("sec.fn_FilterByTenantOrGlobal"));
        assertTrue(s.contains("sec.fn_FilterByTenant"));
        assertTrue(s.contains("ALTER SECURITY POLICY "));
        assertFalse(s.contains("CREATE SECURITY POLICY"));
        assertTrue(s.contains("END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK TRANSACTION; THROW; END CATCH;"));
        assertEquals(1, GO.matcher(s).results().count());
    }

    @Test void verificationIsReadOnlyGuardedAndCoversRequiredFindings() throws Exception {
        String v = read("docs/sql/verification/2026-09-08_organization_types_foundation_phase1a_verification.sql");
        for (String finding : new String[]{"column contract mismatches", "ShaleClientId defaults still present", "unexpected mutation of baseline OrganizationTypes", "invalid SystemKeys", "duplicate global SystemKeys", "missing/incompatible required indexes", "missing/invalid assignment or actor foreign keys", "duplicate active assignments", "multiple active primary assignments", "assignment tenant mismatches", "definition tenant mismatches", "Organizations missing active primary assignment", "assignments not matching compatibility OrganizationTypeId", "live baseline Organization count changed", "missing/wrong/unexpected RLS predicates"}) assertTrue(v.contains(finding), finding);
        assertTrue(v.contains("expected 176"));
        assertTrue(v.contains("=171") && v.contains("=5"));
        assertTrue(v.contains("IF EXISTS(SELECT 1 FROM @Findings WHERE FindingCount<>0) THROW"));
        assertFalse(v.contains("ALTER TABLE"));
        assertFalse(v.contains("ALTER SECURITY POLICY"));
        assertFalse(v.contains("INSERT dbo."));
        assertFalse(GO.matcher(v).find());
    }

    @Test void forbiddenPhaseOneCSurfacesRemainUntouched() throws Exception {
        String s = read("docs/sql/2026-09-08_organization_types_foundation_phase1a.sql");
        for (String table : new String[]{"CaseParties", "PartyRoles", "CaseOrganizations", "EntityActionAuditLog"}) assertFalse(s.contains(table), table);
        assertFalse(s.contains("Contacts.OrganizationId"));
    }
}
