package com.shale.data.dao;

import com.shale.core.dto.CaseDateDto;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.CaseServicePort.CreateCaseDateCommand;
import com.shale.core.service.CaseServicePort.UpdateCaseDateCommand;
import com.shale.core.service.CaseServicePort.DeleteCaseDateCommand;
import com.shale.core.service.CaseServicePort.RestoreCaseDateCommand;
import com.shale.core.service.CaseServicePort.CaseDateTypeCommand;
import com.shale.core.service.CaseServicePort.SetCaseDateTypeActiveCommand;
import com.shale.core.service.CaseServicePort.ResetCaseDateTypeOverrideCommand;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class CaseDateDao {
    private final DbSessionProvider db;
    private final PhiAuditService phiAuditService;
    private final EntityActionAuditDao entityActionAuditDao = new EntityActionAuditDao();
    public CaseDateDao(DbSessionProvider db) { this.db = Objects.requireNonNull(db, "db"); this.phiAuditService = new PhiAuditService(new AuditLogDao(db)); }

    public List<EffectiveCaseDateTypeDto> listEffectiveCaseDateTypes(int tenant, int actor) {
        String sql = """
                WITH visible AS (
                  SELECT t.*, CASE WHEN t.ShaleClientId IS NOT NULL AND g.Id IS NOT NULL THEN 'TENANT_OVERRIDE' WHEN t.ShaleClientId IS NOT NULL THEN 'TENANT_CREATED' ELSE 'GLOBAL' END AS Origin,
                         ROW_NUMBER() OVER (PARTITION BY t.SystemKey ORDER BY CASE WHEN t.ShaleClientId = ? AND t.IsDeleted = 0 THEN 0 ELSE 1 END, t.Id) AS rn
                  FROM dbo.CaseDateTypes t
                  LEFT JOIN dbo.CaseDateTypes g ON g.ShaleClientId IS NULL AND g.SystemKey = t.SystemKey
                  WHERE (t.ShaleClientId = ? OR t.ShaleClientId IS NULL) AND t.SystemKey IS NOT NULL
                )
                SELECT Id, ShaleClientId, SystemKey, Name, Description, CalendarCategory, Color, SupportsTime, SortOrder, IsActive, IsDeleted, Origin, RowVer
                FROM visible
                WHERE rn = 1 AND IsDeleted = 0 AND IsActive = 1
                UNION ALL
                SELECT Id, ShaleClientId, SystemKey, Name, Description, CalendarCategory, Color, SupportsTime, SortOrder, IsActive, IsDeleted, 'TENANT_CREATED', RowVer
                FROM dbo.CaseDateTypes
                WHERE ShaleClientId = ? AND SystemKey IS NULL AND IsDeleted = 0 AND IsActive = 1
                ORDER BY SortOrder, Name, Id
                """;
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            verifyTenant(con, tenant); validateActor(con, tenant, actor);
            ps.setInt(1, tenant); ps.setInt(2, tenant); ps.setInt(3, tenant);
            try (ResultSet rs = ps.executeQuery()) { List<EffectiveCaseDateTypeDto> out = new ArrayList<>(); while (rs.next()) out.add(mapType(rs)); return List.copyOf(out); }
        } catch (SQLException e) { throw fail(e); }
    }

    public List<CaseDateDto> listCaseDatesForCase(long caseId, int tenant, int actor) {
        String sql = occurrenceSql("cd.CaseId = ? AND cd.ShaleClientId = ? AND cd.IsDeleted = 0 ORDER BY cd.StartsAt, cd.EndsAt, COALESCE(eff.SortOrder, st.SortOrder), COALESCE(eff.Name, st.Name), cd.Id");
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            verifyTenant(con, tenant); validateActor(con, tenant, actor); validateCase(con, tenant, caseId);
            ps.setInt(1, tenant); ps.setInt(2, tenant); ps.setLong(3, caseId); ps.setInt(4, tenant);
            try (ResultSet rs = ps.executeQuery()) { List<CaseDateDto> out = new ArrayList<>(); while (rs.next()) out.add(mapDate(rs)); return List.copyOf(out); }
        } catch (SQLException e) { throw fail(e); }
    }

    public List<CaseDateDto> listDeletedCaseDatesForCase(long caseId, int tenant, int actor) {
        String sql = occurrenceSql("cd.CaseId = ? AND cd.ShaleClientId = ? AND cd.IsDeleted = 1 ORDER BY cd.StartsAt, cd.EndsAt, COALESCE(eff.SortOrder, st.SortOrder), COALESCE(eff.Name, st.Name), cd.Id");
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            verifyTenant(con, tenant); validateActor(con, tenant, actor); validateCase(con, tenant, caseId);
            ps.setInt(1, tenant); ps.setInt(2, tenant); ps.setLong(3, caseId); ps.setInt(4, tenant);
            try (ResultSet rs = ps.executeQuery()) { List<CaseDateDto> out = new ArrayList<>(); while (rs.next()) out.add(mapDate(rs)); return List.copyOf(out); }
        } catch (SQLException e) { throw fail(e); }
    }

    public Optional<CaseDateDto> getCaseDate(long id, int tenant, int actor) {
        String sql = occurrenceSql("cd.Id = ? AND cd.ShaleClientId = ? AND cd.IsDeleted = 0");
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            verifyTenant(con, tenant); validateActor(con, tenant, actor);
            ps.setInt(1, tenant); ps.setInt(2, tenant); ps.setLong(3, id); ps.setInt(4, tenant);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(mapDate(rs)) : Optional.empty(); }
        } catch (SQLException e) { throw fail(e); }
    }



    public List<EffectiveCaseDateTypeDto> listCaseDateTypesForAdministration(int tenant, int actor) {
        String sql = """
                SELECT t.Id, t.ShaleClientId, t.SystemKey, t.Name, t.Description, t.CalendarCategory, t.Color, t.SupportsTime, t.SortOrder, t.IsActive, t.IsDeleted,
                       CASE WHEN t.ShaleClientId IS NOT NULL AND g.Id IS NOT NULL THEN 'TENANT_OVERRIDE' WHEN t.ShaleClientId IS NOT NULL THEN 'TENANT_CREATED' ELSE 'GLOBAL' END AS Origin,
                       t.RowVer
                FROM dbo.CaseDateTypes t
                LEFT JOIN dbo.CaseDateTypes g ON g.ShaleClientId IS NULL AND g.SystemKey = t.SystemKey
                WHERE t.ShaleClientId IS NULL OR t.ShaleClientId = ?
                ORDER BY t.SortOrder, t.Name, t.Id
                """;
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            verifyTenant(con, tenant); validateAdminActor(con, tenant, actor); ps.setInt(1, tenant);
            try (ResultSet rs = ps.executeQuery()) { List<EffectiveCaseDateTypeDto> out = new ArrayList<>(); while (rs.next()) out.add(mapType(rs)); return List.copyOf(out); }
        } catch (SQLException e) { throw fail(e); }
    }

    public EffectiveCaseDateTypeDto createCaseDateType(CaseDateTypeCommand c) { return mutateType(c.shaleClientId(), c.actorUserId(), con -> insertType(con, c, normalizeSystemKey(c.systemKey()))); }
    public EffectiveCaseDateTypeDto updateCaseDateType(CaseDateTypeCommand c) { return mutateType(c.shaleClientId(), c.actorUserId(), con -> {
        requireExpected(c.expectedRowVer()); EffectiveCaseDateTypeDto e=findType(con,c.id()); requireTypeForTenant(e,c.shaleClientId());
        if(e.shaleClientId()==null){ requireRowVerMatch(e.rowVer(),c.expectedRowVer()); return upsertOverride(con,c,e); }
        return updateTypeRow(con,c,e.id(),c.expectedRowVer(), e.systemKey());
    }); }
    public EffectiveCaseDateTypeDto setCaseDateTypeActive(SetCaseDateTypeActiveCommand c) { return mutateType(c.shaleClientId(), c.actorUserId(), con -> {
        requireExpected(c.expectedRowVer()); EffectiveCaseDateTypeDto e=findType(con,c.id()); requireTypeForTenant(e,c.shaleClientId());
        CaseDateTypeCommand cmd=new CaseDateTypeCommand(e.shaleClientId()==null?null:e.id(),c.shaleClientId(),c.actorUserId(),e.systemKey(),e.name(),e.description(),e.calendarCategory(),e.color(),e.supportsTime(),e.sortOrder(),c.active(),e.shaleClientId()==null?c.expectedRowVer():e.rowVer());
        if(e.shaleClientId()==null){ requireRowVerMatch(e.rowVer(),c.expectedRowVer()); return upsertOverride(con,cmd,e); }
        return updateTypeRow(con,cmd,e.id(),c.expectedRowVer(), e.systemKey());
    }); }
    public void resetCaseDateTypeOverride(ResetCaseDateTypeOverrideCommand c) { mutateType(c.shaleClientId(), c.actorUserId(), con -> { EffectiveCaseDateTypeDto e=findType(con,c.id()); requireTypeForTenant(e,c.shaleClientId()); String key=e.systemKey(); if(key==null||key.isBlank()) key=null; EffectiveCaseDateTypeDto tenant=e.shaleClientId()==null?findTenantTypeByKey(con,c.shaleClientId(),key):e; if(tenant==null||tenant.shaleClientId()==null) throw new IllegalArgumentException("Tenant case date type override is not available."); softDeleteType(con,c.shaleClientId(),c.actorUserId(),tenant.id()); return tenant; }); }


    public CaseDateDto createCaseDate(CreateCaseDateCommand c) {
        try (Connection con = db.requireConnection()) {
            verifyTenant(con, c.shaleClientId()); validateActor(con, c.shaleClientId(), c.actorUserId()); validateCase(con, c.shaleClientId(), c.caseId());
            TypeRow type = requireSelectableType(con, c.shaleClientId(), c.caseDateTypeId()); validateAllDay(type, c.allDay());
            con.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = con.prepareStatement("""
                        INSERT dbo.CaseDates (ShaleClientId, CaseId, CaseDateTypeId, StartsAt, EndsAt, AllDay, Notes, CreatedAt, CreatedByUserId)
                        OUTPUT INSERTED.Id
                        VALUES (?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME(), ?)
                        """)) {
                    ps.setInt(1, c.shaleClientId()); ps.setLong(2, c.caseId()); ps.setInt(3, c.caseDateTypeId()); setLdt(ps,4,c.startsAt()); setLdt(ps,5,c.endsAt()); ps.setBoolean(6,c.allDay()); ps.setString(7, norm(c.notes())); ps.setInt(8,c.actorUserId());
                    try(ResultSet rs=ps.executeQuery()){ if(!rs.next()) throw new IllegalStateException("Case date was not created."); id=rs.getLong(1); }
                }
                touchCase(con, c.caseId(), c.shaleClientId()); audit(con,c.shaleClientId(),c.actorUserId(),c.caseId(),id,EntityActionAuditEvent.Action.CREATED); phiAuditService.auditCreate(con,c.actorUserId(),"CaseDates","StartsAt",id,c.startsAt()); phiAuditService.auditCreate(con,c.actorUserId(),"CaseDates","EndsAt",id,c.endsAt()); phiAuditService.auditCreate(con,c.actorUserId(),"CaseDates","Notes",id,norm(c.notes()));
                CaseDateDto dto = requireDate(con, id, c.shaleClientId()); con.commit(); return dto;
            } catch(Exception e){ con.rollback(); throw e; } finally { con.setAutoCommit(true); }
        } catch (SQLException e) { throw fail(e); }
    }

    public CaseDateDto updateCaseDate(UpdateCaseDateCommand c) {
        try (Connection con = db.requireConnection()) {
            verifyTenant(con, c.shaleClientId()); validateActor(con, c.shaleClientId(), c.actorUserId()); validateCase(con, c.shaleClientId(), c.caseId());
            MutationRow before = requireMutationRow(con,c.shaleClientId(),c.caseId(),c.caseDateId(),false); requireRowVerMatch(before.rowVer,c.expectedRowVer());
            TypeRow type = c.caseDateTypeId()==before.typeId ? requireHistoricalType(con,c.shaleClientId(),c.caseDateTypeId()) : requireSelectableType(con,c.shaleClientId(),c.caseDateTypeId()); validateAllDay(type,c.allDay());
            String notes=norm(c.notes());
            if(before.typeId==c.caseDateTypeId() && Objects.equals(before.startsAt,c.startsAt()) && Objects.equals(before.endsAt,c.endsAt()) && before.allDay==c.allDay() && Objects.equals(before.notes,notes)) return requireDate(con,c.caseDateId(),c.shaleClientId());
            con.setAutoCommit(false);
            try { int rows; try(PreparedStatement ps=con.prepareStatement("""
                    UPDATE dbo.CaseDates SET CaseDateTypeId=?, StartsAt=?, EndsAt=?, AllDay=?, Notes=?, UpdatedAt=SYSUTCDATETIME(), UpdatedByUserId=?
                    WHERE Id=? AND ShaleClientId=? AND CaseId=? AND IsDeleted=0 AND RowVer=?
                    """)){ ps.setInt(1,c.caseDateTypeId()); setLdt(ps,2,c.startsAt()); setLdt(ps,3,c.endsAt()); ps.setBoolean(4,c.allDay()); ps.setString(5,notes); ps.setInt(6,c.actorUserId()); ps.setLong(7,c.caseDateId()); ps.setInt(8,c.shaleClientId()); ps.setLong(9,c.caseId()); ps.setBytes(10,c.expectedRowVer()); rows=ps.executeUpdate(); }
                if(rows!=1) throw new IllegalStateException("Case date changed."); touchCase(con,c.caseId(),c.shaleClientId()); audit(con,c.shaleClientId(),c.actorUserId(),c.caseId(),c.caseDateId(),EntityActionAuditEvent.Action.UPDATED); phiAuditService.auditUpdate(con,c.actorUserId(),"CaseDates","StartsAt",c.caseDateId(),before.startsAt,c.startsAt()); phiAuditService.auditUpdate(con,c.actorUserId(),"CaseDates","EndsAt",c.caseDateId(),before.endsAt,c.endsAt()); phiAuditService.auditUpdate(con,c.actorUserId(),"CaseDates","Notes",c.caseDateId(),before.notes,notes); CaseDateDto dto=requireDate(con,c.caseDateId(),c.shaleClientId()); con.commit(); return dto;
            } catch(Exception e){ con.rollback(); throw e; } finally { con.setAutoCommit(true); }
        } catch (SQLException e) { throw fail(e); }
    }

    public void deleteCaseDate(DeleteCaseDateCommand c) { mutateDeleted(c.shaleClientId(),c.actorUserId(),c.caseId(),c.caseDateId(),c.expectedRowVer(),false); }
    public CaseDateDto restoreCaseDate(RestoreCaseDateCommand c) { mutateDeleted(c.shaleClientId(),c.actorUserId(),c.caseId(),c.caseDateId(),c.expectedRowVer(),true); try(Connection con=db.requireConnection()){return requireDate(con,c.caseDateId(),c.shaleClientId());} catch(SQLException e){throw fail(e);} }
    private void mutateDeleted(int t,int a,long caseId,long id,byte[] rv,boolean restore){ try(Connection con=db.requireConnection()){ verifyTenant(con,t); validateActor(con,t,a); validateCase(con,t,caseId); MutationRow before=requireMutationRow(con,t,caseId,id,restore); requireRowVerMatch(before.rowVer,rv); requireHistoricalType(con,t,before.typeId); con.setAutoCommit(false); try{String sql= restore ? "UPDATE dbo.CaseDates SET IsDeleted=0, DeletedAt=NULL, DeletedByUserId=NULL, UpdatedAt=SYSUTCDATETIME(), UpdatedByUserId=? WHERE Id=? AND ShaleClientId=? AND CaseId=? AND IsDeleted=1 AND RowVer=?" : "UPDATE dbo.CaseDates SET IsDeleted=1, DeletedAt=SYSUTCDATETIME(), DeletedByUserId=?, UpdatedAt=SYSUTCDATETIME(), UpdatedByUserId=? WHERE Id=? AND ShaleClientId=? AND CaseId=? AND IsDeleted=0 AND RowVer=?"; int rows; try(PreparedStatement ps=con.prepareStatement(sql)){int i=1; ps.setInt(i++,a); if(!restore) ps.setInt(i++,a); ps.setLong(i++,id); ps.setInt(i++,t); ps.setLong(i++,caseId); ps.setBytes(i,rv); rows=ps.executeUpdate();} if(rows!=1) throw new IllegalStateException("Case date changed."); touchCase(con,caseId,t); audit(con,t,a,caseId,id, restore?EntityActionAuditEvent.Action.ACTIVATED:EntityActionAuditEvent.Action.DELETED); if(!restore) phiAuditService.auditDelete(con,a,"CaseDates","Notes",id,before.notes); con.commit(); }catch(Exception e){con.rollback(); throw e;}finally{con.setAutoCommit(true);} }catch(SQLException e){throw fail(e);} }

    static String occurrenceSql(String where) { return """
            SELECT cd.Id, cd.ShaleClientId, cd.CaseId, cd.CaseDateTypeId,
                   COALESCE(eff.SystemKey, st.SystemKey) AS TypeSystemKey,
                   COALESCE(eff.Name, st.Name) AS TypeName,
                   COALESCE(eff.Description, st.Description) AS TypeDescription,
                   COALESCE(eff.CalendarCategory, st.CalendarCategory) AS CalendarCategory,
                   COALESCE(eff.Color, st.Color) AS Color,
                   COALESCE(eff.SupportsTime, st.SupportsTime) AS SupportsTime,
                   cd.StartsAt, cd.EndsAt, cd.AllDay, cd.Notes, cd.CreatedAt, cd.CreatedByUserId,
                   COALESCE(NULLIF(LTRIM(RTRIM(COALESCE(cu.name_first, '') + CASE WHEN COALESCE(cu.name_first, '') = '' OR COALESCE(cu.name_last, '') = '' THEN '' ELSE ' ' END + COALESCE(cu.name_last, ''))), ''), CONCAT('User #', cd.CreatedByUserId)) AS CreatedByDisplayName,
                   cd.UpdatedAt, cd.UpdatedByUserId,
                   CASE WHEN cd.UpdatedByUserId IS NULL THEN NULL ELSE COALESCE(NULLIF(LTRIM(RTRIM(COALESCE(uu.name_first, '') + CASE WHEN COALESCE(uu.name_first, '') = '' OR COALESCE(uu.name_last, '') = '' THEN '' ELSE ' ' END + COALESCE(uu.name_last, ''))), ''), CONCAT('User #', cd.UpdatedByUserId)) END AS UpdatedByDisplayName,
                   cd.RowVer
            FROM dbo.CaseDates cd
            JOIN dbo.Cases c ON c.Id = cd.CaseId AND c.ShaleClientId = cd.ShaleClientId AND c.IsDeleted = 0
            JOIN dbo.CaseDateTypes st ON st.Id = cd.CaseDateTypeId AND (st.ShaleClientId = cd.ShaleClientId OR st.ShaleClientId IS NULL)
            OUTER APPLY (
              SELECT TOP (1) t.* FROM dbo.CaseDateTypes t
              WHERE st.SystemKey IS NOT NULL AND t.SystemKey = st.SystemKey AND (t.ShaleClientId = ? OR t.ShaleClientId IS NULL) AND t.IsDeleted = 0 AND t.IsActive = 1
              ORDER BY CASE WHEN t.ShaleClientId = ? THEN 0 ELSE 1 END, t.Id
            ) eff
            LEFT JOIN dbo.Users cu ON cu.Id = cd.CreatedByUserId AND cu.ShaleClientId = cd.ShaleClientId
            LEFT JOIN dbo.Users uu ON uu.Id = cd.UpdatedByUserId AND uu.ShaleClientId = cd.ShaleClientId
            WHERE """ + where; }



    private interface SqlTypeMutation<T>{T run(Connection con)throws Exception;}
    private <T> T mutateType(int tenant,int actor,SqlTypeMutation<T> op){try(Connection con=db.requireConnection()){verifyTenant(con,tenant);validateAdminActor(con,tenant,actor);con.setAutoCommit(false);try{T r=op.run(con);con.commit();return r;}catch(Exception e){con.rollback(); if(e instanceof RuntimeException re) throw re; throw new IllegalStateException("Case date type mutation failed.", e);}finally{con.setAutoCommit(true);}}catch(SQLException e){throw fail(e);}}
    private EffectiveCaseDateTypeDto insertType(Connection con,CaseDateTypeCommand c,String key)throws SQLException{validateTypeValues(c.name(),c.calendarCategory(),c.color(),key,c.sortOrder());try(PreparedStatement ps=con.prepareStatement("INSERT dbo.CaseDateTypes (ShaleClientId,SystemKey,Name,Description,CalendarCategory,Color,SupportsTime,SortOrder,IsActive,CreatedByUserId) OUTPUT INSERTED.Id VALUES (?,?,?,?,?,?,?,?,?,?)")){int i=1;ps.setInt(i++,c.shaleClientId());ps.setString(i++,key);ps.setString(i++,trimReq(c.name(),"Name"));ps.setString(i++,norm(c.description()));ps.setString(i++,category(c.calendarCategory()));ps.setString(i++,norm(c.color()));ps.setBoolean(i++,c.supportsTime());ps.setInt(i++,c.sortOrder()==null?nextSort(con,c.shaleClientId()):c.sortOrder());ps.setBoolean(i++,c.active());ps.setInt(i,c.actorUserId());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalStateException("Case date type was not created.");return findType(con,rs.getInt(1));}}}
    private EffectiveCaseDateTypeDto upsertOverride(Connection con,CaseDateTypeCommand c,EffectiveCaseDateTypeDto global)throws SQLException{if(global.systemKey()==null||global.systemKey().isBlank())throw new IllegalArgumentException("Global case date type cannot be customized without a SystemKey.");EffectiveCaseDateTypeDto existing=findTenantTypeByKey(con,c.shaleClientId(),global.systemKey());CaseDateTypeCommand cmd=new CaseDateTypeCommand(existing==null?null:existing.id(),c.shaleClientId(),c.actorUserId(),global.systemKey(),c.name(),c.description(),c.calendarCategory(),c.color(),c.supportsTime(),c.sortOrder(),c.active(),existing==null?null:existing.rowVer());return existing==null?insertType(con,cmd,global.systemKey()):updateTypeRow(con,cmd,existing.id(),existing.rowVer(),global.systemKey());}
    private EffectiveCaseDateTypeDto updateTypeRow(Connection con,CaseDateTypeCommand c,int id,byte[] expected,String stableKey)throws SQLException{validateTypeValues(c.name(),c.calendarCategory(),c.color(),stableKey,c.sortOrder());try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.CaseDateTypes SET Name=?,Description=?,CalendarCategory=?,Color=?,SupportsTime=?,SortOrder=?,IsActive=?,IsDeleted=0,DeletedAt=NULL,DeletedByUserId=NULL,UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? WHERE Id=? AND ShaleClientId=? AND RowVer=?")){int i=1;ps.setString(i++,trimReq(c.name(),"Name"));ps.setString(i++,norm(c.description()));ps.setString(i++,category(c.calendarCategory()));ps.setString(i++,norm(c.color()));ps.setBoolean(i++,c.supportsTime());ps.setInt(i++,c.sortOrder()==null?nextSort(con,c.shaleClientId()):c.sortOrder());ps.setBoolean(i++,c.active());ps.setInt(i++,c.actorUserId());ps.setInt(i++,id);ps.setInt(i++,c.shaleClientId());ps.setBytes(i,expected);if(ps.executeUpdate()!=1)throw new IllegalStateException("Case date type changed.");return findType(con,id);}}
    private void softDeleteType(Connection con,int tenant,int actor,int id)throws SQLException{try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.CaseDateTypes SET IsDeleted=1,IsActive=0,DeletedAt=SYSUTCDATETIME(),DeletedByUserId=?,UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? WHERE Id=? AND ShaleClientId=?")){ps.setInt(1,actor);ps.setInt(2,actor);ps.setInt(3,id);ps.setInt(4,tenant);if(ps.executeUpdate()!=1)throw new IllegalArgumentException("Tenant case date type is not available.");}}
    private EffectiveCaseDateTypeDto findType(Connection con,Integer id)throws SQLException{if(id==null)return null;try(PreparedStatement ps=con.prepareStatement("SELECT t.Id,t.ShaleClientId,t.SystemKey,t.Name,t.Description,t.CalendarCategory,t.Color,t.SupportsTime,t.SortOrder,t.IsActive,t.IsDeleted,CASE WHEN t.ShaleClientId IS NOT NULL AND g.Id IS NOT NULL THEN 'TENANT_OVERRIDE' WHEN t.ShaleClientId IS NOT NULL THEN 'TENANT_CREATED' ELSE 'GLOBAL' END AS Origin,t.RowVer FROM dbo.CaseDateTypes t LEFT JOIN dbo.CaseDateTypes g ON g.ShaleClientId IS NULL AND g.SystemKey=t.SystemKey WHERE t.Id=?")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?mapType(rs):null;}}}
    private EffectiveCaseDateTypeDto findTenantTypeByKey(Connection con,int tenant,String key)throws SQLException{if(key==null)return null;try(PreparedStatement ps=con.prepareStatement("SELECT t.Id,t.ShaleClientId,t.SystemKey,t.Name,t.Description,t.CalendarCategory,t.Color,t.SupportsTime,t.SortOrder,t.IsActive,t.IsDeleted,'TENANT_OVERRIDE' AS Origin,t.RowVer FROM dbo.CaseDateTypes t WHERE t.ShaleClientId=? AND t.SystemKey=?")){ps.setInt(1,tenant);ps.setString(2,key);try(ResultSet rs=ps.executeQuery()){return rs.next()?mapType(rs):null;}}}
    private static void requireTypeForTenant(EffectiveCaseDateTypeDto e,int tenant){if(e==null)throw new IllegalArgumentException("Case date type is not available.");if(e.shaleClientId()!=null&&e.shaleClientId()!=tenant)throw new IllegalArgumentException("Case date type is not available for this tenant.");}
    private static void requireExpected(byte[] rv){if(rv==null||rv.length==0)throw new IllegalArgumentException("expectedRowVer is required");}
    private static void validateTypeValues(String name,String cat,String color,String key,Integer sort){trimReq(name,"Name");category(cat);String c=norm(color);if(c!=null&&!c.matches("#[0-9A-Fa-f]{6}"))throw new IllegalArgumentException("Color must be #RRGGBB.");if(key!=null&&!key.matches("[a-z0-9_\\-]{1,64}"))throw new IllegalArgumentException("SystemKey is invalid.");if(sort!=null&&(sort<-100000||sort>100000))throw new IllegalArgumentException("Sort order is out of range.");}
    private static String trimReq(String s,String n){String v=norm(s);if(v==null)throw new IllegalArgumentException(n+" is required.");if(v.length()>100)throw new IllegalArgumentException(n+" must be 100 characters or fewer.");return v;}
    private static String category(String c){String v=norm(c);if(v==null)v="OTHER";v=v.toUpperCase(Locale.ROOT);if(!Set.of("DEADLINE","TRIAL","HEARING","MEDIATION","DEPOSITION","NOTICE","APPOINTMENT","MILESTONE","OTHER").contains(v))throw new IllegalArgumentException("Calendar category is invalid.");return v;}
    private static String normalizeSystemKey(String s){String v=norm(s);return v==null?null:v.toLowerCase(Locale.ROOT);}
    private static int nextSort(Connection con,int tenant)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT COALESCE(MAX(SortOrder),0)+10 FROM dbo.CaseDateTypes WHERE ShaleClientId=? AND IsDeleted=0")){ps.setInt(1,tenant);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1);}}}

    private CaseDateDto requireDate(Connection con,long id,int tenant)throws SQLException{String sql=occurrenceSql("cd.Id = ? AND cd.ShaleClientId = ?");try(PreparedStatement ps=con.prepareStatement(sql)){ps.setInt(1,tenant);ps.setInt(2,tenant);ps.setLong(3,id);ps.setInt(4,tenant);try(ResultSet rs=ps.executeQuery()){if(rs.next())return mapDate(rs);throw new IllegalStateException("Case date is not available.");}}}
    private record TypeRow(int id, boolean supportsTime){}
    private record MutationRow(long id,int typeId,LocalDateTime startsAt,LocalDateTime endsAt,boolean allDay,String notes,byte[] rowVer){}
    private static void requireRowVerMatch(byte[] actual, byte[] expected){ if(expected==null||expected.length==0) throw new IllegalArgumentException("expectedRowVer is required"); if(!Arrays.equals(actual, expected)) throw new IllegalStateException("Case date changed."); }
    private static void validateAllDay(TypeRow t, boolean allDay){ if(!t.supportsTime && !allDay) throw new IllegalArgumentException("Case date type requires all-day occurrences."); }
    private static String norm(String s){ if(s==null)return null; String t=s.trim(); return t.isEmpty()?null:t; }
    private static void setLdt(PreparedStatement ps,int i,LocalDateTime v)throws SQLException{ if(v==null)ps.setNull(i,Types.TIMESTAMP); else ps.setTimestamp(i,Timestamp.valueOf(v)); }
    private static TypeRow requireSelectableType(Connection con,int tenant,int id)throws SQLException{ try(PreparedStatement ps=con.prepareStatement("""
            WITH visible AS (SELECT t.Id,t.SupportsTime,t.IsActive,t.IsDeleted,ROW_NUMBER() OVER (PARTITION BY t.SystemKey ORDER BY CASE WHEN t.ShaleClientId=? AND t.IsDeleted=0 THEN 0 ELSE 1 END,t.Id) rn FROM dbo.CaseDateTypes t WHERE (t.ShaleClientId=? OR t.ShaleClientId IS NULL) AND t.SystemKey IS NOT NULL UNION ALL SELECT t.Id,t.SupportsTime,t.IsActive,t.IsDeleted,1 FROM dbo.CaseDateTypes t WHERE t.ShaleClientId=? AND t.SystemKey IS NULL)
            SELECT Id, SupportsTime FROM visible WHERE Id=? AND rn=1 AND IsActive=1 AND IsDeleted=0
            """)){ps.setInt(1,tenant);ps.setInt(2,tenant);ps.setInt(3,tenant);ps.setInt(4,id);try(ResultSet rs=ps.executeQuery()){if(rs.next())return new TypeRow(rs.getInt(1),rs.getBoolean(2));throw new IllegalArgumentException("Case date type is not selectable for this tenant.");}}}
    private static TypeRow requireHistoricalType(Connection con,int tenant,int id)throws SQLException{ try(PreparedStatement ps=con.prepareStatement("SELECT Id, SupportsTime FROM dbo.CaseDateTypes WHERE Id=? AND (ShaleClientId=? OR ShaleClientId IS NULL)")){ps.setInt(1,id);ps.setInt(2,tenant);try(ResultSet rs=ps.executeQuery()){if(rs.next())return new TypeRow(rs.getInt(1),rs.getBoolean(2));throw new IllegalArgumentException("Case date type is not available for this tenant.");}}}
    private static MutationRow requireMutationRow(Connection con,int tenant,long caseId,long id,boolean deleted)throws SQLException{ try(PreparedStatement ps=con.prepareStatement("SELECT Id,CaseDateTypeId,StartsAt,EndsAt,AllDay,Notes,RowVer FROM dbo.CaseDates WHERE Id=? AND ShaleClientId=? AND CaseId=? AND IsDeleted=?")){ps.setLong(1,id);ps.setInt(2,tenant);ps.setLong(3,caseId);ps.setBoolean(4,deleted);try(ResultSet rs=ps.executeQuery()){if(rs.next())return new MutationRow(rs.getLong(1),rs.getInt(2),ldt(rs,"StartsAt"),ldt(rs,"EndsAt"),rs.getBoolean(5),rs.getString(6),rs.getBytes(7));throw new IllegalArgumentException(deleted?"Deleted case date is not available for this case.":"Active case date is not available for this case.");}}}
    private static void touchCase(Connection con,long caseId,int tenant)throws SQLException{try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.Cases SET UpdatedAt=SYSDATETIME() WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0")){ps.setLong(1,caseId);ps.setInt(2,tenant);if(ps.executeUpdate()!=1)throw new IllegalStateException("Case is not available for this tenant.");}}
    private void audit(Connection con,int tenant,int actor,long caseId,long id,EntityActionAuditEvent.Action action)throws SQLException{entityActionAuditDao.append(con,EntityActionAuditEvent.now(tenant,actor,EntityActionAuditEvent.EntityType.CASE_DATE,id,action,null,null,Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID,caseId,EntityActionAuditEvent.MetadataKey.CASE_DATE_ID,id)));}

    private static EffectiveCaseDateTypeDto mapType(ResultSet rs) throws SQLException { return new EffectiveCaseDateTypeDto(rs.getInt(1),(Integer)rs.getObject(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getBoolean(8),rs.getInt(9),rs.getBoolean(10),rs.getBoolean(11),EffectiveCaseDateTypeDto.Origin.valueOf(rs.getString(12)),rs.getBytes(13)); }
    private static CaseDateDto mapDate(ResultSet rs) throws SQLException { return new CaseDateDto(rs.getLong("Id"),rs.getInt("ShaleClientId"),rs.getLong("CaseId"),rs.getInt("CaseDateTypeId"),rs.getString("TypeSystemKey"),rs.getString("TypeName"),rs.getString("TypeDescription"),rs.getString("CalendarCategory"),rs.getString("Color"),rs.getBoolean("SupportsTime"),ldt(rs,"StartsAt"),ldt(rs,"EndsAt"),rs.getBoolean("AllDay"),rs.getString("Notes"),ldt(rs,"CreatedAt"),rs.getInt("CreatedByUserId"),rs.getString("CreatedByDisplayName"),ldt(rs,"UpdatedAt"),(Integer)rs.getObject("UpdatedByUserId"),rs.getString("UpdatedByDisplayName"),rs.getBytes("RowVer")); }
    private static LocalDateTime ldt(ResultSet rs, String c) throws SQLException { Timestamp ts = rs.getTimestamp(c); return ts == null ? null : ts.toLocalDateTime(); }
    private static void verifyTenant(Connection con,int t)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT CAST(SESSION_CONTEXT(N'ShaleClientId') AS INT)");ResultSet rs=ps.executeQuery()){if(!rs.next()||rs.getInt(1)!=t)throw new IllegalStateException("ShaleClientId session context mismatch.");}}
    private static void validateAdminActor(Connection con,int t,int u)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo.Users WHERE id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0 AND ISNULL(is_admin,0)=1")){ps.setInt(1,u);ps.setInt(2,t);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Administrator user is not available for this tenant.");}}}
    private static void validateActor(Connection con,int t,int u)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo.Users WHERE id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0")){ps.setInt(1,u);ps.setInt(2,t);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Actor user is not available for this tenant.");}}}
    private static void validateCase(Connection con,int t,long c)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo.Cases WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0")){ps.setLong(1,c);ps.setInt(2,t);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Case is not available for this tenant.");}}}
    private static RuntimeException fail(SQLException e){return new IllegalStateException("Database operation failed.", e);}
}
