package com.shale.core.model;

public record CreateCalendarCaseDateTypeMappingCommand(
        int calendarEventTypeId, int caseDateTypeId,
        boolean caseDateToCalendar, boolean calendarToCaseDate, boolean active) {}
