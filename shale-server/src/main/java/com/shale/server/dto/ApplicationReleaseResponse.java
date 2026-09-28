package com.shale.server.dto;

import java.time.Instant;
import java.util.List;

import com.shale.core.model.ReleaseChannel;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A published application release newer than the requested exclusive lower bound.")
public record ApplicationReleaseResponse(
        long id,
        @Schema(example = "1.0.130", pattern = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$")
        String version,
        @Schema(allowableValues = {"PRODUCTION", "PILOT", "DEVELOPMENT"})
        ReleaseChannel channel,
        Instant publishedAt,
        String summary,
        List<ApplicationReleaseItemResponse> items) {
    public ApplicationReleaseResponse {
        items = List.copyOf(items);
    }
}
