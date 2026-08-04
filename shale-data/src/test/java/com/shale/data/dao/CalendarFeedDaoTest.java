package com.shale.data.dao;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CalendarFeedDaoTest {

    @Test
    void caseDateProjectionsCoverVerifiedCaseFieldsWithStableKeys() {
        Map<String, CalendarFeedDao.CaseDateProjection> byField = CalendarFeedDao.CASE_DATE_PROJECTIONS.stream()
                .collect(Collectors.toMap(CalendarFeedDao.CaseDateProjection::columnName, projection -> projection));

        assertProjection(byField, "StatuteOfLimitations", "CASE_SOL", "STATUTE_OF_LIMITATIONS", true);
        assertProjection(byField, "TortNoticeDeadline", "CASE_TORT", "TORT_NOTICE_DEADLINE", true);
        assertProjection(byField, "DiscoveryDeadline", "CASE_DISC", "DISCOVERY_DEADLINE", true);
        assertProjection(byField, "CallerDate", "CASE_CALLER", "CASE_DATE", false);
        assertProjection(byField, "AcceptedDate", "CASE_ACCEPTED", "CASE_DATE", false);
        assertProjection(byField, "DeniedDate", "CASE_DENIED", "CASE_DATE", false);
        assertProjection(byField, "ClosedDate", "CASE_CLOSED", "CASE_DATE", false);
        assertProjection(byField, "DateOfInjury", "CASE_INJURY", "CASE_DATE", false);
        assertProjection(byField, "DateFeeAgreementSigned", "CASE_FEE_AGREEMENT", "CASE_DATE", false);
        assertProjection(byField, "DateNonEngagementLetterSent", "CASE_NON_ENGAGEMENT", "CASE_DATE", false);
        assertProjection(byField, "DateOfMedicalNegligence", "CASE_MED_NEG", "CASE_DATE", false);
        assertProjection(byField, "DateMedicalNegligenceWasDiscovered", "CASE_MED_NEG_DISCOVERED", "CASE_DATE", false);

        assertFalse(byField.containsKey("CreatedAt"));
        assertFalse(byField.containsKey("UpdatedAt"));
        assertFalse(byField.containsKey("CallerTime"));
        assertEquals(CalendarFeedDao.CASE_DATE_PROJECTIONS.size(), new HashSet<>(byField.keySet()).size());
    }

    @Test
    void feedSqlKeepsFilteringInSqlAndAppliesDefaultVisibilityRules() {
        String sql = CalendarFeedDao.buildCalendarFeedSql();

        assertTrue(sql.contains("AND ISNULL(e.IsCancelled, 0) = 0"));
        assertTrue(sql.contains("AND t.CompletedAt IS NULL"));
        assertTrue(sql.contains("AND ISNULL(t.IsDeleted, 0) = 0"));
        assertTrue(sql.contains("AND ISNULL(c.IsDeleted, 0) = 0"));
        assertTrue(sql.contains("WHERE t.ShaleClientId = ?"));
        assertTrue(sql.contains("WHERE c.ShaleClientId = ?"));
        assertTrue(sql.contains("LEFT JOIN dbo.Cases c ON c.Id = t.CaseId AND c.ShaleClientId = t.ShaleClientId AND ISNULL(c.IsDeleted, 0) = 0"));
        assertTrue(sql.contains("c.Name AS CaseName"));
        assertTrue(sql.contains("c.Name AS RelatedDisplayName"));
        assertFalse(sql.contains("t.Title,\n                           'TASK_DUE'"), "task title must not occupy the case/related label slot");
        assertTrue(sql.contains("ORDER BY StartsAt ASC, AllDay DESC, KeyValue ASC"));
    }

    @Test
    void feedSqlProjectsEveryCaseDateAsAllDayWithStableSourceFieldAndRangeFilter() {
        String sql = CalendarFeedDao.buildCalendarFeedSql();
        Set<String> keys = new HashSet<>();
        for (CalendarFeedDao.CaseDateProjection projection : CalendarFeedDao.CASE_DATE_PROJECTIONS) {
            assertTrue(keys.add(projection.keyPrefix()), "duplicate key prefix " + projection.keyPrefix());
            assertTrue(sql.contains(projection.keyPrefix()), projection.keyPrefix());
            assertTrue(sql.contains("'" + projection.columnName() + "'"));
            assertTrue(sql.contains("AND c." + projection.columnName() + " IS NOT NULL"));
            assertTrue(sql.contains("AND c." + projection.columnName() + " >= CAST(? AS date)"));
            assertTrue(sql.contains("AND c." + projection.columnName() + " < CAST(? AS date)"));
        }
    }

    @Test
    void feedSqlProjectsAuthoritativeCaseDatesReadOnlyWithIntersectionAndHistoricalPresentation() {
        String sql = CalendarFeedDao.buildCalendarFeedSql();

        assertTrue(sql.contains("FROM dbo.CaseDates cd"));
        assertTrue(sql.contains("CONCAT('CASE_DATE:', CAST(cd.Id AS varchar(20)))"));
        assertTrue(sql.contains("cd.ShaleClientId = ?"));
        assertTrue(sql.contains("INNER JOIN dbo.Cases c ON c.Id = cd.CaseId AND c.ShaleClientId = cd.ShaleClientId AND ISNULL(c.IsDeleted, 0) = 0"));
        assertTrue(sql.contains("INNER JOIN dbo.CaseDateTypes storedType ON storedType.Id = cd.CaseDateTypeId AND (storedType.ShaleClientId = cd.ShaleClientId OR storedType.ShaleClientId IS NULL)"));
        assertTrue(sql.contains("COALESCE(effectiveType.Name, storedType.Name) AS Name"));
        assertTrue(sql.contains("COALESCE(effectiveType.CalendarCategory, storedType.CalendarCategory) AS CalendarCategory"));
        assertTrue(sql.contains("typePresentation.CalendarCategory IN ('DEADLINE','TRIAL','HEARING','MEDIATION','DEPOSITION','NOTICE','APPOINTMENT','MILESTONE','OTHER')"));
        assertTrue(sql.contains("AND ISNULL(cd.IsDeleted, 0) = 0"));
        assertTrue(sql.contains("AND cd.StartsAt < ?"));
        assertTrue(sql.contains("AND COALESCE(cd.EndsAt, cd.StartsAt) >= ?"));
        assertFalse(sql.contains("INSERT dbo.CalendarEvents"));
        assertFalse(sql.contains("UPDATE dbo.CalendarEvents"));
        assertFalse(sql.contains("DELETE FROM dbo.CalendarEvents"));
    }

    @Test
    void authoritativeCaseDatesPreserveStoredLocalDateTimeAndDoNotShiftAllDayRanges() {
        String sql = CalendarFeedDao.buildCalendarFeedSql();

        assertTrue(sql.contains("cd.StartsAt,"));
        assertTrue(sql.contains("cd.EndsAt,"));
        assertTrue(sql.contains("cd.AllDay,"));
        assertFalse(sql.contains("DATEADD(day"));
        assertFalse(sql.contains("23:59:59"));
        assertFalse(sql.contains("AT TIME ZONE"));
        assertFalse(sql.contains("SWITCHOFFSET"));
        assertFalse(sql.contains("CONVERT(time(0), cd.StartsAt)"));
    }

    @Test
    void caseFilteredFeedSqlPushesCaseIdIntoEveryUnifiedFeedBranch() {
        String sql = CalendarFeedDao.buildCalendarFeedSql(true);

        assertTrue(sql.contains("AND e.CaseId = ?"));
        assertTrue(sql.contains("AND t.CaseId = ?"));
        assertEquals(CalendarFeedDao.CASE_DATE_PROJECTIONS.size() + 1, count(sql, "AND c.Id = ?"));
        assertTrue(sql.contains("AND ISNULL(e.IsCancelled, 0) = 0"));
        assertTrue(sql.contains("AND t.CompletedAt IS NULL"));
        assertTrue(sql.contains("AND ISNULL(t.IsDeleted, 0) = 0"));
        assertTrue(sql.contains("AND ISNULL(c.IsDeleted, 0) = 0"));
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void assertProjection(
            Map<String, CalendarFeedDao.CaseDateProjection> byField,
            String field,
            String keyPrefix,
            String systemKey,
            boolean deadline) {
        CalendarFeedDao.CaseDateProjection projection = byField.get(field);
        assertNotNull(projection, field);
        assertEquals(keyPrefix, projection.keyPrefix());
        assertEquals(systemKey, projection.systemKey());
        assertEquals(deadline, projection.deadline());
    }
}
