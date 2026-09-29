package com.shale.server.dto;

import java.time.Instant;

import com.shale.core.model.ApplicationAccessMode;
import com.shale.core.model.ReleaseChannel;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The current effective application policy for one release channel.")
public record ApplicationPolicyResponse(
        long id,
        @Schema(allowableValues = {"PRODUCTION", "PILOT", "DEVELOPMENT"})
        ReleaseChannel channel,
        long revisionNumber,
        String latestVersion,
        Long latestReleaseId,
        @Schema(nullable = true) String minimumRecommendedVersion,
        @Schema(nullable = true) Long minimumRecommendedReleaseId,
        @Schema(nullable = true) String minimumAllowedVersion,
        @Schema(nullable = true) Long minimumAllowedReleaseId,
        @Schema(nullable = true) Instant requiredUpdateDeadline,
        @Schema(allowableValues = {"NORMAL", "READ_ONLY", "MAINTENANCE", "BLOCKED"})
        ApplicationAccessMode accessMode,
        Instant publishedAt,
        Instant serverTime) {
}
