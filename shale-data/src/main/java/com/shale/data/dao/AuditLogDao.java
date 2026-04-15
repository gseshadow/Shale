package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;

public final class AuditLogDao {
    private final DbSessionProvider db;

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
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, SYSUTCDATETIME());
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
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
            ps.setString(5, actionType);
            ps.setString(6, stringValue);
            if (dateValue == null) {
                ps.setNull(7, java.sql.Types.DATE);
            } else {
                ps.setDate(7, Date.valueOf(dateValue));
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to append PHI write audit entry", e);
        }
    }
}
