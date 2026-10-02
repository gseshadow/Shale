package com.shale.server.runtime;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Validates the complete tenant/user/session/JTI relationship for session-bound JWTs. */
public final class DurableSessionTokenValidator {
    private final DurableSessionStore sessions; private final Clock clock;
    public DurableSessionTokenValidator(DurableSessionStore sessions){this(sessions,Clock.systemUTC());}
    DurableSessionTokenValidator(DurableSessionStore sessions,Clock clock){this.sessions=java.util.Objects.requireNonNull(sessions);this.clock=java.util.Objects.requireNonNull(clock);}
    public boolean isValid(VerifiedAuthToken token){
        if(!token.isSessionBound())return false;
        final UUID sid,jti;try{sid=UUID.fromString(token.sessionId());jti=UUID.fromString(token.tokenId());}catch(IllegalArgumentException e){return false;}
        return sessions.find(token.principal(),sid).filter(s -> s.revokedAt()==null)
                .filter(s -> clock.instant().isBefore(s.expiresAt()))
                .filter(s -> s.currentAccessJti().equals(jti)).isPresent();
    }
}
