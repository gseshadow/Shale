package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class AuditLogDao {
    public record AuditLogEntryRow(
            LocalDateTime entryDate,
            Integer userId,
            Integer objectTypeId,
            Long objectId,
            String fieldName,
            String fieldCode,
            String stringValue,
            LocalDate dateValue,
            Boolean booleanValue,
            Integer intValue) {
    }

    private enum FieldCodeBindingMode {
        NUMERIC,
        TEXT
    }

    private final DbSessionProvider db;
    private final AtomicReference<FieldCodeBindingMode> fieldCodeBindingModeRef = new AtomicReference<>();

    public AuditLogDao(DbSessionProvider db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public List<AuditLogEntryRow> listAuditLogEntries(
            Integer userId,
            Long objectId,
            String fieldName,
            Integer objectTypeId,
            LocalDate startDate,
            LocalDate endDateInclusive) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                  EntryDate,
                  UserId,
                  ObjectTypeId,
                  ObjectId,
                  FieldName,
                  FieldCode,
                  StringValue,
                  DateValue,
                  BooleanValue,
                  IntValue
                FROM dbo.AuditLog
                WHERE 1=1
                """);
        sql.append(" AND ShaleClientId = ?");
        if (userId != null && userId > 0) {
            sql.append(" AND UserId = ?");
        }
        if (objectId != null && objectId > 0) {
            sql.append(" AND ObjectId = ?");
        }
        if (fieldName != null && !fieldName.isBlank()) {
            sql.append(" AND FieldName = ?");
        }
        if (objectTypeId != null && objectTypeId > 0) {
            sql.append(" AND ObjectTypeId = ?");
        }
        if (startDate != null) {
            sql.append(" AND EntryDate >= ?");
        }
        if (endDateInclusive != null) {
            sql.append(" AND EntryDate < ?");
        }
        sql.append(" ORDER BY EntryDate DESC");
        String finalSql = sql.toString();
        try (Connection con = db.requireConnection()) {
            int shaleClientId = requireCurrentShaleClientId(con);
            try (PreparedStatement ps = con.prepareStatement(finalSql)) {
                int placeholderCount = countPlaceholders(finalSql);
                int parameterIndex = 1;
                ps.setInt(parameterIndex, shaleClientId);
                logAuditListParamBinding(parameterIndex++, shaleClientId);
                if (userId != null && userId > 0) {
                    ps.setInt(parameterIndex, userId);
                    logAuditListParamBinding(parameterIndex++, userId);
                }
                if (objectId != null && objectId > 0) {
                    ps.setLong(parameterIndex, objectId);
                    logAuditListParamBinding(parameterIndex++, objectId);
                }
                if (fieldName != null && !fieldName.isBlank()) {
                    String trimmedFieldName = fieldName.trim();
                    ps.setString(parameterIndex, trimmedFieldName);
                    logAuditListParamBinding(parameterIndex++, trimmedFieldName);
                }
                if (objectTypeId != null && objectTypeId > 0) {
                    ps.setInt(parameterIndex, objectTypeId);
                    logAuditListParamBinding(parameterIndex++, objectTypeId);
                }
                if (startDate != null) {
                    Timestamp startTs = Timestamp.valueOf(startDate.atStartOfDay());
                    ps.setTimestamp(parameterIndex, startTs);
                    logAuditListParamBinding(parameterIndex++, startTs);
                }
                if (endDateInclusive != null) {
                    Timestamp endExclusiveTs = Timestamp.valueOf(endDateInclusive.plusDays(1).atStartOfDay());
                    ps.setTimestamp(parameterIndex, endExclusiveTs);
                    logAuditListParamBinding(parameterIndex++, endExclusiveTs);
                }
                int boundParamCount = parameterIndex - 1;
                System.err.println("[AUDIT_LOG_DAO] listAuditLogEntries sql=" + finalSql);
                System.err.println("[AUDIT_LOG_DAO] listAuditLogEntries placeholders=" + placeholderCount + " bound=" + boundParamCount);
                if (boundParamCount != placeholderCount) {
                    throw new IllegalStateException(
                            "AuditLogDao.listAuditLogEntries parameter mismatch: placeholders="
                                    + placeholderCount + ", bound=" + boundParamCount);
                }
                List<AuditLogEntryRow> rows = new java.util.ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp entryDateTs = rs.getTimestamp("EntryDate");
                        Date dateValueSql = rs.getDate("DateValue");
                        rows.add(new AuditLogEntryRow(
                                entryDateTs == null ? null : entryDateTs.toLocalDateTime(),
                                asInteger(rs, "UserId"),
                                asInteger(rs, "ObjectTypeId"),
                                asLong(rs, "ObjectId"),
                                rs.getString("FieldName"),
                                rs.getString("FieldCode"),
                                rs.getString("StringValue"),
                                dateValueSql == null ? null : dateValueSql.toLocalDate(),
                                asBoolean(rs, "BooleanValue"),
                                asInteger(rs, "IntValue")));
                    }
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list audit log entries", e);
        }
    }

    private static int countPlaceholders(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }

    private static void logAuditListParamBinding(int parameterIndex, Object value) {
        System.err.println("[AUDIT_LOG_DAO] listAuditLogEntries bind[" + parameterIndex + "]=" + value);
    }

    public void appendPhiWriteAudit(
            Integer userId,
            Integer objectTypeId,
            Long objectId,
            String fieldName,
            Integer fieldCode,
            String stringValue,
            LocalDate dateValue) {
        String sql = """
                INSERT INTO dbo.AuditLog (
                  ShaleClientId,
                  UserId,
                  ObjectTypeId,
                  ObjectId,
                  FieldName,
                  FieldCode,
                  StringValue,
                  DateValue,
                  BooleanValue,
                  IntValue,
                  EntryDate
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?);
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            FieldCodeBindingMode bindingMode = resolveFieldCodeBindingMode(con);
            ps.setInt(1, requireCurrentShaleClientId(con));
            if (userId == null || userId <= 0) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, userId);
            }
            if (objectTypeId == null || objectTypeId <= 0) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(3, objectTypeId);
            }
            if (objectId == null || objectId <= 0) {
                ps.setNull(4, java.sql.Types.BIGINT);
            } else {
                ps.setLong(4, objectId);
            }
            ps.setString(5, fieldName);
            bindFieldCode(ps, 6, fieldCode, bindingMode);
            ps.setString(7, stringValue);
            if (dateValue == null) {
                ps.setNull(8, java.sql.Types.DATE);
            } else {
                ps.setDate(8, Date.valueOf(dateValue));
            }
            ps.setTimestamp(9, Timestamp.from(java.time.Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[PHI_AUDIT] insert failed"
                    + " field=" + fieldName
                    + " fieldCode=" + fieldCode
                    + " objectId=" + objectId
                    + " objectTypeId=" + objectTypeId
                    + " userId=" + userId
                    + " dateValue=" + dateValue
                    + " sqlState=" + e.getSQLState()
                    + " errorCode=" + e.getErrorCode()
                    + " message=" + e.getMessage());
            throw new RuntimeException("Failed to append PHI write audit entry", e);
        }
    }

    private static int requireCurrentShaleClientId(Connection con) throws SQLException {
        String sql = "SELECT CAST(SESSION_CONTEXT(N'ShaleClientId') AS INT);";
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("ShaleClientId session context is missing.");
            }
            int shaleClientId = rs.getInt(1);
            if (rs.wasNull()) {
                throw new IllegalStateException("ShaleClientId session context is missing.");
            }
            return shaleClientId;
        }
    }

    private FieldCodeBindingMode resolveFieldCodeBindingMode(Connection con) {
        FieldCodeBindingMode cached = fieldCodeBindingModeRef.get();
        if (cached != null) {
            return cached;
        }
        FieldCodeBindingMode resolved = FieldCodeBindingMode.TEXT;
        try {
            DatabaseMetaData metaData = con.getMetaData();
            try (var rs = metaData.getColumns(con.getCatalog(), "dbo", "AuditLog", "FieldCode")) {
                if (rs.next()) {
                    int dataType = rs.getInt("DATA_TYPE");
                    if (dataType == java.sql.Types.INTEGER
                            || dataType == java.sql.Types.SMALLINT
                            || dataType == java.sql.Types.TINYINT
                            || dataType == java.sql.Types.BIGINT
                            || dataType == java.sql.Types.NUMERIC
                            || dataType == java.sql.Types.DECIMAL) {
                        resolved = FieldCodeBindingMode.NUMERIC;
                    }
                }
            }
        } catch (SQLException ex) {
            System.err.println("[PHI_AUDIT] failed to inspect AuditLog.FieldCode type, defaulting to text. " + ex.getMessage());
        }
        fieldCodeBindingModeRef.compareAndSet(null, resolved);
        return fieldCodeBindingModeRef.get();
    }

    private static void bindFieldCode(PreparedStatement ps, int parameterIndex, Integer fieldCode, FieldCodeBindingMode mode) throws SQLException {
        int resolvedCode = fieldCode == null ? 0 : fieldCode;
        if (mode == FieldCodeBindingMode.NUMERIC) {
            ps.setInt(parameterIndex, resolvedCode);
            return;
        }
        ps.setString(parameterIndex, Integer.toString(resolvedCode));
    }

    private static Integer asInteger(ResultSet rs, String columnLabel) throws SQLException {
        int value = rs.getInt(columnLabel);
        return rs.wasNull() ? null : value;
    }

    private static Long asLong(ResultSet rs, String columnLabel) throws SQLException {
        long value = rs.getLong(columnLabel);
        return rs.wasNull() ? null : value;
    }

    private static Boolean asBoolean(ResultSet rs, String columnLabel) throws SQLException {
        boolean value = rs.getBoolean(columnLabel);
        return rs.wasNull() ? null : value;
    }
}
