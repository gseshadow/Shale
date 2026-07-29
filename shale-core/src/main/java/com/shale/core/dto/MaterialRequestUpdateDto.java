package com.shale.core.dto;

import java.time.LocalDateTime;

/** An immutable, append-only user note or system change in a material request history. */
public record MaterialRequestUpdateDto(long id, int shaleClientId, long caseId, long materialRequestId,
        String updateType, String fieldKey, String body, int actorUserId,
        String actorDisplayName, LocalDateTime createdAt) {}
