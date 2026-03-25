package com.shale.core.dto;

import java.time.LocalDateTime;

/**
 * Editable task detail model used by task detail dialogs.
 */
public record TaskDetailDto(
        long id,
        int shaleClientId,
        long caseId,
        String caseName,
        String title,
        String description,
        LocalDateTime dueAt,
        Integer priorityId,
        LocalDateTime completedAt,
        Integer assignedUserId,
        String assignedUserDisplayName,
        String assignedUserColor
) {
}
