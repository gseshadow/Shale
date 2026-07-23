package com.shale.core.dto;

import java.time.LocalDateTime;

public record MaterialRequestSummaryDto(long id, int shaleClientId, long caseId, int materialTypeId, String materialTypeName, String materialTypeSystemKey, String materialTypeColor, String title, int requestedByUserId, String requestedByDisplayName, String requestedByUserColor, Integer assignedToUserId, String assignedToDisplayName, String assignedToUserColor, Integer requestedFromContactId, String requestedFromContactDisplayName, Integer requestedFromOrganizationId, String requestedFromOrganizationName, String requestedFromText, String requestMethod, LocalDateTime requestedAt, String status, LocalDateTime expectedResponseDate, LocalDateTime nextFollowUpAt, LocalDateTime lastFollowUpAt, LocalDateTime updatedAt, byte[] rowVer) {}
