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
    public record CalendarTaskCardRow(long taskId, Integer caseId, String caseName, String caseResponsibleAttorney, String caseResponsibleAttorneyColor, Boolean caseNonEngagementLetterSent, String title, LocalDateTime dueAt, LocalDateTime completedAt, String createdByDisplayName, String priorityColorHex) {}

    public List<CalendarCaseCardRow> listCaseCardRows(int shaleClientId, List<Integer> caseIds) {
        if (shaleClientId <= 0 || caseIds == null || caseIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(caseIds.size(), "?"));
        String sql = """
                SELECT c.Id, c.Name, ra.DisplayName AS ResponsibleAttorney, ra.Color AS ResponsibleAttorneyColor, c.NonEngagementLetterSent
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
                SELECT t.Id, t.CaseId, c.Name AS CaseName, ra.DisplayName AS CaseResponsibleAttorney, ra.Color AS CaseResponsibleAttorneyColor,
                       c.NonEngagementLetterSent AS CaseNonEngagementLetterSent, t.Title, t.DueAt, t.CompletedAt, p.ColorHex AS PriorityColorHex,
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
                while (rs.next()) rows.add(new CalendarTaskCardRow(rs.getLong("Id"), (Integer) rs.getObject("CaseId"), rs.getString("CaseName"), rs.getString("CaseResponsibleAttorney"), rs.getString("CaseResponsibleAttorneyColor"), (Boolean) rs.getObject("CaseNonEngagementLetterSent"), rs.getString("Title"), rs.getTimestamp("DueAt") == null ? null : rs.getTimestamp("DueAt").toLocalDateTime(), rs.getTimestamp("CompletedAt") == null ? null : rs.getTimestamp("CompletedAt").toLocalDateTime(), rs.getString("CreatedByDisplayName"), rs.getString("PriorityColorHex")));
                return rows;
            }
        } catch (SQLException e) { throw new RuntimeException("Failed to load calendar task card rows", e); }
    }
    public List<CalendarFeedItem> listCalendarFeed(int shaleClientId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        if (shaleClientId <= 0 || startInclusive == null || endExclusive == null) {
            return List.of();
        }
        String sql = """
                SELECT KeyValue, Title, StartsAt, EndsAt, AllDay, SourceType, SourceField, CaseId, TaskId, CalendarEventTypeSystemKey, DisplayTypeName
                FROM (
                    SELECT CONCAT('EVENT:', CAST(e.CalendarEventId AS varchar(20))) AS KeyValue,
                           e.Title,
                           e.StartsAt,
                           e.EndsAt,
                           e.AllDay,
                           e.SourceType,
                           e.SourceField,
                           e.CaseId,
                           e.TaskId,
                           et.SystemKey AS CalendarEventTypeSystemKey,
                           COALESCE(et.Name, 'Event') AS DisplayTypeName
                    FROM dbo.CalendarEvents e
                    LEFT JOIN dbo.CalendarEventTypes et ON et.CalendarEventTypeId = e.CalendarEventTypeId
                    WHERE e.ShaleClientId = ?
                      AND e.StartsAt >= ?
                      AND e.StartsAt < ?

                    UNION ALL

                    SELECT CONCAT('TASK:', CAST(t.Id AS varchar(20))),
                           'Task due',
                           t.DueAt,
                           NULL,
                           0,
                           'PROJECTED',
                           'DueAt',
                           t.CaseId,
                           t.Id,
                           'TASK_DUE',
                           'Task Due'
                    FROM dbo.Tasks t
                    WHERE t.ShaleClientId = ?
                      AND t.DueAt IS NOT NULL
                      AND t.DueAt >= ?
                      AND t.DueAt < ?
                      AND ISNULL(t.IsDeleted, 0) = 0

                    UNION ALL

                    SELECT CONCAT('CASE_SOL:', CAST(c.Id AS varchar(20))),
                           'Statute of limitations',
                           CAST(c.StatuteOfLimitations AS datetime2),
                           NULL,
                           1,
                           'PROJECTED',
                           'StatuteOfLimitations',
                           c.Id,
                           NULL,
                           'STATUTE_OF_LIMITATIONS',
                           'Statute of Limitations'
                    FROM dbo.Cases c
                    WHERE c.ShaleClientId = ?
                      AND c.StatuteOfLimitations IS NOT NULL
                      AND c.StatuteOfLimitations >= CAST(? AS date)
                      AND c.StatuteOfLimitations < CAST(? AS date)
                      AND ISNULL(c.IsDeleted, 0) = 0

                    UNION ALL

                    SELECT CONCAT('CASE_TORT:', CAST(c.Id AS varchar(20))),
                           'Tort notice deadline',
                           CAST(c.TortNoticeDeadline AS datetime2),
                           NULL,
                           1,
                           'PROJECTED',
                           'TortNoticeDeadline',
                           c.Id,
                           NULL,
                           'TORT_NOTICE_DEADLINE',
                           'Tort Notice Deadline'
                    FROM dbo.Cases c
                    WHERE c.ShaleClientId = ?
                      AND c.TortNoticeDeadline IS NOT NULL
                      AND c.TortNoticeDeadline >= CAST(? AS date)
                      AND c.TortNoticeDeadline < CAST(? AS date)
                      AND ISNULL(c.IsDeleted, 0) = 0

                    UNION ALL

                    SELECT CONCAT('CASE_DISC:', CAST(c.Id AS varchar(20))),
                           'Discovery deadline',
                           CAST(c.DiscoveryDeadline AS datetime2),
                           NULL,
                           1,
                           'PROJECTED',
                           'DiscoveryDeadline',
                           c.Id,
                           NULL,
                           'DISCOVERY_DEADLINE',
                           'Discovery Deadline'
                    FROM dbo.Cases c
                    WHERE c.ShaleClientId = ?
                      AND c.DiscoveryDeadline IS NOT NULL
                      AND c.DiscoveryDeadline >= CAST(? AS date)
                      AND c.DiscoveryDeadline < CAST(? AS date)
                      AND ISNULL(c.IsDeleted, 0) = 0
                ) feed
                ORDER BY StartsAt ASC, KeyValue ASC;
                """;
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, shaleClientId); ps.setTimestamp(i++, Timestamp.valueOf(startInclusive)); ps.setTimestamp(i++, Timestamp.valueOf(endExclusive));
            ps.setInt(i++, shaleClientId); ps.setTimestamp(i++, Timestamp.valueOf(startInclusive)); ps.setTimestamp(i++, Timestamp.valueOf(endExclusive));
            LocalDate startDate = startInclusive.toLocalDate();
            LocalDate endDate = endExclusive.toLocalDate();
            ps.setInt(i++, shaleClientId); ps.setDate(i++, Date.valueOf(startDate)); ps.setDate(i++, Date.valueOf(endDate));
            ps.setInt(i++, shaleClientId); ps.setDate(i++, Date.valueOf(startDate)); ps.setDate(i++, Date.valueOf(endDate));
            ps.setInt(i++, shaleClientId); ps.setDate(i++, Date.valueOf(startDate)); ps.setDate(i++, Date.valueOf(endDate));
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
                            (Integer) rs.getObject("TaskId"),
                            rs.getString("CalendarEventTypeSystemKey"),
                            rs.getString("DisplayTypeName")));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list calendar feed", e);
        }
    }
}
