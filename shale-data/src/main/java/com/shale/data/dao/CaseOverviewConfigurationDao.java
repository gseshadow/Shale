package com.shale.data.dao;

import com.shale.core.dto.CaseOverviewDateConfigurationDto;
import com.shale.core.dto.CaseOverviewAdministrationDto;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.CaseServicePort.IntakeTakenByMutationResult;
import com.shale.core.service.CaseServicePort.ReplaceCaseOverviewDateConfigurationCommand;
import com.shale.core.service.CaseServicePort.UpdateIntakeTakenByCommand;
import com.shale.core.service.CaseServicePort.UpdateCaseOverviewCommand;
import com.shale.core.service.CaseServicePort.CaseOverviewMutationResult;
import java.sql.*;
import java.util.*;

/** Transaction owner for the non-UI Case Overview administration foundation. */
public final class CaseOverviewConfigurationDao {
    private static final List<String> DEFAULT_KEYS = List.of("date_of_injury", "date_of_medical_negligence",
            "intake", "statute_of_limitations", "tort_notice_deadline");
    private final DbSessionProvider db;
    private final CaseDateDao caseDates;
    private final EntityActionAuditDao audits;

    public CaseOverviewConfigurationDao(DbSessionProvider db) {
        this(db, new CaseDateDao(db), new EntityActionAuditDao());
    }
    CaseOverviewConfigurationDao(DbSessionProvider db, CaseDateDao caseDates, EntityActionAuditDao audits) {
        this.db=Objects.requireNonNull(db); this.caseDates=Objects.requireNonNull(caseDates); this.audits=Objects.requireNonNull(audits);
    }

    public CaseOverviewDateConfigurationDto get(long caseId,int tenant,int actor) {
        List<EffectiveCaseDateTypeDto> effective=caseDates.listEffectiveCaseDateTypes(tenant,actor);
        try(Connection con=db.requireConnection()) {
            verifyTenant(con,tenant); validateActor(con,tenant,actor); validateCase(con,tenant,caseId);
            Config config=findConfig(con,tenant,caseId);
            if(config==null) return new CaseOverviewDateConfigurationDto(caseId,false,defaults(effective),null);
            return new CaseOverviewDateConfigurationDto(caseId,true,readSelected(con,tenant,config.id,effective),config.rowVer);
        } catch(SQLException e){throw failure(e);}
    }

    public CaseOverviewAdministrationDto getAdministration(long caseId,int tenant,int actor) {
        List<EffectiveCaseDateTypeDto> effective=caseDates.listEffectiveCaseDateTypes(tenant,actor);
        try(Connection con=db.requireConnection()) {
            verifyTenant(con,tenant); validateAdmin(con,tenant,actor);
            CaseIntake intake=readIntake(con,tenant,caseId); Config config=findConfig(con,tenant,caseId);
            CaseOverviewDateConfigurationDto resolved=config==null
                    ?new CaseOverviewDateConfigurationDto(caseId,false,defaults(effective),null)
                    :new CaseOverviewDateConfigurationDto(caseId,true,readSelected(con,tenant,config.id,effective),config.rowVer);
            return administration(resolved,effective,intake);
        } catch(SQLException e){throw failure(e);}
    }

    /** One transaction and one authorization boundary for the dialog's single Save action. */
    public CaseOverviewMutationResult update(UpdateCaseOverviewCommand c) {
        Objects.requireNonNull(c); rejectDuplicates(c.orderedCaseDateTypeIds()); requireRowVer(c.expectedCaseRowVer());
        if(!c.layoutChanged()&&!c.intakeTakenByChanged())
            return new CaseOverviewMutationResult(getAdministration(c.caseId(),c.shaleClientId(),c.actorUserId()),false,false,false);
        try(Connection con=db.requireConnection()) {
            verifyTenant(con,c.shaleClientId()); validateAdmin(con,c.shaleClientId(),c.actorUserId()); con.setAutoCommit(false);
            try {
                CaseIntake before=readIntake(con,c.shaleClientId(),c.caseId());
                if(!Arrays.equals(before.rowVer,c.expectedCaseRowVer())) throw new IllegalStateException("Case changed. Refresh and reopen Edit Overview.");
                Config old=findConfig(con,c.shaleClientId(),c.caseId());
                if(c.layoutChanged()) {
                    if(old==null&&c.expectedConfigurationRowVer()!=null || old!=null&&!Arrays.equals(old.rowVer,c.expectedConfigurationRowVer()))
                        throw new IllegalStateException("Case Overview configuration changed. Refresh and reopen Edit Overview.");
                    Set<Integer> retained=old==null?Set.of():new HashSet<>(readSelectedIds(con,c.shaleClientId(),old.id));
                    validateSelections(con,c.shaleClientId(),c.orderedCaseDateTypeIds(),retained);
                    ReplaceCaseOverviewDateConfigurationCommand layout=new ReplaceCaseOverviewDateConfigurationCommand(c.shaleClientId(),c.actorUserId(),c.caseId(),c.orderedCaseDateTypeIds(),c.expectedConfigurationRowVer());
                    long configId=old==null?insertConfig(con,layout):updateConfig(con,layout,old);
                    replaceSelections(con,layout,configId);
                    audits.append(con,EntityActionAuditEvent.now(c.shaleClientId(),c.actorUserId(),EntityActionAuditEvent.EntityType.CASE_OVERVIEW_CONFIGURATION,configId,old==null?EntityActionAuditEvent.Action.CREATED:EntityActionAuditEvent.Action.UPDATED,EntityActionAuditEvent.EntityType.CASE,c.caseId(),Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID,c.caseId(),EntityActionAuditEvent.MetadataKey.ORDERING_COUNT,c.orderedCaseDateTypeIds().size())));
                }
                User target=null;
                if(c.intakeTakenByChanged()) {
                    if(Objects.equals(before.userId,c.intakeTakenByUserId())) throw new IllegalArgumentException("Intake By change did not match the authoritative value.");
                    target=c.intakeTakenByUserId()==null?null:requireAssignableUser(con,c.shaleClientId(),c.intakeTakenByUserId());
                }
                updateCaseOnce(con,c,before,target);
                if(c.intakeTakenByChanged()) {
                    String next=target==null?"Unknown":target.name, prior=before.name==null||before.name.isBlank()?"Unknown":before.name;
                    CaseTimelineWriter.append(con,c.caseId(),c.shaleClientId(),c.actorUserId(),"INTAKE_TAKEN_BY_CHANGED","Intake By changed from "+prior+" to "+next,null);
                    audits.append(con,EntityActionAuditEvent.now(c.shaleClientId(),c.actorUserId(),EntityActionAuditEvent.EntityType.CASE,c.caseId(),EntityActionAuditEvent.Action.UPDATED,null,null,Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID,c.caseId())));
                }
                con.commit();
            } catch(Exception e){con.rollback();if(e instanceof RuntimeException r)throw r;throw new IllegalStateException("Case Overview mutation failed.",e);} finally {con.setAutoCommit(true);}
        } catch(SQLException e){throw failure(e);}
        return new CaseOverviewMutationResult(getAdministration(c.caseId(),c.shaleClientId(),c.actorUserId()),true,c.layoutChanged(),c.intakeTakenByChanged());
    }

    public CaseOverviewDateConfigurationDto replace(ReplaceCaseOverviewDateConfigurationCommand c) {
        Objects.requireNonNull(c); rejectDuplicates(c.orderedCaseDateTypeIds());
        try(Connection con=db.requireConnection()) {
            verifyTenant(con,c.shaleClientId()); validateAdmin(con,c.shaleClientId(),c.actorUserId()); validateCase(con,c.shaleClientId(),c.caseId());
            con.setAutoCommit(false);
            try {
                Config old=findConfig(con,c.shaleClientId(),c.caseId());
                if(old==null && c.expectedRowVer()!=null) throw new IllegalStateException("Case Overview configuration changed.");
                if(old!=null && !Arrays.equals(old.rowVer,c.expectedRowVer())) throw new IllegalStateException("Case Overview configuration changed.");
                validateSelections(con,c.shaleClientId(),c.orderedCaseDateTypeIds(),Set.of());
                long id=old==null?insertConfig(con,c):updateConfig(con,c,old);
                try(PreparedStatement ps=con.prepareStatement("DELETE FROM dbo.CaseOverviewDateSelections WHERE CaseOverviewConfigurationId=? AND ShaleClientId=?")){ps.setLong(1,id);ps.setInt(2,c.shaleClientId());ps.executeUpdate();}
                try(PreparedStatement ps=con.prepareStatement("INSERT dbo.CaseOverviewDateSelections(ShaleClientId,CaseOverviewConfigurationId,CaseDateTypeId,SortOrder,CreatedAt,CreatedByUserId) VALUES(?,?,?,?,SYSUTCDATETIME(),?)")){
                    for(int i=0;i<c.orderedCaseDateTypeIds().size();i++){ps.setInt(1,c.shaleClientId());ps.setLong(2,id);ps.setInt(3,c.orderedCaseDateTypeIds().get(i));ps.setInt(4,i);ps.setInt(5,c.actorUserId());ps.addBatch();} ps.executeBatch();
                }
                touchCase(con,c.caseId(),c.shaleClientId());
                audits.append(con,EntityActionAuditEvent.now(c.shaleClientId(),c.actorUserId(),EntityActionAuditEvent.EntityType.CASE_OVERVIEW_CONFIGURATION,id,old==null?EntityActionAuditEvent.Action.CREATED:EntityActionAuditEvent.Action.UPDATED,EntityActionAuditEvent.EntityType.CASE,c.caseId(),Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID,c.caseId(),EntityActionAuditEvent.MetadataKey.ORDERING_COUNT,c.orderedCaseDateTypeIds().size())));
                con.commit();
            } catch(Exception e){con.rollback(); if(e instanceof RuntimeException r)throw r; throw new IllegalStateException("Case Overview mutation failed.",e);} finally {con.setAutoCommit(true);}
        } catch(SQLException e){throw failure(e);}
        return get(c.caseId(),c.shaleClientId(),c.actorUserId());
    }

    public IntakeTakenByMutationResult updateIntakeTakenBy(UpdateIntakeTakenByCommand c) {
        Objects.requireNonNull(c); requireRowVer(c.expectedCaseRowVer());
        try(Connection con=db.requireConnection()) {
            verifyTenant(con,c.shaleClientId()); validateAdmin(con,c.shaleClientId(),c.actorUserId());
            con.setAutoCommit(false);
            try {
                CaseIntake before=readIntake(con,c.shaleClientId(),c.caseId());
                if(!Arrays.equals(before.rowVer,c.expectedCaseRowVer())) throw new IllegalStateException("Case changed.");
                if(Objects.equals(before.userId,c.intakeTakenByUserId())) { con.rollback(); return new IntakeTakenByMutationResult(c.caseId(),before.userId,before.name,before.rowVer,false); }
                User target=c.intakeTakenByUserId()==null?null:requireAssignableUser(con,c.shaleClientId(),c.intakeTakenByUserId());
                try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.Cases SET IntakeTakenByUserId=?,UpdatedAt=SYSDATETIME() WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0 AND RowVer=?")){
                    if(target==null)ps.setNull(1,Types.INTEGER);else ps.setInt(1,target.id); ps.setLong(2,c.caseId());ps.setInt(3,c.shaleClientId());ps.setBytes(4,c.expectedCaseRowVer());if(ps.executeUpdate()!=1)throw new IllegalStateException("Case changed.");
                }
                String next=target==null?"Unknown":target.name; String prior=before.name==null||before.name.isBlank()?"Unknown":before.name;
                CaseTimelineWriter.append(con,c.caseId(),c.shaleClientId(),c.actorUserId(),"INTAKE_TAKEN_BY_CHANGED","Intake By changed from "+prior+" to "+next,null);
                audits.append(con,EntityActionAuditEvent.now(c.shaleClientId(),c.actorUserId(),EntityActionAuditEvent.EntityType.CASE,c.caseId(),EntityActionAuditEvent.Action.UPDATED,null,null,Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID,c.caseId())));
                con.commit();
            } catch(Exception e){con.rollback(); if(e instanceof RuntimeException r)throw r; throw new IllegalStateException("Case Overview mutation failed.",e);} finally {con.setAutoCommit(true);}
        } catch(SQLException e){throw failure(e);}
        try(Connection con=db.requireConnection()){verifyTenant(con,c.shaleClientId());CaseIntake v=readIntake(con,c.shaleClientId(),c.caseId());return new IntakeTakenByMutationResult(c.caseId(),v.userId,v.name,v.rowVer,true);}catch(SQLException e){throw failure(e);}
    }

    static void rejectDuplicates(List<Integer> ids){if(ids==null)throw new IllegalArgumentException("orderedCaseDateTypeIds is required.");if(new HashSet<>(ids).size()!=ids.size()||ids.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Duplicate Case Date Types are not allowed.");}
    static List<EffectiveCaseDateTypeDto> defaults(List<EffectiveCaseDateTypeDto> types){Map<String,EffectiveCaseDateTypeDto> byKey=new HashMap<>();for(var t:types)if(t.systemKey()!=null)byKey.put(t.systemKey().toLowerCase(Locale.ROOT),t);return DEFAULT_KEYS.stream().map(byKey::get).filter(Objects::nonNull).toList();}
    private record Config(long id,byte[] rowVer){} private record CaseIntake(Integer userId,String name,boolean active,byte[] rowVer){} private record User(int id,String name){}
    private static CaseOverviewAdministrationDto administration(CaseOverviewDateConfigurationDto config,List<EffectiveCaseDateTypeDto> types,CaseIntake intake){return new CaseOverviewAdministrationDto(config,types,intake.userId,intake.name,intake.userId==null||intake.active,intake.rowVer);}
    private static Config findConfig(Connection c,int t,long caseId)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT Id,RowVer FROM dbo.CaseOverviewConfigurations WHERE ShaleClientId=? AND CaseId=?")){p.setInt(1,t);p.setLong(2,caseId);try(ResultSet r=p.executeQuery()){return r.next()?new Config(r.getLong(1),r.getBytes(2)):null;}}}
    private static List<EffectiveCaseDateTypeDto> readSelected(Connection c,int t,long id,List<EffectiveCaseDateTypeDto> effective)throws SQLException{Map<Integer,EffectiveCaseDateTypeDto> map=new HashMap<>();effective.forEach(x->map.put(x.id(),x));List<EffectiveCaseDateTypeDto> out=new ArrayList<>();for(int typeId:readSelectedIds(c,t,id)){var x=map.get(typeId);out.add(x==null?historicalType(c,t,typeId):x);}return List.copyOf(out);}
    private static List<Integer> readSelectedIds(Connection c,int t,long id)throws SQLException{List<Integer> out=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT CaseDateTypeId FROM dbo.CaseOverviewDateSelections WHERE ShaleClientId=? AND CaseOverviewConfigurationId=? ORDER BY SortOrder,Id")){p.setInt(1,t);p.setLong(2,id);try(ResultSet r=p.executeQuery()){while(r.next())out.add(r.getInt(1));}}return out;}
    private static EffectiveCaseDateTypeDto historicalType(Connection c,int t,int id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT Id,ShaleClientId,SystemKey,Name,Description,CalendarCategory,Color,SupportsTime,SortOrder,IsActive,IsDeleted,RowVer FROM dbo.CaseDateTypes WHERE Id=? AND (ShaleClientId=? OR ShaleClientId IS NULL)")){p.setInt(1,id);p.setInt(2,t);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Configured Case Date Type is unavailable.");Integer owner=getNullableInt(r,2);return new EffectiveCaseDateTypeDto(r.getInt(1),owner,r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getBoolean(8),r.getInt(9),r.getBoolean(10),r.getBoolean(11),owner==null?EffectiveCaseDateTypeDto.Origin.GLOBAL:EffectiveCaseDateTypeDto.Origin.TENANT_CREATED,r.getBytes(12));}}}
    private static void validateSelections(Connection c,int t,List<Integer> ids,Set<Integer> retainedHistorical)throws SQLException{for(int id:ids){if(retainedHistorical.contains(id))continue;try(PreparedStatement p=c.prepareStatement("""
            WITH visible AS (SELECT x.Id,ROW_NUMBER() OVER(PARTITION BY x.SystemKey ORDER BY CASE WHEN x.ShaleClientId=? AND x.IsDeleted=0 THEN 0 ELSE 1 END,x.Id) rn
            FROM dbo.CaseDateTypes x WHERE (x.ShaleClientId=? OR (x.ShaleClientId IS NULL AND EXISTS(SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings m WHERE m.CaseDateTypeId=x.Id AND m.ShaleClientId IS NULL AND m.IsActive=1 AND m.IsDeleted=0))) AND x.SystemKey IS NOT NULL
            UNION ALL SELECT x.Id,1 FROM dbo.CaseDateTypes x WHERE x.ShaleClientId=? AND x.SystemKey IS NULL AND x.IsActive=1 AND x.IsDeleted=0)
            SELECT 1 FROM visible v JOIN dbo.CaseDateTypes x ON x.Id=v.Id WHERE v.Id=? AND v.rn=1 AND x.IsActive=1 AND x.IsDeleted=0
            """)){p.setInt(1,t);p.setInt(2,t);p.setInt(3,t);p.setInt(4,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("A selected Case Date Type is not effective for this tenant.");}}}}
    private static long insertConfig(Connection con,ReplaceCaseOverviewDateConfigurationCommand c)throws SQLException{try(PreparedStatement p=con.prepareStatement("INSERT dbo.CaseOverviewConfigurations(ShaleClientId,CaseId,CreatedAt,CreatedByUserId) OUTPUT INSERTED.Id VALUES(?,?,SYSUTCDATETIME(),?)")){p.setInt(1,c.shaleClientId());p.setLong(2,c.caseId());p.setInt(3,c.actorUserId());try(ResultSet r=p.executeQuery()){r.next();return r.getLong(1);}}}
    private static long updateConfig(Connection con,ReplaceCaseOverviewDateConfigurationCommand c,Config old)throws SQLException{try(PreparedStatement p=con.prepareStatement("UPDATE dbo.CaseOverviewConfigurations SET UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? WHERE Id=? AND ShaleClientId=? AND CaseId=? AND RowVer=?")){p.setInt(1,c.actorUserId());p.setLong(2,old.id);p.setInt(3,c.shaleClientId());p.setLong(4,c.caseId());p.setBytes(5,c.expectedRowVer());if(p.executeUpdate()!=1)throw new IllegalStateException("Case Overview configuration changed.");return old.id;}}
    private static void replaceSelections(Connection con,ReplaceCaseOverviewDateConfigurationCommand c,long id)throws SQLException{try(PreparedStatement ps=con.prepareStatement("DELETE FROM dbo.CaseOverviewDateSelections WHERE CaseOverviewConfigurationId=? AND ShaleClientId=?")){ps.setLong(1,id);ps.setInt(2,c.shaleClientId());ps.executeUpdate();}try(PreparedStatement ps=con.prepareStatement("INSERT dbo.CaseOverviewDateSelections(ShaleClientId,CaseOverviewConfigurationId,CaseDateTypeId,SortOrder,CreatedAt,CreatedByUserId) VALUES(?,?,?,?,SYSUTCDATETIME(),?)")){for(int i=0;i<c.orderedCaseDateTypeIds().size();i++){ps.setInt(1,c.shaleClientId());ps.setLong(2,id);ps.setInt(3,c.orderedCaseDateTypeIds().get(i));ps.setInt(4,i);ps.setInt(5,c.actorUserId());ps.addBatch();}ps.executeBatch();}}
    private static CaseIntake readIntake(Connection c,int t,long id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT x.IntakeTakenByUserId,LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))),CASE WHEN u.id IS NULL OR ISNULL(u.is_deleted,0)=1 OR ISNULL(u.IsRemoved,0)=1 THEN 0 ELSE 1 END,x.RowVer FROM dbo.Cases x LEFT JOIN dbo.Users u ON u.id=x.IntakeTakenByUserId AND u.ShaleClientId=x.ShaleClientId WHERE x.Id=? AND x.ShaleClientId=? AND ISNULL(x.IsDeleted,0)=0")){p.setLong(1,id);p.setInt(2,t);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Case is not available for this tenant.");return new CaseIntake(getNullableInt(r,1),r.getString(2),r.getBoolean(3),r.getBytes(4));}}}
    private static void updateCaseOnce(Connection con,UpdateCaseOverviewCommand c,CaseIntake before,User target)throws SQLException{String sql=c.intakeTakenByChanged()?"UPDATE dbo.Cases SET IntakeTakenByUserId=?,UpdatedAt=SYSDATETIME() WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0 AND RowVer=?":"UPDATE dbo.Cases SET UpdatedAt=SYSDATETIME() WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0 AND RowVer=?";try(PreparedStatement p=con.prepareStatement(sql)){int i=1;if(c.intakeTakenByChanged()){if(target==null)p.setNull(i++,Types.INTEGER);else p.setInt(i++,target.id);}p.setLong(i++,c.caseId());p.setInt(i++,c.shaleClientId());p.setBytes(i,before.rowVer);if(p.executeUpdate()!=1)throw new IllegalStateException("Case changed. Refresh and reopen Edit Overview.");}}
    private static User requireAssignableUser(Connection c,int t,int id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT id,LTRIM(RTRIM(CONCAT(name_first,' ',name_last))) FROM dbo.Users WHERE id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0 AND ISNULL(IsRemoved,0)=0")){p.setInt(1,id);p.setInt(2,t);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Intake By user must be an active user in this tenant.");return new User(r.getInt(1),r.getString(2));}}}
    private static void touchCase(Connection c,long id,int t)throws SQLException{try(PreparedStatement p=c.prepareStatement("UPDATE dbo.Cases SET UpdatedAt=SYSDATETIME() WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0")){p.setLong(1,id);p.setInt(2,t);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Case is not available for this tenant.");}}
    private static void verifyTenant(Connection c,int t)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT CAST(SESSION_CONTEXT(N'ShaleClientId') AS INT)" );ResultSet r=p.executeQuery()){if(!r.next()||r.getInt(1)!=t)throw new IllegalStateException("ShaleClientId session context mismatch.");}}
    private static void validateActor(Connection c,int t,int a)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM dbo.Users WHERE id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0")){p.setInt(1,a);p.setInt(2,t);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Actor user is not available for this tenant.");}}}
    private static void validateAdmin(Connection c,int t,int a)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM dbo.Users WHERE id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0 AND ISNULL(IsRemoved,0)=0 AND ISNULL(is_admin,0)=1")){p.setInt(1,a);p.setInt(2,t);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Administrator user is not available for this tenant.");}}}
    private static void validateCase(Connection c,int t,long id)throws SQLException{readIntake(c,t,id);}
    static Integer getNullableInt(ResultSet r,int column)throws SQLException{Object value=r.getObject(column);if(value==null)return null;if(!(value instanceof Number number))throw new SQLException("Expected an integer JDBC value in column "+column+".");long converted=number.longValue();if(converted<Integer.MIN_VALUE||converted>Integer.MAX_VALUE)throw new SQLException("Integer JDBC value is out of range in column "+column+".");return (int)converted;}
    private static void requireRowVer(byte[] v){if(v==null||v.length==0)throw new IllegalArgumentException("expectedCaseRowVer is required.");}
    private static RuntimeException failure(SQLException e){return new IllegalStateException("Case Overview database operation failed.",e);}
}
