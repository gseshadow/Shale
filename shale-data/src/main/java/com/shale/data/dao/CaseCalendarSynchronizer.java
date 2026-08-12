package com.shale.data.dao;

import java.sql.*;
import java.util.EnumMap;

/**
 * Connection-bound participant for Case Date/Calendar synchronization.  It never
 * owns a connection or commits, which keeps projection writes and their audit in
 * the authoritative source mutation transaction.
 */
final class CaseCalendarSynchronizer {
    private final EntityActionAuditDao audit = new EntityActionAuditDao();

    void fromCaseDate(Connection con, int tenant, long caseDateId, String operation, boolean allowCreate) throws SQLException {
        int actor = authenticatedActor(con, tenant);
        DateRow date = lockDate(con, tenant, caseDateId);
        EventRow linked = lockLinkedEvent(con, tenant, caseDateId);
        Mapping mapping = mappingForDate(con, tenant, date.typeId, true);

        if (mapping == null) {
            // A type/mapping change ends synchronization without rewriting or pairing history.
            if (linked != null) unlink(con, tenant, linked);
            return;
        }
        if ("DELETE".equals(operation)) {
            if (linked != null && !linked.cancelled) updateEventLifecycle(con, linked, true, actor);
            if (linked != null && !linked.cancelled) audit(con, tenant, actor, EntityActionAuditEvent.EntityType.CALENDAR_EVENT,
                    linked.id, EntityActionAuditEvent.Action.DELETED, date.caseId, caseDateId, "CASE_DATE_TO_CALENDAR");
            return;
        }
        if (linked == null && allowCreate) {
            int id = insertEvent(con, tenant, actor, date, mapping.eventTypeId);
            audit(con, tenant, actor, EntityActionAuditEvent.EntityType.CALENDAR_EVENT, id,
                    EntityActionAuditEvent.Action.CREATED, date.caseId, caseDateId, "CASE_DATE_TO_CALENDAR");
        } else if (linked != null) {
            boolean restore = "RESTORE".equals(operation) && linked.cancelled
                    && lastSynchronizationAction(con, tenant, "CALENDAR_EVENT", linked.id, "CASE_DATE_TO_CALENDAR", "DELETED");
            boolean changed = updateEventProjection(con, linked, mapping.eventTypeId, date, restore ? false : linked.cancelled);
            if (changed) audit(con, tenant, actor, EntityActionAuditEvent.EntityType.CALENDAR_EVENT, linked.id,
                    restore ? EntityActionAuditEvent.Action.RESTORED : EntityActionAuditEvent.Action.UPDATED,
                    date.caseId, caseDateId, "CASE_DATE_TO_CALENDAR");
        }
    }

    void fromCalendar(Connection con, int tenant, int eventId, String operation, boolean allowCreate) throws SQLException {
        int actor = authenticatedActor(con, tenant);
        EventRow event = lockEvent(con, tenant, eventId);
        if (event.caseId == null) return;
        DateRow linked = event.caseDateId == null ? null : lockDate(con, tenant, event.caseDateId);
        Mapping mapping = mappingForEvent(con, tenant, event.eventTypeId, true);
        if (mapping == null) {
            if (linked != null) unlink(con, tenant, event);
            return;
        }
        if ("DELETE".equals(operation)) {
            if (linked != null && !linked.deleted) {
                updateDateLifecycle(con, linked, true, actor);
                audit(con, tenant, actor, EntityActionAuditEvent.EntityType.CASE_DATE, linked.id,
                        EntityActionAuditEvent.Action.DELETED, event.caseId, linked.id, "CALENDAR_TO_CASE_DATE");
            }
            if (linked != null) unlink(con, tenant, event);
            return;
        }
        if (linked == null && allowCreate) {
            long dateId = insertDate(con, tenant, actor, event, mapping.dateTypeId);
            link(con, tenant, event, dateId);
            audit(con, tenant, actor, EntityActionAuditEvent.EntityType.CASE_DATE, dateId,
                    EntityActionAuditEvent.Action.CREATED, event.caseId, dateId, "CALENDAR_TO_CASE_DATE");
        } else if (linked != null) {
            if (linked.caseId != event.caseId) throw new IllegalArgumentException("Linked records must belong to the same Case.");
            boolean delete = "CANCEL".equals(operation) && !linked.deleted;
            boolean restore = "RESTORE".equals(operation) && linked.deleted
                    && lastSynchronizationAction(con, tenant, "CASE_DATE", linked.id, "CALENDAR_TO_CASE_DATE", "DELETED");
            boolean targetDeleted = delete || (linked.deleted && !restore);
            boolean changed = updateDateProjection(con, linked, mapping.dateTypeId, event, actor, targetDeleted);
            if (changed) audit(con, tenant, actor, EntityActionAuditEvent.EntityType.CASE_DATE, linked.id,
                    delete ? EntityActionAuditEvent.Action.DELETED : (restore ? EntityActionAuditEvent.Action.RESTORED : EntityActionAuditEvent.Action.UPDATED),
                    event.caseId, linked.id, "CALENDAR_TO_CASE_DATE");
        }
    }

    private static Mapping mappingForDate(Connection c,int t,int type,boolean direction)throws SQLException{return mapping(c,"CaseDateTypeId=? AND CaseDateToCalendar=?",t,type,direction);}
    private static Mapping mappingForEvent(Connection c,int t,int type,boolean direction)throws SQLException{return mapping(c,"CalendarEventTypeId=? AND CalendarToCaseDate=?",t,type,direction);}
    private static Mapping mapping(Connection c,String predicate,int t,int type,boolean direction)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT CalendarEventTypeId,CaseDateTypeId FROM dbo.CalendarCaseDateTypeMappings WITH(UPDLOCK,HOLDLOCK) WHERE ShaleClientId=? AND IsActive=1 AND "+predicate)){
            p.setInt(1,t);p.setInt(2,type);p.setBoolean(3,direction);try(ResultSet r=p.executeQuery()){if(!r.next())return null;Mapping m=new Mapping(r.getInt(1),r.getInt(2));if(r.next())throw new IllegalStateException("Active synchronization mapping is ambiguous.");return m;}}
    }
    private static DateRow lockDate(Connection c,int t,long id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT Id,CaseId,CaseDateTypeId,StartsAt,EndsAt,AllDay,IsDeleted,RowVer FROM dbo.CaseDates WITH(UPDLOCK,HOLDLOCK) WHERE ShaleClientId=? AND Id=?")){p.setInt(1,t);p.setLong(2,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Record is not available.");return new DateRow(r.getLong(1),r.getInt(2),r.getInt(3),r.getTimestamp(4),r.getTimestamp(5),r.getBoolean(6),r.getBoolean(7),r.getBytes(8));}}}
    private static EventRow lockEvent(Connection c,int t,int id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT CalendarEventId,CaseId,CalendarEventTypeId,CaseDateId,StartsAt,EndsAt,AllDay,IsCancelled,RowVer FROM dbo.CalendarEvents WITH(UPDLOCK,HOLDLOCK) WHERE ShaleClientId=? AND CalendarEventId=?")){p.setInt(1,t);p.setInt(2,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Record is not available.");return event(r);}}}
    private static EventRow lockLinkedEvent(Connection c,int t,long dateId)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT CalendarEventId,CaseId,CalendarEventTypeId,CaseDateId,StartsAt,EndsAt,AllDay,IsCancelled,RowVer FROM dbo.CalendarEvents WITH(UPDLOCK,HOLDLOCK) WHERE ShaleClientId=? AND CaseDateId=?")){p.setInt(1,t);p.setLong(2,dateId);try(ResultSet r=p.executeQuery()){if(!r.next())return null;EventRow e=event(r);if(r.next())throw new IllegalStateException("Case Date has multiple linked Calendar events.");return e;}}}
    private static EventRow event(ResultSet r)throws SQLException{return new EventRow(r.getInt(1),(Integer)r.getObject(2),r.getInt(3),(Long)r.getObject(4),r.getTimestamp(5),r.getTimestamp(6),r.getBoolean(7),r.getBoolean(8),r.getBytes(9));}
    private static int insertEvent(Connection c,int t,int a,DateRow d,int type)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT dbo.CalendarEvents(ShaleClientId,CalendarEventTypeId,CaseId,Title,StartsAt,EndsAt,AllDay,SourceType,IsCompleted,IsCancelled,CreatedByUserId,CaseDateId) OUTPUT INSERTED.CalendarEventId VALUES(?,?,?,(SELECT Name FROM dbo.CalendarEventTypes WHERE CalendarEventTypeId=?),?,?,?,'CASE_DATE',0,0,?,?)")){p.setInt(1,t);p.setInt(2,type);p.setInt(3,d.caseId);p.setInt(4,type);p.setTimestamp(5,d.starts);p.setTimestamp(6,d.ends);p.setBoolean(7,d.allDay);p.setInt(8,a);p.setLong(9,d.id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Synchronized Calendar event was not created.");return r.getInt(1);}}}
    private static long insertDate(Connection c,int t,int a,EventRow e,int type)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT dbo.CaseDates(ShaleClientId,CaseId,CaseDateTypeId,StartsAt,EndsAt,AllDay,CreatedAt,CreatedByUserId) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,SYSUTCDATETIME(),?)")){p.setInt(1,t);p.setInt(2,e.caseId);p.setInt(3,type);p.setTimestamp(4,e.starts);p.setTimestamp(5,e.ends);p.setBoolean(6,e.allDay);p.setInt(7,a);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Synchronized Case Date was not created.");return r.getLong(1);}}}
    private static boolean updateEventProjection(Connection c,EventRow e,int type,DateRow d,boolean cancelled)throws SQLException{if(e.eventTypeId==type&&same(e.starts,d.starts)&&same(e.ends,d.ends)&&e.allDay==d.allDay&&e.cancelled==cancelled)return false;try(PreparedStatement p=c.prepareStatement("UPDATE dbo.CalendarEvents SET CalendarEventTypeId=?,StartsAt=?,EndsAt=?,AllDay=?,IsCancelled=?,UpdatedAt=SYSUTCDATETIME() WHERE CalendarEventId=? AND RowVer=?")){p.setInt(1,type);p.setTimestamp(2,d.starts);p.setTimestamp(3,d.ends);p.setBoolean(4,d.allDay);p.setBoolean(5,cancelled);p.setInt(6,e.id);p.setBytes(7,e.rowVer);changed(p);return true;}}
    private static boolean updateDateProjection(Connection c,DateRow d,int type,EventRow e,int a,boolean deleted)throws SQLException{if(d.typeId==type&&same(d.starts,e.starts)&&same(d.ends,e.ends)&&d.allDay==e.allDay&&d.deleted==deleted)return false;try(PreparedStatement p=c.prepareStatement("UPDATE dbo.CaseDates SET CaseDateTypeId=?,StartsAt=?,EndsAt=?,AllDay=?,IsDeleted=?,DeletedAt=CASE WHEN ?=1 THEN COALESCE(DeletedAt,SYSUTCDATETIME()) ELSE NULL END,DeletedByUserId=CASE WHEN ?=1 THEN COALESCE(DeletedByUserId,?) ELSE NULL END,UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? WHERE Id=? AND RowVer=?")){p.setInt(1,type);p.setTimestamp(2,e.starts);p.setTimestamp(3,e.ends);p.setBoolean(4,e.allDay);p.setBoolean(5,deleted);p.setBoolean(6,deleted);p.setBoolean(7,deleted);p.setInt(8,a);p.setInt(9,a);p.setLong(10,d.id);p.setBytes(11,d.rowVer);changed(p);return true;}}
    private static void updateEventLifecycle(Connection c,EventRow e,boolean deleted,int a)throws SQLException{try(PreparedStatement p=c.prepareStatement("UPDATE dbo.CalendarEvents SET IsCancelled=?,UpdatedAt=SYSUTCDATETIME() WHERE CalendarEventId=? AND RowVer=?")){p.setBoolean(1,deleted);p.setInt(2,e.id);p.setBytes(3,e.rowVer);changed(p);}}
    private static void updateDateLifecycle(Connection c,DateRow d,boolean deleted,int a)throws SQLException{try(PreparedStatement p=c.prepareStatement("UPDATE dbo.CaseDates SET IsDeleted=?,DeletedAt=CASE WHEN ?=1 THEN SYSUTCDATETIME() ELSE NULL END,DeletedByUserId=CASE WHEN ?=1 THEN ? ELSE NULL END,UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? WHERE Id=? AND RowVer=?")){p.setBoolean(1,deleted);p.setBoolean(2,deleted);p.setBoolean(3,deleted);p.setInt(4,a);p.setInt(5,a);p.setLong(6,d.id);p.setBytes(7,d.rowVer);changed(p);}}
    private static void unlink(Connection c,int t,EventRow e)throws SQLException{try(PreparedStatement p=c.prepareStatement("UPDATE dbo.CalendarEvents SET CaseDateId=NULL,UpdatedAt=SYSUTCDATETIME() WHERE ShaleClientId=? AND CalendarEventId=? AND RowVer=?")){p.setInt(1,t);p.setInt(2,e.id);p.setBytes(3,e.rowVer);changed(p);}}
    private static void link(Connection c,int t,EventRow e,long date)throws SQLException{try(PreparedStatement p=c.prepareStatement("UPDATE dbo.CalendarEvents SET CaseDateId=?,UpdatedAt=SYSUTCDATETIME() WHERE ShaleClientId=? AND CalendarEventId=? AND RowVer=? AND CaseDateId IS NULL")){p.setLong(1,date);p.setInt(2,t);p.setInt(3,e.id);p.setBytes(4,e.rowVer);changed(p);}}
    private static void changed(PreparedStatement p)throws SQLException{if(p.executeUpdate()!=1)throw new IllegalStateException("Synchronized record changed; reload and try again.");}
    private static boolean same(Object a,Object b){return java.util.Objects.equals(a,b);}
    private static int authenticatedActor(Connection c,int tenant)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId'))");ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Authenticated SQL session context is required.");int actor=r.getInt(1);if(actor<=0||r.wasNull())throw new IllegalStateException("Authenticated SQL session context is required.");try(PreparedStatement u=c.prepareStatement("SELECT 1 FROM dbo.Users WHERE id=? AND ShaleClientId=? AND COALESCE(is_deleted,0)=0 AND COALESCE(IsRemoved,0)=0")){u.setInt(1,actor);u.setInt(2,tenant);try(ResultSet ur=u.executeQuery()){if(!ur.next())throw new IllegalStateException("Authenticated SQL session context is required.");}}return actor;}}
    private static boolean lastSynchronizationAction(Connection c,int tenant,String entityType,long entityId,String direction,String action)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT TOP (1) Action,Metadata FROM dbo.EntityActionAuditLog WHERE ShaleClientId=? AND EntityType=? AND EntityId=? ORDER BY OccurredAt DESC,Id DESC")){p.setInt(1,tenant);p.setString(2,entityType);p.setLong(3,entityId);try(ResultSet r=p.executeQuery()){if(!r.next())return false;String metadata=r.getString(2);return action.equals(r.getString(1))&&metadata!=null&&metadata.contains("\\\"SYNCHRONIZATION_DIRECTION\\\":\\\""+direction+"\\\"");}}}
    private void audit(Connection c,int t,int a,EntityActionAuditEvent.EntityType et,long id,EntityActionAuditEvent.Action action,int caseId,long dateId,String direction)throws SQLException{var m=new EnumMap<EntityActionAuditEvent.MetadataKey,Object>(EntityActionAuditEvent.MetadataKey.class);m.put(EntityActionAuditEvent.MetadataKey.CASE_ID,caseId);m.put(EntityActionAuditEvent.MetadataKey.CASE_DATE_ID,dateId);m.put(EntityActionAuditEvent.MetadataKey.SYNCHRONIZATION_DIRECTION,direction);audit.append(c,EntityActionAuditEvent.now(t,a,et,id,action,EntityActionAuditEvent.EntityType.CASE,(long)caseId,m));}
    private record Mapping(int eventTypeId,int dateTypeId){}
    private record DateRow(long id,int caseId,int typeId,Timestamp starts,Timestamp ends,boolean allDay,boolean deleted,byte[] rowVer){}
    private record EventRow(int id,Integer caseId,int eventTypeId,Long caseDateId,Timestamp starts,Timestamp ends,boolean allDay,boolean cancelled,byte[] rowVer){}
}
