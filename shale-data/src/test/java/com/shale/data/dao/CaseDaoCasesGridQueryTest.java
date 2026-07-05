package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseDaoCasesGridQueryTest {

    @Test
    void casesGridQueryUsesCurrentIncidentDateColumn() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));

        assertFalse(source.contains("IncidentOccurred"),
                "Cases grid queries must not reference IncidentOccurred unless schema detection guards it");
        assertTrue(source.contains("c.DateOfInjury AS DateOfIncident"),
                "Cases grid should hydrate Date of Incident from the existing DateOfInjury column");
        assertTrue(source.contains("c.StatuteOfLimitations"),
                "Cases grid should hydrate Statute of Limitations from the existing StatuteOfLimitations column");
        assertTrue(source.contains("c.TortNoticeDeadline"),
                "Cases grid should hydrate Tort Claims Notice Deadline from the existing TortNoticeDeadline column");
    }

    @Test
    void caseOverviewQueryHydratesTortNoticeDeadline() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(source.indexOf("public com.shale.core.dto.CaseOverviewDto getOverview"), source.indexOf("private List<com.shale.core.dto.CaseOverviewDto.ContactSummary>"));

        assertTrue(method.contains("c.TortNoticeDeadline"),
                "Case Overview query should select the existing TortNoticeDeadline column.");
        assertTrue(method.contains("toLocalDate(rs.getDate(\"TortNoticeDeadline\"))"),
                "Case Overview DTO should be hydrated from the selected TortNoticeDeadline column.");
    }

    @Test
    void casesGridQueryUsesDescriptionSource() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));

        assertTrue(source.contains("c.Description AS Description"),
                "Cases grid should expose Description from dbo.Cases.Description");
    }

    @Test
    void casesGridQueryHydratesLatestUpdateFromCaseUpdates() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));

        assertTrue(source.contains("FROM dbo.CaseUpdates cu"));
        assertTrue(source.contains("cu.NoteText"));
        assertTrue(source.contains("ORDER BY cu.CreatedAt DESC, cu.Id DESC"));
    }
    @Test
    void activeCaseSearchRestoresLatestUpdateAlias() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(source.indexOf("public List<CaseRow> searchCasesByName"), source.indexOf("public List<CaseRow> searchDeletedCasesByName"));

        assertTrue(method.contains("latestUpdate.LatestCaseUpdate"));
        assertTrue(method.contains(") latestUpdate"),
                "Active case search must define the latestUpdate OUTER APPLY alias used by the SELECT list");
        assertTrue(method.contains("FROM dbo.CaseUpdates cu"));
        assertTrue(method.contains("ORDER BY cu.CreatedAt DESC, cu.Id DESC"));
    }

    @Test
    void deletedCaseSearchRestoresLatestUpdateAlias() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(source.indexOf("public List<CaseRow> searchDeletedCasesByName"), source.indexOf("private PagedResult<CaseRow> findPageInternal"));

        assertTrue(method.contains("latestUpdate.LatestCaseUpdate"));
        assertTrue(method.contains(") latestUpdate"),
                "Deleted case search must define the latestUpdate OUTER APPLY alias used by the SELECT list");
        assertTrue(method.contains("FROM dbo.CaseUpdates cu"));
        assertTrue(method.contains("ORDER BY cu.CreatedAt DESC, cu.Id DESC"));
    }

    @Test
    void caseStatusReportUsesCurrentCaseStatusesAndTenantFilters() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(source.indexOf("public List<CaseStatusReportRowDto> listCaseStatusReport"), source.indexOf("public List<ReportCaseDetailRowDto> listCaseStatusReportCases"));

        assertTrue(method.contains("FROM dbo.Cases c"));
        assertTrue(method.contains("FROM dbo.CaseStatuses cs"));
        assertTrue(method.contains("cs.EndDate IS NULL"));
        assertTrue(method.contains("cs.IsPrimary DESC"));
        assertTrue(method.contains("cs.EffectiveDate DESC"));
        assertTrue(method.contains("cs.Id DESC"));
        assertTrue(method.contains("INNER JOIN dbo.Statuses s"));
        assertTrue(method.contains("WHERE c.ShaleClientId = ?"));
        assertTrue(method.contains("ISNULL(c.IsDeleted, 0) = 0"));
        assertTrue(method.contains("(? IS NULL OR c.CallerDate >= ?)"));
        assertTrue(method.contains("(? IS NULL OR c.CallerDate < DATEADD(day, 1, ?))"));
        assertTrue(method.contains("s.Id IN (%s)"));
        assertTrue(method.contains("sqlPlaceholders(selectedStatusIds.size())"));
        assertTrue(method.contains("status.lifecycleKey()"));
        assertTrue(method.contains("status.sortOrder()"));
        assertFalse(method.contains("Cases.CaseStatusId"));
        assertFalse(method.contains("c.CaseStatusId"));
        assertFalse(method.contains("AcceptedDate"));
        assertFalse(method.contains("DeniedDate"));
        assertFalse(method.contains("ClosedDate"));
        assertFalse(method.contains("NonEngagementLetterSent"));
    }


    @Test
    void assignedCaseBoardUsesDynamicCurrentStatusAliasesAndTenantStatuses() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(source.indexOf("public List<CaseRow> listAssignedCasesForBoard"), source.indexOf("public List<CaseRow> searchCasesByName"));

        assertTrue(method.contains("current_status.CurrentStatusName"),
                "Assigned case board must select the status alias read by the mapper");
        assertTrue(method.contains("s.Name AS CurrentStatusName"),
                "Current status display name must come from dbo.Statuses");
        assertTrue(method.contains("s.Color AS PrimaryStatusColor"),
                "Current status color must come from dbo.Statuses");
        assertTrue(method.contains("FROM %s cs"));
        assertTrue(method.contains("INNER JOIN %s s"));
        assertTrue(method.contains("AND (s.ShaleClientId = ? OR s.ShaleClientId IS NULL)"),
                "Assigned case board must allow tenant-specific and global statuses");
        assertTrue(method.contains("cs.EndDate IS NULL"),
                "Assigned case board must resolve the current CaseStatuses row");
        assertTrue(method.contains("cs.IsPrimary DESC"));
        assertTrue(method.contains("s.SortOrder"),
                "Assigned case board ordering must use status sort order instead of fixed names");
        assertTrue(method.contains("cs.EffectiveDate DESC"));
        assertTrue(method.contains("rs.getString(\"CurrentStatusName\")"));
        assertTrue(method.contains("rs.getString(\"PrimaryStatusColor\")"));
        assertFalse(method.contains("c.CaseStatusId"));
        assertFalse(method.contains("Accepted"));
        assertFalse(method.contains("Denied"));
        assertFalse(method.contains("Closed"));
        assertFalse(method.contains("Prelitigation"));
        assertFalse(method.contains("Testing"));
    }

}
