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
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.core.runtime.DbSessionProvider;

/**
 * DAO for task reads used by case task sections.
 */
public final class TaskDao {
    /**
     * Temporary default tinyint role used for primary task assignee rows.
     * <p>
     * The schema stores TaskAssignments.Role as tinyint and this phase does not expose
     * assignment-role semantics yet, so we use a single internal default code.
     */
    public static final byte DEFAULT_PRIMARY_ASSIGNMENT_ROLE = 1;

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
                  assignment.UserId AS AssignedUserId,
                  assignment.DisplayName AS AssignedUserDisplayName,
                  assignment.Color AS AssignedUserColor,
                  t.CreatedByUserId,
                  t.CreatedAt,
                  t.UpdatedAt,
                  t.IsDeleted
                FROM dbo.Tasks t
                OUTER APPLY (
                  SELECT TOP (1)
                    ta.UserId,
                    LTRIM(RTRIM(
                      COALESCE(u.name_first, '') +
                      CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
                      COALESCE(u.name_last, '')
                    )) AS DisplayName,
                    u.Color
                  FROM dbo.TaskAssignments ta
                  INNER JOIN dbo.Users u
                    ON u.Id = ta.UserId
                   AND u.ShaleClientId = ta.ShaleClientId
                  WHERE ta.TaskId = t.Id
                    AND ta.ShaleClientId = t.ShaleClientId
                    AND ta.IsPrimary = 1
                  ORDER BY
                    ta.AssignedAt DESC,
                    ta.UserId DESC
                ) assignment
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
                            (Integer) rs.getObject("AssignedUserId"),
                            rs.getString("AssignedUserDisplayName"),
                            rs.getString("AssignedUserColor"),
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
            Integer priorityId,
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
                  StatusId,
                  PriorityId,
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
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, SYSDATETIME(), SYSDATETIME(), 0);
                """;

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int defaultStatusId = resolveDefaultTaskStatusId(con, shaleClientId);
            int resolvedPriorityId = resolvePriorityIdForCreate(con, shaleClientId, priorityId);
            int i = 1;
            ps.setInt(i++, shaleClientId);
            ps.setInt(i++, defaultStatusId);
            ps.setInt(i++, resolvedPriorityId);
            ps.setString(i++, normalizedTitle);
            setNullableString(ps, i++, description);
            ps.setLong(i++, caseId);
            setNullableTimestamp(ps, i++, dueAt);
            ps.setInt(i++, createdByUserId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("Failed to create task for caseId=" + caseId);
                }
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to create task for caseId=" + caseId
                            + ", requestedPriorityId=" + priorityId
                            + " (sqlState=" + e.getSQLState() + ", errorCode=" + e.getErrorCode() + ")",
                    e);
        }
    }

    public List<TaskPriorityOptionDto> listActivePriorities(int shaleClientId) {
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        try (Connection con = db.requireConnection()) {
            String tableName = "dbo.Priorities";
            if (!tableExists(con, tableName)) {
                return List.of();
            }

            boolean hasName = hasColumn(con, tableName, "Name");
            boolean hasSortOrder = hasColumn(con, tableName, "SortOrder");
            boolean hasIsActive = hasColumn(con, tableName, "IsActive");

            StringBuilder sql = new StringBuilder("SELECT p.Id");
            if (hasName) {
                sql.append(", p.Name");
            }
            if (hasSortOrder) {
                sql.append(", p.SortOrder");
            }
            sql.append("\nFROM dbo.Priorities p\nWHERE p.ShaleClientId = ?");
            if (hasIsActive) {
                sql.append("\n  AND ISNULL(p.IsActive, 1) = 1");
            }
            sql.append("\nORDER BY ");
            if (hasSortOrder) {
                sql.append("ISNULL(p.SortOrder, 2147483647), ");
            }
            sql.append("p.Id;");

            try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
                ps.setInt(1, shaleClientId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<TaskPriorityOptionDto> out = new ArrayList<>();
                    while (rs.next()) {
                        int id = rs.getInt("Id");
                        String name = hasName ? rs.getString("Name") : null;
                        Integer sortOrder = hasSortOrder ? (Integer) rs.getObject("SortOrder") : null;
                        String displayName = name == null || name.isBlank() ? "Priority " + id : name.trim();
                        out.add(new TaskPriorityOptionDto(id, displayName, sortOrder));
                    }
                    return out;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list task priorities for shaleClientId=" + shaleClientId, e);
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

    public void assignPrimaryUserToTask(long taskId, int shaleClientId, int userId, int assignedByUserId) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be > 0");
        }
        if (assignedByUserId <= 0) {
            throw new IllegalArgumentException("assignedByUserId must be > 0");
        }

        String sql = """
                BEGIN TRY
                  BEGIN TRAN;

                  DECLARE @now datetime2 = SYSDATETIME();

                  IF NOT EXISTS (
                    SELECT 1
                    FROM dbo.Tasks t
                    WHERE t.Id = ?
                      AND t.ShaleClientId = ?
                      AND ISNULL(t.IsDeleted, 0) = 0
                  )
                  BEGIN
                    THROW 50001, 'Task not found for tenant.', 1;
                  END

                  IF NOT EXISTS (
                    SELECT 1
                    FROM dbo.Users u
                    WHERE u.Id = ?
                      AND u.ShaleClientId = ?
                  )
                  BEGIN
                    THROW 50002, 'Assignee user not found for tenant.', 1;
                  END

                  UPDATE dbo.TaskAssignments
                  SET IsPrimary = 0
                  WHERE TaskId = ?
                    AND ShaleClientId = ?
                    AND IsPrimary = 1;

                  UPDATE dbo.TaskAssignments
                  SET IsPrimary = 1,
                      Role = ?,
                      AssignedByUserId = ?,
                      AssignedAt = @now
                  WHERE TaskId = ?
                    AND ShaleClientId = ?
                    AND UserId = ?;

                  IF @@ROWCOUNT = 0
                  BEGIN
                    INSERT INTO dbo.TaskAssignments (
                      TaskId,
                      UserId,
                      ShaleClientId,
                      Role,
                      IsPrimary,
                      AssignedByUserId,
                      AssignedAt
                    )
                    VALUES (?, ?, ?, ?, 1, ?, @now);
                  END

                  UPDATE dbo.Tasks
                  SET UpdatedAt = @now
                  WHERE Id = ?
                    AND ShaleClientId = ?;

                  COMMIT;
                END TRY
                BEGIN CATCH
                  IF @@TRANCOUNT > 0 ROLLBACK;
                  THROW;
                END CATCH;
                """;

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setLong(i++, taskId);
            ps.setInt(i++, shaleClientId);
            ps.setInt(i++, userId);
            ps.setInt(i++, shaleClientId);
            ps.setLong(i++, taskId);
            ps.setInt(i++, shaleClientId);
            ps.setByte(i++, DEFAULT_PRIMARY_ASSIGNMENT_ROLE);
            ps.setInt(i++, assignedByUserId);
            ps.setLong(i++, taskId);
            ps.setInt(i++, shaleClientId);
            ps.setInt(i++, userId);
            ps.setLong(i++, taskId);
            ps.setInt(i++, userId);
            ps.setInt(i++, shaleClientId);
            ps.setByte(i++, DEFAULT_PRIMARY_ASSIGNMENT_ROLE);
            ps.setInt(i++, assignedByUserId);
            ps.setLong(i++, taskId);
            ps.setInt(i++, shaleClientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to assign taskId=" + taskId + " to userId=" + userId + " for shaleClientId=" + shaleClientId,
                    e);
        }
    }

    public void clearPrimaryUserAssignment(long taskId, int shaleClientId) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        String sql = """
                BEGIN TRY
                  BEGIN TRAN;

                  DECLARE @now datetime2 = SYSDATETIME();

                  UPDATE dbo.TaskAssignments
                  SET IsPrimary = 0
                  WHERE TaskId = ?
                    AND ShaleClientId = ?
                    AND IsPrimary = 1;

                  UPDATE dbo.Tasks
                  SET UpdatedAt = @now
                  WHERE Id = ?
                    AND ShaleClientId = ?
                    AND ISNULL(IsDeleted, 0) = 0;

                  COMMIT;
                END TRY
                BEGIN CATCH
                  IF @@TRANCOUNT > 0 ROLLBACK;
                  THROW;
                END CATCH;
                """;

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            ps.setInt(2, shaleClientId);
            ps.setLong(3, taskId);
            ps.setInt(4, shaleClientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear primary assignment for taskId=" + taskId, e);
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
        ps.setObject(index, value);
    }

    private static int resolveDefaultTaskStatusId(Connection con, int shaleClientId) throws SQLException {
        String sql = """
                SELECT TOP (1) s.Id
                FROM dbo.Statuses s
                WHERE s.ShaleClientId = ?
                  AND ISNULL(s.IsClosed, 0) = 0
                ORDER BY
                  CASE WHEN LOWER(LTRIM(RTRIM(ISNULL(s.Name, '')))) IN ('open', 'todo', 'to do', 'active') THEN 0 ELSE 1 END,
                  ISNULL(s.SortOrder, 2147483647),
                  s.Id;
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("No default open task status found for shaleClientId=" + shaleClientId);
    }

    private static int resolveDefaultTaskPriorityId(Connection con, int shaleClientId) throws SQLException {
        String tableName = "dbo.Priorities";
        if (!tableExists(con, tableName)) {
            throw new IllegalStateException("Priority resolution failed: table " + tableName + " does not exist.");
        }

        boolean hasName = hasColumn(con, tableName, "Name");
        boolean hasSortOrder = hasColumn(con, tableName, "SortOrder");
        boolean hasIsActive = hasColumn(con, tableName, "IsActive");

        StringBuilder sql = new StringBuilder("""
                SELECT TOP (1) p.Id
                FROM dbo.Priorities p
                WHERE p.ShaleClientId = ?
                """);
        if (hasIsActive) {
            sql.append("\n  AND ISNULL(p.IsActive, 1) = 1");
        }

        sql.append("\nORDER BY ");
        if (hasName) {
            sql.append("\n  CASE WHEN LOWER(LTRIM(RTRIM(ISNULL(p.Name, '')))) IN ('normal', 'medium', 'default', 'standard') THEN 0 ELSE 1 END,");
        }
        if (hasSortOrder) {
            sql.append("\n  ISNULL(p.SortOrder, 2147483647),");
        }
        sql.append("\n  p.Id;");

        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            ps.setInt(1, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("No default task priority found for shaleClientId=" + shaleClientId);
    }

    private static int resolvePriorityIdForCreate(Connection con, int shaleClientId, Integer requestedPriorityId) throws SQLException {
        if (requestedPriorityId != null && requestedPriorityId > 0
                && isPrioritySelectable(con, shaleClientId, requestedPriorityId)) {
            return requestedPriorityId;
        }
        return resolveDefaultTaskPriorityId(con, shaleClientId);
    }

    private static boolean isPrioritySelectable(Connection con, int shaleClientId, int priorityId) throws SQLException {
        String tableName = "dbo.Priorities";
        if (!tableExists(con, tableName)) {
            return false;
        }
        boolean hasIsActive = hasColumn(con, tableName, "IsActive");
        StringBuilder sql = new StringBuilder("""
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM dbo.Priorities p
                    WHERE p.Id = ?
                      AND p.ShaleClientId = ?
                """);
        if (hasIsActive) {
            sql.append("\n  AND ISNULL(p.IsActive, 1) = 1");
        }
        sql.append("\n) THEN 1 ELSE 0 END;");

        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            ps.setInt(1, priorityId);
            ps.setInt(2, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private static boolean tableExists(Connection con, String fullyQualifiedTableName) throws SQLException {
        String sql = "SELECT CASE WHEN OBJECT_ID(?, 'U') IS NULL THEN 0 ELSE 1 END;";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fullyQualifiedTableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private static boolean hasColumn(Connection con, String fullyQualifiedTableName, String columnName) throws SQLException {
        String sql = """
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM sys.columns c
                    WHERE c.object_id = OBJECT_ID(?)
                      AND c.name = ?
                ) THEN 1 ELSE 0 END;
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fullyQualifiedTableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
