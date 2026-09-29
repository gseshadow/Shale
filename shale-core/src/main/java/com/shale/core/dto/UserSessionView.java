package com.shale.core.dto;

import java.time.Instant;
import java.util.UUID;
import com.shale.core.model.ClientType;

/** Immutable internal durable-session state; never contains bearer or refresh-token material. */
public record UserSessionView(long id, UUID sessionId, ClientType clientType, Long applicationInstanceId,
		UUID currentAccessJti, Instant issuedAt, Instant expiresAt, Instant revokedAt, String revocationReason) {
	public boolean isActive(Instant now) { return revokedAt == null && now.isBefore(expiresAt); }
}
