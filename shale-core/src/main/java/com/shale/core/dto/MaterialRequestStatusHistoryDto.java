package com.shale.core.dto;

import java.time.LocalDateTime;

/** One append-only, persisted Material Request status occurrence. */
public record MaterialRequestStatusHistoryDto(long id, int shaleClientId, long caseId, long materialRequestId,
        String statusSystemKey, String storedStatus, int actorUserId, String actorDisplayName, LocalDateTime occurredAt) {}
