package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Sole JDBC transaction owner for a case aggregate cutover mutation.
 * Collaborators receive the same connection and must neither commit nor open a
 * nested transaction. This boundary is intentionally not wired to legacy-shaped
 * clients until they can submit the complete concurrency contract.
 */
public final class CaseAggregateTransaction {
    private final DbSessionProvider db;

    public CaseAggregateTransaction(DbSessionProvider db) { this.db = Objects.requireNonNull(db, "db"); }

    @FunctionalInterface
    public interface Work<T> { T execute(Connection connection) throws Exception; }

    public <T> T execute(Work<T> work) {
        Objects.requireNonNull(work, "work");
        try (Connection connection = db.requireConnection()) {
            if (!connection.getAutoCommit()) throw new IllegalStateException("Aggregate transaction requires an unowned JDBC connection.");
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Exception failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Case aggregate mutation failed.", failure);
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) { /* connection is closing */ }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Case aggregate transaction failed.", e);
        }
    }
}
