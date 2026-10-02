package com.shale.core.dto;

import java.time.Instant;
import java.util.UUID;

import com.shale.core.model.ClientType;
import com.shale.core.model.SemanticVersion;

/** Safe immutable tenant-administrator view of one client-process launch. */
public record AdminApplicationInstanceView(long applicationInstanceId, int userId, String userDisplayName,
		String userEmail, UUID machineId, ClientType clientType, SemanticVersion applicationVersion,
		Instant startedAt, Instant endedAt, Instant lastHeartbeatAt, Instant lastHumanActivityAt) {
	public AdminApplicationInstanceView {
		if (applicationInstanceId <= 0 || userId <= 0) throw new IllegalArgumentException("Instance and user ids must be positive.");
		java.util.Objects.requireNonNull(userDisplayName, "userDisplayName");
		java.util.Objects.requireNonNull(userEmail, "userEmail");
		java.util.Objects.requireNonNull(clientType, "clientType");
		java.util.Objects.requireNonNull(applicationVersion, "applicationVersion");
		java.util.Objects.requireNonNull(startedAt, "startedAt");
	}
}
