package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseDaoCasesGridQueryTest {

    @Test
    void activePagingUsesOnlyTheAuthoritativeCaseSummaryBoundary() throws Exception {
        String legacyDao = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String summaryDao = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseSummaryDao.java"));
        String page = method(summaryDao, "public GridPage findActiveGridPage");
        String query = method(summaryDao, "private static String gridSql");

        assertTrue(page.contains("gridSql(searchPredicate, statusPredicate, orderBy)"));
        assertTrue(page.contains("bindGridCriteria(ps, requestedTenantId"));
        assertTrue(page.contains("OFFSET") || query.contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
        assertTrue(query.contains("FROM dbo.CaseDates"));
        assertTrue(query.contains("CaseDateTypeSemanticRoleMappings"));
        assertFalse(query.contains("c.CallerDate"));
        assertFalse(query.contains("c.StatuteOfLimitations"));
        assertFalse(query.contains("c.TortNoticeDeadline"));
        assertFalse(legacyDao.contains("AUTHORITATIVE_MIGRATED_DATES"));
        assertFalse(legacyDao.contains("LEGACY_MIGRATED_DATE_COMPATIBILITY"));
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
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseSummaryDao.java"));
        String query = method(source, "private static String gridSql");
        String ordering = method(source, "private static String gridOrderSql");
        assertTrue(query.contains("FROM dbo.CaseDates cd"));
        assertTrue(query.contains("cd.CaseId=c.Id AND cd.ShaleClientId=c.ShaleClientId"));
        assertTrue(query.contains("cd.IsDeleted=0"));
        assertTrue(query.contains("effective.SemanticRoleKey='INTAKE'"));
        assertTrue(query.contains("effective.SemanticRoleKey='STATUTE_OF_LIMITATIONS'"));
        assertTrue(ordering.contains("dates.IntakeDate"));
        assertTrue(ordering.contains("dates.StatuteDate"));
        for (String legacy : new String[] { "c.CallerDate", "c.DateOfInjury", "c.StatuteOfLimitations", "c.TortNoticeDeadline" })
            assertFalse(query.contains(legacy), legacy + " must not be a paging fallback");
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
    void caseOverviewQueryDoesNotHydrateMigratedDates() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String method = source.substring(source.indexOf("public com.shale.core.dto.CaseOverviewDto getOverview"), source.indexOf("private List<com.shale.core.dto.CaseOverviewDto.ContactSummary>"));

        assertFalse(method.contains("c.CallerDate"));
        assertFalse(method.contains("c.DateOfInjury"));
        assertFalse(method.contains("c.StatuteOfLimitations"));
        assertFalse(method.contains("c.TortNoticeDeadline"));
        assertFalse(method.contains("rs.getDate(\"CallerDate\")"));
        assertFalse(method.contains("rs.getDate(\"DateOfInjury\")"));
        assertFalse(method.contains("rs.getDate(\"StatuteOfLimitations\")"));
        assertFalse(method.contains("rs.getDate(\"TortNoticeDeadline\")"));
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

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        int open = source.indexOf('{', start);
        assertTrue(start >= 0 && open >= 0, "Missing method: " + signature);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("Unbalanced method: " + signature);
    }

}
