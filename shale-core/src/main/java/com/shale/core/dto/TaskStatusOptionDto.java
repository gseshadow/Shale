package com.shale.core.dto;

public record TaskStatusOptionDto(
        int id,
        String name,
        Integer sortOrder,
        String colorHex) {
}
