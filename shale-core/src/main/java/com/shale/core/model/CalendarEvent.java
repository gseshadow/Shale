package com.shale.core.model;

import java.time.LocalDateTime;

public record CalendarEvent(
        Integer calendarEventId,
        int shaleClientId,
        int calendarEventTypeId,
        Integer caseId,
        Long taskId,
        String title,
        String description,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean allDay,
        String sourceType,
        String sourceField,
        Integer sourceId,
        Integer assignedToUserId,
        boolean completed,
        boolean cancelled,
        Integer createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
