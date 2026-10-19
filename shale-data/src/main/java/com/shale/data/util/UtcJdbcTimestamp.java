package com.shale.data.util;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

/** JDBC boundary for datetime2 columns whose contract is UTC, never local business dates. */
public final class UtcJdbcTimestamp {
    private UtcJdbcTimestamp() {}

    // Calendar is mutable and JDBC drivers may modify it. Never share one across calls/threads.
    private static Calendar utc() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    public static void set(PreparedStatement statement, int index, Instant value) throws SQLException {
        statement.setTimestamp(index, Timestamp.from(value), utc());
    }

    public static Timestamp get(ResultSet rows, int index) throws SQLException {
        return rows.getTimestamp(index, utc());
    }

    public static Timestamp get(ResultSet rows, String column) throws SQLException {
        return rows.getTimestamp(column, utc());
    }
}
