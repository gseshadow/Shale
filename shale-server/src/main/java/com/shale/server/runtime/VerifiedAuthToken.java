package com.shale.server.runtime;

public record VerifiedAuthToken(ServerPrincipal principal, String tokenId, long issuedAtEpochSeconds,
        long expiresAtEpochSeconds, String sessionId) {
    public VerifiedAuthToken {
        java.util.Objects.requireNonNull(principal, "principal");
        if (tokenId == null || tokenId.isBlank()) {
            throw new IllegalArgumentException("tokenId is required");
        }
        if (expiresAtEpochSeconds <= 0) {
            throw new IllegalArgumentException("expiresAtEpochSeconds must be > 0");
        }
		if (issuedAtEpochSeconds <= 0 || issuedAtEpochSeconds >= expiresAtEpochSeconds) {
			throw new IllegalArgumentException("invalid token lifetime");
		}
    }

	public boolean isSessionBound() { return sessionId != null; }
}
