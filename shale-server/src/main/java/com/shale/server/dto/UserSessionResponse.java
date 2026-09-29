package com.shale.server.dto;

import java.time.Instant;
import java.util.UUID;
import com.shale.core.model.ClientType;

/** Safe public durable-session projection. */
public record UserSessionResponse(UUID sessionId, int userId, String userDisplayName, String userEmail, ClientType clientType, Instant issuedAt,
		Instant expiresAt, Instant lastRefreshedAt, Instant revokedAt, String revocationReason,
		boolean currentSession) {}
