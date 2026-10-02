package com.shale.server.runtime;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.shale.core.dto.UserSessionView;
import com.shale.core.model.ClientType;

/** Server-authentication boundary for the shared SQL UserSessions authority. */
public interface DurableSessionStore {
    UserSessionView create(ServerPrincipal principal, ClientType clientType, Long applicationInstanceId,
            UUID currentAccessJti, Instant expiresAt);
    Optional<UserSessionView> find(ServerPrincipal principal, UUID sessionId);
    UserSessionView rotate(ServerPrincipal principal, UUID sessionId, UUID expectedJti, UUID replacementJti,
            Instant expiresAt);
    UserSessionView revoke(ServerPrincipal principal, UUID sessionId, String reason);
}
