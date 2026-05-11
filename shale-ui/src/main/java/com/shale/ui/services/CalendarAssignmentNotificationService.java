package com.shale.ui.services;

import com.shale.core.model.CalendarEvent;
import com.shale.data.dao.NotificationDao;

import java.util.Locale;
import java.util.Objects;

final class CalendarAssignmentNotificationService {
    static final String ASSIGNED_TITLE = "Calendar event assigned";
    static final String ASSIGNED_MESSAGE = "You were assigned to a calendar event.";
    static final String ACTION_TYPE = "CALENDAR_EVENT_ASSIGNED";

    private final NotificationPublisher notificationPublisher;

    CalendarAssignmentNotificationService(NotificationDao notificationDao) {
        Objects.requireNonNull(notificationDao, "notificationDao");
        this.notificationPublisher = (event, assignee, actorUserId, eventKey) ->
                notificationDao.createCalendarEventAssignedNotification(
                        event.shaleClientId(),
                        assignee,
                        ASSIGNED_TITLE,
                        ASSIGNED_MESSAGE,
                        event.calendarEventId(),
                        actorUserId == null ? 0 : actorUserId,
                        ACTION_TYPE,
                        eventKey);
    }

    CalendarAssignmentNotificationService(NotificationPublisher notificationPublisher) {
        this.notificationPublisher = Objects.requireNonNull(notificationPublisher, "notificationPublisher");
    }

    void notifyIfNeeded(CalendarEvent previous, CalendarEvent current, Integer actorUserId) {
        if (current == null || current.calendarEventId() == null) return;
        if (!isManualSource(current.sourceType())) return;
        Integer assignee = current.assignedToUserId();
        if (assignee == null || assignee <= 0) return;
        if (actorUserId != null && actorUserId > 0 && actorUserId.equals(assignee)) return;
        Integer previousAssignee = previous == null ? null : previous.assignedToUserId();
        if (Objects.equals(previousAssignee, assignee)) return;
        String eventKey = "calendar-event-assigned:" + current.calendarEventId() + ":" + assignee;
        notificationPublisher.publish(current, assignee, actorUserId, eventKey);
    }

    static boolean isManualSource(String sourceType) {
        String normalized = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
        return "MANUAL".equals(normalized) || "CALENDAR_EVENT".equals(normalized);
    }

    interface NotificationPublisher {
        void publish(CalendarEvent event, int assignee, Integer actorUserId, String eventKey);
    }
}
