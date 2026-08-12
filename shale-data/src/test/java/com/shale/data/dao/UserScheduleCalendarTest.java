package com.shale.data.dao;

import com.shale.core.model.CalendarFeedCategory;
import com.shale.core.model.CalendarFeedSourceFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserScheduleCalendarTest {
    @Test
    void userScheduleSqlScopesPersistedEventsTasksAndCaseDatesToViewedUserRelationships() {
        String sql = CalendarFeedDao.buildCalendarFeedSql(false, true);

        assertTrue(sql.contains("AND (e.AssignedToUserId IS NULL OR e.AssignedToUserId = ?)"));
        assertEquals(1, count(sql, "e.AssignedToUserId = ?"), "persisted events should bind viewed user once");
        assertTrue(sql.contains("FROM dbo.TaskAssignments userTaskAssignment"));
        assertTrue(sql.contains("userTaskAssignment.TaskId = t.Id"));
        assertTrue(sql.contains("userTaskAssignment.ShaleClientId = t.ShaleClientId"));
        assertTrue(sql.contains("userTaskAssignment.UserId = ?"));
        assertEquals(1, count(sql, "userTaskAssignment.UserId = ?"), "task EXISTS should bind viewed user once and avoid duplicate rows");
        assertTrue(sql.contains("FROM dbo.CaseUsers responsibleAttorney"));
        assertTrue(sql.contains("responsibleAttorney.RoleId = 4"));
        assertTrue(sql.contains("responsibleAttorney.IsPrimary = 1"));
        assertTrue(sql.contains("responsibleAttorney.UserId = ?"));
        assertEquals(CalendarFeedDao.LIFECYCLE_DATE_PROJECTIONS.size() + 1, count(sql, "responsibleAttorney.UserId = ?"), "every projected case-date branch should require viewed user as responsible attorney");
    }

    @Test
    void userScheduleSqlKeepsExistingVisibilityAndTenantRules() {
        String sql = CalendarFeedDao.buildCalendarFeedSql(false, true);

        assertTrue(sql.contains("WHERE e.ShaleClientId = ?"));
        assertTrue(sql.contains("WHERE t.ShaleClientId = ?"));
        assertTrue(sql.contains("WHERE c.ShaleClientId = ?"));
        assertTrue(sql.contains("AND ISNULL(e.IsCancelled, 0) = 0"));
        assertTrue(sql.contains("AND t.CompletedAt IS NULL"));
        assertTrue(sql.contains("AND ISNULL(t.IsDeleted, 0) = 0"));
        assertTrue(sql.contains("AND ISNULL(c.IsDeleted, 0) = 0"));
        assertFalse(sql.contains("t.CreatedByUserId = ?"));
    }

    @Test
    void normalAndCaseCalendarSqlRemainUnscopedByUserScheduleRelationships() {
        String mainSql = CalendarFeedDao.buildCalendarFeedSql();
        String caseSql = CalendarFeedDao.buildCalendarFeedSql(true);

        assertFalse(mainSql.contains("userTaskAssignment.UserId = ?"));
        assertFalse(mainSql.contains("responsibleAttorney.UserId = ?"));
        assertFalse(mainSql.contains("AssignedToUserId = ?"));
        assertFalse(caseSql.contains("userTaskAssignment.UserId = ?"));
        assertFalse(caseSql.contains("responsibleAttorney.UserId = ?"));
        assertFalse(caseSql.contains("AssignedToUserId = ?"));
        assertTrue(caseSql.contains("AND e.CaseId = ?"));
        assertTrue(caseSql.contains("AND t.CaseId = ?"));
        assertEquals(CalendarFeedDao.LIFECYCLE_DATE_PROJECTIONS.size() + 1, count(caseSql, "AND c.Id = ?"));
    }

    @Test
    void everyInformationalAndDeadlineCaseDateUsesResponsibleAttorneyPredicate() {
        String sql = CalendarFeedDao.buildCalendarFeedSql(false, true);
        for (CalendarFeedDao.LifecycleDateProjection projection : CalendarFeedDao.LIFECYCLE_DATE_PROJECTIONS) {
            int field = sql.indexOf("'" + projection.columnName() + "'");
            assertTrue(field >= 0, projection.columnName());
            int nextUnion = sql.indexOf("UNION ALL", field + 1);
            String branch = nextUnion < 0 ? sql.substring(field) : sql.substring(field, nextUnion);
            assertTrue(branch.contains("responsibleAttorney.RoleId = 4"), projection.columnName());
            assertTrue(branch.contains("responsibleAttorney.UserId = ?"), projection.columnName());
        }
    }

    @Test
    void userScheduleSourceLayersDefaultToAllFour() {
        CalendarFeedSourceFilter filter = CalendarFeedSourceFilter.caseCalendarDefaults();
        assertTrue(filter.isEnabled(CalendarFeedCategory.CALENDAR_EVENTS));
        assertTrue(filter.isEnabled(CalendarFeedCategory.TASKS));
        assertTrue(filter.isEnabled(CalendarFeedCategory.CASE_DEADLINES));
        assertTrue(filter.isEnabled(CalendarFeedCategory.OTHER_CASE_DATES));
    }

    private static int count(String value, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = value.indexOf(needle, idx)) >= 0) { count++; idx += needle.length(); }
        return count;
    }
}
