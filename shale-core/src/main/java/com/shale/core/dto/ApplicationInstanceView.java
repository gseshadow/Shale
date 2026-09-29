package com.shale.core.dto;

import java.time.Instant;
import java.util.UUID;

import com.shale.core.model.ClientType;
import com.shale.core.model.SemanticVersion;

/** Immutable view of one enrolled client process; it is not an authentication session. */
public record ApplicationInstanceView(long id, UUID machineId, ClientType clientType,
		SemanticVersion applicationVersion, Instant startedAt, Instant endedAt,
		Instant lastHeartbeatAt, Instant lastHumanActivityAt) {
	public ApplicationInstanceView(long id, UUID machineId, ClientType clientType,
			SemanticVersion applicationVersion, Instant startedAt, Instant endedAt) {
		this(id, machineId, clientType, applicationVersion, startedAt, endedAt, null, null);
	}
	public ApplicationInstanceView {
		if (id <= 0) throw new IllegalArgumentException("id must be positive");
		if (clientType == ClientType.DESKTOP && machineId == null) throw new IllegalArgumentException("DESKTOP requires machineId");
		if (clientType != ClientType.DESKTOP && machineId != null) throw new IllegalArgumentException("machineId is only valid for DESKTOP");
		java.util.Objects.requireNonNull(clientType, "clientType");
		java.util.Objects.requireNonNull(applicationVersion, "applicationVersion");
		java.util.Objects.requireNonNull(startedAt, "startedAt");
		if (endedAt != null && endedAt.isBefore(startedAt)) throw new IllegalArgumentException("endedAt precedes startedAt");
	}
}
