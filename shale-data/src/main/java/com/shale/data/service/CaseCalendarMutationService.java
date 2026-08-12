package com.shale.data.service;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.data.dao.EntityActionAuditDao;
import com.shale.data.dao.EntityActionAuditEvent;
import java.sql.*;
import java.util.Arrays;
import java.util.Objects;

/** Transactional, UI-neutral foundation for explicit CaseDate/Calendar linkage. */
public final class CaseCalendarMutationService {
    public interface TrustedActorContext { int actorUserId(); }
    public record LinkCommand(long caseDateId, int calendarEventId, byte[] expectedCaseDateRowVer, byte[] expectedCalendarEventRowVer) {
        public LinkCommand { expectedCaseDateRowVer=copy(expectedCaseDateRowVer); expectedCalendarEventRowVer=copy(expectedCalendarEventRowVer); }
        @Override public byte[] expectedCaseDateRowVer(){return copy(expectedCaseDateRowVer);}
        @Override public byte[] expectedCalendarEventRowVer(){return copy(expectedCalendarEventRowVer);}
        private static byte[] copy(byte[] v){return v==null?null:v.clone();}
    }
    private final DbSessionProvider db; private final TrustedActorContext actor; private final EntityActionAuditDao audit=new EntityActionAuditDao();
    public CaseCalendarMutationService(DbSessionProvider db, TrustedActorContext actor){this.db=Objects.requireNonNull(db);this.actor=Objects.requireNonNull(actor);}

    public void link(LinkCommand command){
        Objects.requireNonNull(command,"command"); requireId(command.caseDateId(),"caseDateId"); requireId(command.calendarEventId(),"calendarEventId"); requireRowVer(command.expectedCaseDateRowVer());requireRowVer(command.expectedCalendarEventRowVer());
        try(Connection con=db.requireConnection()){
            int tenant=sessionTenant(con), actorId=actor.actorUserId(); validateActor(con,tenant,actorId); con.setAutoCommit(false);
            try {
                DateRow date=date(con,tenant,command.caseDateId()); EventRow event=event(con,tenant,command.calendarEventId());
                if(date.deleted || event.cancelled) throw new IllegalStateException("Deleted or cancelled records cannot be linked.");
                if(!Arrays.equals(date.rowVer,command.expectedCaseDateRowVer()) || !Arrays.equals(event.rowVer,command.expectedCalendarEventRowVer())) throw new IllegalStateException("Record has changed. Reload and try again.");
                if(event.caseDateId!=null && event.caseDateId!=command.caseDateId()) throw new IllegalStateException("Calendar event is already linked.");
                if(event.caseId==null || event.caseId!=date.caseId) throw new IllegalArgumentException("Linked records must belong to the same Case.");
                requireActiveMapping(con,tenant,event.eventTypeId,date.dateTypeId);
                try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.CalendarEvents SET CaseDateId=?,UpdatedAt=SYSUTCDATETIME() WHERE ShaleClientId=? AND CalendarEventId=? AND RowVer=? AND CaseDateId IS NULL")){
                    ps.setLong(1,command.caseDateId());ps.setInt(2,tenant);ps.setInt(3,command.calendarEventId());ps.setBytes(4,command.expectedCalendarEventRowVer());if(ps.executeUpdate()!=1)throw new IllegalStateException("Calendar event has changed or is already linked.");
                }
                audit.append(con,EntityActionAuditEvent.now(tenant,actorId,EntityActionAuditEvent.EntityType.CASE_DATE,command.caseDateId(),EntityActionAuditEvent.Action.LINKED,EntityActionAuditEvent.EntityType.CASE,(long)date.caseId,java.util.Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID,date.caseId,EntityActionAuditEvent.MetadataKey.CALENDAR_EVENT_ID,command.calendarEventId())));
                con.commit();
            } catch(SQLException e){con.rollback();throw e;} catch(RuntimeException e){con.rollback();throw e;}
        }catch(SQLException e){throw new RuntimeException("Failed to link Case Date and Calendar event",e);}
    }
    private static int sessionTenant(Connection c)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT CAST(SESSION_CONTEXT(N'ShaleClientId') AS int)")){if(!r.next()){throw new IllegalStateException("Trusted tenant context is missing.");}int id=r.getInt(1);if(r.wasNull()||id<=0)throw new IllegalStateException("Trusted tenant context is missing.");return id;}}
    private static void validateActor(Connection c,int t,int a)throws SQLException{if(a<=0)throw new IllegalStateException("Trusted actor context is missing.");try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM dbo.Users WHERE id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0")){p.setInt(1,a);p.setInt(2,t);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Trusted actor is not active in the session tenant.");}}}
    private static DateRow date(Connection c,int t,long id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT CaseId,CaseDateTypeId,IsDeleted,RowVer FROM dbo.CaseDates WITH(UPDLOCK,HOLDLOCK) WHERE ShaleClientId=? AND Id=?")){p.setInt(1,t);p.setLong(2,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Case Date not found.");return new DateRow(r.getInt(1),r.getInt(2),r.getBoolean(3),r.getBytes(4));}}}
    private static EventRow event(Connection c,int t,int id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT CaseId,CalendarEventTypeId,CaseDateId,IsCancelled,RowVer FROM dbo.CalendarEvents WITH(UPDLOCK,HOLDLOCK) WHERE ShaleClientId=? AND CalendarEventId=?")){p.setInt(1,t);p.setInt(2,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Calendar event not found.");return new EventRow((Integer)r.getObject(1),r.getInt(2),(Long)r.getObject(3),r.getBoolean(4),r.getBytes(5));}}}
    private static void requireActiveMapping(Connection c,int t,int et,int dt)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM dbo.CalendarCaseDateTypeMappings WHERE ShaleClientId=? AND CalendarEventTypeId=? AND CaseDateTypeId=? AND IsActive=1")){p.setInt(1,t);p.setInt(2,et);p.setInt(3,dt);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Event Type and Case Date Type are not explicitly mapped.");}}}
    private static void requireId(long v,String n){if(v<=0)throw new IllegalArgumentException(n+" must be > 0");} private static void requireRowVer(byte[] v){if(v==null||v.length==0)throw new IllegalArgumentException("Expected RowVer is required.");}
    private record DateRow(int caseId,int dateTypeId,boolean deleted,byte[] rowVer){} private record EventRow(Integer caseId,int eventTypeId,Long caseDateId,boolean cancelled,byte[] rowVer){}
}
