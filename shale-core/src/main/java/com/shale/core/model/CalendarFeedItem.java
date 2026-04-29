package com.shale.core.model;

import java.time.LocalDateTime;

public record CalendarFeedItem(
        String key,
        String title,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean allDay,
        String sourceType,
        String sourceField,
        Integer caseId,
        Integer taskId,
        String calendarEventTypeSystemKey,
        String displayTypeName) {
}
