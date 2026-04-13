package com.shale.core.dto;

import java.time.LocalDateTime;

/**
 * Lightweight task read model for case-level task list rendering.
 */
public record CaseTaskListItemDto(
        long id,
        int shaleClientId,
        long caseId,
        String caseName,
        String caseResponsibleAttorney,
        String caseResponsibleAttorneyColor,
        Boolean caseNonEngagementLetterSent,
        String title,
        String description,
        String priorityColorHex,
        LocalDateTime dueAt,
        LocalDateTime completedAt,
        Integer assignedUserId,
        String assignedUserDisplayName,
        String assignedUserColor,
        Integer createdByUserId,
        String createdByDisplayName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean deleted
) {
}
