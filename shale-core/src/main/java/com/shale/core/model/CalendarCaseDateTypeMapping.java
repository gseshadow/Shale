package com.shale.core.model;

import java.time.LocalDateTime;

/** Tenant-owned configuration joining an eligible calendar type to an eligible case-date type. */
public record CalendarCaseDateTypeMapping(
        long id,
        int calendarEventTypeId,
        int caseDateTypeId,
        boolean caseDateToCalendar,
        boolean calendarToCaseDate,
        boolean active,
        LocalDateTime createdAt,
        int createdByUserId,
        LocalDateTime updatedAt,
        Integer updatedByUserId,
        byte[] rowVer) {
    public CalendarCaseDateTypeMapping { rowVer = copy(rowVer); }
    @Override public byte[] rowVer() { return copy(rowVer); }
    private static byte[] copy(byte[] value) { return value == null ? null : value.clone(); }
}
