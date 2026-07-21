package com.shale.core.dto;

import java.time.LocalDateTime;

public record MaterialRequestFollowUpDto(long id, int shaleClientId, long materialRequestId, long caseId, LocalDateTime attemptedAt, int attemptedByUserId, String attemptedByDisplayName, String method, String outcome, LocalDateTime nextFollowUpAt, String notes, LocalDateTime createdAt, int createdByUserId, byte[] rowVer) {}
