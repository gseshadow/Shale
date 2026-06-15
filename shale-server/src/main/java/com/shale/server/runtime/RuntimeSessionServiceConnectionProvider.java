package com.shale.server.runtime;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.sql.DataSource;

import com.shale.data.runtime.RuntimeSessionService;

/**
 * Runtime connection provider that reuses the same SQL SESSION_CONTEXT
 * initialization path as the desktop runtime session service.
 */
public final class RuntimeSessionServiceConnectionProvider implements RuntimeConnectionProvider {
    private final DataSource runtimeDataSource;

    public RuntimeSessionServiceConnectionProvider(DataSource runtimeDataSource) {
        this.runtimeDataSource = Objects.requireNonNull(runtimeDataSource, "runtimeDataSource");
    }

    @Override
    public Connection openConnection(ServerPrincipal principal) throws SQLException {
        Objects.requireNonNull(principal, "principal");
        RuntimeSessionService runtimeSessionService = new RuntimeSessionService(runtimeDataSource);
        runtimeSessionService.initialize(principal.shaleClientId(), principal.userId());
        return runtimeSessionService.getConnection();
    }
}
