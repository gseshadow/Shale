package com.shale.data.dao;

import com.shale.core.model.CalendarFeedCategory;
import com.shale.core.model.CalendarFeedItem;
import com.shale.core.model.CalendarFeedSourceFilter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserScheduleCalendarTest {
    @Test
    void userScheduleSqlScopesOnlyPersistedEventsToSharedOrViewedUser() {
        String sql = CalendarFeedDao.buildCalendarFeedSql(false, true);
        assertTrue(sql.contains("AND (e.AssignedToUserId IS NULL OR e.AssignedToUserId = ?)"));
        assertEquals(1, count(sql, "AssignedToUserId = ?"));
        assertTrue(sql.contains("FROM dbo.Tasks t"));
        assertTrue(sql.contains("FROM dbo.Cases c"));
        assertTrue(sql.contains("AND t.CompletedAt IS NULL"));
        assertTrue(sql.contains("AND ISNULL(e.IsCancelled, 0) = 0"));
        assertTrue(sql.contains("AND ISNULL(t.IsDeleted, 0) = 0"));
        assertTrue(sql.contains("AND ISNULL(c.IsDeleted, 0) = 0"));
    }

    @Test
    void normalAndCaseCalendarSqlAreNotUserScheduleScoped() {
        assertFalse(CalendarFeedDao.buildCalendarFeedSql().contains("AssignedToUserId = ?"));
        assertFalse(CalendarFeedDao.buildCalendarFeedSql(true).contains("AssignedToUserId = ?"));
        assertTrue(CalendarFeedDao.buildCalendarFeedSql(true).contains("AND e.CaseId = ?"));
    }

    @Test
    void userScheduleSourceLayersDefaultToAllFour() {
        CalendarFeedSourceFilter filter = CalendarFeedSourceFilter.caseCalendarDefaults();
        assertTrue(filter.isEnabled(CalendarFeedCategory.CALENDAR_EVENTS));
        assertTrue(filter.isEnabled(CalendarFeedCategory.TASKS));
        assertTrue(filter.isEnabled(CalendarFeedCategory.CASE_DEADLINES));
        assertTrue(filter.isEnabled(CalendarFeedCategory.OTHER_CASE_DATES));
    }


    private static CalendarFeedItem item(String key, LocalDateTime startsAt, boolean allDay, String sourceField, Integer taskId) {
        return new CalendarFeedItem(key, "Title", startsAt, null, allDay, key.startsWith("EVENT:") ? "MANUAL" : "PROJECTED", sourceField, null, null, taskId, null, key.startsWith("TASK:") ? "TASK_DUE" : "MEETING", "Event", null, null, null, null);
    }

    private static int count(String value, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = value.indexOf(needle, idx)) >= 0) { count++; idx += needle.length(); }
        return count;
    }
}
