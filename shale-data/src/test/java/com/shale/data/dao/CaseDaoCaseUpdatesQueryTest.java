package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseDaoCaseUpdatesQueryTest {

    @Test
    void caseUpdatesQueryResolvesUsersDeletedColumnBeforeFilteringAuthors() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(
                source.indexOf("private List<CaseUpdateDto> listCaseUpdatesInternal"),
                source.indexOf("public long addCaseTimelineEvent"));

        assertTrue(method.contains("resolveUsersDeletedColumn(con)"),
                "Case updates should resolve the available Users soft-delete column before adding an author filter");
        assertTrue(method.contains("u.ShaleClientId = caseUpdate.ShaleClientId"),
                "Case update authors should remain tenant-scoped");
        assertTrue(method.contains("AND ISNULL(caseUpdate.IsDeleted, 0) = 0"),
                "Case updates should continue filtering soft-deleted notes");
        assertTrue(method.contains("AND NULLIF(LTRIM(RTRIM(caseUpdate.NoteText)), '') IS NOT NULL"),
                "Case updates should continue excluding empty notes");
        assertFalse(method.contains("COALESCE(u.is_deleted, 0) = 0"),
                "Case updates must not hard-code Users.is_deleted because some deployments expose Users.IsDeleted instead");
    }

    @Test
    void recentAssignedCaseUpdatesQueryIsTenantScopedCappedAndOrderedInSql() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(
                source.indexOf("public List<RecentCaseUpdateActivityDto> listRecentCaseUpdatesForAssignedCases"),
                source.indexOf("public List<CaseUpdateDto> listCaseUpdates(long caseId)"));

        assertTrue(method.contains("SELECT TOP (?)"),
                "Recent Case Activity should cap results in SQL instead of loading all updates into Java");
        assertTrue(method.contains("JOIN dbo.Cases c"),
                "Recent Case Activity should join updates to Cases for tenant and case-name scope");
        assertTrue(method.contains("FROM dbo.CaseUsers caseUser"),
                "Recent Case Activity should restrict to cases assigned through CaseUsers");
        assertTrue(method.contains("caseUser.UserId = ?"),
                "Recent Case Activity should bind the current user in the CaseUsers join");
        assertTrue(method.contains("c.ShaleClientId = ?") && method.contains("caseUpdate.ShaleClientId = ?"),
                "Recent Case Activity should preserve tenant filters on both Cases and CaseUpdates");
        assertTrue(method.contains("activeFilter(schema.deletedColumn(), \"c\")"),
                "Recent Case Activity should exclude deleted Cases using the resolved schema column");
        assertTrue(method.contains("ISNULL(caseUpdate.IsDeleted, 0) = 0"),
                "Recent Case Activity should exclude deleted CaseUpdates");
        assertTrue(method.contains("ORDER BY caseUpdate.CreatedAt DESC, caseUpdate.Id DESC"),
                "Recent Case Activity should let SQL Server order newest-first with a stable tie-breaker");
        assertFalse(method.contains("listCaseUpdates("),
                "Recent Case Activity DAO batch method must not call the per-case update loader");
    }
}
