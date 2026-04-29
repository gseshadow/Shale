package com.shale.ui.services;

import com.shale.core.model.CalendarEvent;
import com.shale.core.model.CalendarEventType;
import com.shale.data.dao.CalendarEventDao;
import com.shale.data.dao.CalendarEventTypeDao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public final class CalendarService {
    private final CalendarEventTypeDao calendarEventTypeDao;
    private final CalendarEventDao calendarEventDao;

    public CalendarService(CalendarEventTypeDao calendarEventTypeDao, CalendarEventDao calendarEventDao) {
        this.calendarEventTypeDao = Objects.requireNonNull(calendarEventTypeDao, "calendarEventTypeDao");
        this.calendarEventDao = Objects.requireNonNull(calendarEventDao, "calendarEventDao");
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

    public List<CalendarEvent> listProjectedEventsPlaceholder(int shaleClientId, LocalDateTime startsAt, LocalDateTime endsAt) {
        return List.of();
    }
}
