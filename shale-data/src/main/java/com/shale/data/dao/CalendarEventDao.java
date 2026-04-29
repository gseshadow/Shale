package com.shale.data.dao;

import com.shale.core.model.CalendarEvent;
import com.shale.core.runtime.DbSessionProvider;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CalendarEventDao {
    private final DbSessionProvider db;

    public CalendarEventDao(DbSessionProvider db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public Integer create(CalendarEvent event) {
        Objects.requireNonNull(event, "event");
        String sql = """
                INSERT INTO dbo.CalendarEvents (
                    ShaleClientId, CalendarEventTypeId, CaseId, TaskId, Title, Description,
                    StartsAt, EndsAt, AllDay, SourceType, SourceField, SourceId,
                    AssignedToUserId, IsCompleted, IsCancelled, CreatedByUserId
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindUpsert(ps, event);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create calendar event", e);
        }
    }

    public void update(CalendarEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.calendarEventId() == null || event.calendarEventId() <= 0) {
            throw new IllegalArgumentException("calendarEventId must be present for update");
        }
        String sql = """
                UPDATE dbo.CalendarEvents
                SET CalendarEventTypeId = ?,
                    CaseId = ?,
                    TaskId = ?,
                    Title = ?,
                    Description = ?,
                    StartsAt = ?,
                    EndsAt = ?,
                    AllDay = ?,
                    SourceType = ?,
                    SourceField = ?,
                    SourceId = ?,
                    AssignedToUserId = ?,
                    IsCompleted = ?,
                    IsCancelled = ?,
                    UpdatedAt = SYSUTCDATETIME()
                WHERE CalendarEventId = ?
                  AND ShaleClientId = ?;
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, event.calendarEventTypeId());
            ps.setObject(2, event.caseId());
            ps.setObject(3, event.taskId());
            ps.setString(4, event.title());
            ps.setString(5, event.description());
            ps.setTimestamp(6, Timestamp.valueOf(event.startsAt()));
            ps.setTimestamp(7, event.endsAt() == null ? null : Timestamp.valueOf(event.endsAt()));
            ps.setBoolean(8, event.allDay());
            ps.setString(9, event.sourceType());
            ps.setString(10, event.sourceField());
            ps.setObject(11, event.sourceId());
            ps.setObject(12, event.assignedToUserId());
            ps.setBoolean(13, event.completed());
            ps.setBoolean(14, event.cancelled());
            ps.setInt(15, event.calendarEventId());
            ps.setInt(16, event.shaleClientId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update calendar event", e);
        }
    }

    public List<CalendarEvent> listByDateRange(int shaleClientId, LocalDateTime startsAt, LocalDateTime endsAt) {
        if (shaleClientId <= 0 || startsAt == null || endsAt == null) {
            return List.of();
        }
        String sql = """
                SELECT CalendarEventId, ShaleClientId, CalendarEventTypeId, CaseId, TaskId,
                       Title, Description, StartsAt, EndsAt, AllDay, SourceType, SourceField,
                       SourceId, AssignedToUserId, IsCompleted, IsCancelled, CreatedByUserId,
                       CreatedAt, UpdatedAt
                FROM dbo.CalendarEvents
                WHERE ShaleClientId = ?
                  AND StartsAt >= ?
                  AND StartsAt < ?
                ORDER BY StartsAt ASC, CalendarEventId ASC;
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shaleClientId);
            ps.setTimestamp(2, Timestamp.valueOf(startsAt));
            ps.setTimestamp(3, Timestamp.valueOf(endsAt));
            try (ResultSet rs = ps.executeQuery()) {
                List<CalendarEvent> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list calendar events by date range", e);
        }
    }

    private void bindUpsert(PreparedStatement ps, CalendarEvent event) throws SQLException {
        ps.setInt(1, event.shaleClientId());
        ps.setInt(2, event.calendarEventTypeId());
        ps.setObject(3, event.caseId());
        ps.setObject(4, event.taskId());
        ps.setString(5, event.title());
        ps.setString(6, event.description());
        ps.setTimestamp(7, Timestamp.valueOf(event.startsAt()));
        ps.setTimestamp(8, event.endsAt() == null ? null : Timestamp.valueOf(event.endsAt()));
        ps.setBoolean(9, event.allDay());
        ps.setString(10, event.sourceType());
        ps.setString(11, event.sourceField());
        ps.setObject(12, event.sourceId());
        ps.setObject(13, event.assignedToUserId());
        ps.setBoolean(14, event.completed());
        ps.setBoolean(15, event.cancelled());
        ps.setObject(16, event.createdByUserId());
    }

    private CalendarEvent mapRow(ResultSet rs) throws SQLException {
        return new CalendarEvent(
                rs.getInt("CalendarEventId"),
                rs.getInt("ShaleClientId"),
                rs.getInt("CalendarEventTypeId"),
                (Integer) rs.getObject("CaseId"),
                rs.getObject("TaskId") == null ? null : rs.getLong("TaskId"),
                rs.getString("Title"),
                rs.getString("Description"),
                rs.getTimestamp("StartsAt").toLocalDateTime(),
                rs.getTimestamp("EndsAt") == null ? null : rs.getTimestamp("EndsAt").toLocalDateTime(),
                rs.getBoolean("AllDay"),
                rs.getString("SourceType"),
                rs.getString("SourceField"),
                (Integer) rs.getObject("SourceId"),
                (Integer) rs.getObject("AssignedToUserId"),
                rs.getBoolean("IsCompleted"),
                rs.getBoolean("IsCancelled"),
                (Integer) rs.getObject("CreatedByUserId"),
                rs.getTimestamp("CreatedAt").toLocalDateTime(),
                rs.getTimestamp("UpdatedAt") == null ? null : rs.getTimestamp("UpdatedAt").toLocalDateTime());
    }
}
