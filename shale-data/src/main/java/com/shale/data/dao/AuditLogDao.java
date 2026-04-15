package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class AuditLogDao {
    private enum FieldCodeBindingMode {
        NUMERIC,
        TEXT
    }

    private final DbSessionProvider db;
    private final AtomicReference<FieldCodeBindingMode> fieldCodeBindingModeRef = new AtomicReference<>();

    public AuditLogDao(DbSessionProvider db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public void appendPhiWriteAudit(
            Integer userId,
            Integer objectTypeId,
            Long objectId,
            String fieldName,
            String actionType,
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
            bindFieldCode(ps, 5, actionType, bindingMode);
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
                    + " action=" + actionType
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

    private static void bindFieldCode(PreparedStatement ps, int parameterIndex, String actionType, FieldCodeBindingMode mode) throws SQLException {
        if (mode == FieldCodeBindingMode.NUMERIC) {
            ps.setInt(parameterIndex, actionCode(actionType));
            return;
        }
        ps.setString(parameterIndex, actionType);
    }

    private static int actionCode(String actionType) {
        if (actionType == null) {
            return 0;
        }
        return switch (actionType.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "CREATE" -> 1;
            case "UPDATE" -> 2;
            case "DELETE" -> 3;
            default -> 0;
        };
    }
}
