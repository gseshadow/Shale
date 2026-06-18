package com.shale.server.runtime;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

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
    public static final String AUTHENTICATION_REQUIRED_MESSAGE =
            "Authentication is required to access tenant-scoped data.";

    private final ServerSessionResolver sessionResolver;
    private final ObjectProvider<HttpServletRequest> currentRequest;

    public ServerRuntimeSessionState() {
        this(new UnauthenticatedServerSessionResolver(), new EmptyHttpServletRequestProvider());
    }

    public ServerRuntimeSessionState(
            ServerSessionResolver sessionResolver,
            ObjectProvider<HttpServletRequest> currentRequest) {
        this.sessionResolver = java.util.Objects.requireNonNull(sessionResolver, "sessionResolver");
        this.currentRequest = java.util.Objects.requireNonNull(currentRequest, "currentRequest");
    }

    public ServerPrincipal requirePrincipal() {
        ServerSessionContext context = sessionResolver.resolve(currentRequest.getIfAvailable());
        if (context == null || context.principal().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, AUTHENTICATION_REQUIRED_MESSAGE);
        }
        return context.principal().orElseThrow();
    }

    public int requireShaleClientId() {
        return requirePrincipal().shaleClientId();
    }

    public int requireUserId() {
        return requirePrincipal().userId();
    }

    private static final class EmptyHttpServletRequestProvider implements ObjectProvider<HttpServletRequest> {
        @Override
        public HttpServletRequest getObject(Object... args) {
            return null;
        }

        @Override
        public HttpServletRequest getIfAvailable() {
            return null;
        }

        @Override
        public HttpServletRequest getIfUnique() {
            return null;
        }

        @Override
        public HttpServletRequest getObject() {
            return null;
        }
    }
}
