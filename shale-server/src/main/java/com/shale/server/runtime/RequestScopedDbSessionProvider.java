package com.shale.server.runtime;

import java.sql.Connection;
import java.sql.SQLException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.shale.core.runtime.DbSessionProvider;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Planned request-scoped runtime DB provider for server API calls.
 *
 * <p>It resolves the request principal, borrows a runtime connection, initializes
 * SQL Server SESSION_CONTEXT values for tenant and principal user, then returns
 * that scoped connection to DAO/service-port adapters. Without a resolved principal
 * it still fails closed before any database access.</p>
 */
public final class RequestScopedDbSessionProvider implements DbSessionProvider {
    private final ServerSessionResolver sessionResolver;
    private final ObjectProvider<HttpServletRequest> currentRequest;
    private final RuntimeConnectionProvider runtimeConnectionProvider;

    public RequestScopedDbSessionProvider(
            ServerSessionResolver sessionResolver,
            ObjectProvider<HttpServletRequest> currentRequest,
            RuntimeConnectionProvider runtimeConnectionProvider) {
        this.sessionResolver = java.util.Objects.requireNonNull(sessionResolver, "sessionResolver");
        this.currentRequest = java.util.Objects.requireNonNull(currentRequest, "currentRequest");
        this.runtimeConnectionProvider = java.util.Objects.requireNonNull(runtimeConnectionProvider, "runtimeConnectionProvider");
    }

    @Override
    public Connection requireConnection() {
        ServerSessionContext context = sessionResolver.resolve(currentRequest.getIfAvailable());
        if (context == null || context.principal().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, ServerRuntimeSessionState.NOT_IMPLEMENTED_MESSAGE);
        }
        try {
            return runtimeConnectionProvider.openConnection(context.principal().orElseThrow());
        } catch (SQLException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to open request-scoped runtime database connection.", e);
        }
    }
}
