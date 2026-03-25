package com.shale.core.dto;

import java.time.LocalDateTime;

/**
 * Lightweight task read model for case-level task list rendering.
 */
public record CaseTaskListItemDto(
        long id,
        int shaleClientId,
        long caseId,
        String title,
        String description,
        LocalDateTime dueAt,
        LocalDateTime completedAt,
        Integer assignedUserId,
        String assignedUserDisplayName,
        String assignedUserColor,
        Integer createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean deleted
) {
}
