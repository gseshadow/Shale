package com.shale.ui.services;

import com.shale.core.model.CalendarEvent;
import com.shale.core.model.CalendarEventType;
import com.shale.core.model.CalendarFeedItem;
import com.shale.data.dao.CalendarEventDao;
import com.shale.data.dao.CalendarEventTypeDao;
import com.shale.data.dao.CalendarFeedDao;
import com.shale.data.dao.NotificationDao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public final class CalendarService {
    private final CalendarEventTypeDao calendarEventTypeDao;
    private final CalendarEventDao calendarEventDao;
    private final CalendarFeedDao calendarFeedDao;
    private final CalendarAssignmentNotificationService calendarAssignmentNotificationService;

    public CalendarService(
            CalendarEventTypeDao calendarEventTypeDao,
            CalendarEventDao calendarEventDao,
            CalendarFeedDao calendarFeedDao,
            NotificationDao notificationDao,
            UiRuntimeBridge runtimeBridge) {
        this.calendarEventTypeDao = Objects.requireNonNull(calendarEventTypeDao, "calendarEventTypeDao");
        this.calendarEventDao = Objects.requireNonNull(calendarEventDao, "calendarEventDao");
        this.calendarFeedDao = Objects.requireNonNull(calendarFeedDao, "calendarFeedDao");
        this.calendarAssignmentNotificationService = new CalendarAssignmentNotificationService(Objects.requireNonNull(notificationDao, "notificationDao"), Objects.requireNonNull(runtimeBridge, "runtimeBridge"));
    }

    public List<CalendarEventType> listEffectiveEventTypes(int shaleClientId) {
        return calendarEventTypeDao.listEffectiveEventTypes(shaleClientId).stream()
                .filter(CalendarService::isSelectableManualType)
                .toList();
    }

    public Integer createEvent(CalendarEvent event) {
        Integer calendarEventId = calendarEventDao.create(event);
        if (calendarEventId != null && calendarEventId > 0) {
            CalendarEvent persisted = calendarEventDao.getById(calendarEventId, event.shaleClientId());
            calendarAssignmentNotificationService.notifyIfNeeded(null, persisted, event.createdByUserId());
        }
        return calendarEventId;
    }

    public void updateEvent(CalendarEvent event) {
        CalendarEvent previous = calendarEventDao.getById(event.calendarEventId(), event.shaleClientId());
        calendarEventDao.update(event);
        CalendarEvent current = calendarEventDao.getById(event.calendarEventId(), event.shaleClientId());
        calendarAssignmentNotificationService.notifyIfNeeded(previous, current, event.createdByUserId());
    }

    public List<CalendarEvent> listEventsByDateRange(int shaleClientId, LocalDateTime startsAt, LocalDateTime endsAt) {
        return calendarEventDao.listByDateRange(shaleClientId, startsAt, endsAt);
    }

    public List<CalendarFeedItem> listCalendarFeed(int shaleClientId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return calendarFeedDao.listCalendarFeed(shaleClientId, startInclusive, endExclusive);
    }

    public CalendarEvent getEventById(int calendarEventId, int shaleClientId) {
        return calendarEventDao.getById(calendarEventId, shaleClientId);
    }

    public void deleteCalendarEvent(int calendarEventId, int shaleClientId) {
        calendarEventDao.deleteCalendarEvent(calendarEventId, shaleClientId);
    }

    static boolean isSelectableManualType(CalendarEventType type) {
        if (type == null || !type.active()) return false;
        String key = type.systemKey() == null ? "" : type.systemKey().trim().toUpperCase();
        return !key.startsWith("PROJECTED_")
                && !"TASK_DUE_DATE".equals(key)
                && !"CASE_DEADLINE".equals(key);
    }
}
