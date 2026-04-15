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
import java.util.ArrayList;
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
        List<Object> params = new ArrayList<>();
        if (userId != null && userId > 0) {
            sql.append(" AND UserId = ?");
            params.add(userId);
        }
        if (objectId != null && objectId > 0) {
            sql.append(" AND ObjectId = ?");
            params.add(objectId);
        }
        if (fieldName != null && !fieldName.isBlank()) {
            sql.append(" AND FieldName = ?");
            params.add(fieldName.trim());
        }
        if (objectTypeId != null && objectTypeId > 0) {
            sql.append(" AND ObjectTypeId = ?");
            params.add(objectTypeId);
        }
        if (startDate != null) {
            sql.append(" AND EntryDate >= ?");
            params.add(Timestamp.valueOf(startDate.atStartOfDay()));
        }
        if (endDateInclusive != null) {
            sql.append(" AND EntryDate < ?");
            params.add(Timestamp.valueOf(endDateInclusive.plusDays(1).atStartOfDay()));
        }
        sql.append(" ORDER BY EntryDate DESC");
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            List<AuditLogEntryRow> rows = new ArrayList<>();
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
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list audit log entries", e);
        }
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
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?);
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            FieldCodeBindingMode bindingMode = resolveFieldCodeBindingMode(con);
            if (userId == null || userId <= 0) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, userId);
            }
            if (objectTypeId == null || objectTypeId <= 0) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, objectTypeId);
            }
            if (objectId == null || objectId <= 0) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, objectId);
            }
            ps.setString(4, fieldName);
            bindFieldCode(ps, 5, fieldCode, bindingMode);
            ps.setString(6, stringValue);
            if (dateValue == null) {
                ps.setNull(7, java.sql.Types.DATE);
            } else {
                ps.setDate(7, Date.valueOf(dateValue));
            }
            ps.setTimestamp(8, Timestamp.from(java.time.Instant.now()));
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
