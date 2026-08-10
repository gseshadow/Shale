package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseDaoCasesGridQueryTest {

    @Test
    void everyPagingEntryPointChoosesAnExplicitDateAuthorityMode() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        assertTrue(source.contains("AUTHORITATIVE_MIGRATED_DATES = true"));
        assertTrue(source.contains("LEGACY_MIGRATED_DATE_COMPATIBILITY = false"));
        assertTrue(source.contains("null, null, null, null, AUTHORITATIVE_MIGRATED_DATES)"),
                "The general public Cases paging entry point must use authoritative migrated dates");
        assertTrue(source.contains("selectedStatusIds, knownTotal, AUTHORITATIVE_MIGRATED_DATES)"),
                "The converted Cases grid path must be authoritative");
        assertTrue(source.contains("null, AUTHORITATIVE_MIGRATED_DATES))"),
                "The converted Cases export path must be authoritative");
        assertTrue(source.contains("userId, null, null, null, LEGACY_MIGRATED_DATE_COMPATIBILITY)"),
                "Deferred MyShale paging must remain explicitly compatible");
        long internalCalls = source.lines().filter(line -> line.contains("findPageInternal(")).count();
        assertEquals(5, internalCalls, "Four callers plus the private declaration must be reviewed together");
    }

    @Test
    void casesGridUsesAuthoritativeCaseDatesForDisplaySortingAndPaging() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(source.indexOf("private PagedResult<CaseRow> findPageInternal"),
                source.indexOf("private static String normalizeSearchQuery"));
        assertTrue(method.contains("authoritativeCasesDateApplySql()"));
        assertTrue(method.contains("migrated.IntakeDate AS CallerDate"));
        assertTrue(method.contains("migrated.StatuteDate AS StatuteOfLimitations"));
        assertTrue(method.contains("migrated.IncidentDate AS DateOfIncident"));
        assertTrue(method.contains("migrated.TortDate AS TortNoticeDeadline"));
        assertTrue(source.contains("authoritativeMigratedDates ? \"migrated.IntakeDate\""));
        assertTrue(source.contains("authoritativeMigratedDates ? \"migrated.StatuteDate\""));
        assertTrue(source.contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
        assertTrue(source.contains("dbo.CaseDates cd"));
        assertTrue(source.contains("type_key.SystemKey IN ('intake','date_of_injury','statute_of_limitations','tort_notice_deadline')"));
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
        assertTrue(method.contains("status.color()"),
                "Reports rows must carry the authoritative color from the effective status record.");
        assertFalse(method.contains("Cases.CaseStatusId"));
        assertFalse(method.contains("c.CaseStatusId"));
        assertFalse(method.contains("AcceptedDate"));
        assertFalse(method.contains("DeniedDate"));
        assertFalse(method.contains("ClosedDate"));
        assertFalse(method.contains("NonEngagementLetterSent"));
    }
    @Test
    void caseStatusReportUsesEffectiveOverlayStatusesForNamesAndColors() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String reportMethod = source.substring(source.indexOf("public List<CaseStatusReportRowDto> listCaseStatusReport"), source.indexOf("private Map<Integer, Long> loadCaseStatusReportCounts"));
        String overlayMethod = source.substring(source.indexOf("private static List<StatusRow> resolveEffectiveStatuses"), source.indexOf("public List<StatusRow> listStatusesForTenant"));

        assertTrue(reportMethod.contains("List<StatusRow> availableStatuses = listStatusesForTenant(shaleClientId)"),
                "Reports should use the shared effective tenant/global status lookup before creating report rows.");
        assertTrue(reportMethod.contains("status.name()"));
        assertTrue(reportMethod.contains("status.color()"));
        assertTrue(overlayMethod.contains("bySystemKey.putIfAbsent(systemKey, status)"),
                "Global statuses should be loaded first for effective overlay resolution.");
        assertTrue(overlayMethod.contains("bySystemKey.put(systemKey, status)"),
                "Tenant statuses with the same SystemKey should mask the global status name and color.");
        assertTrue(overlayMethod.contains("merged.sort"),
                "Effective status ordering should remain deterministic after overlay resolution.");
    }

    @Test
    void assignedTeamMemberCasesSelectUpdatedAtForMapper() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(source.indexOf("public List<CaseRow> listActiveCasesForUserTeamMember"), source.indexOf("public List<CaseStatusReportRowDto> listCaseStatusReport"));

        assertTrue(method.contains("c.UpdatedAt"),
                "Assigned user detail cases must select UpdatedAt because the CaseRow mapper reads UpdatedAt.");
        assertTrue(method.contains("toLocalDateTime(rs.getTimestamp(\"UpdatedAt\"))"),
                "Assigned user detail cases should continue hydrating CaseRow updatedAt from the UpdatedAt result column.");
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
