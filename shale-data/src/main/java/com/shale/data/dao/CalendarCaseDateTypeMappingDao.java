package com.shale.data.dao;

import com.shale.core.model.CalendarCaseDateTypeMapping;
import com.shale.core.model.CreateCalendarCaseDateTypeMappingCommand;
import com.shale.core.model.DeleteCalendarCaseDateTypeMappingCommand;
import com.shale.core.model.SetCalendarCaseDateTypeMappingActiveCommand;
import com.shale.core.model.UpdateCalendarCaseDateTypeMappingCommand;
import com.shale.core.runtime.DbSessionProvider;

import java.sql.*;
import java.util.*;

/** Transactional, session-authorized persistence for calendar/case-date type mappings. */
public final class CalendarCaseDateTypeMappingDao {
    private static final String CHANGED = "This calendar/case-date type mapping was changed by someone else. Reload and try again.";
    private static final String NOT_FOUND = "Calendar/case-date type mapping was not found.";
    private static final String DUPLICATE = "An active mapping already uses the selected Calendar Event Type or Case Date Type.";
    private final DbSessionProvider db;
    private final EntityActionAuditDao auditDao;

    public CalendarCaseDateTypeMappingDao(DbSessionProvider db) { this(db, new EntityActionAuditDao()); }
    CalendarCaseDateTypeMappingDao(DbSessionProvider db, EntityActionAuditDao auditDao) {
        this.db = Objects.requireNonNull(db, "db"); this.auditDao = Objects.requireNonNull(auditDao, "auditDao");
    }

    public List<CalendarCaseDateTypeMapping> listMappings() {
        try (Connection con = db.requireConnection()) {
            SessionIdentity identity = requireAdminIdentity(con);
            try (PreparedStatement ps = con.prepareStatement(selectSql("ShaleClientId=? ORDER BY Id"))) {
                ps.setInt(1, identity.tenant());
                try (ResultSet rs = ps.executeQuery()) { List<CalendarCaseDateTypeMapping> rows = new ArrayList<>(); while (rs.next()) rows.add(map(rs)); return List.copyOf(rows); }
            }
        } catch (SQLException e) { throw failure("list", e); }
    }

    public CalendarCaseDateTypeMapping createMapping(CreateCalendarCaseDateTypeMappingCommand command) {
        Objects.requireNonNull(command, "command"); validateIds(command.calendarEventTypeId(), command.caseDateTypeId()); validateDirection(command.caseDateToCalendar(), command.calendarToCaseDate());
        return mutate((con, identity) -> {
            validateReferences(con, identity.tenant(), command.calendarEventTypeId(), command.caseDateTypeId());
            if (command.active()) requireNoActiveDuplicate(con, identity.tenant(), 0, command.calendarEventTypeId(), command.caseDateTypeId());
            long id;
            try (PreparedStatement ps = con.prepareStatement("INSERT dbo.CalendarCaseDateTypeMappings(ShaleClientId,CalendarEventTypeId,CaseDateTypeId,CaseDateToCalendar,CalendarToCaseDate,IsActive,CreatedAt,CreatedByUserId) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,SYSUTCDATETIME(),?)")) {
                int i=1; ps.setInt(i++,identity.tenant()); ps.setInt(i++,command.calendarEventTypeId()); ps.setInt(i++,command.caseDateTypeId()); ps.setBoolean(i++,command.caseDateToCalendar()); ps.setBoolean(i++,command.calendarToCaseDate()); ps.setBoolean(i++,command.active()); ps.setInt(i,identity.actor());
                try (ResultSet rs=ps.executeQuery()) { if(!rs.next()) throw new IllegalStateException("Calendar/case-date type mapping was not created."); id=rs.getLong(1); }
            }
            audit(con,identity,id,EntityActionAuditEvent.Action.CREATED,command.calendarEventTypeId(),command.caseDateTypeId(),command.caseDateToCalendar(),command.calendarToCaseDate(),command.active());
            return requireRow(con,identity.tenant(),id);
        });
    }

    public CalendarCaseDateTypeMapping updateMapping(UpdateCalendarCaseDateTypeMappingCommand command) {
        Objects.requireNonNull(command,"command"); validateMutation(command.id(),command.expectedRowVer()); validateIds(command.calendarEventTypeId(),command.caseDateTypeId()); validateDirection(command.caseDateToCalendar(),command.calendarToCaseDate());
        return mutate((con, identity) -> {
            CalendarCaseDateTypeMapping old=requireCurrent(con,identity.tenant(),command.id(),command.expectedRowVer());
            validateReferences(con,identity.tenant(),command.calendarEventTypeId(),command.caseDateTypeId());
            if(old.active()) requireNoActiveDuplicate(con,identity.tenant(),old.id(),command.calendarEventTypeId(),command.caseDateTypeId());
            try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.CalendarCaseDateTypeMappings SET CalendarEventTypeId=?,CaseDateTypeId=?,CaseDateToCalendar=?,CalendarToCaseDate=?,UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? WHERE Id=? AND ShaleClientId=? AND RowVer=?")){
                ps.setInt(1,command.calendarEventTypeId());ps.setInt(2,command.caseDateTypeId());ps.setBoolean(3,command.caseDateToCalendar());ps.setBoolean(4,command.calendarToCaseDate());ps.setInt(5,identity.actor());ps.setLong(6,command.id());ps.setInt(7,identity.tenant());ps.setBytes(8,command.expectedRowVer());requireUpdated(ps);
            }
            audit(con,identity,command.id(),EntityActionAuditEvent.Action.UPDATED,command.calendarEventTypeId(),command.caseDateTypeId(),command.caseDateToCalendar(),command.calendarToCaseDate(),old.active());
            return requireRow(con,identity.tenant(),command.id());
        });
    }

    public CalendarCaseDateTypeMapping setMappingActive(SetCalendarCaseDateTypeMappingActiveCommand command) {
        Objects.requireNonNull(command,"command"); validateMutation(command.id(),command.expectedRowVer());
        return mutate((con, identity) -> {
            CalendarCaseDateTypeMapping old=requireCurrent(con,identity.tenant(),command.id(),command.expectedRowVer());
            validateReferences(con,identity.tenant(),old.calendarEventTypeId(),old.caseDateTypeId());
            if(command.active()) requireNoActiveDuplicate(con,identity.tenant(),old.id(),old.calendarEventTypeId(),old.caseDateTypeId());
            try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.CalendarCaseDateTypeMappings SET IsActive=?,UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? WHERE Id=? AND ShaleClientId=? AND RowVer=?")){
                ps.setBoolean(1,command.active());ps.setInt(2,identity.actor());ps.setLong(3,command.id());ps.setInt(4,identity.tenant());ps.setBytes(5,command.expectedRowVer());requireUpdated(ps);
            }
            audit(con,identity,old.id(),command.active()?EntityActionAuditEvent.Action.ACTIVATED:EntityActionAuditEvent.Action.DEACTIVATED,old.calendarEventTypeId(),old.caseDateTypeId(),old.caseDateToCalendar(),old.calendarToCaseDate(),command.active());
            return requireRow(con,identity.tenant(),old.id());
        });
    }

    public void deleteMapping(DeleteCalendarCaseDateTypeMappingCommand command) {
        Objects.requireNonNull(command,"command"); validateMutation(command.id(),command.expectedRowVer());
        mutate((con,identity)->{
            CalendarCaseDateTypeMapping old=requireCurrent(con,identity.tenant(),command.id(),command.expectedRowVer());
            try(PreparedStatement ps=con.prepareStatement("DELETE FROM dbo.CalendarCaseDateTypeMappings WHERE Id=? AND ShaleClientId=? AND RowVer=?")){ps.setLong(1,old.id());ps.setInt(2,identity.tenant());ps.setBytes(3,command.expectedRowVer());requireUpdated(ps);}
            audit(con,identity,old.id(),EntityActionAuditEvent.Action.DELETED,old.calendarEventTypeId(),old.caseDateTypeId(),old.caseDateToCalendar(),old.calendarToCaseDate(),old.active()); return null;
        });
    }

    private interface Work<T>{T run(Connection con,SessionIdentity identity)throws Exception;}
    private <T>T mutate(Work<T> work){try(Connection con=db.requireConnection()){SessionIdentity identity=requireAdminIdentity(con);con.setAutoCommit(false);try{T result=work.run(con,identity);con.commit();return result;}catch(Exception e){rollback(con);if(e instanceof RuntimeException re)throw re;if(e instanceof SQLException se)throw failure("save",se);throw new IllegalStateException("Failed to save calendar/case-date type mapping.",e);}}catch(SQLException e){throw failure("save",e);}}
    private static void rollback(Connection con){try{con.rollback();}catch(SQLException ignored){}}

    private static SessionIdentity requireAdminIdentity(Connection con)throws SQLException{
        String sql="SELECT TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId'))";
        int tenant,actor;try(PreparedStatement ps=con.prepareStatement(sql);ResultSet rs=ps.executeQuery()){if(!rs.next()||(tenant=rs.getInt(1))<=0||rs.wasNull()||(actor=rs.getInt(2))<=0||rs.wasNull())throw new IllegalStateException("Authenticated tenant and actor session context is required.");}
        try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo.Users WHERE id=? AND ShaleClientId=? AND COALESCE(is_admin,0)=1 AND COALESCE(is_deleted,0)=0 AND COALESCE(IsRemoved,0)=0")){ps.setInt(1,actor);ps.setInt(2,tenant);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalStateException("An active tenant administrator is required.");}}
        return new SessionIdentity(tenant,actor);
    }
    private record SessionIdentity(int tenant,int actor){}

    private static void validateReferences(Connection con,int tenant,int eventType,int dateType)throws SQLException{
        if(!eligible(con,"SELECT 1 FROM dbo.CalendarEventTypes WHERE CalendarEventTypeId=? AND (ShaleClientId IS NULL OR ShaleClientId=?) AND IsActive=1",eventType,tenant))throw new IllegalArgumentException("Select an active Calendar Event Type available to this tenant.");
        if(!eligible(con,"SELECT 1 FROM dbo.CaseDateTypes WHERE Id=? AND (ShaleClientId IS NULL OR ShaleClientId=?) AND IsActive=1 AND IsDeleted=0",dateType,tenant))throw new IllegalArgumentException("Select an active Case Date Type available to this tenant.");
    }
    private static boolean eligible(Connection con,String sql,int id,int tenant)throws SQLException{try(PreparedStatement ps=con.prepareStatement(sql)){ps.setInt(1,id);ps.setInt(2,tenant);try(ResultSet rs=ps.executeQuery()){return rs.next();}}}
    private static void requireNoActiveDuplicate(Connection con,int tenant,long excluded,int eventType,int dateType)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo.CalendarCaseDateTypeMappings WITH (UPDLOCK,HOLDLOCK) WHERE ShaleClientId=? AND IsActive=1 AND Id<>? AND (CalendarEventTypeId=? OR CaseDateTypeId=?)")){ps.setInt(1,tenant);ps.setLong(2,excluded);ps.setInt(3,eventType);ps.setInt(4,dateType);try(ResultSet rs=ps.executeQuery()){if(rs.next())throw new IllegalArgumentException(DUPLICATE);}}}
    private static CalendarCaseDateTypeMapping requireCurrent(Connection con,int tenant,long id,byte[] rv)throws SQLException{CalendarCaseDateTypeMapping row=find(con,tenant,id);if(row==null)throw new IllegalArgumentException(NOT_FOUND);if(!Arrays.equals(row.rowVer(),rv))throw new IllegalStateException(CHANGED);return row;}
    private static CalendarCaseDateTypeMapping requireRow(Connection con,int tenant,long id)throws SQLException{CalendarCaseDateTypeMapping row=find(con,tenant,id);if(row==null)throw new IllegalStateException("Saved calendar/case-date type mapping could not be reloaded.");return row;}
    private static CalendarCaseDateTypeMapping find(Connection con,int tenant,long id)throws SQLException{try(PreparedStatement ps=con.prepareStatement(selectSql("ShaleClientId=? AND Id=?"))){ps.setInt(1,tenant);ps.setLong(2,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?map(rs):null;}}}
    private static String selectSql(String where){return "SELECT Id,CalendarEventTypeId,CaseDateTypeId,CaseDateToCalendar,CalendarToCaseDate,IsActive,CreatedAt,CreatedByUserId,UpdatedAt,UpdatedByUserId,RowVer FROM dbo.CalendarCaseDateTypeMappings WHERE "+where;}
    private static CalendarCaseDateTypeMapping map(ResultSet rs)throws SQLException{Timestamp updated=rs.getTimestamp("UpdatedAt");int rawUpdatedBy=rs.getInt("UpdatedByUserId");Integer updatedBy=rs.wasNull()?null:rawUpdatedBy;return new CalendarCaseDateTypeMapping(rs.getLong("Id"),rs.getInt("CalendarEventTypeId"),rs.getInt("CaseDateTypeId"),rs.getBoolean("CaseDateToCalendar"),rs.getBoolean("CalendarToCaseDate"),rs.getBoolean("IsActive"),rs.getTimestamp("CreatedAt").toLocalDateTime(),rs.getInt("CreatedByUserId"),updated==null?null:updated.toLocalDateTime(),updatedBy,rs.getBytes("RowVer"));}
    private void audit(Connection con,SessionIdentity i,long id,EntityActionAuditEvent.Action action,int eventType,int dateType,boolean cdToCal,boolean calToCd,boolean active)throws SQLException{var md=new EnumMap<EntityActionAuditEvent.MetadataKey,Object>(EntityActionAuditEvent.MetadataKey.class);md.put(EntityActionAuditEvent.MetadataKey.CALENDAR_EVENT_TYPE_ID,eventType);md.put(EntityActionAuditEvent.MetadataKey.CASE_DATE_TYPE_ID,dateType);md.put(EntityActionAuditEvent.MetadataKey.CASE_DATE_TO_CALENDAR,cdToCal);md.put(EntityActionAuditEvent.MetadataKey.CALENDAR_TO_CASE_DATE,calToCd);md.put(EntityActionAuditEvent.MetadataKey.ACTIVE,active);auditDao.append(con,EntityActionAuditEvent.now(i.tenant(),i.actor(),EntityActionAuditEvent.EntityType.CALENDAR_CASE_DATE_TYPE_MAPPING,id,action,null,null,md));}
    private static void validateIds(int eventType,int dateType){if(eventType<=0)throw new IllegalArgumentException("Calendar Event Type ID must be positive.");if(dateType<=0)throw new IllegalArgumentException("Case Date Type ID must be positive.");}
    private static void validateDirection(boolean a,boolean b){if(!a&&!b)throw new IllegalArgumentException("At least one synchronization direction must be enabled.");}
    private static void validateMutation(long id,byte[] rv){if(id<=0)throw new IllegalArgumentException("Mapping ID must be positive.");if(rv==null||rv.length==0)throw new IllegalArgumentException("Mapping version is required.");}
    private static void requireUpdated(PreparedStatement ps)throws SQLException{if(ps.executeUpdate()!=1)throw new IllegalStateException(CHANGED);}
    private static RuntimeException failure(String operation,SQLException e){if(e.getErrorCode()==2601||e.getErrorCode()==2627)return new IllegalArgumentException(DUPLICATE);return new RuntimeException("Failed to "+operation+" calendar/case-date type mappings.",e);}
}
