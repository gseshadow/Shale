package com.shale.data.security.ai;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;

/** SQL bearer proof. Package-private, nonserializable and never part of the identity snapshot.
 * The invocation owner closes this only AFTER failure/release cleanup, including cancellation.
 * Zeroing cannot erase copies retained by JDBC, privileged diagnostics or SQL Server. */
final class ProofDigest implements AutoCloseable {
    private final byte[] digest;
    private boolean closed;

    ProofDigest(byte[] source) {
        if (source == null || source.length != 32) throw new IllegalArgumentException("Invalid integration proof.");
        digest = source.clone();
    }

    @FunctionalInterface interface BoundWork<T> { T execute() throws SQLException; }

    /** The only byte exposure is one prepared binary parameter, cleared even when binding fails.
     * Serialize binding/close so cancellation cannot wipe a buffer while JDBC is consuming it. */
    synchronized <T> T withBinding(PreparedStatement statement, int parameter, BoundWork<T> work) throws SQLException {
        if (closed) throw new IllegalStateException("Integration proof is closed.");
        byte[] binding = digest.clone();
        Throwable primary = null;
        try {
            statement.setBytes(parameter, binding);
            return work.execute();
        } catch (SQLException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            Arrays.fill(binding, (byte) 0);
            try { statement.clearParameters(); }
            catch (SQLException cleanup) {
                if (primary != null) primary.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

    @Override public synchronized void close() {
        Arrays.fill(digest, (byte) 0);
        closed = true;
    }
    @Override public String toString() { return "ProofDigest[redacted]"; }
}
