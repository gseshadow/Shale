package com.shale.server.runtime;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.shale.core.runtime.DbSessionProvider;

/**
 * Opens an unscoped runtime connection for explicitly global control-plane data.
 *
 * <p>This provider deliberately does not resolve a request principal and does not
 * initialize tenant or user {@code SESSION_CONTEXT}. It must never be used by a
 * tenant-scoped DAO.</p>
 */
public final class GlobalControlPlaneDbSessionProvider implements DbSessionProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalControlPlaneDbSessionProvider.class);
    private final ConnectionFactory connections;

    public GlobalControlPlaneDbSessionProvider(DataSource runtimeDataSource) {
        this(Objects.requireNonNull(runtimeDataSource, "runtimeDataSource")::getConnection);
    }

    public GlobalControlPlaneDbSessionProvider(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    @Override
    public Connection requireConnection() {
        try {
            return connections.open();
        } catch (SQLException e) {
            RuntimeConnectionFailureLog.log(LOGGER, "Global control-plane runtime database connection", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to open global control-plane database connection.", e);
        }
    }

    @FunctionalInterface
    public interface ConnectionFactory {
        Connection open() throws SQLException;
    }
}
