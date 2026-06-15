package com.shale.server.runtime;

/**
 * Authenticated browser/mobile principal planned for server request handling.
 *
 * @param userId principal user id from Shale users
 * @param shaleClientId tenant id required for RLS-scoped runtime access
 * @param email optional authenticated email/subject for diagnostics
 */
public record ServerPrincipal(int userId, int shaleClientId, String email) {
    public ServerPrincipal {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
    }
}
