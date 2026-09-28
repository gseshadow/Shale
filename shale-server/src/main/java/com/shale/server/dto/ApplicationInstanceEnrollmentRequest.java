package com.shale.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ApplicationInstanceEnrollmentRequest(
	@Schema(format="uuid", pattern="^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$", requiredMode=Schema.RequiredMode.REQUIRED) @NotBlank @Pattern(regexp="^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$") String machineId,
	@Schema(allowableValues={"DESKTOP"}, requiredMode=Schema.RequiredMode.REQUIRED) @NotBlank String clientType,
	@Schema(pattern="^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$", example="1.0.127", requiredMode=Schema.RequiredMode.REQUIRED) @NotBlank String applicationVersion) {}
