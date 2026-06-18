package com.shale.server.runtime;

public interface TokenRevocationStore {
    void revoke(String tokenId, long expiresAtEpochSeconds);
    boolean isRevoked(String tokenId);
}
