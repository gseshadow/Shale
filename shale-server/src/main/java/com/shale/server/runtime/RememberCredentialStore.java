package com.shale.server.runtime;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Atomic, one-time desktop remember-credential authority. Raw credentials never cross this boundary. */
public interface RememberCredentialStore {
    record Record(ServerPrincipal principal, UUID sessionId, UUID installationId, Instant absoluteExpiresAt, boolean admin, boolean attorney) {}
    void create(ServerPrincipal principal, UUID sessionId, UUID installationId, byte[] credentialHash, Instant absoluteExpiresAt);
    Optional<Record> rotate(byte[] presentedHash, UUID installationId, byte[] replacementHash, UUID replacementAccessJti, Instant now);
    void deleteForSession(ServerPrincipal principal, UUID sessionId);
}
