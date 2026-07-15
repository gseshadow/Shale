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
        String caseName,
        Integer taskId,
        String relatedDisplayName,
        String calendarEventTypeSystemKey,
        String displayTypeName,
        String colorHex,
        String assignedUserColor,
        Integer assignedToUserId,
        String assignedUserDisplayName) {
}
