package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

/** Release gate for forward-only SOL/TCN mapping retirement and its runtime boundary. */
final class CaseDateSemanticMappingRetirementContractTest {
    private static String read(String path) {
        try { return Files.readString(Path.of(path)); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private static final String PREFLIGHT=read("../docs/sql/2026-09-26_case_date_sol_tcn_mapping_retirement_preflight.sql");
    private static final String MIGRATION=read("../docs/sql/2026-09-26_case_date_sol_tcn_mapping_retirement.sql");
    private static final String VERIFY=read("../docs/sql/2026-09-26_case_date_sol_tcn_mapping_retirement_verify.sql");
    private static final String CASE_DATE_DAO=read("src/main/java/com/shale/data/dao/CaseDateDao.java");
    private static final String CASE_DAO=read("src/main/java/com/shale/data/dao/CaseDao.java");
    private static final String SUMMARY=read("src/main/java/com/shale/data/dao/CaseSummaryDao.java");

    @Test void preflightIsReadOnlyAllTenantAndInventoriesDatabaseModulesAndBlockers() {
        assertAll(
            () -> assertTrue(PREFLIGHT.contains("approved all-tenant"), "operator scope must be explicit"),
            () -> assertTrue(PREFLIGHT.contains("SESSION_CONTEXT(N'ShaleClientId')"), "tenant context must be null"),
            () -> assertTrue(PREFLIGHT.contains("sys.sql_modules"), "database modules must be inventoried"),
            () -> assertTrue(PREFLIGHT.contains("BLOCKING_MAPPING_CARDINALITY"), "ambiguous Intake must block"),
            () -> assertFalse(PREFLIGHT.matches("(?is).*\\b(UPDATE|DELETE|INSERT|MERGE|ALTER|DROP|CREATE)\\b.*"), "preflight must remain read-only"));
    }

    @Test void migrationIsAcknowledgedTransactionalRerunnableAndPreservesEveryNonMappingIdentity() {
        assertAll(
            () -> assertTrue(MIGRATION.contains("@OperatorVerifiedAllTenantVisibilityAndPreflight bit=0")),
            () -> assertTrue(MIGRATION.contains("BEGIN TRANSACTION")),
            () -> assertTrue(MIGRATION.contains("WHERE SemanticRoleKey IN('STATUTE_OF_LIMITATIONS','TORT_NOTICE_DEADLINE') AND IsActive=1 AND IsDeleted=0")),
            () -> assertTrue(MIGRATION.contains("UPDATE dbo.CaseDateSemanticRoles SET IsProtected=0")),
            () -> assertTrue(MIGRATION.contains("#DatesBefore")),
            () -> assertTrue(MIGRATION.contains("#TypesBefore")),
            () -> assertTrue(MIGRATION.contains("#PresentationSelectionsBefore")),
            () -> assertTrue(MIGRATION.contains("#OverviewSelectionsBefore")),
            () -> assertTrue(MIGRATION.contains("#ConfirmationPoliciesBefore")),
            () -> assertFalse(MIGRATION.matches("(?is).*DELETE\\s+FROM\\s+dbo\\.(CaseDates|CaseDateTypes|CaseDateTypeSemanticRoleMappings).*")));
    }

    @Test void verificationEmitsConcreteMappingOccurrencePresentationOverviewAndPolicyRows() {
        assertAll(
            () -> assertTrue(VERIFY.contains("CaseDateId")),
            () -> assertTrue(VERIFY.contains("SelectionId")),
            () -> assertTrue(VERIFY.contains("OverviewConfigurationId")),
            () -> assertTrue(VERIFY.contains("PolicyId")),
            () -> assertTrue(VERIFY.contains("Active SOL/TCN semantic mappings remain")),
            () -> assertTrue(VERIFY.contains("Intake is not the sole protected semantic role")));
    }

    @Test void activeRuntimeMappingSqlIsIntakeOnlyWhileOrdinaryFamiliesRemainMultiOccurrence() {
        String production=CASE_DATE_DAO+CASE_DAO+SUMMARY;
        assertAll(
            () -> assertFalse(production.contains("SemanticRoleKey='STATUTE_OF_LIMITATIONS'"), "SOL runtime must not resolve a role"),
            () -> assertFalse(production.contains("SemanticRoleKey='TORT_NOTICE_DEADLINE'"), "TCN runtime must not resolve a role"),
            () -> assertTrue(CASE_DATE_DAO.contains("Only Intake is an operative protected Case Date role.")),
            () -> assertTrue(CASE_DATE_DAO.contains("'intake','statute_of_limitations','tort_notice_deadline'")),
            () -> assertTrue(SUMMARY.contains("ORDER BY family_date.StartsAt ASC,family_date.Id ASC"), "compatibility values must be deterministic"),
            () -> assertFalse(CASE_DATE_DAO.contains("DELETE FROM dbo.CaseDates"), "ordinary occurrences must be retained"));
    }
}
