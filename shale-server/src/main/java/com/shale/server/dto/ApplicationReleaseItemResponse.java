package com.shale.server.dto;

import com.shale.core.model.ReleaseItemType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An active, ordered item belonging to a published application release.")
public record ApplicationReleaseItemResponse(
        long id,
        int sortOrder,
        @Schema(allowableValues = {"FEATURE", "FIX", "IMPROVEMENT", "IMPORTANT", "LINK", "VIDEO"})
        ReleaseItemType type,
        String title,
        String body,
        @Schema(nullable = true, description = "Inert resource metadata; the API does not fetch or proxy this URL.")
        String resourceUrl) {
}
