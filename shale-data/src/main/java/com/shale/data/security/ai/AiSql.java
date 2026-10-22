package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.data.util.UtcJdbcTimestamp;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Procedure-owned transactions; drain completion before returning any result. */
final class AiSql {
    private AiSql() {}
    record TypedNull(int sqlType) {}
    static Object nullable(Object value,int sqlType) { return value==null ? new TypedNull(sqlType) : value; }
    interface Mapper<T> { T read(ResultSet result) throws SQLException; }

    static <T> List<T> call(DbSessionProvider db, String procedure, Mapper<T> mapper, Object... args) {
        String markers = String.join(",", Collections.nCopies(args.length, "?"));
        try (Connection c = db.requireConnection()) {
            if (!c.getAutoCommit()) throw new IllegalStateException("AI procedures require a fresh transaction lease.");
            try (CallableStatement p = c.prepareCall("{call dbo." + procedure + "(" + markers + ")}")) {
                p.setQueryTimeout(5);
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof TypedNull nil) p.setNull(i + 1, nil.sqlType());
                    else if (args[i] == null) p.setNull(i + 1, Types.VARBINARY);
                    else if (args[i] instanceof Instant instant) UtcJdbcTimestamp.set(p, i + 1, instant);
                    else if (args[i] instanceof byte[] bytes) p.setBytes(i + 1, bytes);
                    else p.setObject(i + 1, args[i]);
                }
                List<T> values = new ArrayList<>();
                boolean result = p.execute();
                boolean mapped = false;
                while (true) {
                    if (result) {
                        try (ResultSet rows = p.getResultSet()) {
                            if (!mapped) while (rows.next()) values.add(mapper.read(rows));
                        }
                        mapped = true;
                    } else if (p.getUpdateCount() == -1) break;
                    result = p.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
                }
                return List.copyOf(values);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("AI security operation failed closed.", failure);
        }
    }

    static <T> T one(List<T> rows) {
        if (rows.size() != 1) throw new SecurityException("AI state unavailable.");
        return rows.getFirst();
    }
    static UUID uuid(ResultSet r, String column) throws SQLException { return UUID.fromString(r.getString(column)); }
    static Instant instant(ResultSet r, String column) throws SQLException { return UtcJdbcTimestamp.get(r, column).toInstant(); }

    static byte[] hash(byte[] raw) {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(raw); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
