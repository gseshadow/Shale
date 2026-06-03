package com.shale.server.runtime;

import java.sql.Connection;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.shale.core.runtime.DbSessionProvider;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Planned request-scoped runtime DB provider for server API calls.
 *
 * <p>It intentionally does not open database connections yet. Future work should
 * resolve the request principal, borrow a runtime connection, set SQL Server
 * SESSION_CONTEXT values for tenant and principal user, then return that scoped
 * connection to DAO/service-port adapters.</p>
 */
public final class RequestScopedDbSessionProvider implements DbSessionProvider {
    private final ServerSessionResolver sessionResolver;
    private final ObjectProvider<HttpServletRequest> currentRequest;

    public RequestScopedDbSessionProvider(
            ServerSessionResolver sessionResolver,
            ObjectProvider<HttpServletRequest> currentRequest) {
        this.sessionResolver = java.util.Objects.requireNonNull(sessionResolver, "sessionResolver");
        this.currentRequest = java.util.Objects.requireNonNull(currentRequest, "currentRequest");
    }

    @Override
    public Connection requireConnection() {
        ServerSessionContext context = sessionResolver.resolve(currentRequest.getIfAvailable());
        if (context == null || context.principal().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, ServerRuntimeSessionState.NOT_IMPLEMENTED_MESSAGE);
        }
        throw new UnsupportedOperationException(
                "TODO: create request-scoped runtime DB connection and set SQL SESSION_CONTEXT before DAO access.");
    }
}
