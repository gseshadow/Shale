package com.shale.data.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.runtime.DbSessionProvider;

/**
 * DAO for task reads used by case task sections.
 */
public final class TaskDao {

    private final DbSessionProvider db;

    public TaskDao(DbSessionProvider db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public List<CaseTaskListItemDto> listActiveTasksForCase(long caseId, int shaleClientId) {
        if (caseId <= 0) {
            throw new IllegalArgumentException("caseId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        String sql = """
                SELECT
                  t.Id,
                  t.ShaleClientId,
                  t.CaseId,
                  t.Title,
                  t.Description,
                  t.DueAt,
                  t.CompletedAt,
                  t.CreatedByUserId,
                  t.CreatedAt,
                  t.UpdatedAt,
                  t.IsDeleted
                FROM dbo.Tasks t
                WHERE t.CaseId = ?
                  AND t.ShaleClientId = ?
                  AND ISNULL(t.IsDeleted, 0) = 0
                ORDER BY
                  CASE WHEN t.CompletedAt IS NULL THEN 0 ELSE 1 END ASC,
                  CASE WHEN t.DueAt IS NULL THEN 1 ELSE 0 END ASC,
                  t.DueAt ASC,
                  t.CreatedAt DESC,
                  t.Id DESC;
                """;

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, caseId);
            ps.setInt(2, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                List<CaseTaskListItemDto> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new CaseTaskListItemDto(
                            rs.getLong("Id"),
                            rs.getInt("ShaleClientId"),
                            rs.getLong("CaseId"),
                            rs.getString("Title"),
                            rs.getString("Description"),
                            toLocalDateTime(rs.getTimestamp("DueAt")),
                            toLocalDateTime(rs.getTimestamp("CompletedAt")),
                            (Integer) rs.getObject("CreatedByUserId"),
                            toLocalDateTime(rs.getTimestamp("CreatedAt")),
                            toLocalDateTime(rs.getTimestamp("UpdatedAt")),
                            rs.getBoolean("IsDeleted")
                    ));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load tasks for caseId=" + caseId, e);
        }
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
