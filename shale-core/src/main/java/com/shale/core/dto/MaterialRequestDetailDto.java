package com.shale.core.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

public record MaterialRequestDetailDto(long id, int shaleClientId, long caseId, int materialTypeId, String materialTypeName, String materialTypeSystemKey, String title, String description, Integer requestedByUserId, String requestedByDisplayName, Integer assignedToUserId, String assignedToDisplayName, Integer requestedFromContactId, String requestedFromContactDisplayName, Integer requestedFromOrganizationId, String requestedFromOrganizationName, String requestedFromText, String requestMethod, LocalDateTime requestedAt, LocalDate requestedRangeStartDate, LocalDate requestedRangeEndDate, String status, LocalDate expectedResponseDate, LocalDateTime nextFollowUpAt, Integer followUpIntervalDays, LocalDateTime lastFollowUpAt, LocalDateTime firstReceivedAt, LocalDateTime fullyReceivedAt, LocalDateTime closedAt, Integer closedByUserId, String closureReason, String notes, LocalDateTime createdAt, Integer createdByUserId, String createdByDisplayName, LocalDateTime updatedAt, Integer updatedByUserId, byte[] rowVer, boolean deleted) {
    public MaterialRequestDetailDto {
        rowVer = rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length);
    }

    @Override
    public byte[] rowVer() {
        return rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length);
    }
}
