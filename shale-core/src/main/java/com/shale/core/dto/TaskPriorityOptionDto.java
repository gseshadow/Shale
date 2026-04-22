package com.shale.core.dto;

/**
 * Minimal task-priority option model for create-task UI selection.
 */
public record TaskPriorityOptionDto(
        int id,
        String name,
        Integer sortOrder,
        String colorHex) {
}
