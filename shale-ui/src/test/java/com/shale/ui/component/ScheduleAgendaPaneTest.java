package com.shale.ui.component;

import com.shale.core.model.CalendarFeedItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleAgendaPaneTest {
    @Test
    void agendaOrderingMatchesScheduleRules() {
        CalendarFeedItem timed = item("EVENT:2", LocalDateTime.of(2026, 7, 15, 9, 0), false, null, null);
        CalendarFeedItem allDay = item("EVENT:1", LocalDateTime.of(2026, 7, 15, 0, 0), true, null, null);
        CalendarFeedItem tomorrow = item("TASK:3", LocalDateTime.of(2026, 7, 16, 8, 0), false, "DueAt", 3);
        assertEquals(List.of(allDay, timed, tomorrow), List.of(tomorrow, timed, allDay).stream().sorted(ScheduleAgendaPane.upcomingComparator()).toList());

        CalendarFeedItem newerPast = item("EVENT:4", LocalDateTime.of(2026, 7, 14, 9, 0), false, null, null);
        CalendarFeedItem olderPast = item("EVENT:5", LocalDateTime.of(2026, 7, 13, 9, 0), false, null, null);
        assertEquals(List.of(newerPast, olderPast), List.of(olderPast, newerPast).stream().sorted(ScheduleAgendaPane.pastComparator()).toList());
    }

    private static CalendarFeedItem item(String key, LocalDateTime startsAt, boolean allDay, String sourceField, Integer taskId) {
        return new CalendarFeedItem(key, "Title", startsAt, null, allDay, key.startsWith("EVENT:") ? "MANUAL" : "PROJECTED", sourceField, null, null, taskId, null, key.startsWith("TASK:") ? "TASK_DUE" : "MEETING", "Event", null, null, null, null);
    }
}
