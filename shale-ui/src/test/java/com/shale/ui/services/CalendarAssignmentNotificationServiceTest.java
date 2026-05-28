package com.shale.ui.services;

import com.shale.core.model.CalendarEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalendarAssignmentNotificationServiceTest {

    @Test
    void assigningManualEventCreatesNotificationOnce() {
        List<String> eventKeys = new ArrayList<>();
        CalendarAssignmentNotificationService service = new CalendarAssignmentNotificationService((event, assignee, actorUserId, eventKey) -> { eventKeys.add(eventKey); return 1L; });
        CalendarEvent current = event(100, 10, "MANUAL", 22);

        service.notifyIfNeeded(null, current, 5);

        assertEquals(List.of("calendar-event-assigned:100:22"), eventKeys);
    }

    @Test
    void assigningSameUserTwiceDoesNotNotifyAgain() {
        List<String> eventKeys = new ArrayList<>();
        CalendarAssignmentNotificationService service = new CalendarAssignmentNotificationService((event, assignee, actorUserId, eventKey) -> { eventKeys.add(eventKey); return 1L; });
        CalendarEvent previous = event(100, 10, "MANUAL", 22);
        CalendarEvent current = event(100, 10, "MANUAL", 22);

        service.notifyIfNeeded(previous, current, 5);

        assertEquals(List.of(), eventKeys);
    }

    @Test
    void projectedItemsDoNotNotify() {
        List<String> eventKeys = new ArrayList<>();
        CalendarAssignmentNotificationService service = new CalendarAssignmentNotificationService((event, assignee, actorUserId, eventKey) -> { eventKeys.add(eventKey); return 1L; });

        service.notifyIfNeeded(null, event(100, 10, "TASK_DUE_DATE", 22), 5);
        service.notifyIfNeeded(null, event(101, 10, "CASE_DEADLINE", 22), 5);

        assertEquals(List.of(), eventKeys);
    }

    @Test
    void tenantContextUsedFromEvent() {
        List<Integer> tenantIds = new ArrayList<>();
        CalendarAssignmentNotificationService service = new CalendarAssignmentNotificationService((event, assignee, actorUserId, eventKey) -> { tenantIds.add(event.shaleClientId()); return 1L; });

        service.notifyIfNeeded(null, event(200, 77, "MANUAL", 22), 5);

        assertEquals(List.of(77), tenantIds);
    }

    private static CalendarEvent event(int eventId, int tenantId, String sourceType, Integer assignedToUserId) {
        return new CalendarEvent(eventId, tenantId, 1, null, null, "Title", "Description", LocalDateTime.now(), null, true, sourceType, null, null, assignedToUserId, false, false, 5, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void selfAssignmentIsSuppressed() {
        List<String> eventKeys = new ArrayList<>();
        CalendarAssignmentNotificationService service = new CalendarAssignmentNotificationService((event, assignee, actorUserId, eventKey) -> { eventKeys.add(eventKey); return 1L; });
        service.notifyIfNeeded(null, event(300, 10, "MANUAL", 22), 22);
        assertEquals(List.of(), eventKeys);
    }
}
