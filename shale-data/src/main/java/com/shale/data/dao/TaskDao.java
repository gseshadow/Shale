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

    public long createTask(
            int shaleClientId,
            long caseId,
            String title,
            String description,
            LocalDateTime dueAt,
            int createdByUserId) {
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        if (caseId <= 0) {
            throw new IllegalArgumentException("caseId must be > 0");
        }
        if (createdByUserId <= 0) {
            throw new IllegalArgumentException("createdByUserId must be > 0");
        }
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }

        String sql = """
                INSERT INTO dbo.Tasks (
                  ShaleClientId,
                  Title,
                  Description,
                  CaseId,
                  DueAt,
                  CompletedAt,
                  CreatedByUserId,
                  CreatedAt,
                  UpdatedAt,
                  IsDeleted
                )
                OUTPUT INSERTED.Id
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, 0);
                """;

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, shaleClientId);
            ps.setString(i++, normalizedTitle);
            setNullableString(ps, i++, description);
            ps.setLong(i++, caseId);
            setNullableTimestamp(ps, i++, dueAt);
            ps.setInt(i++, createdByUserId);
            ps.setTimestamp(i++, now);
            ps.setTimestamp(i++, now);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("Failed to create task for caseId=" + caseId);
                }
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create task for caseId=" + caseId, e);
        }
    }

    public void markTaskCompleted(long taskId, int shaleClientId) {
        updateTaskCompletion(taskId, shaleClientId, true);
    }

    public void clearTaskCompleted(long taskId, int shaleClientId) {
        updateTaskCompletion(taskId, shaleClientId, false);
    }

    public void softDeleteTask(long taskId, int shaleClientId) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        String sql = """
                UPDATE dbo.Tasks
                SET IsDeleted = 1,
                    UpdatedAt = SYSDATETIME()
                WHERE Id = ?
                  AND ShaleClientId = ?
                  AND ISNULL(IsDeleted, 0) = 0;
                """;

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            ps.setInt(2, shaleClientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete taskId=" + taskId, e);
        }
    }

    private void updateTaskCompletion(long taskId, int shaleClientId, boolean completed) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        String sql = """
                UPDATE dbo.Tasks
                SET CompletedAt = %s,
                    UpdatedAt = SYSDATETIME()
                WHERE Id = ?
                  AND ShaleClientId = ?
                  AND ISNULL(IsDeleted, 0) = 0;
                """.formatted(completed ? "SYSDATETIME()" : "NULL");

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            ps.setInt(2, shaleClientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update completion for taskId=" + taskId, e);
        }
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, java.sql.Types.NVARCHAR);
            return;
        }
        ps.setString(index, value.trim());
    }

    private static void setNullableTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.TIMESTAMP);
            return;
        }
        ps.setTimestamp(index, Timestamp.valueOf(value));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
