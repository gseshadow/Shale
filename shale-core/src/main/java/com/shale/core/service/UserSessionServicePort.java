package com.shale.core.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.shale.core.dto.UserSessionView;
import com.shale.core.model.ClientType;

/** Internal-only durable authentication-session foundation. Values are supplied by trusted server code. */
public interface UserSessionServicePort {
	UserSessionView create(int shaleClientId, int userId, ClientType clientType, Long applicationInstanceId,
			UUID currentAccessJti, Instant expiresAt);
	Optional<UserSessionView> find(int shaleClientId, int userId, UUID sessionId);
	UserSessionView revoke(int shaleClientId, int userId, UUID sessionId, String reason);
	UserSessionView rotateAccessCredential(int shaleClientId, int userId, UUID sessionId,
			UUID expectedCurrentAccessJti, UUID replacementAccessJti, Instant expiresAt);
}
