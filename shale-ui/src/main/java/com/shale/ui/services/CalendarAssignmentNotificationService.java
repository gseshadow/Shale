package com.shale.ui.services;

import com.shale.core.model.CalendarEvent;
import com.shale.data.dao.NotificationDao;
import com.shale.ui.services.UiRuntimeBridge;

import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CalendarAssignmentNotificationService {
    private static final Logger log = LoggerFactory.getLogger(CalendarAssignmentNotificationService.class);
    static final String ASSIGNED_TITLE = "Calendar event assigned";
    static final String ASSIGNED_MESSAGE = "You were assigned to a calendar event.";
    static final String ACTION_TYPE = "CALENDAR_EVENT_ASSIGNED";

    private final NotificationPublisher notificationPublisher;
    private final UiRuntimeBridge runtimeBridge;

    CalendarAssignmentNotificationService(NotificationDao notificationDao, UiRuntimeBridge runtimeBridge) {
        Objects.requireNonNull(notificationDao, "notificationDao");
        this.runtimeBridge = Objects.requireNonNull(runtimeBridge, "runtimeBridge");
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
        this.runtimeBridge = new UiRuntimeBridge() {
            @Override public void onLoginSuccess(int userId, int shaleClientId, String email) {}
            @Override public void onLogout() {}
        };
    }

    void notifyIfNeeded(CalendarEvent previous, CalendarEvent current, Integer actorUserId) {
        if (current == null || current.calendarEventId() == null) {
            log.info("Calendar assignment notification skipped reason=missing_event eventId=null sourceType=null tenantId=null actorUserId={} assignedUsersCount=0 newlyAssignedUsersCount=0", actorUserId);
            return;
        }
        if (!isManualSource(current.sourceType())) {
            log.info("Calendar assignment notification skipped reason=non_manual_source eventId={} sourceType={} tenantId={} actorUserId={} assignedUsersCount=1 newlyAssignedUsersCount=0",
                    current.calendarEventId(), current.sourceType(), current.shaleClientId(), actorUserId);
            return;
        }
        Integer assignee = current.assignedToUserId();
        if (assignee == null || assignee <= 0) {
            log.info("Calendar assignment notification skipped reason=no_assignee eventId={} sourceType={} tenantId={} actorUserId={} assignedUsersCount=0 newlyAssignedUsersCount=0",
                    current.calendarEventId(), current.sourceType(), current.shaleClientId(), actorUserId);
            return;
        }
        if (actorUserId != null && actorUserId > 0 && actorUserId.equals(assignee)) {
            log.info("Calendar assignment notification skipped reason=self_assignment eventId={} sourceType={} tenantId={} actorUserId={} assignedUsersCount=1 newlyAssignedUsersCount=0",
                    current.calendarEventId(), current.sourceType(), current.shaleClientId(), actorUserId);
            return;
        }
        Integer previousAssignee = previous == null ? null : previous.assignedToUserId();
        if (Objects.equals(previousAssignee, assignee)) {
            log.info("Calendar assignment notification skipped reason=unchanged_assignee eventId={} sourceType={} tenantId={} actorUserId={} assignedUsersCount=1 newlyAssignedUsersCount=0",
                    current.calendarEventId(), current.sourceType(), current.shaleClientId(), actorUserId);
            return;
        }
        String eventKey = "calendar-event-assigned:" + current.calendarEventId() + ":" + assignee;
        log.info("Calendar assignment notification create eventId={} sourceType={} tenantId={} actorUserId={} assignedUsersCount=1 newlyAssignedUsersCount=1",
                current.calendarEventId(), current.sourceType(), current.shaleClientId(), actorUserId);
        Long durableId = notificationPublisher.publish(current, assignee, actorUserId, eventKey);
        String patch = "{\"notificationType\":\"CALENDAR_EVENT_ASSIGNED\""
                + ",\"recipientUserId\":" + assignee
                + ",\"eventKey\":\"" + eventKey + "\""
                + ",\"title\":\"" + ASSIGNED_TITLE + "\""
                + ",\"message\":\"" + ASSIGNED_MESSAGE + "\""
                + (durableId == null ? "" : ",\"durableNotificationId\":" + durableId)
                + ",\"calendarEventId\":" + current.calendarEventId()
                + "}";
        runtimeBridge.publishEntityUpdated("CalendarEvent", current.calendarEventId(), current.shaleClientId(), actorUserId == null ? 0 : actorUserId, patch);
    }

    static boolean isManualSource(String sourceType) {
        String normalized = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
        return "MANUAL".equals(normalized) || "CALENDAR_EVENT".equals(normalized);
    }

    interface NotificationPublisher {
        Long publish(CalendarEvent event, int assignee, Integer actorUserId, String eventKey);
    }
}
