package com.shale.server.runtime;

public record VerifiedAuthToken(ServerPrincipal principal, String tokenId, long expiresAtEpochSeconds) {
    public VerifiedAuthToken {
        java.util.Objects.requireNonNull(principal, "principal");
        if (tokenId == null || tokenId.isBlank()) {
            throw new IllegalArgumentException("tokenId is required");
        }
        if (expiresAtEpochSeconds <= 0) {
            throw new IllegalArgumentException("expiresAtEpochSeconds must be > 0");
        }
    }
}
