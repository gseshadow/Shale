package com.shale.core.model;

import java.time.LocalDateTime;

public record CalendarEventType(
        int calendarEventTypeId,
        Integer shaleClientId,
        String systemKey,
        String name,
        String colorHex,
        int sortOrder,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
