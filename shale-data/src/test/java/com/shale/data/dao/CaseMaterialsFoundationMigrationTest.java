package com.shale.data.dao;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class CaseMaterialsFoundationMigrationTest {
    private static final Path MIGRATION = resolve("docs/sql/2026-07-21_case_materials_foundation_phase1.sql");
    private static final Path RLS = resolve("docs/sql/verification/case_materials_tenant7_tenant8_rls.sql");

    @Test
    void migrationDefinesAllFourTablesAndNoDeferredTables() throws Exception {
        String sql = read(MIGRATION);
        for (String table : new String[]{"MaterialTypes", "MaterialRequests", "MaterialRequestFollowUps", "MaterialItems"}) assertContains(sql, "CREATE TABLE dbo." + table);
        assertFalse(sql.contains("CREATE TABLE dbo.MaterialCustodyEvents"));
    }

    @Test
    void schemaIncludesKeysConstraintsIndexesRowVersionsAndSoftDeleteColumns() throws Exception {
        String sql = read(MIGRATION);
        for (String token : new String[]{"PK_MaterialTypes", "PK_MaterialRequests", "PK_MaterialRequestFollowUps", "PK_MaterialItems", "RowVer rowversion NOT NULL", "IsDeleted bit NOT NULL", "DeletedAt datetime2 NULL", "DeletedByUserId int NULL", "UX_MaterialTypes_ShaleClientId_SystemKey_NonNull", "IX_MaterialRequests_Case_Active", "IX_MaterialRequests_Assignee_Open", "IX_MaterialRequests_Type_Status", "IX_MaterialRequestFollowUps_Request_Chronology", "IX_MaterialItems_Case_Active", "IX_MaterialItems_Request", "IX_MaterialItems_Type", "IX_MaterialItems_ExternalLink", "CK_MaterialRequests_Method", "CK_MaterialRequests_Status", "CK_MaterialRequests_Source", "CK_MaterialRequests_Closure", "CK_MaterialItems_Format", "CK_MaterialItems_Completeness", "CK_MaterialItems_CustodyStatus"}) assertContains(sql, token);
        assertContains(sql, "CONSTRAINT CK_MaterialRequests_Closure CHECK ((Status IN ('CLOSED','CANCELLED') AND ClosedAt IS NOT NULL AND ClosedByUserId IS NOT NULL AND ClosureReason IS NOT NULL) OR (Status NOT IN ('CLOSED','CANCELLED') AND ClosedAt IS NULL AND ClosedByUserId IS NULL AND ClosureReason IS NULL))");
    }

    @Test
    void tenantAndCaseIntegrityUsesStrictRlsAndCompositeRequestLinkProtection() throws Exception {
        String sql = read(MIGRATION);
        assertContains(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.MaterialTypes");
        assertContains(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.MaterialRequests");
        assertContains(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.MaterialRequestFollowUps");
        assertContains(sql, "ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.MaterialItems");
        assertContains(sql, "UX_MaterialRequests_Tenant_Case_Id");
        assertContains(sql, "FOREIGN KEY (ShaleClientId, CaseId, MaterialRequestId) REFERENCES dbo.MaterialRequests(ShaleClientId, CaseId, Id)");
        assertContains(sql, "tenant/global compatibility is registered for DAO validation");
    }

    @Test
    void materialTypeSeedsAndOverlayBehaviorAreAuthoritative() throws Exception {
        String sql = read(MIGRATION);
        for (String key : new String[]{"medical_records", "billing_records", "police_report", "photographs", "other"}) assertContains(sql, "N'" + key + "'");
        assertContains(sql, "prefer a non-deleted current-tenant row over the global row");
        assertContains(sql, "deleted tenant override as a reset marker so the global default is effective again");
        assertFalse(sql.contains("MaterialFormats"));
    }

    @Test
    void auditSqlAllowlistsPreserveExistingValuesAndAddCaseMaterialsValues() throws Exception {
        String sql = read(MIGRATION);
        for (String value : new String[]{"LINK_TYPE", "CASE_LINK", "CASE_LINK_SHARE", "MATERIAL_TYPE", "MATERIAL_REQUEST", "MATERIAL_REQUEST_FOLLOW_UP", "MATERIAL_ITEM", "CREATED", "OVERRIDE_CREATED", "UPDATED", "ACTIVATED", "DEACTIVATED", "OVERRIDE_RESET", "DELETED", "PRIMARY_SET", "REORDERED", "ADDED", "REMOVED", "STATUS_CHANGED", "FOLLOW_UP_ADDED", "LINKED", "UNLINKED", "LOCATION_UPDATED", "RELEASED"}) assertContains(sql, "'" + value + "'");
    }

    @Test
    void appendOnlyFollowUpsHaveNoOrdinaryUpdateDeleteOrSoftDeleteFields() throws Exception {
        String sql = read(MIGRATION);
        String followUps = sql.substring(sql.indexOf("CREATE TABLE dbo.MaterialRequestFollowUps"), sql.indexOf("CREATE TABLE dbo.MaterialItems"));
        assertFalse(followUps.contains("IsDeleted"));
        assertFalse(followUps.contains("DeletedAt"));
        assertFalse(followUps.contains("UpdatedAt"));
        assertContains(sql, "Append-only follow-ups");
        assertContains(sql, "corrections must be additive rows");
    }

    @Test
    void rlsVerificationScriptCoversTenantsSevenAndEightAndGlobalTypes() throws Exception {
        String sql = read(RLS);
        for (String token : new String[]{"@value = 7", "@value = 8", "Tenant 7 should see zero tenant 8 requests", "Tenant 8 should see zero tenant 7 requests", "Tenant 7 should see global MaterialTypes", "Tenant 8 should see global MaterialTypes", "@value = NULL"}) assertContains(sql, token);
    }

    @Test
    void phaseOneDoesNotIntroduceDatabaseProceduresOrTriggers() throws Exception {
        String sql = read(MIGRATION).toUpperCase(java.util.Locale.ROOT);
        assertFalse(sql.contains("CREATE PROCEDURE"));
        assertFalse(sql.contains("CREATE TRIGGER"));
    }

    private static String read(Path p) throws Exception { return Files.readString(p); }
    private static Path resolve(String repoRelative) { Path p = Path.of("..", repoRelative); return Files.exists(p) ? p : Path.of(repoRelative); }
    private static void assertContains(String s, String n) { assertTrue(s.contains(n), () -> "Expected to contain: " + n); }
}
