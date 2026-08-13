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
        assertTrue(boundaryDate.contains("role_mapping.SemanticRoleKey=?"));
        assertTrue(boundaryDate.contains("cd.CaseId = c.Id AND cd.ShaleClientId = c.ShaleClientId"));
        assertFalse(boundaryDate.contains("c.CallerDate"));
        assertFalse(boundaryDate.contains("c.StatuteOfLimitations"));
        assertTrue(source.contains("authoritativeSortSemanticRole(effectiveSort)"));
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









}
