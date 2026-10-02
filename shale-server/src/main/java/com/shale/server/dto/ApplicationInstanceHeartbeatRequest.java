package com.shale.server.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ApplicationInstanceHeartbeatRequest(
		@Schema(pattern="^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$",example="1.0.130",requiredMode=Schema.RequiredMode.REQUIRED) @NotBlank String applicationVersion,
		@Schema(nullable=true,description="Latest qualifying foreground input time in UTC; null means none observed.") Instant lastHumanActivityAt) {}
