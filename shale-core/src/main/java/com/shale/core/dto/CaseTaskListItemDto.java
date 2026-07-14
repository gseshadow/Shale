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
        String casePrimaryStatusName,
        String casePrimaryStatusColor,
        String casePracticeAreaColor,
        String caseResponsibleAttorney,
        String caseResponsibleAttorneyColor,
        Boolean caseNonEngagementLetterSent,
        String title,
        String description,
        String taskStatusName,
        String taskStatusColorHex,
        Integer priorityId,
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
        public CaseTaskListItemDto(
                long id,
                int shaleClientId,
                long caseId,
                String caseName,
                String casePrimaryStatusName,
                String casePrimaryStatusColor,
                String casePracticeAreaColor,
                String caseResponsibleAttorney,
                String caseResponsibleAttorneyColor,
                Boolean caseNonEngagementLetterSent,
                String title,
                String description,
                Integer priorityId,
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
                boolean deleted) {
            this(id, shaleClientId, caseId, caseName, casePrimaryStatusName, casePrimaryStatusColor,
                    casePracticeAreaColor, caseResponsibleAttorney, caseResponsibleAttorneyColor,
                    caseNonEngagementLetterSent, title, description, null, null, priorityId,
                    priorityColorHex, dueAt, completedAt, assignedUserId, assignedUserDisplayName,
                    assignedUserColor, createdByUserId, createdByDisplayName, createdAt, updatedAt, deleted);
        }
}
