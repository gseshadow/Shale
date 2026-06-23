package com.shale.server.runtime;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTokenRevocationStore implements TokenRevocationStore {
    private final ConcurrentHashMap<String, Long> revokedTokenExpirations = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryTokenRevocationStore() {
        this(Clock.systemUTC());
    }

    InMemoryTokenRevocationStore(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void revoke(String tokenId, long expiresAtEpochSeconds) {
        if (tokenId == null || tokenId.isBlank()) {
            return;
        }
        cleanExpired();
        if (expiresAtEpochSeconds > now()) {
            revokedTokenExpirations.put(tokenId, expiresAtEpochSeconds);
        }
    }

    @Override
    public boolean isRevoked(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }
        cleanExpired();
        Long expiresAt = revokedTokenExpirations.get(tokenId);
        return expiresAt != null && expiresAt > now();
    }

    private void cleanExpired() {
        long now = now();
        Iterator<Map.Entry<String, Long>> iterator = revokedTokenExpirations.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
                iterator.remove();
            }
        }
    }

    private long now() {
        return Instant.now(clock).getEpochSecond();
    }
}
