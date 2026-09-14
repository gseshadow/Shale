package com.shale.core.dto;

import java.time.LocalDateTime;
import java.util.Arrays;

public record CaseDateDto(
        long id,
        int shaleClientId,
        long caseId,
        int caseDateTypeId,
        String typeSystemKey,
        String typeName,
        String typeDescription,
        String calendarCategory,
        String color,
        boolean supportsTime,
        String title,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean allDay,
        String notes,
        LocalDateTime createdAt,
        int createdByUserId,
        String createdByDisplayName,
        LocalDateTime updatedAt,
        Integer updatedByUserId,
        String updatedByDisplayName,
        byte[] rowVer) {
    public CaseDateDto {
        rowVer = rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length);
    }

    @Override public byte[] rowVer() { return rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length); }

    public String displayTitle() { return title == null || title.isBlank() ? typeName : title; }
}
