package com.shale.data.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.core.dto.TaskStatusOptionDto;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.semantics.RoleSemantics;

/**
 * DAO for task reads used by case task sections.
 */
public final class TaskDao {
    private static final int ROLE_RESPONSIBLE_ATTORNEY = RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY;
    private static final String PRIORITIES_TABLE = "dbo.Priorities";
    private static final String TASK_STATUSES_TABLE = "dbo.TaskStatuses";
    private static final String PRIORITY_SYSTEM_KEY_NORMAL = "normal";
    private static final String TASK_STATUS_SYSTEM_KEY_OPEN = "open";
    public static final class TaskTimelineEventTypes {
        public static final String TASK_CREATED = "TASK_CREATED";
        public static final String TASK_COMPLETED = "TASK_COMPLETED";
        public static final String TASK_REOPENED = "TASK_REOPENED";
        public static final String TASK_TITLE_CHANGED = "TASK_TITLE_CHANGED";
        public static final String TASK_DESCRIPTION_CHANGED = "TASK_DESCRIPTION_CHANGED";
        public static final String TASK_DUE_DATE_CHANGED = "TASK_DUE_DATE_CHANGED";
        public static final String TASK_PRIORITY_CHANGED = "TASK_PRIORITY_CHANGED";
        public static final String TASK_STATUS_CHANGED = "TASK_STATUS_CHANGED";
        public static final String TASK_ASSIGNMENT_ADDED = "TASK_ASSIGNMENT_ADDED";
        public static final String TASK_ASSIGNMENT_REMOVED = "TASK_ASSIGNMENT_REMOVED";
        public static final String TASK_DELETED = "TASK_DELETED";

        private static final Set<String> ALLOWED = Set.of(
                TASK_CREATED,
                TASK_COMPLETED,
                TASK_REOPENED,
                TASK_TITLE_CHANGED,
                TASK_DESCRIPTION_CHANGED,
                TASK_DUE_DATE_CHANGED,
                TASK_PRIORITY_CHANGED,
                TASK_STATUS_CHANGED,
                TASK_ASSIGNMENT_ADDED,
                TASK_ASSIGNMENT_REMOVED,
                TASK_DELETED
        );

        private TaskTimelineEventTypes() {
        }
    }

    public enum MyTaskSort {
        DEFAULT,
        DUE_DATE_ASC,
        DUE_DATE_DESC
    }

    public enum CaseTaskSort {
        DEFAULT,
        DUE_DATE_ASC,
        DUE_DATE_DESC,
        PRIORITY_ASC,
        PRIORITY_DESC
    }
    /**
     * Temporary default tinyint role used for primary task assignee rows.
     * <p>
     * The schema stores TaskAssignments.Role as tinyint and this phase does not expose
     * assignment-role semantics yet, so we use a single internal default code.
     */
    public static final byte DEFAULT_PRIMARY_ASSIGNMENT_ROLE = 1;

    private record PriorityLookupRow(int id, String name, Integer sortOrder, String systemKey, String colorHex) {
    }
    private record TaskStatusLookupRow(int id, String name, Integer sortOrder, String systemKey, String colorHex) {
    }
    public record TaskAssignedUserRow(int userId, String displayName, String color) {
    }
    public record TaskAssignedTaskUserRow(long taskId, int userId, String displayName, String color) {
    }
    public record AssignedUserTaskRow(
            long taskId,
            int shaleClientId,
            long caseId,
            String caseName,
            String caseResponsibleAttorney,
            String caseResponsibleAttorneyColor,
            Boolean caseNonEngagementLetterSent,
            String title,
            String description,
            String priorityColorHex,
            String priorityName,
            Integer prioritySortOrder,
            LocalDateTime dueAt,
            LocalDateTime completedAt,
            Integer assignedUserId,
            String assignedUserDisplayName,
            String assignedUserColor,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
    public record TaskAssignableUserRow(int id, String displayName, String color) {
    }
    public record TaskTimelineEventRow(
            long id,
            long taskId,
            int caseId,
            int shaleClientId,
            String taskTitle,
            String eventType,
            Integer actorUserId,
            String actorDisplayName,
            String title,
            String body,
            LocalDateTime occurredAt,
            boolean isDeleted) {
    }
    public record TaskUpdateRow(
            long id,
            long taskId,
            int caseId,
            int shaleClientId,
            int userId,
            String userDisplayName,
            String userColor,
            String body,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean isDeleted) {
    }

    private final DbSessionProvider db;
    private final PhiAuditService phiAuditService;

    public TaskDao(DbSessionProvider db) {
        this.db = Objects.requireNonNull(db, "db");
        this.phiAuditService = new PhiAuditService(new AuditLogDao(this.db));
    }

    public List<CaseTaskListItemDto> listActiveTasksForCase(long caseId, int shaleClientId, CaseTaskSort sort) {
        if (caseId <= 0) {
            throw new IllegalArgumentException("caseId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        CaseTaskSort resolvedSort = sort == null ? CaseTaskSort.DEFAULT : sort;

        String sortOrderClause = switch (resolvedSort) {
            case DUE_DATE_ASC, DEFAULT -> """
                    CASE WHEN t.DueAt IS NULL THEN 1 ELSE 0 END ASC,
                    t.DueAt ASC,
                    t.UpdatedAt DESC,
                    t.CreatedAt DESC,
                    t.Id DESC
                    """;
            case DUE_DATE_DESC -> """
                    CASE WHEN t.DueAt IS NULL THEN 1 ELSE 0 END ASC,
                    t.DueAt DESC,
                    t.UpdatedAt DESC,
                    t.CreatedAt DESC,
                    t.Id DESC
                    """;
            case PRIORITY_ASC -> """
                    CASE WHEN p.SortOrder IS NULL THEN 1 ELSE 0 END ASC,
                    p.SortOrder ASC,
                    CASE WHEN t.DueAt IS NULL THEN 1 ELSE 0 END ASC,
                    t.DueAt ASC,
                    t.UpdatedAt DESC,
                    t.CreatedAt DESC,
                    t.Id DESC
                    """;
            case PRIORITY_DESC -> """
                    CASE WHEN p.SortOrder IS NULL THEN 1 ELSE 0 END ASC,
                    p.SortOrder DESC,
                    CASE WHEN t.DueAt IS NULL THEN 1 ELSE 0 END ASC,
                    t.DueAt ASC,
                    t.UpdatedAt DESC,
                    t.CreatedAt DESC,
                    t.Id DESC
                    """;
        };

        String sql = """
                SELECT
                  t.Id,
                  t.ShaleClientId,
                  t.CaseId,
                  c.Name AS CaseName,
                  caseAttorney.DisplayName AS CaseResponsibleAttorney,
                  caseAttorney.Color AS CaseResponsibleAttorneyColor,
                  c.NonEngagementLetterSent AS CaseNonEngagementLetterSent,
                  t.Title,
                  t.Description,
                  t.PriorityId,
                  p.ColorHex AS PriorityColorHex,
                  t.DueAt,
                  t.CompletedAt,
                  assignment.UserId AS AssignedUserId,
                  assignment.DisplayName AS AssignedUserDisplayName,
                  assignment.Color AS AssignedUserColor,
                  t.CreatedByUserId,
                  LTRIM(RTRIM(
                    COALESCE(createdByUser.name_first, '') +
                    CASE WHEN COALESCE(createdByUser.name_first, '') = '' OR COALESCE(createdByUser.name_last, '') = '' THEN '' ELSE ' ' END +
                    COALESCE(createdByUser.name_last, '')
                  )) AS CreatedByDisplayName,
                  t.CreatedAt,
                  t.UpdatedAt,
                  t.IsDeleted
                FROM dbo.Tasks t
                INNER JOIN dbo.Cases c
                  ON c.Id = t.CaseId
                 AND c.ShaleClientId = t.ShaleClientId
                LEFT JOIN dbo.Users createdByUser
                  ON createdByUser.Id = t.CreatedByUserId
                 AND createdByUser.ShaleClientId = t.ShaleClientId
                LEFT JOIN dbo.Priorities p
                  ON p.Id = t.PriorityId
                 AND (p.ShaleClientId = t.ShaleClientId OR p.ShaleClientId IS NULL)
                OUTER APPLY (
                  SELECT TOP (1)
                    LTRIM(RTRIM(
                      COALESCE(u.name_first, '') +
                      CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
                      COALESCE(u.name_last, '')
                    )) AS DisplayName,
                    u.Color
                  FROM dbo.CaseUsers cu
                  INNER JOIN dbo.Users u
                    ON u.Id = cu.UserId
                   AND u.ShaleClientId = c.ShaleClientId
                  WHERE cu.CaseId = c.Id
                    AND cu.RoleId = ?
                    AND cu.IsPrimary = 1
                  ORDER BY
                    cu.UpdatedAt DESC,
                    cu.CreatedAt DESC,
                    cu.Id DESC
                ) caseAttorney
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
                OUTER APPLY (
                  SELECT
                    LTRIM(RTRIM(
                      COALESCE(u.name_first, '') +
                      CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
                      COALESCE(u.name_last, '')
                    )) AS DisplayName
                  FROM dbo.Users u
                  WHERE u.Id = t.CreatedByUserId
                    AND u.ShaleClientId = t.ShaleClientId
                ) createdBy
                WHERE t.CaseId = ?
                  AND t.ShaleClientId = ?
                  AND ISNULL(t.IsDeleted, 0) = 0
                ORDER BY
                  CASE WHEN t.CompletedAt IS NULL THEN 0 ELSE 1 END ASC,
                  %s;
                """.formatted(sortOrderClause);

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ROLE_RESPONSIBLE_ATTORNEY);
            ps.setLong(2, caseId);
            ps.setInt(3, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                List<CaseTaskListItemDto> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new CaseTaskListItemDto(
                            rs.getLong("Id"),
                            rs.getInt("ShaleClientId"),
                            rs.getLong("CaseId"),
                            rs.getString("CaseName"),
                            rs.getString("CaseResponsibleAttorney"),
                            rs.getString("CaseResponsibleAttorneyColor"),
                            (Boolean) rs.getObject("CaseNonEngagementLetterSent"),
                            rs.getString("Title"),
                            rs.getString("Description"),
                            (Integer) rs.getObject("PriorityId"),
                            rs.getString("PriorityColorHex"),
                            toLocalDateTime(rs.getTimestamp("DueAt")),
                            toLocalDateTime(rs.getTimestamp("CompletedAt")),
                            (Integer) rs.getObject("AssignedUserId"),
                            rs.getString("AssignedUserDisplayName"),
                            rs.getString("AssignedUserColor"),
                            (Integer) rs.getObject("CreatedByUserId"),
                            rs.getString("CreatedByDisplayName"),
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

    public List<CaseTaskListItemDto> listActiveTasksAssignedToUser(int shaleClientId, int assignedUserId, MyTaskSort sort) {
        return listActiveTasksAssignedToUser(shaleClientId, assignedUserId, sort, false);
    }

    public List<CaseTaskListItemDto> listActiveTasksAssignedToUser(
            int shaleClientId,
            int assignedUserId,
            MyTaskSort sort,
            boolean includeCompleted) {
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        if (assignedUserId <= 0) {
            throw new IllegalArgumentException("assignedUserId must be > 0");
        }

        MyTaskSort resolvedSort = sort == null ? MyTaskSort.DEFAULT : sort;
        String dueOrderSql = resolvedSort == MyTaskSort.DUE_DATE_DESC ? "DESC" : "ASC";

        String sql = """
                SELECT
                  t.Id,
                  t.ShaleClientId,
                  t.CaseId,
                  c.Name AS CaseName,
                  caseAttorney.DisplayName AS CaseResponsibleAttorney,
                  caseAttorney.Color AS CaseResponsibleAttorneyColor,
                  c.NonEngagementLetterSent AS CaseNonEngagementLetterSent,
                  t.Title,
                  t.Description,
                  t.PriorityId,
                  p.ColorHex AS PriorityColorHex,
                  t.DueAt,
                  t.CompletedAt,
                  assignment.UserId AS AssignedUserId,
                  assignment.DisplayName AS AssignedUserDisplayName,
                  assignment.Color AS AssignedUserColor,
                  t.CreatedByUserId,
                  LTRIM(RTRIM(
                    COALESCE(createdByUser.name_first, '') +
                    CASE WHEN COALESCE(createdByUser.name_first, '') = '' OR COALESCE(createdByUser.name_last, '') = '' THEN '' ELSE ' ' END +
                    COALESCE(createdByUser.name_last, '')
                  )) AS CreatedByDisplayName,
                  t.CreatedAt,
                  t.UpdatedAt,
                  t.IsDeleted
                FROM dbo.Tasks t
                INNER JOIN dbo.Cases c
                  ON c.Id = t.CaseId
                 AND c.ShaleClientId = t.ShaleClientId
                LEFT JOIN dbo.Users createdByUser
                  ON createdByUser.Id = t.CreatedByUserId
                 AND createdByUser.ShaleClientId = t.ShaleClientId
                LEFT JOIN dbo.Priorities p
                  ON p.Id = t.PriorityId
                 AND (p.ShaleClientId = t.ShaleClientId OR p.ShaleClientId IS NULL)
                OUTER APPLY (
                  SELECT TOP (1)
                    LTRIM(RTRIM(
                      COALESCE(u.name_first, '') +
                      CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
                      COALESCE(u.name_last, '')
                    )) AS DisplayName,
                    u.Color
                  FROM dbo.CaseUsers cu
                  INNER JOIN dbo.Users u
                    ON u.Id = cu.UserId
                   AND u.ShaleClientId = c.ShaleClientId
                  WHERE cu.CaseId = c.Id
                    AND cu.RoleId = ?
                    AND cu.IsPrimary = 1
                  ORDER BY
                    cu.UpdatedAt DESC,
                    cu.CreatedAt DESC,
                    cu.Id DESC
                ) caseAttorney
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
                OUTER APPLY (
                  SELECT
                    LTRIM(RTRIM(
                      COALESCE(u.name_first, '') +
                      CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
                      COALESCE(u.name_last, '')
                    )) AS DisplayName
                  FROM dbo.Users u
                  WHERE u.Id = t.CreatedByUserId
                    AND u.ShaleClientId = t.ShaleClientId
                ) createdBy
                WHERE t.ShaleClientId = ?
                  AND EXISTS (
                    SELECT 1
                    FROM dbo.TaskAssignments myAssignment
                    WHERE myAssignment.TaskId = t.Id
                      AND myAssignment.ShaleClientId = t.ShaleClientId
                      AND myAssignment.UserId = ?
                  )
                  AND ISNULL(t.IsDeleted, 0) = 0
                  AND ISNULL(t.StatusId, 0) <> 3
                  %s
                ORDER BY
                  CASE WHEN t.CompletedAt IS NULL THEN 0 ELSE 1 END ASC,
                  CASE WHEN t.DueAt IS NULL THEN 1 ELSE 0 END ASC,
                  t.DueAt %s,
                  t.UpdatedAt DESC,
                  t.CreatedAt DESC,
                  t.Id DESC;
                """.formatted(includeCompleted ? "" : "AND t.CompletedAt IS NULL", dueOrderSql);

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ROLE_RESPONSIBLE_ATTORNEY);
            ps.setInt(2, shaleClientId);
            ps.setInt(3, assignedUserId);
            try (ResultSet rs = ps.executeQuery()) {
                List<CaseTaskListItemDto> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new CaseTaskListItemDto(
                            rs.getLong("Id"),
                            rs.getInt("ShaleClientId"),
                            rs.getLong("CaseId"),
                            rs.getString("CaseName"),
                            rs.getString("CaseResponsibleAttorney"),
                            rs.getString("CaseResponsibleAttorneyColor"),
                            (Boolean) rs.getObject("CaseNonEngagementLetterSent"),
                            rs.getString("Title"),
                            rs.getString("Description"),
                            (Integer) rs.getObject("PriorityId"),
                            rs.getString("PriorityColorHex"),
                            toLocalDateTime(rs.getTimestamp("DueAt")),
                            toLocalDateTime(rs.getTimestamp("CompletedAt")),
                            (Integer) rs.getObject("AssignedUserId"),
                            rs.getString("AssignedUserDisplayName"),
                            rs.getString("AssignedUserColor"),
                            (Integer) rs.getObject("CreatedByUserId"),
                            rs.getString("CreatedByDisplayName"),
                            toLocalDateTime(rs.getTimestamp("CreatedAt")),
                            toLocalDateTime(rs.getTimestamp("UpdatedAt")),
                            rs.getBoolean("IsDeleted")
                    ));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to load tasks for assignedUserId=" + assignedUserId + " shaleClientId=" + shaleClientId,
                    e);
        }
    }

    public List<AssignedUserTaskRow> listActiveTasksForAssigneeInTenant(int shaleClientId, int assignedUserId) {
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        if (assignedUserId <= 0) {
            throw new IllegalArgumentException("assignedUserId must be > 0");
        }

        String sql = """
                SELECT
                  t.Id,
                  t.ShaleClientId,
                  t.CaseId,
                  c.Name AS CaseName,
                  caseAttorney.DisplayName AS CaseResponsibleAttorney,
                  caseAttorney.Color AS CaseResponsibleAttorneyColor,
                  c.NonEngagementLetterSent AS CaseNonEngagementLetterSent,
                  t.Title,
                  t.Description,
                  p.ColorHex AS PriorityColorHex,
                  p.Name AS PriorityName,
                  p.SortOrder AS PrioritySortOrder,
                  t.DueAt,
                  t.CompletedAt,
                  assignment.UserId AS AssignedUserId,
                  assignment.DisplayName AS AssignedUserDisplayName,
                  assignment.Color AS AssignedUserColor,
                  t.CreatedAt,
                  t.UpdatedAt
                FROM dbo.Tasks t
                INNER JOIN dbo.Cases c
                  ON c.Id = t.CaseId
                 AND c.ShaleClientId = t.ShaleClientId
                LEFT JOIN dbo.Priorities p
                  ON p.Id = t.PriorityId
                 AND (p.ShaleClientId = t.ShaleClientId OR p.ShaleClientId IS NULL)
                OUTER APPLY (
                  SELECT TOP (1)
                    LTRIM(RTRIM(
                      COALESCE(u.name_first, '') +
                      CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
                      COALESCE(u.name_last, '')
                    )) AS DisplayName,
                    u.Color
                  FROM dbo.CaseUsers cu
                  INNER JOIN dbo.Users u
                    ON u.Id = cu.UserId
                   AND u.ShaleClientId = c.ShaleClientId
                  WHERE cu.CaseId = c.Id
                    AND cu.RoleId = ?
                    AND cu.IsPrimary = 1
                  ORDER BY
                    cu.UpdatedAt DESC,
                    cu.CreatedAt DESC,
                    cu.Id DESC
                ) caseAttorney
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
                WHERE t.ShaleClientId = ?
                  AND EXISTS (
                    SELECT 1
                    FROM dbo.TaskAssignments myAssignment
                    WHERE myAssignment.TaskId = t.Id
                      AND myAssignment.ShaleClientId = t.ShaleClientId
                      AND myAssignment.UserId = ?
                  )
                  AND ISNULL(t.IsDeleted, 0) = 0
                ORDER BY
                  CASE WHEN t.CompletedAt IS NULL THEN 0 ELSE 1 END ASC,
                  CASE WHEN t.DueAt IS NULL THEN 1 ELSE 0 END ASC,
                  t.DueAt ASC,
                  t.UpdatedAt DESC,
                  t.CreatedAt DESC,
                  t.Id DESC;
                """;

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ROLE_RESPONSIBLE_ATTORNEY);
            ps.setInt(2, shaleClientId);
            ps.setInt(3, assignedUserId);
            try (ResultSet rs = ps.executeQuery()) {
                List<AssignedUserTaskRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new AssignedUserTaskRow(
                            rs.getLong("Id"),
                            rs.getInt("ShaleClientId"),
                            rs.getLong("CaseId"),
                            rs.getString("CaseName"),
                            rs.getString("CaseResponsibleAttorney"),
                            rs.getString("CaseResponsibleAttorneyColor"),
                            (Boolean) rs.getObject("CaseNonEngagementLetterSent"),
                            rs.getString("Title"),
                            rs.getString("Description"),
                            rs.getString("PriorityColorHex"),
                            rs.getString("PriorityName"),
                            (Integer) rs.getObject("PrioritySortOrder"),
                            toLocalDateTime(rs.getTimestamp("DueAt")),
                            toLocalDateTime(rs.getTimestamp("CompletedAt")),
                            (Integer) rs.getObject("AssignedUserId"),
                            rs.getString("AssignedUserDisplayName"),
                            rs.getString("AssignedUserColor"),
                            toLocalDateTime(rs.getTimestamp("CreatedAt")),
                            toLocalDateTime(rs.getTimestamp("UpdatedAt"))));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to load assigned user tasks for assignedUserId=" + assignedUserId + " shaleClientId=" + shaleClientId,
                    e);
        }
    }

    public TaskDetailDto findTaskDetail(long taskId, int shaleClientId) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        String sql = """
                SELECT
                  t.Id,
                  t.ShaleClientId,
                  t.CaseId,
                  c.Name AS CaseName,
                  caseAttorney.DisplayName AS CaseResponsibleAttorney,
                  caseAttorney.Color AS CaseResponsibleAttorneyColor,
                  c.NonEngagementLetterSent AS CaseNonEngagementLetterSent,
                  t.Title,
                  t.Description,
                  t.DueAt,
                  t.StatusId,
                  t.PriorityId,
                  t.CompletedAt,
                  assignment.UserId AS AssignedUserId,
                  assignment.DisplayName AS AssignedUserDisplayName,
                  assignment.Color AS AssignedUserColor,
                  creator.DisplayName AS CreatedByDisplayName
                FROM dbo.Tasks t
                INNER JOIN dbo.Cases c
                  ON c.Id = t.CaseId
                 AND c.ShaleClientId = t.ShaleClientId
                LEFT JOIN dbo.Users createdBy
                  ON createdBy.Id = t.CreatedByUserId
                 AND createdBy.ShaleClientId = t.ShaleClientId
                OUTER APPLY (
                  SELECT LTRIM(RTRIM(
                    COALESCE(createdBy.name_first, '') +
                    CASE WHEN COALESCE(createdBy.name_first, '') = '' OR COALESCE(createdBy.name_last, '') = '' THEN '' ELSE ' ' END +
                    COALESCE(createdBy.name_last, '')
                  )) AS DisplayName
                ) creator
                OUTER APPLY (
                  SELECT TOP (1)
                    LTRIM(RTRIM(
                      COALESCE(u.name_first, '') +
                      CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
                      COALESCE(u.name_last, '')
                    )) AS DisplayName,
                    u.Color
                  FROM dbo.CaseUsers cu
                  INNER JOIN dbo.Users u
                    ON u.Id = cu.UserId
                   AND u.ShaleClientId = c.ShaleClientId
                  WHERE cu.CaseId = c.Id
                    AND cu.RoleId = ?
                    AND cu.IsPrimary = 1
                  ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
                ) caseAttorney
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
                  ORDER BY ta.AssignedAt DESC, ta.UserId DESC
                ) assignment
                WHERE t.Id = ?
                  AND t.ShaleClientId = ?
                  AND ISNULL(t.IsDeleted, 0) = 0;
                """;

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ROLE_RESPONSIBLE_ATTORNEY);
            ps.setLong(2, taskId);
            ps.setInt(3, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new TaskDetailDto(
                        rs.getLong("Id"),
                        rs.getInt("ShaleClientId"),
                        rs.getLong("CaseId"),
                        rs.getString("CaseName"),
                        rs.getString("CaseResponsibleAttorney"),
                        rs.getString("CaseResponsibleAttorneyColor"),
                        (Boolean) rs.getObject("CaseNonEngagementLetterSent"),
                        rs.getString("Title"),
                        rs.getString("Description"),
                        toLocalDateTime(rs.getTimestamp("DueAt")),
                        (Integer) rs.getObject("StatusId"),
                        (Integer) rs.getObject("PriorityId"),
                        toLocalDateTime(rs.getTimestamp("CompletedAt")),
                        (Integer) rs.getObject("AssignedUserId"),
                        rs.getString("AssignedUserDisplayName"),
                        rs.getString("AssignedUserColor"),
                        rs.getString("CreatedByDisplayName"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load task detail for taskId=" + taskId, e);
        }
    }

    public List<TaskAssignedUserRow> listAssignedUsersForTask(long taskId, int shaleClientId) {
        if (taskId <= 0) {
            return List.of();
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        String sql = """
                SELECT
                  u.Id AS UserId,
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
                WHERE ta.TaskId = ?
                  AND ta.ShaleClientId = ?
                ORDER BY
                  ta.IsPrimary DESC,
                  u.name_first ASC,
                  u.name_last ASC,
                  u.Id ASC;
                """;

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            ps.setInt(2, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TaskAssignedUserRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new TaskAssignedUserRow(
                            rs.getInt("UserId"),
                            rs.getString("DisplayName"),
                            rs.getString("Color")));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to list assigned users for taskId=" + taskId + " shaleClientId=" + shaleClientId,
                    e);
        }
    }

    public List<TaskAssignableUserRow> listAssignableUsersForTask(long taskId, int shaleClientId) {
        if (taskId <= 0) {
            return List.of();
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        String sql = """
                SELECT
                  u.Id,
                  LTRIM(RTRIM(
                    COALESCE(u.name_first, '') +
                    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
                    COALESCE(u.name_last, '')
                  )) AS DisplayName,
                  u.Color
                FROM dbo.Users u
                WHERE u.ShaleClientId = ?
                  AND NULLIF(LTRIM(RTRIM(
                    COALESCE(u.name_first, '') +
                    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
                    COALESCE(u.name_last, '')
                  )), '') IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1
                    FROM dbo.TaskAssignments ta
                    WHERE ta.TaskId = ?
                      AND ta.ShaleClientId = ?
                      AND ta.UserId = u.Id
                  )
                ORDER BY
                  u.name_first ASC,
                  u.name_last ASC,
                  u.Id ASC;
                """;

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shaleClientId);
            ps.setLong(2, taskId);
            ps.setInt(3, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TaskAssignableUserRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new TaskAssignableUserRow(
                            rs.getInt("Id"),
                            rs.getString("DisplayName"),
                            rs.getString("Color")));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to list assignable users for taskId=" + taskId + " shaleClientId=" + shaleClientId,
                    e);
        }
    }

    public List<TaskAssignedTaskUserRow> listAssignedUsersForTasks(List<Long> taskIds, int shaleClientId) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        List<Long> validTaskIds = taskIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (validTaskIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(validTaskIds.size(), "?"));
        String sql = """
                SELECT
                  ta.TaskId,
                  u.Id AS UserId,
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
                WHERE ta.ShaleClientId = ?
                  AND ta.TaskId IN (%s)
                ORDER BY
                  ta.TaskId ASC,
                  ta.IsPrimary DESC,
                  u.name_first ASC,
                  u.name_last ASC,
                  u.Id ASC;
                """.formatted(placeholders);

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, shaleClientId);
            for (Long taskId : validTaskIds) {
                ps.setLong(i++, taskId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<TaskAssignedTaskUserRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new TaskAssignedTaskUserRow(
                            rs.getLong("TaskId"),
                            rs.getInt("UserId"),
                            rs.getString("DisplayName"),
                            rs.getString("Color")));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list assigned users for task collection", e);
        }
    }

    public boolean addTaskAssignment(long taskId, int shaleClientId, int userId, int assignedByUserId) {
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
                  DECLARE @inserted bit = 0;

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
                    THROW 50002, 'Assignable user not found for tenant.', 1;
                  END

                  IF NOT EXISTS (
                    SELECT 1
                    FROM dbo.TaskAssignments ta
                    WHERE ta.TaskId = ?
                      AND ta.ShaleClientId = ?
                      AND ta.UserId = ?
                  )
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
                    VALUES (?, ?, ?, ?, 0, ?, @now);
                    SET @inserted = 1;

                    UPDATE dbo.Tasks
                    SET UpdatedAt = @now
                    WHERE Id = ?
                      AND ShaleClientId = ?;
                  END

                  SELECT @inserted AS Inserted;
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
            ps.setInt(i++, userId);
            ps.setLong(i++, taskId);
            ps.setInt(i++, userId);
            ps.setInt(i++, shaleClientId);
            ps.setByte(i++, DEFAULT_PRIMARY_ASSIGNMENT_ROLE);
            ps.setInt(i++, assignedByUserId);
            ps.setLong(i++, taskId);
            ps.setInt(i++, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("Inserted");
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to add assignment for taskId=" + taskId + " userId=" + userId + " shaleClientId=" + shaleClientId,
                    e);
        }
    }

    public void removeTaskAssignment(long taskId, int shaleClientId, int userId) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be > 0");
        }

        String sql = """
                BEGIN TRY
                  BEGIN TRAN;

                  DECLARE @now datetime2 = SYSDATETIME();

                  DELETE FROM dbo.TaskAssignments
                  WHERE TaskId = ?
                    AND ShaleClientId = ?
                    AND UserId = ?;

                  IF @@ROWCOUNT > 0
                  BEGIN
                    UPDATE dbo.Tasks
                    SET UpdatedAt = @now
                    WHERE Id = ?
                      AND ShaleClientId = ?;
                  END

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
            ps.setLong(i++, taskId);
            ps.setInt(i++, shaleClientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to remove assignment for taskId=" + taskId + " userId=" + userId + " shaleClientId=" + shaleClientId,
                    e);
        }
    }

    public List<TaskDueNotificationCandidate> listDueNotificationCandidates(int shaleClientId) {
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        String sql = """
                SELECT
                  t.Id,
                  t.ShaleClientId,
                  t.CaseId,
                  c.Name AS CaseName,
                  t.Title,
                  t.DueAt,
                  t.CompletedAt,
                  ISNULL(t.IsDeleted, 0) AS IsDeleted,
                  assignment.UserId AS AssignedUserId
                FROM dbo.Tasks t
                INNER JOIN dbo.Cases c
                  ON c.Id = t.CaseId
                 AND c.ShaleClientId = t.ShaleClientId
                OUTER APPLY (
                  SELECT TOP (1) ta.UserId
                  FROM dbo.TaskAssignments ta
                  WHERE ta.TaskId = t.Id
                    AND ta.ShaleClientId = t.ShaleClientId
                    AND ta.IsPrimary = 1
                  ORDER BY ta.AssignedAt DESC, ta.UserId DESC
                ) assignment
                WHERE t.ShaleClientId = ?
                  AND t.DueAt IS NOT NULL
                  AND ISNULL(t.IsDeleted, 0) = 0
                  AND t.CompletedAt IS NULL
                ORDER BY t.DueAt ASC, t.Id ASC;
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TaskDueNotificationCandidate> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new TaskDueNotificationCandidate(
                            rs.getLong("Id"),
                            rs.getInt("ShaleClientId"),
                            rs.getLong("CaseId"),
                            rs.getString("CaseName"),
                            rs.getString("Title"),
                            toLocalDateTime(rs.getTimestamp("DueAt")),
                            toLocalDateTime(rs.getTimestamp("CompletedAt")),
                            rs.getBoolean("IsDeleted"),
                            (Integer) rs.getObject("AssignedUserId")));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load due notification candidates", e);
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
                long taskId = rs.getLong(1);
                phiAuditService.auditCreate(createdByUserId, "Tasks", "Title", taskId, normalizedTitle);
                phiAuditService.auditCreate(createdByUserId, "Tasks", "Description", taskId, description);
                return taskId;
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
            if (!tableExists(con, PRIORITIES_TABLE)) {
                return List.of();
            }
            List<PriorityLookupRow> effective = listEffectivePrioritiesForTenant(con, shaleClientId, true);
            List<TaskPriorityOptionDto> out = new ArrayList<>(effective.size());
            for (PriorityLookupRow row : effective) {
                if (row == null) {
                    continue;
                }
                String displayName = row.name() == null || row.name().isBlank()
                        ? "Priority " + row.id()
                        : row.name().trim();
                out.add(new TaskPriorityOptionDto(row.id(), displayName, row.sortOrder(), row.colorHex()));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list task priorities for shaleClientId=" + shaleClientId, e);
        }
    }

    public List<TaskStatusOptionDto> listActiveTaskStatuses(int shaleClientId) {
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        try (Connection con = db.requireConnection()) {
            if (!tableExists(con, TASK_STATUSES_TABLE)) {
                return List.of();
            }
            List<TaskStatusLookupRow> effective = listEffectiveTaskStatusesForTenant(con, shaleClientId, true);
            List<TaskStatusOptionDto> out = new ArrayList<>(effective.size());
            for (TaskStatusLookupRow row : effective) {
                if (row == null) {
                    continue;
                }
                String displayName = row.name() == null || row.name().isBlank()
                        ? "Status " + row.id()
                        : row.name().trim();
                out.add(new TaskStatusOptionDto(row.id(), displayName, row.sortOrder(), row.colorHex()));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list task statuses for shaleClientId=" + shaleClientId, e);
        }
    }

    public void updateTask(
            long taskId,
            int shaleClientId,
            String title,
            String description,
            LocalDateTime dueAt,
            Integer statusId,
            Integer priorityId,
            boolean completed,
            Integer updatedByUserId) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }

        String sql = """
                UPDATE dbo.Tasks
                SET Title = ?,
                    Description = ?,
                    DueAt = ?,
                    StatusId = ?,
                    PriorityId = ?,
                    CompletedAt = %s,
                    UpdatedAt = SYSDATETIME()
                WHERE Id = ?
                  AND ShaleClientId = ?
                  AND ISNULL(IsDeleted, 0) = 0;
                """.formatted(completed ? "SYSDATETIME()" : "NULL");

        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            TaskDetailDto before = findTaskDetail(taskId, shaleClientId);
            int resolvedStatusId = resolveStatusIdForUpdate(con, shaleClientId, statusId);
            int resolvedPriorityId = resolvePriorityIdForCreate(con, shaleClientId, priorityId);
            int i = 1;
            ps.setString(i++, normalizedTitle);
            setNullableString(ps, i++, description);
            setNullableTimestamp(ps, i++, dueAt);
            ps.setInt(i++, resolvedStatusId);
            ps.setInt(i++, resolvedPriorityId);
            ps.setLong(i++, taskId);
            ps.setInt(i++, shaleClientId);
            ps.executeUpdate();
            TaskDetailDto after = findTaskDetail(taskId, shaleClientId);
            if (before != null && after != null) {
                phiAuditService.auditUpdate(updatedByUserId, "Tasks", "Title", taskId, before.title(), after.title());
                phiAuditService.auditUpdate(updatedByUserId, "Tasks", "Description", taskId, before.description(), after.description());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update taskId=" + taskId, e);
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

    public long addTaskTimelineEvent(
            long taskId,
            int caseId,
            int shaleClientId,
            String eventType,
            Integer actorUserId,
            String title,
            String body) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be > 0");
        }
        if (caseId <= 0) {
            throw new IllegalArgumentException("caseId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }

        String normalizedEventType = eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
        if (!TaskTimelineEventTypes.ALLOWED.contains(normalizedEventType)) {
            throw new IllegalArgumentException("Unsupported task timeline eventType: " + eventType);
        }
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isBlank()) {
            throw new IllegalArgumentException("Task timeline event title is required");
        }
        String normalizedBody = body == null ? null : body.trim();

        String sql = """
                INSERT INTO dbo.TaskTimelineEvents (
                  TaskId,
                  CaseId,
                  ShaleClientId,
                  EventType,
                  ActorUserId,
                  Title,
                  Body
                )
                OUTPUT INSERTED.Id
                VALUES (?, ?, ?, ?, ?, ?, ?);
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setLong(i++, taskId);
            ps.setInt(i++, caseId);
            ps.setInt(i++, shaleClientId);
            ps.setString(i++, normalizedEventType);
            if (actorUserId == null) {
                ps.setNull(i++, java.sql.Types.INTEGER);
            } else {
                ps.setInt(i++, actorUserId);
            }
            ps.setString(i++, normalizedTitle);
            setNullableString(ps, i, normalizedBody);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("Task timeline insert did not return inserted id");
                }
                long timelineEventId = rs.getLong(1);
                phiAuditService.auditCreate(actorUserId, "TaskTimelineEvents", "Body", timelineEventId, normalizedBody);
                return timelineEventId;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add task timeline event (taskId=" + taskId + ")", e);
        }
    }

    public List<TaskTimelineEventRow> listTaskTimelineEvents(long taskId) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be > 0");
        }
        String sql = """
                SELECT
                  tte.Id,
                  tte.TaskId,
                  tte.CaseId,
                  tte.ShaleClientId,
                  t.Title AS TaskTitle,
                  tte.EventType,
                  tte.ActorUserId,
                  LTRIM(RTRIM(
                    COALESCE(u.name_first, '') +
                    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
                    COALESCE(u.name_last, '')
                  )) AS ActorDisplayName,
                  tte.Title,
                  tte.Body,
                  tte.OccurredAt,
                  ISNULL(tte.IsDeleted, 0) AS IsDeleted
                FROM dbo.TaskTimelineEvents tte
                INNER JOIN dbo.Tasks t
                  ON t.Id = tte.TaskId
                 AND t.ShaleClientId = tte.ShaleClientId
                LEFT JOIN dbo.Users u
                  ON u.Id = tte.ActorUserId
                 AND u.ShaleClientId = tte.ShaleClientId
                WHERE tte.TaskId = ?
                  AND ISNULL(tte.IsDeleted, 0) = 0
                ORDER BY tte.OccurredAt DESC, tte.Id DESC;
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TaskTimelineEventRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapTaskTimelineEventRow(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list task timeline events for taskId=" + taskId, e);
        }
    }

    public List<TaskTimelineEventRow> listCaseTaskTimelineEvents(int caseId) {
        if (caseId <= 0) {
            throw new IllegalArgumentException("caseId must be > 0");
        }
        String sql = """
                SELECT
                  tte.Id,
                  tte.TaskId,
                  tte.CaseId,
                  tte.ShaleClientId,
                  t.Title AS TaskTitle,
                  tte.EventType,
                  tte.ActorUserId,
                  LTRIM(RTRIM(
                    COALESCE(u.name_first, '') +
                    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
                    COALESCE(u.name_last, '')
                  )) AS ActorDisplayName,
                  tte.Title,
                  tte.Body,
                  tte.OccurredAt,
                  ISNULL(tte.IsDeleted, 0) AS IsDeleted
                FROM dbo.TaskTimelineEvents tte
                INNER JOIN dbo.Tasks t
                  ON t.Id = tte.TaskId
                 AND t.ShaleClientId = tte.ShaleClientId
                LEFT JOIN dbo.Users u
                  ON u.Id = tte.ActorUserId
                 AND u.ShaleClientId = tte.ShaleClientId
                WHERE tte.CaseId = ?
                  AND ISNULL(tte.IsDeleted, 0) = 0
                ORDER BY tte.OccurredAt DESC, tte.Id DESC;
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TaskTimelineEventRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapTaskTimelineEventRow(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list case task timeline events for caseId=" + caseId, e);
        }
    }

    public long addTaskUpdate(long taskId, int caseId, int shaleClientId, int userId, String body) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be > 0");
        }
        if (caseId <= 0) {
            throw new IllegalArgumentException("caseId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be > 0");
        }
        String trimmedBody = body == null ? "" : body.trim();
        if (trimmedBody.isBlank()) {
            throw new IllegalArgumentException("Task update body is required");
        }

        String sql = """
                INSERT INTO dbo.TaskUpdates (
                  TaskId,
                  CaseId,
                  ShaleClientId,
                  UserId,
                  Body,
                  CreatedAt,
                  UpdatedAt,
                  IsDeleted
                )
                OUTPUT INSERTED.Id
                SELECT ?, ?, ?, ?, ?, SYSUTCDATETIME(), NULL, 0
                WHERE EXISTS (
                  SELECT 1
                  FROM dbo.Tasks t
                  WHERE t.Id = ?
                    AND t.CaseId = ?
                    AND t.ShaleClientId = ?
                    AND ISNULL(t.IsDeleted, 0) = 0
                );
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setLong(i++, taskId);
            ps.setInt(i++, caseId);
            ps.setInt(i++, shaleClientId);
            ps.setInt(i++, userId);
            ps.setString(i++, trimmedBody);
            ps.setLong(i++, taskId);
            ps.setInt(i++, caseId);
            ps.setInt(i++, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("Task update insert did not return inserted id");
                }
                long taskUpdateId = rs.getLong(1);
                phiAuditService.auditCreate(userId, "TaskUpdates", "Body", taskUpdateId, trimmedBody);
                return taskUpdateId;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add task update (taskId=" + taskId + ", caseId=" + caseId + ")", e);
        }
    }

    public boolean updateTaskUpdate(long taskUpdateId, int shaleClientId, int userId, String body) {
        if (taskUpdateId <= 0) {
            throw new IllegalArgumentException("taskUpdateId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be > 0");
        }
        String trimmedBody = body == null ? "" : body.trim();
        if (trimmedBody.isBlank()) {
            throw new IllegalArgumentException("Task update body is required");
        }

        String existingSql = """
                SELECT Body
                FROM dbo.TaskUpdates
                WHERE Id = ?
                  AND ShaleClientId = ?
                  AND UserId = ?
                  AND ISNULL(IsDeleted, 0) = 0;
                """;
        String sql = """
                UPDATE dbo.TaskUpdates
                SET Body = ?,
                    UpdatedAt = SYSUTCDATETIME()
                WHERE Id = ?
                  AND ShaleClientId = ?
                  AND UserId = ?
                  AND ISNULL(IsDeleted, 0) = 0;
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement existingPs = con.prepareStatement(existingSql);
             PreparedStatement ps = con.prepareStatement(sql)) {
            existingPs.setLong(1, taskUpdateId);
            existingPs.setInt(2, shaleClientId);
            existingPs.setInt(3, userId);
            String oldBody = null;
            try (ResultSet rs = existingPs.executeQuery()) {
                if (rs.next()) {
                    oldBody = rs.getString("Body");
                }
            }
            ps.setString(1, trimmedBody);
            ps.setLong(2, taskUpdateId);
            ps.setInt(3, shaleClientId);
            ps.setInt(4, userId);
            boolean updated = ps.executeUpdate() == 1;
            if (updated) {
                phiAuditService.auditUpdate(userId, "TaskUpdates", "Body", taskUpdateId, oldBody, trimmedBody);
            }
            return updated;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update task update (id=" + taskUpdateId + ")", e);
        }
    }

    public boolean softDeleteTaskUpdate(long taskUpdateId, int shaleClientId, int userId) {
        if (taskUpdateId <= 0) {
            throw new IllegalArgumentException("taskUpdateId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be > 0");
        }
        String existingSql = """
                SELECT Body
                FROM dbo.TaskUpdates
                WHERE Id = ?
                  AND ShaleClientId = ?
                  AND UserId = ?
                  AND ISNULL(IsDeleted, 0) = 0;
                """;
        String sql = """
                UPDATE dbo.TaskUpdates
                SET IsDeleted = 1,
                    UpdatedAt = SYSUTCDATETIME()
                WHERE Id = ?
                  AND ShaleClientId = ?
                  AND UserId = ?
                  AND ISNULL(IsDeleted, 0) = 0;
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement existingPs = con.prepareStatement(existingSql);
             PreparedStatement ps = con.prepareStatement(sql)) {
            existingPs.setLong(1, taskUpdateId);
            existingPs.setInt(2, shaleClientId);
            existingPs.setInt(3, userId);
            String oldBody = null;
            try (ResultSet rs = existingPs.executeQuery()) {
                if (rs.next()) {
                    oldBody = rs.getString("Body");
                }
            }
            ps.setLong(1, taskUpdateId);
            ps.setInt(2, shaleClientId);
            ps.setInt(3, userId);
            boolean deleted = ps.executeUpdate() == 1;
            if (deleted) {
                phiAuditService.auditDelete(userId, "TaskUpdates", "Body", taskUpdateId, oldBody);
            }
            return deleted;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to soft delete task update (id=" + taskUpdateId + ")", e);
        }
    }

    public List<TaskUpdateRow> listTaskUpdates(long taskId) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be > 0");
        }
        String sql = """
                SELECT
                  tu.Id,
                  tu.TaskId,
                  tu.CaseId,
                  tu.ShaleClientId,
                  tu.UserId,
                  LTRIM(RTRIM(
                    COALESCE(u.name_first, '') +
                    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
                    COALESCE(u.name_last, '')
                  )) AS UserDisplayName,
                  u.Color AS UserColor,
                  tu.Body,
                  tu.CreatedAt,
                  tu.UpdatedAt,
                  ISNULL(tu.IsDeleted, 0) AS IsDeleted
                FROM dbo.TaskUpdates tu
                INNER JOIN dbo.Tasks t
                  ON t.Id = tu.TaskId
                 AND t.ShaleClientId = tu.ShaleClientId
                LEFT JOIN dbo.Users u
                  ON u.Id = tu.UserId
                 AND u.ShaleClientId = tu.ShaleClientId
                WHERE tu.TaskId = ?
                  AND ISNULL(tu.IsDeleted, 0) = 0
                ORDER BY tu.CreatedAt DESC, tu.Id DESC;
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TaskUpdateRow> out = new ArrayList<>();
                while (rs.next()) {
                    int userId = rs.getInt("UserId");
                    out.add(new TaskUpdateRow(
                            rs.getLong("Id"),
                            rs.getLong("TaskId"),
                            rs.getInt("CaseId"),
                            rs.getInt("ShaleClientId"),
                            userId,
                            safeTaskUpdateUserDisplayName(rs.getString("UserDisplayName"), userId),
                            rs.getString("UserColor"),
                            rs.getString("Body"),
                            toLocalDateTime(rs.getTimestamp("CreatedAt")),
                            toLocalDateTime(rs.getTimestamp("UpdatedAt")),
                            rs.getBoolean("IsDeleted")
                    ));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list task updates for taskId=" + taskId, e);
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

    private static String safeTaskUpdateUserDisplayName(String displayName, int userId) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (!normalized.isBlank()) {
            return normalized;
        }
        return userId > 0 ? "User #" + userId : "Unknown user";
    }

    private static TaskTimelineEventRow mapTaskTimelineEventRow(ResultSet rs) throws SQLException {
        return new TaskTimelineEventRow(
                rs.getLong("Id"),
                rs.getLong("TaskId"),
                rs.getInt("CaseId"),
                rs.getInt("ShaleClientId"),
                rs.getString("TaskTitle"),
                rs.getString("EventType"),
                (Integer) rs.getObject("ActorUserId"),
                rs.getString("ActorDisplayName"),
                rs.getString("Title"),
                rs.getString("Body"),
                toLocalDateTime(rs.getTimestamp("OccurredAt")),
                rs.getBoolean("IsDeleted")
        );
    }

    private static void setNullableTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.TIMESTAMP);
            return;
        }
        ps.setObject(index, value);
    }

    private static String normalizeSystemKey(String systemKey) {
        String normalized = systemKey == null ? "" : systemKey.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static String resolveLegacyPrioritySystemKeyFromName(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "normal", "medium", "default", "standard" -> PRIORITY_SYSTEM_KEY_NORMAL;
            default -> null;
        };
    }

    private static String resolvePrioritySystemKey(String systemKey, String name) {
        String normalizedSystem = normalizeSystemKey(systemKey);
        if (normalizedSystem != null) {
            return normalizedSystem;
        }
        return resolveLegacyPrioritySystemKeyFromName(name);
    }

    private static PriorityLookupRow mapPriorityLookupRow(ResultSet rs, boolean hasName, boolean hasSortOrder) throws SQLException {
        return new PriorityLookupRow(
                rs.getInt("Id"),
                hasName ? rs.getString("Name") : null,
                hasSortOrder ? (Integer) rs.getObject("SortOrder") : null,
                resolvePrioritySystemKey(rs.getString("SystemKey"), hasName ? rs.getString("Name") : null),
                rs.getString("ColorHex")
        );
    }

    private static List<PriorityLookupRow> resolveEffectivePriorities(List<PriorityLookupRow> globalPriorities, List<PriorityLookupRow> tenantPriorities) {
        List<PriorityLookupRow> globalUnkeyed = new ArrayList<>();
        List<PriorityLookupRow> tenantUnkeyed = new ArrayList<>();
        java.util.Map<String, PriorityLookupRow> bySystemKey = new java.util.LinkedHashMap<>();

        if (globalPriorities != null) {
            for (PriorityLookupRow priority : globalPriorities) {
                if (priority == null) {
                    continue;
                }
                String systemKey = resolvePrioritySystemKey(priority.systemKey(), priority.name());
                if (systemKey == null) {
                    globalUnkeyed.add(priority);
                    continue;
                }
                bySystemKey.putIfAbsent(systemKey, priority);
            }
        }

        if (tenantPriorities != null) {
            for (PriorityLookupRow priority : tenantPriorities) {
                if (priority == null) {
                    continue;
                }
                String systemKey = resolvePrioritySystemKey(priority.systemKey(), priority.name());
                if (systemKey == null) {
                    tenantUnkeyed.add(priority);
                    continue;
                }
                bySystemKey.put(systemKey, priority);
            }
        }

        List<PriorityLookupRow> merged = new ArrayList<>(globalUnkeyed.size() + bySystemKey.size() + tenantUnkeyed.size());
        merged.addAll(globalUnkeyed);
        merged.addAll(bySystemKey.values());
        merged.addAll(tenantUnkeyed);
        merged.sort((a, b) -> {
            if (a == b) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            int aSort = a.sortOrder() == null ? Integer.MAX_VALUE : a.sortOrder();
            int bSort = b.sortOrder() == null ? Integer.MAX_VALUE : b.sortOrder();
            int bySort = Integer.compare(aSort, bSort);
            if (bySort != 0) {
                return bySort;
            }
            return Integer.compare(a.id(), b.id());
        });
        return merged;
    }

    private static List<PriorityLookupRow> listEffectivePrioritiesForTenant(Connection con, int shaleClientId, boolean activeOnly) throws SQLException {
        if (!tableExists(con, PRIORITIES_TABLE)) {
            return List.of();
        }
        boolean hasName = hasColumn(con, PRIORITIES_TABLE, "Name");
        boolean hasSortOrder = hasColumn(con, PRIORITIES_TABLE, "SortOrder");
        boolean hasIsActive = hasColumn(con, PRIORITIES_TABLE, "IsActive");
        boolean hasSystemKey = hasColumn(con, PRIORITIES_TABLE, "SystemKey");
        boolean hasColorHex = hasColumn(con, PRIORITIES_TABLE, "ColorHex");

        String nameSelect = hasName ? "p.Name" : "NULL AS Name";
        String sortOrderSelect = hasSortOrder ? "p.SortOrder" : "NULL AS SortOrder";
        String systemKeySelect = hasSystemKey ? "p.SystemKey" : "NULL AS SystemKey";
        String colorHexSelect = hasColorHex ? "p.ColorHex" : "NULL AS ColorHex";
        String activeFilter = (activeOnly && hasIsActive) ? "\n  AND ISNULL(p.IsActive, 1) = 1" : "";

        String tenantSql = """
                SELECT p.Id, %s, %s, %s, %s
                FROM dbo.Priorities p
                WHERE p.ShaleClientId = ?%s
                ORDER BY %s, p.Id;
                """.formatted(
                nameSelect,
                sortOrderSelect,
                systemKeySelect,
                colorHexSelect,
                activeFilter,
                hasSortOrder ? "ISNULL(p.SortOrder, 2147483647)" : "p.Id");
        try (PreparedStatement tenantPs = con.prepareStatement(tenantSql)) {
            tenantPs.setInt(1, shaleClientId);
            try (ResultSet tenantRs = tenantPs.executeQuery()) {
                List<PriorityLookupRow> tenantPriorities = new ArrayList<>();
                while (tenantRs.next()) {
                    tenantPriorities.add(mapPriorityLookupRow(tenantRs, hasName, hasSortOrder));
                }

                String globalSql = """
                        SELECT p.Id, %s, %s, %s, %s
                        FROM dbo.Priorities p
                        WHERE p.ShaleClientId IS NULL%s
                        ORDER BY %s, p.Id;
                        """.formatted(
                        nameSelect,
                        sortOrderSelect,
                        systemKeySelect,
                        colorHexSelect,
                        activeFilter,
                        hasSortOrder ? "ISNULL(p.SortOrder, 2147483647)" : "p.Id");
                try (PreparedStatement globalPs = con.prepareStatement(globalSql);
                     ResultSet globalRs = globalPs.executeQuery()) {
                    List<PriorityLookupRow> globalPriorities = new ArrayList<>();
                    while (globalRs.next()) {
                        globalPriorities.add(mapPriorityLookupRow(globalRs, hasName, hasSortOrder));
                    }
                    return resolveEffectivePriorities(globalPriorities, tenantPriorities);
                }
            }
        }
    }

    private static TaskStatusLookupRow mapTaskStatusLookupRow(ResultSet rs, boolean hasName, boolean hasSortOrder) throws SQLException {
        return new TaskStatusLookupRow(
                rs.getInt("Id"),
                hasName ? rs.getString("Name") : null,
                hasSortOrder ? (Integer) rs.getObject("SortOrder") : null,
                normalizeSystemKey(rs.getString("SystemKey")),
                rs.getString("ColorHex")
        );
    }

    private static List<TaskStatusLookupRow> resolveEffectiveTaskStatuses(List<TaskStatusLookupRow> globalStatuses, List<TaskStatusLookupRow> tenantStatuses) {
        List<TaskStatusLookupRow> globalUnkeyed = new ArrayList<>();
        List<TaskStatusLookupRow> tenantUnkeyed = new ArrayList<>();
        java.util.Map<String, TaskStatusLookupRow> bySystemKey = new java.util.LinkedHashMap<>();

        if (globalStatuses != null) {
            for (TaskStatusLookupRow status : globalStatuses) {
                if (status == null) {
                    continue;
                }
                String systemKey = normalizeSystemKey(status.systemKey());
                if (systemKey == null) {
                    globalUnkeyed.add(status);
                    continue;
                }
                bySystemKey.putIfAbsent(systemKey, status);
            }
        }

        if (tenantStatuses != null) {
            for (TaskStatusLookupRow status : tenantStatuses) {
                if (status == null) {
                    continue;
                }
                String systemKey = normalizeSystemKey(status.systemKey());
                if (systemKey == null) {
                    tenantUnkeyed.add(status);
                    continue;
                }
                bySystemKey.put(systemKey, status);
            }
        }

        List<TaskStatusLookupRow> merged = new ArrayList<>(globalUnkeyed.size() + bySystemKey.size() + tenantUnkeyed.size());
        merged.addAll(globalUnkeyed);
        merged.addAll(bySystemKey.values());
        merged.addAll(tenantUnkeyed);
        merged.sort((a, b) -> {
            if (a == b) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            int aSort = a.sortOrder() == null ? Integer.MAX_VALUE : a.sortOrder();
            int bSort = b.sortOrder() == null ? Integer.MAX_VALUE : b.sortOrder();
            int bySort = Integer.compare(aSort, bSort);
            if (bySort != 0) {
                return bySort;
            }
            return Integer.compare(a.id(), b.id());
        });
        return merged;
    }

    private static List<TaskStatusLookupRow> listEffectiveTaskStatusesForTenant(Connection con, int shaleClientId, boolean activeOnly) throws SQLException {
        if (!tableExists(con, TASK_STATUSES_TABLE)) {
            return List.of();
        }
        boolean hasName = hasColumn(con, TASK_STATUSES_TABLE, "Name");
        boolean hasSortOrder = hasColumn(con, TASK_STATUSES_TABLE, "SortOrder");
        boolean hasIsActive = hasColumn(con, TASK_STATUSES_TABLE, "IsActive");
        boolean hasSystemKey = hasColumn(con, TASK_STATUSES_TABLE, "SystemKey");
        boolean hasColorHex = hasColumn(con, TASK_STATUSES_TABLE, "ColorHex");

        String nameSelect = hasName ? "s.Name" : "NULL AS Name";
        String sortOrderSelect = hasSortOrder ? "s.SortOrder" : "NULL AS SortOrder";
        String systemKeySelect = hasSystemKey ? "s.SystemKey" : "NULL AS SystemKey";
        String colorHexSelect = hasColorHex ? "s.ColorHex" : "NULL AS ColorHex";
        String activeFilter = (activeOnly && hasIsActive) ? "\n  AND ISNULL(s.IsActive, 1) = 1" : "";
        String orderExpr = hasSortOrder ? "ISNULL(s.SortOrder, 2147483647)" : "s.Id";

        String tenantSql = """
                SELECT s.Id, %s, %s, %s, %s
                FROM dbo.TaskStatuses s
                WHERE s.ShaleClientId = ?%s
                ORDER BY %s, s.Id;
                """.formatted(nameSelect, sortOrderSelect, systemKeySelect, colorHexSelect, activeFilter, orderExpr);
        try (PreparedStatement tenantPs = con.prepareStatement(tenantSql)) {
            tenantPs.setInt(1, shaleClientId);
            try (ResultSet tenantRs = tenantPs.executeQuery()) {
                List<TaskStatusLookupRow> tenantStatuses = new ArrayList<>();
                while (tenantRs.next()) {
                    tenantStatuses.add(mapTaskStatusLookupRow(tenantRs, hasName, hasSortOrder));
                }

                String globalSql = """
                        SELECT s.Id, %s, %s, %s, %s
                        FROM dbo.TaskStatuses s
                        WHERE s.ShaleClientId IS NULL%s
                        ORDER BY %s, s.Id;
                        """.formatted(nameSelect, sortOrderSelect, systemKeySelect, colorHexSelect, activeFilter, orderExpr);
                try (PreparedStatement globalPs = con.prepareStatement(globalSql);
                     ResultSet globalRs = globalPs.executeQuery()) {
                    List<TaskStatusLookupRow> globalStatuses = new ArrayList<>();
                    while (globalRs.next()) {
                        globalStatuses.add(mapTaskStatusLookupRow(globalRs, hasName, hasSortOrder));
                    }
                    return resolveEffectiveTaskStatuses(globalStatuses, tenantStatuses);
                }
            }
        }
    }

    private static int resolveDefaultTaskStatusId(Connection con, int shaleClientId) throws SQLException {
        List<TaskStatusLookupRow> effective = listEffectiveTaskStatusesForTenant(con, shaleClientId, true);
        for (TaskStatusLookupRow row : effective) {
            if (row == null) {
                continue;
            }
            if (TASK_STATUS_SYSTEM_KEY_OPEN.equals(normalizeSystemKey(row.systemKey()))) {
                return row.id();
            }
        }
        if (!effective.isEmpty()) {
            return effective.get(0).id();
        }
        throw new IllegalStateException("No default open task status found for shaleClientId=" + shaleClientId);
    }

    private static int resolveStatusIdForUpdate(Connection con, int shaleClientId, Integer requestedStatusId) throws SQLException {
        if (requestedStatusId != null && requestedStatusId > 0
                && isTaskStatusSelectable(con, shaleClientId, requestedStatusId)) {
            return requestedStatusId;
        }
        return resolveDefaultTaskStatusId(con, shaleClientId);
    }

    private static int resolveDefaultTaskPriorityId(Connection con, int shaleClientId) throws SQLException {
        List<PriorityLookupRow> effective = listEffectivePrioritiesForTenant(con, shaleClientId, true);
        for (PriorityLookupRow row : effective) {
            if (row == null) {
                continue;
            }
            if (PRIORITY_SYSTEM_KEY_NORMAL.equals(normalizeSystemKey(row.systemKey()))) {
                return row.id();
            }
        }
        if (!effective.isEmpty()) {
            return effective.get(0).id();
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
        if (!tableExists(con, PRIORITIES_TABLE)) {
            return false;
        }
        boolean hasIsActive = hasColumn(con, PRIORITIES_TABLE, "IsActive");
        StringBuilder sql = new StringBuilder("""
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM dbo.Priorities p
                    WHERE p.Id = ?
                      AND (p.ShaleClientId = ? OR p.ShaleClientId IS NULL)
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

    private static boolean isTaskStatusSelectable(Connection con, int shaleClientId, int statusId) throws SQLException {
        if (!tableExists(con, TASK_STATUSES_TABLE)) {
            return false;
        }
        boolean hasIsActive = hasColumn(con, TASK_STATUSES_TABLE, "IsActive");
        StringBuilder sql = new StringBuilder("""
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM dbo.TaskStatuses s
                    WHERE s.Id = ?
                      AND (s.ShaleClientId = ? OR s.ShaleClientId IS NULL)
                """);
        if (hasIsActive) {
            sql.append("\n  AND ISNULL(s.IsActive, 1) = 1");
        }
        sql.append("\n) THEN 1 ELSE 0 END;");

        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            ps.setInt(1, statusId);
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

    public record TaskDueNotificationCandidate(
            long taskId,
            int shaleClientId,
            long caseId,
            String caseName,
            String title,
            LocalDateTime dueAt,
            LocalDateTime completedAt,
            boolean deleted,
            Integer assignedUserId) {
    }
}
