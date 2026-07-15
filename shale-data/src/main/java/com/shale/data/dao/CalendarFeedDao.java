package com.shale.data.dao;

import com.shale.core.model.CalendarFeedItem;
import com.shale.core.runtime.DbSessionProvider;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CalendarFeedDao {
    private final DbSessionProvider db;

    public CalendarFeedDao(DbSessionProvider db) {
        this.db = Objects.requireNonNull(db, "db");
    }


    public record CalendarCaseCardRow(int caseId, String caseName, String responsibleAttorney, String responsibleAttorneyColor, Boolean nonEngagementLetterSent) {}
    public record CalendarTaskCardRow(long taskId, Integer caseId, String caseName, String caseResponsibleAttorney, String caseResponsibleAttorneyColor, Boolean caseNonEngagementLetterSent, String title, String description, LocalDateTime dueAt, LocalDateTime completedAt, String createdByDisplayName, String priorityColorHex) {}

    public List<CalendarCaseCardRow> listCaseCardRows(int shaleClientId, List<Integer> caseIds) {
        if (shaleClientId <= 0 || caseIds == null || caseIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(caseIds.size(), "?"));
        String sql = """
                SELECT c.Id,
                       c.Name,
                       LTRIM(RTRIM(
                         COALESCE(ra.name_first, '') +
                         CASE WHEN COALESCE(ra.name_first, '') = '' OR COALESCE(ra.name_last, '') = '' THEN '' ELSE ' ' END +
                         COALESCE(ra.name_last, '')
                       )) AS ResponsibleAttorney,
                       ra.color AS ResponsibleAttorneyColor,
                       c.NonEngagementLetterSent
                FROM dbo.Cases c
                LEFT JOIN dbo.CaseUsers cu ON cu.CaseId = c.Id AND cu.RoleId = 1
                LEFT JOIN dbo.Users ra ON ra.Id = cu.UserId
                WHERE c.ShaleClientId = ? AND c.Id IN (""" + placeholders + ") AND ISNULL(c.IsDeleted,0)=0";
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, shaleClientId);
            for (Integer id : caseIds) ps.setInt(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<CalendarCaseCardRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(new CalendarCaseCardRow(rs.getInt("Id"), rs.getString("Name"), rs.getString("ResponsibleAttorney"), rs.getString("ResponsibleAttorneyColor"), (Boolean) rs.getObject("NonEngagementLetterSent")));
                return rows;
            }
        } catch (SQLException e) { throw new RuntimeException("Failed to load calendar case card rows", e); }
    }

    public List<CalendarTaskCardRow> listTaskCardRows(int shaleClientId, List<Integer> taskIds) {
        if (shaleClientId <= 0 || taskIds == null || taskIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(taskIds.size(), "?"));
        String sql = """
                SELECT t.Id,
                       t.CaseId,
                       c.Name AS CaseName,
                       LTRIM(RTRIM(
                         COALESCE(ra.name_first, '') +
                         CASE WHEN COALESCE(ra.name_first, '') = '' OR COALESCE(ra.name_last, '') = '' THEN '' ELSE ' ' END +
                         COALESCE(ra.name_last, '')
                       )) AS CaseResponsibleAttorney,
                       ra.color AS CaseResponsibleAttorneyColor,
                       c.NonEngagementLetterSent AS CaseNonEngagementLetterSent, t.Title, t.Description, t.DueAt, t.CompletedAt, p.ColorHex AS PriorityColorHex,
                       LTRIM(RTRIM(COALESCE(u.name_first,'') + CASE WHEN COALESCE(u.name_first,'')='' OR COALESCE(u.name_last,'')='' THEN '' ELSE ' ' END + COALESCE(u.name_last,''))) AS CreatedByDisplayName
                FROM dbo.Tasks t
                LEFT JOIN dbo.Cases c ON c.Id = t.CaseId
                LEFT JOIN dbo.CaseUsers cu ON cu.CaseId = c.Id AND cu.RoleId = 1
                LEFT JOIN dbo.Users ra ON ra.Id = cu.UserId
                LEFT JOIN dbo.Users u ON u.Id = t.CreatedByUserId
                LEFT JOIN dbo.Priorities p ON p.Id = t.PriorityId
                WHERE t.ShaleClientId = ? AND t.Id IN (""" + placeholders + ") AND ISNULL(t.IsDeleted,0)=0";
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, shaleClientId);
            for (Integer id : taskIds) ps.setInt(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<CalendarTaskCardRow> rows = new ArrayList<>();
                while (rs.next()) rows.add(new CalendarTaskCardRow(rs.getLong("Id"), (Integer) rs.getObject("CaseId"), rs.getString("CaseName"), rs.getString("CaseResponsibleAttorney"), rs.getString("CaseResponsibleAttorneyColor"), (Boolean) rs.getObject("CaseNonEngagementLetterSent"), rs.getString("Title"), rs.getString("Description"), rs.getTimestamp("DueAt") == null ? null : rs.getTimestamp("DueAt").toLocalDateTime(), rs.getTimestamp("CompletedAt") == null ? null : rs.getTimestamp("CompletedAt").toLocalDateTime(), rs.getString("CreatedByDisplayName"), rs.getString("PriorityColorHex")));
                return rows;
            }
        } catch (SQLException e) { throw new RuntimeException("Failed to load calendar task card rows", e); }
    }
    record CaseDateProjection(String keyPrefix, String columnName, String titlePrefix, String systemKey, String displayTypeName) {
        boolean deadline() {
            return "STATUTE_OF_LIMITATIONS".equals(systemKey)
                    || "TORT_NOTICE_DEADLINE".equals(systemKey)
                    || "DISCOVERY_DEADLINE".equals(systemKey);
        }
    }

    static final List<CaseDateProjection> CASE_DATE_PROJECTIONS = List.of(
            new CaseDateProjection("CASE_SOL", "StatuteOfLimitations", "SOL", "STATUTE_OF_LIMITATIONS", "Statute of Limitations"),
            new CaseDateProjection("CASE_TORT", "TortNoticeDeadline", "Tort Notice", "TORT_NOTICE_DEADLINE", "Tort Notice Deadline"),
            new CaseDateProjection("CASE_DISC", "DiscoveryDeadline", "Discovery Deadline", "DISCOVERY_DEADLINE", "Discovery Deadline"),
            new CaseDateProjection("CASE_CALLER", "CallerDate", "Intake", "CASE_DATE", "Case Date"),
            new CaseDateProjection("CASE_ACCEPTED", "AcceptedDate", "Accepted", "CASE_DATE", "Case Date"),
            new CaseDateProjection("CASE_DENIED", "DeniedDate", "Denied", "CASE_DATE", "Case Date"),
            new CaseDateProjection("CASE_CLOSED", "ClosedDate", "Closed", "CASE_DATE", "Case Date"),
            new CaseDateProjection("CASE_INJURY", "DateOfInjury", "Date of Injury", "CASE_DATE", "Case Date"),
            new CaseDateProjection("CASE_FEE_AGREEMENT", "DateFeeAgreementSigned", "Fee Agreement Signed", "CASE_DATE", "Case Date"),
            new CaseDateProjection("CASE_NON_ENGAGEMENT", "DateNonEngagementLetterSent", "Non-Engagement Letter Sent", "CASE_DATE", "Case Date"),
            new CaseDateProjection("CASE_MED_NEG", "DateOfMedicalNegligence", "Medical Negligence", "CASE_DATE", "Case Date"),
            new CaseDateProjection("CASE_MED_NEG_DISCOVERED", "DateMedicalNegligenceWasDiscovered", "Medical Negligence Discovered", "CASE_DATE", "Case Date"));

    public List<CalendarFeedItem> listCalendarFeed(int shaleClientId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return listCalendarFeed(shaleClientId, startInclusive, endExclusive, null, null);
    }

    public List<CalendarFeedItem> listCalendarFeedForUserSchedule(int shaleClientId, LocalDateTime startInclusive, LocalDateTime endExclusive, int viewedUserId) {
        if (viewedUserId <= 0) return List.of();
        return listCalendarFeed(shaleClientId, startInclusive, endExclusive, null, viewedUserId);
    }

    public List<CalendarFeedItem> listCalendarFeedForCase(int shaleClientId, LocalDateTime startInclusive, LocalDateTime endExclusive, int caseId) {
        if (caseId <= 0) return List.of();
        return listCalendarFeed(shaleClientId, startInclusive, endExclusive, caseId, null);
    }

    private List<CalendarFeedItem> listCalendarFeed(int shaleClientId, LocalDateTime startInclusive, LocalDateTime endExclusive, Integer caseId, Integer userScheduleUserId) {
        if (shaleClientId <= 0 || startInclusive == null || endExclusive == null) {
            return List.of();
        }
        String sql = buildCalendarFeedSql(caseId != null, userScheduleUserId != null);
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, shaleClientId); ps.setTimestamp(i++, Timestamp.valueOf(startInclusive)); ps.setTimestamp(i++, Timestamp.valueOf(endExclusive)); if (userScheduleUserId != null) ps.setInt(i++, userScheduleUserId); if (caseId != null) ps.setInt(i++, caseId);
            ps.setInt(i++, shaleClientId); ps.setTimestamp(i++, Timestamp.valueOf(startInclusive)); ps.setTimestamp(i++, Timestamp.valueOf(endExclusive)); if (caseId != null) ps.setInt(i++, caseId);
            LocalDate startDate = startInclusive.toLocalDate();
            LocalDate endDate = endExclusive.toLocalDate();
            for (int branch = 0; branch < CASE_DATE_PROJECTIONS.size(); branch++) {
                ps.setInt(i++, shaleClientId); ps.setDate(i++, Date.valueOf(startDate)); ps.setDate(i++, Date.valueOf(endDate)); if (caseId != null) ps.setInt(i++, caseId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<CalendarFeedItem> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new CalendarFeedItem(
                            rs.getString("KeyValue"),
                            rs.getString("Title"),
                            rs.getTimestamp("StartsAt").toLocalDateTime(),
                            rs.getTimestamp("EndsAt") == null ? null : rs.getTimestamp("EndsAt").toLocalDateTime(),
                            rs.getBoolean("AllDay"),
                            rs.getString("SourceType"),
                            rs.getString("SourceField"),
                            (Integer) rs.getObject("CaseId"),
                            rs.getString("CaseName"),
                            (Integer) rs.getObject("TaskId"),
                            rs.getString("RelatedDisplayName"),
                            rs.getString("CalendarEventTypeSystemKey"),
                            rs.getString("DisplayTypeName"),
                            rs.getString("ColorHex"),
                            rs.getString("AssignedUserColor"),
                            (Integer) rs.getObject("AssignedToUserId"),
                            rs.getString("AssignedUserDisplayName")));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list calendar feed", e);
        }
    }

    static String buildCalendarFeedSql() {
        return buildCalendarFeedSql(false);
    }

    static String buildCalendarFeedSql(boolean caseFiltered) {
        return buildCalendarFeedSql(caseFiltered, false);
    }

    static String buildCalendarFeedSql(boolean caseFiltered, boolean userScheduleScoped) {
        StringBuilder sql = new StringBuilder("""
                SELECT KeyValue, Title, StartsAt, EndsAt, AllDay, SourceType, SourceField, CaseId, CaseName, TaskId, RelatedDisplayName, CalendarEventTypeSystemKey, DisplayTypeName, ColorHex, AssignedUserColor, AssignedToUserId, AssignedUserDisplayName
                FROM (
                    SELECT CONCAT('EVENT:', CAST(e.CalendarEventId AS varchar(20))) AS KeyValue,
                           e.Title,
                           e.StartsAt,
                           e.EndsAt,
                           e.AllDay,
                           e.SourceType,
                           e.SourceField,
                           e.CaseId,
                           c.Name AS CaseName,
                           e.TaskId,
                           c.Name AS RelatedDisplayName,
                           et.SystemKey AS CalendarEventTypeSystemKey,
                           COALESCE(et.Name, 'Event') AS DisplayTypeName,
                           COALESCE(assignedUser.color, et.ColorHex) AS ColorHex,
                           assignedUser.color AS AssignedUserColor,
                           e.AssignedToUserId,
                           LTRIM(RTRIM(COALESCE(assignedUser.name_first, '') + CASE WHEN COALESCE(assignedUser.name_first, '') = '' OR COALESCE(assignedUser.name_last, '') = '' THEN '' ELSE ' ' END + COALESCE(assignedUser.name_last, ''))) AS AssignedUserDisplayName
                    FROM dbo.CalendarEvents e
                    LEFT JOIN dbo.CalendarEventTypes et ON et.CalendarEventTypeId = e.CalendarEventTypeId
                    LEFT JOIN dbo.Cases c ON c.Id = e.CaseId AND c.ShaleClientId = e.ShaleClientId AND ISNULL(c.IsDeleted, 0) = 0
                    LEFT JOIN dbo.Users assignedUser ON assignedUser.Id = e.AssignedToUserId AND assignedUser.ShaleClientId = e.ShaleClientId AND ISNULL(assignedUser.is_deleted, 0) = 0
                    WHERE e.ShaleClientId = ?
                      AND e.StartsAt >= ?
                      AND e.StartsAt < ?
                      AND ISNULL(e.IsCancelled, 0) = 0
                      """ + (userScheduleScoped ? "AND (e.AssignedToUserId IS NULL OR e.AssignedToUserId = ?)\n" : "") + (caseFiltered ? "AND e.CaseId = ?\n" : "") + """
                    UNION ALL

                    SELECT CONCAT('TASK:', CAST(t.Id AS varchar(20))),
                           t.Title,
                           t.DueAt,
                           NULL,
                           CASE WHEN CONVERT(time(0), t.DueAt) = '00:00:00' THEN 1 ELSE 0 END,
                           'PROJECTED',
                           'DueAt',
                           t.CaseId,
                           c.Name AS CaseName,
                           t.Id,
                           c.Name AS RelatedDisplayName,
                           'TASK_DUE',
                           'Task Due',
                           projectedType.ColorHex,
                           NULL AS AssignedUserColor,
                           NULL AS AssignedToUserId,
                           NULL AS AssignedUserDisplayName
                    FROM dbo.Tasks t
                    OUTER APPLY (
                      SELECT TOP (1) cet.ColorHex
                      FROM dbo.CalendarEventTypes cet
                      WHERE cet.SystemKey = 'TASK_DUE'
                        AND cet.IsActive = 1
                        AND (cet.ShaleClientId = t.ShaleClientId OR cet.ShaleClientId IS NULL)
                      ORDER BY CASE WHEN cet.ShaleClientId = t.ShaleClientId THEN 0 ELSE 1 END,
                               cet.CalendarEventTypeId DESC
                    ) projectedType
                    LEFT JOIN dbo.Cases c ON c.Id = t.CaseId AND c.ShaleClientId = t.ShaleClientId AND ISNULL(c.IsDeleted, 0) = 0
                    WHERE t.ShaleClientId = ?
                      AND t.DueAt IS NOT NULL
                      AND t.DueAt >= ?
                      AND t.DueAt < ?
                      AND ISNULL(t.IsDeleted, 0) = 0
                      AND t.CompletedAt IS NULL
                      """ + (caseFiltered ? "AND t.CaseId = ?\n" : "") + """
                """);
        for (CaseDateProjection projection : CASE_DATE_PROJECTIONS) {
            sql.append("\n                    UNION ALL\n\n").append(caseDateBranch(projection, caseFiltered));
        }
        sql.append("""
                ) feed
                ORDER BY StartsAt ASC, AllDay DESC, KeyValue ASC;
                """);
        return sql.toString();
    }

    private static String caseDateBranch(CaseDateProjection projection, boolean caseFiltered) {
        String fallbackSystemKey = projection.deadline() ? "DEADLINE" : "REMINDER";
        return ("""
                    SELECT CONCAT('%s:', CAST(c.Id AS varchar(20))),
                           CONCAT('%s', N' — ', c.Name),
                           CAST(c.%s AS datetime2),
                           NULL,
                           1,
                           'PROJECTED',
                           '%s',
                           c.Id,
                           c.Name AS CaseName,
                           NULL,
                           c.Name,
                           COALESCE(projectedType.SystemKey, fallbackType.SystemKey, '%s'),
                           COALESCE(projectedType.Name, fallbackType.Name, '%s'),
                           COALESCE(projectedType.ColorHex, fallbackType.ColorHex),
                           NULL AS AssignedUserColor,
                           NULL AS AssignedToUserId,
                           NULL AS AssignedUserDisplayName
                    FROM dbo.Cases c
                    OUTER APPLY (
                      SELECT TOP (1) cet.SystemKey, cet.Name, cet.ColorHex
                      FROM dbo.CalendarEventTypes cet
                      WHERE cet.SystemKey = '%s'
                        AND cet.IsActive = 1
                        AND (cet.ShaleClientId = c.ShaleClientId OR cet.ShaleClientId IS NULL)
                      ORDER BY CASE WHEN cet.ShaleClientId = c.ShaleClientId THEN 0 ELSE 1 END,
                               cet.CalendarEventTypeId DESC
                    ) projectedType
                    OUTER APPLY (
                      SELECT TOP (1) cet.SystemKey, cet.Name, cet.ColorHex
                      FROM dbo.CalendarEventTypes cet
                      WHERE cet.SystemKey = '%s'
                        AND cet.IsActive = 1
                        AND (cet.ShaleClientId = c.ShaleClientId OR cet.ShaleClientId IS NULL)
                      ORDER BY CASE WHEN cet.ShaleClientId = c.ShaleClientId THEN 0 ELSE 1 END,
                               cet.CalendarEventTypeId DESC
                    ) fallbackType
                    WHERE c.ShaleClientId = ?
                      AND c.%s IS NOT NULL
                      AND c.%s >= CAST(? AS date)
                      AND c.%s < CAST(? AS date)
                      AND ISNULL(c.IsDeleted, 0) = 0
                      """ + (caseFiltered ? "AND c.Id = ?\n" : "") + """
                """).formatted(
                projection.keyPrefix(), projection.titlePrefix().replace("'", "''"), projection.columnName(), projection.columnName(),
                projection.systemKey(), projection.displayTypeName().replace("'", "''"), projection.systemKey(), fallbackSystemKey,
                projection.columnName(), projection.columnName(), projection.columnName());
    }

}
