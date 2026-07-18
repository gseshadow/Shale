package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseDaoCaseOverviewPrimaryLegalAssistantQueryTest {
    @Test
    void overviewSelectMapsPrimaryLegalAssistantFromTenantScopedActivePrimaryLegalAssistantAssignment() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = method(source, "public com.shale.core.dto.CaseOverviewDto getOverview", "private List<com.shale.core.dto.CaseOverviewDto.ContactSummary>");

        assertTrue(method.contains("private static final int ROLE_LEGAL_ASSISTANT = RoleSemantics.ROLE_LEGAL_ASSISTANT")
                || source.contains("private static final int ROLE_LEGAL_ASSISTANT = RoleSemantics.ROLE_LEGAL_ASSISTANT"),
                "Legal Assistant role id should come from RoleSemantics, not a guessed literal");
        assertTrue(method.contains("primary_legal_assistant.UserId AS PrimaryLegalAssistantUserId"));
        assertTrue(method.contains("pla_user.color AS PrimaryLegalAssistantColor"));
        assertTrue(method.contains("PrimaryLegalAssistantName"));
        assertTrue(method.contains("getNullableInt(rs, \"PrimaryLegalAssistantUserId\")"));
        assertTrue(method.contains("rs.getString(\"PrimaryLegalAssistantName\")"));
        assertTrue(method.contains("rs.getString(\"PrimaryLegalAssistantColor\")"));
    }

    @Test
    void overviewSelectRejectsNonPrimaryWrongRoleOtherTenantAndSoftDeletedAssignments() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = method(source, "OUTER APPLY (\n\t\t\t\t\t    SELECT TOP (1) pla_cu.UserId", "\t\t\t\t\t) primary_legal_assistant");

        assertTrue(method.contains("pla_cu.CaseId = c.Id"));
        assertTrue(!method.contains("pla_cu.ShaleClientId"),
                "CaseUsers does not have ShaleClientId; tenant isolation must be enforced through Users.ShaleClientId");
        assertTrue(!method(source, "public com.shale.core.dto.CaseOverviewDto getOverview", "private List<com.shale.core.dto.CaseOverviewDto.ContactSummary>").contains("cu.ShaleClientId"),
                "getOverview must not reference CaseUsers.ShaleClientId for any CaseUsers alias");
        assertTrue(method.contains("pla_user.id = pla_cu.UserId"));
        assertTrue(method.contains("pla_user.ShaleClientId = c.ShaleClientId"));
        assertTrue(method.contains("pla_cu.RoleId = ?"));
        assertTrue(source.contains("ps.setInt(idx++, ROLE_LEGAL_ASSISTANT)"));
        assertTrue(method.contains("pla_cu.IsPrimary = 1"));
        assertTrue(source.contains("activeFilter(resolveCaseUsersDeletedColumn(con), \"pla_cu\")"));
        assertTrue(source.contains("activeFilter(resolveUsersDeletedColumn(con), \"pla_user\")"));
    }

    @Test
    void overviewShaleClientIdAliasesResolveOnlyToTablesThatOwnTenantColumn() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String overview = method(source, "public com.shale.core.dto.CaseOverviewDto getOverview", "private List<com.shale.core.dto.CaseOverviewDto.ContactSummary>");

        assertTrue(overview.contains("FROM dbo.Cases c"), "Alias c must resolve to dbo.Cases, which owns ShaleClientId");
        assertTrue(overview.contains("LEFT JOIN dbo.Users u ON u.id = ra.UserId"), "Alias u must resolve to dbo.Users, which owns ShaleClientId");
        assertTrue(overview.contains("INNER JOIN dbo.Users pla_user"), "Apply alias pla_user must resolve to dbo.Users, which owns ShaleClientId");
        assertTrue(overview.contains("LEFT JOIN dbo.Users pla_user"), "Outer alias pla_user must resolve to dbo.Users, which owns ShaleClientId");

        assertTrue(!overview.contains("cu.ShaleClientId"), "dbo.CaseUsers does not own ShaleClientId");
        assertTrue(!overview.contains("pla_cu.ShaleClientId"), "dbo.CaseUsers does not own ShaleClientId");
        assertTrue(!overview.contains("cs.ShaleClientId"), "dbo.CaseStatuses should not be tenant-filtered by a direct ShaleClientId reference in getOverview");
        assertTrue(!overview.contains("cp.ShaleClientId"), "dbo.CaseParties should not be tenant-filtered by a direct ShaleClientId reference in getOverview");
    }

    @Test
    void overviewSelectKeepsExpectedParameterOrdering() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String overview = method(source, "public com.shale.core.dto.CaseOverviewDto getOverview", "private List<com.shale.core.dto.CaseOverviewDto.ContactSummary>");
        int responsibleAttorneyParam = overview.indexOf("ps.setInt(idx++, ROLE_RESPONSIBLE_ATTORNEY)");
        int legalAssistantParam = overview.indexOf("ps.setInt(idx++, ROLE_LEGAL_ASSISTANT)");
        int caseIdParam = overview.indexOf("ps.setLong(idx++, caseId)");

        assertTrue(responsibleAttorneyParam >= 0, "Responsible Attorney role parameter should be present");
        assertTrue(legalAssistantParam > responsibleAttorneyParam, "Legal Assistant role parameter should follow Responsible Attorney");
        assertTrue(caseIdParam > legalAssistantParam, "caseId parameter should follow role parameters");
    }

    @Test
    void duplicatePrimaryLegalAssistantsResolveDeterministicallyToOneCardConvention() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = method(source, "OUTER APPLY (\n\t\t\t\t\t    SELECT TOP (1) pla_cu.UserId", "\t\t\t\t\t) primary_legal_assistant");

        assertTrue(method.contains("SELECT TOP (1) pla_cu.UserId"));
        assertTrue(method.contains("ORDER BY pla_cu.UpdatedAt DESC, pla_cu.CreatedAt DESC, pla_cu.Id DESC"),
                "Bad duplicate primary data should still render one deterministic assignment using the existing primary-assignment ordering convention; duplicates remain a data-integrity concern.");
    }

    @Test
    void overviewProductionSqlBuilderRendersExpectedAliasTablesAndParameterShape() {
        String sql = CaseDao.buildOverviewSql(
                "(cu.IsDeleted = 0 OR cu.IsDeleted IS NULL)",
                "(u.IsDeleted = 0 OR u.IsDeleted IS NULL)",
                "(pla_user.IsDeleted = 0 OR pla_user.IsDeleted IS NULL)",
                "(pla_cu.IsDeleted = 0 OR pla_cu.IsDeleted IS NULL)",
                "LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'caller'",
                "LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'counsel'",
                "(c.IsDeleted = 0 OR c.IsDeleted IS NULL)");

        assertTrue(sql.contains("FROM dbo.Cases c"), renderedFromJoin(sql));
        assertTrue(sql.contains("LEFT JOIN dbo.Users u ON u.id = ra.UserId"), renderedFromJoin(sql));
        assertTrue(sql.contains("INNER JOIN dbo.Users pla_user"), renderedFromJoin(sql));
        assertTrue(sql.contains("LEFT JOIN dbo.Users pla_user"), renderedFromJoin(sql));
        assertTrue(!sql.contains("%s"), "Production SQL builder should return the fully formatted SQL string");
        assertFalse(sql.contains("cu.ShaleClientId"), renderedFromJoin(sql));
        assertFalse(sql.contains("pla_cu.ShaleClientId"), renderedFromJoin(sql));
        assertTrue(!sql.contains("cs.ShaleClientId"), renderedFromJoin(sql));
        assertTrue(!sql.contains("cp.ShaleClientId"), renderedFromJoin(sql));
        assertTrue(sql.contains("WHERE c.Id = ?"), "caseId placeholder should remain in final prepared SQL");
    }


    @Test
    void primaryLegalAssistantMutationPromotesDemotesAndPreservesTenantBoundaries() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = method(source, "public void setPrimaryLegalAssistant", "public record UserRow");
        assertTrue(method.contains("RoleSemantics.ROLE_LEGAL_ASSISTANT") || source.contains("ROLE_LEGAL_ASSISTANT = RoleSemantics.ROLE_LEGAL_ASSISTANT"));
        assertTrue(method.contains("con.setAutoCommit(false)"));
        assertTrue(method.contains("con.rollback()"));
        assertTrue(method.contains("u.ShaleClientId = c.ShaleClientId"));
        assertTrue(method.contains("c.ShaleClientId = ?"));
        assertTrue(method.contains("u.Id = ?"));
        assertTrue(method.contains("u.IsActive = 1 OR u.IsActive IS NULL"));
        assertTrue(method.contains("u.IsDeleted = 0 OR u.IsDeleted IS NULL"));
        assertTrue(method.contains("RoleId = ?"));
        assertTrue(method.contains("AND UserId <> ?"), "Previous primary legal assistants should be demoted");
        assertTrue(method.contains("SET IsPrimary = CAST(1 AS bit)"), "Existing legal assistant assignment should be promoted");
        assertTrue(method.contains("INSERT INTO dbo.CaseUsers (CaseId, UserId, RoleId, IsPrimary"));
        assertTrue(method.contains("touchCaseUpdatedAt(con, caseId, shaleClientId)"));
        assertFalse(method.contains("CaseUsers.ShaleClientId"));
        assertFalse(method.contains("cu.ShaleClientId"));
        assertFalse(method.contains("pla_cu.ShaleClientId"));
        assertFalse(method.contains("ROLE_RESPONSIBLE_ATTORNEY"), "Responsible Attorney assignments must be untouched");
    }

    private static String renderedFromJoin(String sql) {
        StringBuilder out = new StringBuilder("Rendered FROM/JOIN clauses:\n");
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("FROM ") || trimmed.startsWith("LEFT JOIN ") || trimmed.startsWith("INNER JOIN ")) {
                out.append(trimmed).append('\n');
            }
        }
        return out.toString();
    }

    private static String method(String source, String startNeedle, String endNeedle) {
        int start = source.indexOf(startNeedle);
        int end = source.indexOf(endNeedle, start + startNeedle.length());
        assertTrue(start >= 0, "Missing start: " + startNeedle);
        assertTrue(end > start, "Missing end: " + endNeedle);
        return source.substring(start, end);
    }
}
