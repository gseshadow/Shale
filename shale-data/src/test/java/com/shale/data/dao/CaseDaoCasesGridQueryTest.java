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
    void casesGridEstablishesTheGlobalBoundaryBeforeDisplayEnrichment() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(source.indexOf("private PagedResult<CaseRow> findPageInternal"),
                source.indexOf("private static String normalizeSearchQuery"));

        int boundary = method.indexOf("WITH OrderedPage AS");
        int offset = method.indexOf("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        int hydration = method.indexOf("FROM OrderedPage page");
        assertTrue(boundary >= 0 && boundary < offset && offset < hydration);
        assertTrue(method.contains("ROW_NUMBER() OVER (ORDER BY %s) AS PageOrdinal"));
        assertTrue(method.contains("ORDER BY page.PageOrdinal"), "The hydration join must explicitly retain global order");
        assertTrue(method.contains("INNER JOIN %s c ON c.Id = page.CaseId AND c.ShaleClientId = page.ShaleClientId"));

        String orderedStage = method.substring(boundary, hydration);
        assertFalse(orderedStage.contains("dbo.CaseParties"));
        assertFalse(orderedStage.contains("dbo.CaseUpdates"));
        assertFalse(orderedStage.contains("PracticeAreas"));
        assertTrue(method.indexOf("FROM dbo.CaseUpdates cu") > hydration);
        assertTrue(method.indexOf("FROM dbo.CaseParties cp") > hydration);
    }

    @Test
    void authoritativeDateSortIsGlobalParameterizedAndHasNoLegacyFallback() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String boundaryDate = source.substring(source.indexOf("private static String authoritativeBoundaryDateApplySql"),
                source.indexOf("private static boolean requiresAuthoritativeDateSort"));
        assertTrue(boundaryDate.contains("MAX(cd.StartsAt) AS SortDate"));
        assertTrue(boundaryDate.contains("stored_type.SystemKey = ?"));
        assertTrue(boundaryDate.contains("cd.CaseId = c.Id AND cd.ShaleClientId = c.ShaleClientId"));
        assertFalse(boundaryDate.contains("c.CallerDate"));
        assertFalse(boundaryDate.contains("c.StatuteOfLimitations"));
        assertTrue(source.contains("authoritativeSortSystemKey(effectiveSort)"));
        assertTrue(source.contains("boundary_date.SortDate\" : \"c.CallerDate"));
        assertTrue(source.contains("boundary_date.SortDate\" : \"c.StatuteOfLimitations"));
    }

    @Test
    void allSortFamiliesUseTheSameBoundedQueryContract() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String ordering = source.substring(source.indexOf("private static String boundaryOrderByClauseFor"),
                source.indexOf("public long countAll()"));
        for (String sort : new String[] { "INTAKE_OLDEST", "INTAKE_NEWEST", "STATUTE_SOONEST",
                "STATUTE_LATEST", "TORT_NOTICE_SOONEST", "UPDATED_OLDEST", "UPDATED_NEWEST",
                "CASE_NAME_ASC", "CASE_NAME_DESC", "RESPONSIBLE_ATTORNEY_ASC",
                "RESPONSIBLE_ATTORNEY_DESC", "CASE_STATUS_ASC", "CASE_STATUS_DESC" }) {
            assertTrue(ordering.contains("case " + sort), sort + " must retain an explicit global order");
        }
        assertTrue(ordering.contains("c.Id ASC"));
        assertTrue(ordering.contains("c.Id DESC"));
        assertTrue(source.contains("if (boundaryNeedsAuthoritativeDate)"));
        assertFalse(source.contains("type_key.SystemKey IN ('intake','date_of_injury','statute_of_limitations','tort_notice_deadline')"));
    }

    @Test
    void timingAndQueryCountRemainPhiSafeAndSingleStatement() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(source.indexOf("private PagedResult<CaseRow> findPageInternal"),
                source.indexOf("private static String normalizeSearchQuery"));
        assertTrue(method.contains("phase=total-count"));
        assertTrue(method.contains("phase=session-setup"));
        assertTrue(method.contains("phase=page-row-query"));
        assertEquals(1, method.lines().filter(line -> line.contains("ps.executeQuery()")).count());
        assertTrue(method.contains("1 + (totalCached ? 0 : 1)"));
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
