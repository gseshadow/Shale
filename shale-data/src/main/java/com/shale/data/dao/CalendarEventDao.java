package com.shale.data.dao;

import com.shale.core.model.CalendarEvent;
import com.shale.core.runtime.DbSessionProvider;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CalendarEventDao {
    public record GlobalSearchCalendarEventRow(Integer calendarEventId, int shaleClientId, Integer caseId, String caseName, String title, String description, String location, LocalDateTime startsAt, LocalDateTime endsAt) { }
    private final DbSessionProvider db;
    private final CaseCalendarSynchronizer caseCalendarSynchronizer = new CaseCalendarSynchronizer();
    private final EntityActionAuditDao audit = new EntityActionAuditDao();

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
        try (Connection con = db.requireConnection()) {
            SessionIdentity identity=requireIdentity(con); requireTenant(event.shaleClientId(),identity.tenant()); con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindUpsert(ps, event, identity.actor());
            ps.executeUpdate();
            Integer id;
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    id=rs.getInt(1);
                } else { throw new IllegalStateException("Calendar event was not created."); }
            }
            caseCalendarSynchronizer.fromCalendar(con,identity.tenant(),id,"CREATE",true);
            auditCalendar(con,identity,id,EntityActionAuditEvent.Action.CREATED,event.caseId());
            touchCaseUpdatedAt(con, event.caseId(), identity.tenant()); con.commit(); return id;
            } catch(SQLException | RuntimeException e) { con.rollback(); throw e; } finally { con.setAutoCommit(true); }
        } catch (SQLException e) {
            throw mutationFailure(e);
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
                  AND ShaleClientId = ?
                  AND RowVer = ?;
                """;
        try (Connection con = db.requireConnection()) {
            SessionIdentity identity=requireIdentity(con); requireTenant(event.shaleClientId(),identity.tenant()); con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
            CalendarSourceState before = lockCalendarEvent(con,event.calendarEventId(),identity.tenant());
            Integer previousCaseId = before.caseId();
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
            ps.setBytes(17, before.rowVer());
            if (ps.executeUpdate() == 1) {
                String syncOperation=!before.cancelled()&&event.cancelled()?"CANCEL":before.cancelled()&&!event.cancelled()?"RESTORE":"UPDATE";
                caseCalendarSynchronizer.fromCalendar(con,identity.tenant(),event.calendarEventId(),syncOperation,before.eventTypeId()!=event.calendarEventTypeId());
                auditCalendar(con,identity,event.calendarEventId(),EntityActionAuditEvent.Action.UPDATED,event.caseId());
                touchCaseUpdatedAt(con, previousCaseId, event.shaleClientId());
                touchCaseUpdatedAt(con, event.caseId(), event.shaleClientId());
            } else throw new IllegalArgumentException("Record is not available.");
            con.commit();
            } catch(SQLException | RuntimeException e) { con.rollback(); throw e; } finally { con.setAutoCommit(true); }
        } catch (SQLException e) {
            throw mutationFailure(e);
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


    public List<GlobalSearchCalendarEventRow> searchCalendarEvents(int shaleClientId, String query) {
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) {
            return List.of();
        }
        String like = "%" + q + "%";
        String sql = """
                SELECT TOP (100) e.CalendarEventId, e.ShaleClientId, e.CaseId, c.Name AS CaseName,
                       e.Title, e.Description, CAST(NULL AS nvarchar(4000)) AS Location, e.StartsAt, e.EndsAt
                FROM dbo.CalendarEvents e
                LEFT JOIN dbo.Cases c ON c.Id = e.CaseId AND c.ShaleClientId = e.ShaleClientId
                WHERE e.ShaleClientId = ?
                  AND ISNULL(e.IsCancelled, 0) = 0
                  AND (e.Title LIKE ? OR e.Description LIKE ? OR c.Name LIKE ?)
                ORDER BY e.StartsAt ASC, e.CalendarEventId ASC;
                """;
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shaleClientId);
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, like);
            try (ResultSet rs = ps.executeQuery()) {
                List<GlobalSearchCalendarEventRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new GlobalSearchCalendarEventRow(
                            rs.getInt("CalendarEventId"), rs.getInt("ShaleClientId"), (Integer) rs.getObject("CaseId"), rs.getString("CaseName"),
                            rs.getString("Title"), rs.getString("Description"), rs.getString("Location"),
                            rs.getTimestamp("StartsAt").toLocalDateTime(), rs.getTimestamp("EndsAt") == null ? null : rs.getTimestamp("EndsAt").toLocalDateTime()));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search calendar events for tenant (clientId=" + shaleClientId + ")", e);
        }
    }

    public CalendarEvent getById(int calendarEventId, int shaleClientId) {
        if (calendarEventId <= 0 || shaleClientId <= 0) return null;
        String sql = """
                SELECT CalendarEventId, ShaleClientId, CalendarEventTypeId, CaseId, TaskId,
                       Title, Description, StartsAt, EndsAt, AllDay, SourceType, SourceField,
                       SourceId, AssignedToUserId, IsCompleted, IsCancelled, CreatedByUserId,
                       CreatedAt, UpdatedAt
                FROM dbo.CalendarEvents
                WHERE CalendarEventId = ?
                  AND ShaleClientId = ?;
                """;
        try (Connection con = db.requireConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            Integer previousCaseId = findCalendarEventCaseId(con, calendarEventId, shaleClientId);
            ps.setInt(1, calendarEventId);
            ps.setInt(2, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load calendar event", e);
        }
    }

    public void deleteCalendarEvent(int calendarEventId, int shaleClientId) {
        if (calendarEventId <= 0 || shaleClientId <= 0) return;
        String sql = """
                DELETE FROM dbo.CalendarEvents
                WHERE CalendarEventId = ?
                  AND ShaleClientId = ?
                  AND RowVer = ?;
                """;
        try (Connection con = db.requireConnection()) {
            SessionIdentity identity=requireIdentity(con); requireTenant(shaleClientId,identity.tenant()); con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
            CalendarSourceState before=lockCalendarEvent(con,calendarEventId,identity.tenant());
            Integer previousCaseId = before.caseId();
            caseCalendarSynchronizer.fromCalendar(con,identity.tenant(),calendarEventId,"DELETE",false);
            byte[] deleteRowVer=lockCalendarEvent(con,calendarEventId,identity.tenant()).rowVer();
            ps.setInt(1, calendarEventId);
            ps.setInt(2, shaleClientId);
            ps.setBytes(3,deleteRowVer);
            if (ps.executeUpdate() > 0) {
                auditCalendar(con,identity,calendarEventId,EntityActionAuditEvent.Action.DELETED,previousCaseId);
                touchCaseUpdatedAt(con, previousCaseId, shaleClientId);
            } else throw new IllegalArgumentException("Record is not available.");
            con.commit();
            } catch(SQLException | RuntimeException e) { con.rollback(); throw e; } finally { con.setAutoCommit(true); }
        } catch (SQLException e) {
            throw mutationFailure(e);
        }
    }

    private static RuntimeException mutationFailure(SQLException e){
        if(e.getErrorCode()==2601||e.getErrorCode()==2627)return new IllegalStateException("Calendar/Case Date link changed; reload and try again.");
        return new IllegalStateException("Calendar event could not be saved.");
    }

    private record CalendarSourceState(Integer caseId,int eventTypeId,boolean cancelled,byte[] rowVer){}
    private static CalendarSourceState lockCalendarEvent(Connection con,int id,int tenant)throws SQLException{try(PreparedStatement p=con.prepareStatement("SELECT CaseId,CalendarEventTypeId,IsCancelled,RowVer FROM dbo.CalendarEvents WITH(UPDLOCK,HOLDLOCK) WHERE CalendarEventId=? AND ShaleClientId=?")){p.setInt(1,id);p.setInt(2,tenant);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Record is not available.");return new CalendarSourceState((Integer)r.getObject(1),r.getInt(2),r.getBoolean(3),r.getBytes(4));}}}

    private record SessionIdentity(int tenant,int actor){}
    private static SessionIdentity requireIdentity(Connection con)throws SQLException{try(Statement s=con.createStatement();ResultSet r=s.executeQuery("SELECT TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId'))")){if(!r.next())throw new IllegalStateException("Authenticated SQL session context is required.");int t=r.getInt(1),a=r.getInt(2);if(t<=0||a<=0)throw new IllegalStateException("Authenticated SQL session context is required.");try(PreparedStatement p=con.prepareStatement("SELECT 1 FROM dbo.Users WHERE id=? AND ShaleClientId=? AND COALESCE(is_deleted,0)=0 AND COALESCE(IsRemoved,0)=0")){p.setInt(1,a);p.setInt(2,t);try(ResultSet u=p.executeQuery()){if(!u.next())throw new IllegalStateException("Authenticated SQL session context is required.");}}return new SessionIdentity(t,a);}}
    private static void requireTenant(int supplied,int authoritative){if(supplied!=authoritative)throw new IllegalArgumentException("Record is not available.");}
    private void auditCalendar(Connection con,SessionIdentity i,int id,EntityActionAuditEvent.Action action,Integer caseId)throws SQLException{audit.append(con,EntityActionAuditEvent.now(i.tenant(),i.actor(),EntityActionAuditEvent.EntityType.CALENDAR_EVENT,id,action,caseId==null?null:EntityActionAuditEvent.EntityType.CASE,caseId==null?null:caseId.longValue(),caseId==null?java.util.Map.of():java.util.Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID,caseId)));}

    private static void touchCaseUpdatedAt(Connection con, Integer caseId, int shaleClientId) throws SQLException {
        if (caseId == null || caseId <= 0) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement("""
                UPDATE dbo.Cases
                SET UpdatedAt = SYSUTCDATETIME()
                WHERE Id = ?
                  AND ShaleClientId = ?
                  AND (IsDeleted = 0 OR IsDeleted IS NULL);
                """)) {
            ps.setInt(1, caseId);
            ps.setInt(2, shaleClientId);
            ps.executeUpdate();
        }
    }

    private static Integer findCalendarEventCaseId(Connection con, int calendarEventId, int shaleClientId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT CaseId
                FROM dbo.CalendarEvents
                WHERE CalendarEventId = ?
                  AND ShaleClientId = ?;
                """)) {
            ps.setInt(1, calendarEventId);
            ps.setInt(2, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (Integer) rs.getObject("CaseId");
                }
                return null;
            }
        }
    }

    private void bindUpsert(PreparedStatement ps, CalendarEvent event, int authoritativeActor) throws SQLException {
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
        ps.setInt(16, authoritativeActor);
    }

    private CalendarEvent mapRow(ResultSet rs) throws SQLException {
        return new CalendarEvent(
                rs.getInt("CalendarEventId"),
                rs.getInt("ShaleClientId"),
                rs.getInt("CalendarEventTypeId"),
                (Integer) rs.getObject("CaseId"),
                (Integer) rs.getObject("TaskId"),
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
