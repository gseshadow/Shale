package com.shale.ui.services;

import com.shale.core.model.CalendarEvent;
import com.shale.core.model.CalendarEventType;
import com.shale.core.model.CalendarFeedItem;
import com.shale.data.dao.CalendarEventDao;
import com.shale.data.dao.CalendarEventTypeDao;
import com.shale.data.dao.CalendarFeedDao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public final class CalendarService {
    private final CalendarEventTypeDao calendarEventTypeDao;
    private final CalendarEventDao calendarEventDao;
    private final CalendarFeedDao calendarFeedDao;

    public CalendarService(CalendarEventTypeDao calendarEventTypeDao, CalendarEventDao calendarEventDao, CalendarFeedDao calendarFeedDao) {
        this.calendarEventTypeDao = Objects.requireNonNull(calendarEventTypeDao, "calendarEventTypeDao");
        this.calendarEventDao = Objects.requireNonNull(calendarEventDao, "calendarEventDao");
        this.calendarFeedDao = Objects.requireNonNull(calendarFeedDao, "calendarFeedDao");
    }

    public List<CalendarEventType> listEffectiveEventTypes(int shaleClientId) {
        return calendarEventTypeDao.listEffectiveEventTypes(shaleClientId);
    }

    public Integer createEvent(CalendarEvent event) {
        return calendarEventDao.create(event);
    }

    public void updateEvent(CalendarEvent event) {
        calendarEventDao.update(event);
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
}
