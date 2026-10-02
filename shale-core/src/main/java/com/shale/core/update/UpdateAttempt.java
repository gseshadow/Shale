package com.shale.core.update;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import com.shale.core.model.SemanticVersion;

public record UpdateAttempt(
		UUID attemptId,
		String fromVersion,
		String targetVersion,
		String releaseChannel,
		String clientType,
		UpdateAttemptState state,
		UpdateFailureCode failureCode,
		String actualVersion,
		Instant startedAt,
		Instant lastUpdatedAt) {

	public UpdateAttempt {
		Objects.requireNonNull(attemptId, "attemptId");
		fromVersion = bounded(fromVersion, "fromVersion", 32, false);
		targetVersion = bounded(targetVersion, "targetVersion", 32, true);
		releaseChannel = bounded(releaseChannel, "releaseChannel", 20, false);
		clientType = bounded(clientType, "clientType", 20, false);
		Objects.requireNonNull(state, "state");
		actualVersion = bounded(actualVersion, "actualVersion", 32, true);
		Objects.requireNonNull(startedAt, "startedAt");
		Objects.requireNonNull(lastUpdatedAt, "lastUpdatedAt");
		if (state != UpdateAttemptState.FAILED && failureCode != null) {
			throw new IllegalArgumentException("failureCode is valid only for FAILED attempts");
		}
		SemanticVersion.parse(fromVersion);
		if (targetVersion != null) SemanticVersion.parse(targetVersion);
		if (actualVersion != null) SemanticVersion.parse(actualVersion);
		if (!"PRODUCTION".equals(releaseChannel) || !"DESKTOP".equals(clientType)) {
			throw new IllegalArgumentException("Unsupported update attempt context");
		}
	}

	public static UpdateAttempt start(UUID id, String fromVersion, String targetVersion, Instant now) {
		return new UpdateAttempt(id, fromVersion, targetVersion, "PRODUCTION", "DESKTOP",
				UpdateAttemptState.STARTED, null, null, now, now);
	}

	private static String bounded(String value, String field, int max, boolean nullable) {
		if (value == null || value.isBlank()) {
			if (nullable) return null;
			throw new IllegalArgumentException(field + " must not be blank");
		}
		String normalized = value.trim();
		if (normalized.length() > max || normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
			throw new IllegalArgumentException(field + " is invalid");
		}
		return normalized;
	}
}
