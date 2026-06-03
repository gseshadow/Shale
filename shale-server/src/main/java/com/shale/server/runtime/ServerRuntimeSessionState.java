package com.shale.server.runtime;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Temporary server runtime/session placeholder for Step 3.
 *
 * <p>The browser/mobile server does not yet have authenticated request context
 * that can safely set tenant/user SQL Server session context for RLS. Until that
 * exists, DB-backed endpoints must fail closed instead of using fake tenant ids
 * or opening unscoped connections.</p>
 */
public final class ServerRuntimeSessionState {
    public static final String NOT_IMPLEMENTED_MESSAGE =
            "TODO: server auth/session context is not wired yet; this endpoint cannot safely access tenant-scoped data without RLS session context.";

    public void requireTenantSession() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, NOT_IMPLEMENTED_MESSAGE);
    }

    public int requireShaleClientId() {
        requireTenantSession();
        throw new IllegalStateException("unreachable");
    }

    public int requireUserId() {
        requireTenantSession();
        throw new IllegalStateException("unreachable");
    }
}
