package com.shale.data.dao;

import com.shale.core.dto.*;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.MaterialRequestServicePort.CreateMaterialRequestCommand;
import com.shale.core.service.MaterialRequestServicePort.MaterialTypeCommand;
import com.shale.core.service.MaterialRequestServicePort.RequestMethodCommand;
import com.shale.core.service.MaterialRequestServicePort.RequestStatusCommand;
import com.shale.core.service.MaterialRequestServicePort.ResetLookupOverrideCommand;
import com.shale.core.service.MaterialRequestServicePort.SetLookupActiveCommand;
import com.shale.core.service.MaterialRequestServicePort.UpdateMaterialRequestCommand;
import com.shale.core.service.MaterialRequestServicePort.DeleteMaterialRequestCommand;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class MaterialRequestDao {
    private final DbSessionProvider db; private final EntityActionAuditDao actionDao=new EntityActionAuditDao(); private final PhiAuditService phi; private final NotificationDao notifications;
    public MaterialRequestDao(DbSessionProvider db) { this.db = Objects.requireNonNull(db); this.phi=new PhiAuditService(new AuditLogDao(db)); this.notifications=new NotificationDao(db); }

    public List<MaterialTypeDto> listEffectiveMaterialTypes(int shaleClientId) {
        String sql = """
                WITH visible AS (
                  SELECT Id, ShaleClientId, SystemKey, Name, Description, Color, SortOrder, IsActive, IsDeleted, RowVer,
                         ROW_NUMBER() OVER (PARTITION BY SystemKey ORDER BY CASE WHEN ShaleClientId = ? THEN 0 ELSE 1 END, Id) AS rn
                  FROM dbo.MaterialTypes
                  WHERE (ShaleClientId = ? OR ShaleClientId IS NULL) AND SystemKey IS NOT NULL
                )
                SELECT Id, ShaleClientId, SystemKey, Name, Description, Color, SortOrder, IsActive, IsDeleted, RowVer FROM visible
                WHERE rn = 1 AND IsDeleted = 0 AND IsActive = 1
                UNION ALL
                SELECT Id, ShaleClientId, SystemKey, Name, Description, Color, SortOrder, IsActive, IsDeleted, RowVer
                FROM dbo.MaterialTypes
                WHERE ShaleClientId = ? AND SystemKey IS NULL AND IsDeleted = 0 AND IsActive = 1
                ORDER BY SortOrder, Name, Id
                """;
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            verifyTenant(con, shaleClientId); ps.setInt(1, shaleClientId); ps.setInt(2, shaleClientId); ps.setInt(3, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) { List<MaterialTypeDto> out = new ArrayList<>(); while (rs.next()) out.add(new MaterialTypeDto(rs.getInt(1), (Integer)rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getBoolean(8), rs.getBoolean(9), rs.getBytes(10))); return out; }
        } catch (SQLException e) { throw sqlFailure(e); }
    }

    public List<RequestMethodDto> listEffectiveRequestMethods(int shaleClientId) {
        String sql = effectiveRequestMethodSql();
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            verifyTenant(con, shaleClientId);
            ps.setInt(1, shaleClientId); ps.setInt(2, shaleClientId); ps.setInt(3, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RequestMethodDto> out = new ArrayList<>();
                while (rs.next()) out.add(new RequestMethodDto(rs.getInt(1), (Integer) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getBoolean(7), rs.getBoolean(8), rs.getBytes(9)));
                return out;
            }
        } catch (SQLException e) { throw sqlFailure(e); }
    }

    public List<RequestStatusDto> listEffectiveRequestStatuses(int shaleClientId) {
        String sql = effectiveRequestLookupSql("dbo.RequestStatuses");
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            verifyTenant(con, shaleClientId);
            ps.setInt(1, shaleClientId); ps.setInt(2, shaleClientId); ps.setInt(3, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RequestStatusDto> out = new ArrayList<>();
                while (rs.next()) out.add(new RequestStatusDto(rs.getInt(1), (Integer) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getBoolean(7), rs.getBoolean(8), rs.getBytes(9)));
                return out;
            }
        } catch (SQLException e) { throw sqlFailure(e); }
    }

    public List<MaterialTypeDto> listMaterialTypesForAdministration(int shaleClientId, int actorUserId) {
        return listMaterialTypes("WHERE ShaleClientId IS NULL OR ShaleClientId = ? ORDER BY SortOrder, Name, Id", shaleClientId, actorUserId);
    }

    public List<RequestMethodDto> listRequestMethodsForAdministration(int shaleClientId, int actorUserId) {
        return listRequestMethods("WHERE ShaleClientId IS NULL OR ShaleClientId = ? ORDER BY SortOrder, Name, Id", shaleClientId, actorUserId);
    }

    public List<RequestStatusDto> listRequestStatusesForAdministration(int shaleClientId, int actorUserId) {
        return listRequestStatuses("WHERE ShaleClientId IS NULL OR ShaleClientId = ? ORDER BY SortOrder, Name, Id", shaleClientId, actorUserId);
    }

    public MaterialTypeDto createMaterialType(MaterialTypeCommand c) { return mutate(c.shaleClientId(), c.actorUserId(), con -> insertMaterialType(con, c)); }
    public MaterialTypeDto updateMaterialType(MaterialTypeCommand c) { return mutate(c.shaleClientId(), c.actorUserId(), con -> {
        requireRowVer(c.expectedRowVer()); MaterialTypeDto existing = findMaterialType(con, c.id()); requireAvailable(existing, c.shaleClientId(), "Material type");
        if (existing.shaleClientId() == null) { assertRowVer(con, "dbo.MaterialTypes", existing.id(), c.expectedRowVer(), "material type changed."); return upsertMaterialTypeOverride(con, c, existing); }
        return updateMaterialTypeRow(con, c, existing.id(), c.expectedRowVer());
    }); }
    public MaterialTypeDto setMaterialTypeActive(SetLookupActiveCommand c) { return mutate(c.shaleClientId(), c.actorUserId(), con -> {
        requireRowVer(c.expectedRowVer()); MaterialTypeDto e = findMaterialType(con, c.id()); requireAvailable(e, c.shaleClientId(), "Material type");
        MaterialTypeCommand cmd = new MaterialTypeCommand(e.shaleClientId() == null ? null : e.id(), c.shaleClientId(), c.actorUserId(), e.name(), e.description(), e.color(), c.active(), e.systemKey(), e.sortOrder(), e.shaleClientId() == null ? c.expectedRowVer() : e.rowVer());
        if (e.shaleClientId() == null) { assertRowVer(con, "dbo.MaterialTypes", e.id(), c.expectedRowVer(), "material type changed."); return upsertMaterialTypeOverride(con, cmd, e); }
        return updateMaterialTypeRow(con, cmd, e.id(), c.expectedRowVer());
    }); }
    public void resetMaterialTypeOverride(ResetLookupOverrideCommand c) { mutateVoid(c.shaleClientId(), c.actorUserId(), con -> softDeleteOverride(con, "dbo.MaterialTypes", c.id(), c.shaleClientId(), c.actorUserId(), findMaterialType(con, c.id()) == null ? null : findMaterialType(con, c.id()).systemKey())); }

    public RequestMethodDto createRequestMethod(RequestMethodCommand c) { return mutate(c.shaleClientId(), c.actorUserId(), con -> insertRequestMethod(con, c)); }
    public RequestMethodDto updateRequestMethod(RequestMethodCommand c) { return mutate(c.shaleClientId(), c.actorUserId(), con -> {
        requireRowVer(c.expectedRowVer()); RequestMethodDto e = findRequestMethod(con, c.id()); requireAvailable(e, c.shaleClientId(), "Request method");
        if (e.shaleClientId() == null) { assertRowVer(con, "dbo.RequestMethods", e.id(), c.expectedRowVer(), "request method changed."); return upsertRequestMethodOverride(con, c, e); }
        return updateRequestMethodRow(con, c, e.id(), c.expectedRowVer());
    }); }
    public RequestMethodDto setRequestMethodActive(SetLookupActiveCommand c) { return mutate(c.shaleClientId(), c.actorUserId(), con -> {
        requireRowVer(c.expectedRowVer()); RequestMethodDto e = findRequestMethod(con, c.id()); requireAvailable(e, c.shaleClientId(), "Request method");
        RequestMethodCommand cmd = new RequestMethodCommand(e.shaleClientId() == null ? null : e.id(), c.shaleClientId(), c.actorUserId(), e.name(), e.color(), c.active(), e.systemKey(), e.sortOrder(), e.shaleClientId() == null ? c.expectedRowVer() : e.rowVer());
        if (e.shaleClientId() == null) { assertRowVer(con, "dbo.RequestMethods", e.id(), c.expectedRowVer(), "request method changed."); return upsertRequestMethodOverride(con, cmd, e); }
        return updateRequestMethodRow(con, cmd, e.id(), c.expectedRowVer());
    }); }
    public void resetRequestMethodOverride(ResetLookupOverrideCommand c) { mutateVoid(c.shaleClientId(), c.actorUserId(), con -> softDeleteOverride(con, "dbo.RequestMethods", c.id(), c.shaleClientId(), c.actorUserId(), findRequestMethod(con, c.id()) == null ? null : findRequestMethod(con, c.id()).systemKey())); }

    public RequestStatusDto createRequestStatus(RequestStatusCommand c) { return mutate(c.shaleClientId(), c.actorUserId(), con -> { if(sk(c.systemKey())!=null)throw new IllegalArgumentException("Custom request statuses cannot define a built-in System Key."); return insertRequestStatus(con, c); }); }
    public RequestStatusDto updateRequestStatus(RequestStatusCommand c) { return mutate(c.shaleClientId(), c.actorUserId(), con -> {
        requireRowVer(c.expectedRowVer()); RequestStatusDto e = findRequestStatus(con, c.id()); requireAvailable(e, c.shaleClientId(), "Request status");
        if (e.shaleClientId() == null) { assertRowVer(con, "dbo.RequestStatuses", e.id(), c.expectedRowVer(), "request status changed."); return upsertRequestStatusOverride(con, c, e); }
        return updateRequestStatusRow(con, c, e.id(), c.expectedRowVer());
    }); }
    public RequestStatusDto setRequestStatusActive(SetLookupActiveCommand c) { return mutate(c.shaleClientId(), c.actorUserId(), con -> {
        requireRowVer(c.expectedRowVer()); RequestStatusDto e = findRequestStatus(con, c.id()); requireAvailable(e, c.shaleClientId(), "Request status");
        RequestStatusCommand cmd = new RequestStatusCommand(e.shaleClientId() == null ? null : e.id(), c.shaleClientId(), c.actorUserId(), e.name(), e.color(), c.active(), e.systemKey(), e.sortOrder(), e.shaleClientId() == null ? c.expectedRowVer() : e.rowVer());
        if (e.shaleClientId() == null) { assertRowVer(con, "dbo.RequestStatuses", e.id(), c.expectedRowVer(), "request status changed."); return upsertRequestStatusOverride(con, cmd, e); }
        return updateRequestStatusRow(con, cmd, e.id(), c.expectedRowVer());
    }); }
    public void resetRequestStatusOverride(ResetLookupOverrideCommand c) { mutateVoid(c.shaleClientId(), c.actorUserId(), con -> softDeleteOverride(con, "dbo.RequestStatuses", c.id(), c.shaleClientId(), c.actorUserId(), findRequestStatus(con, c.id()) == null ? null : findRequestStatus(con, c.id()).systemKey())); }

    private static String effectiveRequestMethodSql() { return effectiveRequestLookupSql("dbo.RequestMethods").replace("Name,  SortOrder", "Name, Color, SortOrder"); }
    private static int nextRequestMethodSortOrder(Connection con,int tenant)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT COALESCE(MAX(SortOrder),0)+10 FROM dbo.RequestMethods WHERE ShaleClientId=?")){ps.setInt(1,tenant);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1);}}}
    private static int nextRequestStatusSortOrder(Connection con,int tenant)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT COALESCE(MAX(SortOrder),0)+10 FROM dbo.RequestStatuses WHERE ShaleClientId=? AND IsDeleted=0")){ps.setInt(1,tenant);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1);}}}

    private static String effectiveRequestLookupSql(String tableName) {
        return """
                WITH visible AS (
                  SELECT Id, ShaleClientId, SystemKey, Name, %s SortOrder, IsActive, IsDeleted, RowVer,
                         ROW_NUMBER() OVER (PARTITION BY SystemKey ORDER BY CASE WHEN ShaleClientId = ? THEN 0 ELSE 1 END, Id) AS rn
                  FROM %s
                  WHERE (ShaleClientId = ? OR ShaleClientId IS NULL) AND SystemKey IS NOT NULL
                )
                SELECT Id, ShaleClientId, SystemKey, Name, %s SortOrder, IsActive, IsDeleted, RowVer FROM visible
                WHERE rn = 1 AND IsDeleted = 0 AND IsActive = 1
                UNION ALL
                SELECT Id, ShaleClientId, SystemKey, Name, %s SortOrder, IsActive, IsDeleted, RowVer
                FROM %s
                WHERE ShaleClientId = ? AND SystemKey IS NULL AND IsDeleted = 0 AND IsActive = 1
                ORDER BY SortOrder, Name, Id
                """.formatted(tableName.endsWith("RequestStatuses") ? "Color," : "", tableName, tableName.endsWith("RequestStatuses") ? "Color," : "", tableName.endsWith("RequestStatuses") ? "Color," : "", tableName);
    }

    public List<MaterialRequestSummaryDto> listMaterialRequests(long caseId, int tenant) { return listMaterialRequests(caseId,tenant,false); }
    public List<MaterialRequestSummaryDto> listMaterialRequests(long caseId, int tenant, boolean includeDeleted) {
        String sql = baseSelect(false) + " WHERE mr.ShaleClientId=? AND mr.CaseId=? AND (?=1 OR mr.IsDeleted=0) AND ISNULL(c.IsDeleted,0)=0 ORDER BY mr.NextFollowUpAt, mr.RequestedAt DESC, mr.Id DESC";
        try (Connection con=db.requireConnection(); PreparedStatement ps=con.prepareStatement(sql)) { verifyTenant(con, tenant); ps.setInt(1,tenant); ps.setLong(2,caseId); ps.setBoolean(3,includeDeleted); try(ResultSet rs=ps.executeQuery()){List<MaterialRequestSummaryDto> out=new ArrayList<>(); while(rs.next()) out.add(mapSummary(rs)); return out;}} catch(SQLException e){throw sqlFailure(e);} }

    public MaterialRequestDetailDto findMaterialRequest(long caseId,long id,int tenant){
        String sql = baseSelect(true) + " WHERE mr.ShaleClientId=? AND mr.CaseId=? AND mr.Id=? AND ISNULL(c.IsDeleted,0)=0";
        try(Connection con=db.requireConnection(); PreparedStatement ps=con.prepareStatement(sql)){ verifyTenant(con,tenant); ps.setInt(1,tenant); ps.setLong(2,caseId); ps.setLong(3,id); try(ResultSet rs=ps.executeQuery()){return rs.next()?mapDetail(rs):null;}} catch(SQLException e){throw sqlFailure(e);} }

    public MaterialRequestDetailDto create(CreateMaterialRequestCommand c){
        validateCreate(c); LocalDateTime mutationTime=LocalDateTime.now();
        LocalDateTime next=normalizeCreateSchedule(c.nextFollowUpAt(),c.followUpIntervalDays(),mutationTime);
        try(Connection con=db.requireConnection()){verifyTenant(con,c.shaleClientId());con.setAutoCommit(false);try{
            validateRefs(con,c);StatusSemantic status=resolveStatusSemantic(con,c.shaleClientId(),c.status());
            ClosureValues closure=normalizeClosure(status.systemKey(),null,null,null,null,null,null,c.actorUserId(),mutationTime);
            long id=insert(con,c,status.persistedStatus(),closure,next);createRecipientNotifications(con,id,c.shaleClientId(),c.caseId(),c.title(),c.actorUserId(),null,null,c.requestedByUserId(),c.assignedToUserId(),"created");
            touchCase(con,c.caseId(),c.shaleClientId());auditPhiCreate(con,c.actorUserId(),id,c);audit(con,c.shaleClientId(),c.actorUserId(),EntityActionAuditEvent.Action.CREATED,id,c.caseId(),meta(c));con.commit();return findMaterialRequest(c.caseId(),id,c.shaleClientId());
        }catch(Exception ex){rollback(con);throw ex;}}catch(SQLException e){throw sqlFailure(e);}}

    public MaterialRequestDetailDto update(UpdateMaterialRequestCommand c){
        validateUpdate(c); LocalDateTime mutationTime=LocalDateTime.now();
        try(Connection con=db.requireConnection()){verifyTenant(con,c.shaleClientId());con.setAutoCommit(false);try{
            validateUpdateRefs(con,c);StatusSemantic status=resolveStatusSemantic(con,c.shaleClientId(),c.status());RecipientSnapshot previous=findRecipientSnapshot(con,c);ClosureValues existing=findClosureValues(con,c);ScheduleSnapshot schedule=findScheduleSnapshot(con,c);
            ClosureValues closure=normalizeClosure(status.systemKey(),c.closedAt(),c.closedByUserId(),c.closureReason(),existing.closedAt(),existing.closedByUserId(),existing.closureReason(),c.actorUserId(),mutationTime);
            LocalDateTime next=normalizeUpdateSchedule(c.nextFollowUpAt(),c.followUpIntervalDays(),schedule,status.systemKey(),mutationTime);
            int rows=updateRow(con,c,status.persistedStatus(),closure,next);if(rows==0)throw new IllegalStateException("Material request has changed. Please reload and try again.");
            createRecipientNotifications(con,c.materialRequestId(),c.shaleClientId(),c.caseId(),c.title(),c.actorUserId(),previous.requestedByUserId(),previous.assignedToUserId(),c.requestedByUserId(),c.assignedToUserId(),"update-"+HexFormat.of().formatHex(c.rowVer()));touchCase(con,c.caseId(),c.shaleClientId());audit(con,c.shaleClientId(),c.actorUserId(),EntityActionAuditEvent.Action.UPDATED,c.materialRequestId(),c.caseId(),meta(c));con.commit();return findMaterialRequest(c.caseId(),c.materialRequestId(),c.shaleClientId());
        }catch(Exception ex){rollback(con);throw ex;}}catch(SQLException e){throw sqlFailure(e);}}

    public void softDelete(DeleteMaterialRequestCommand c){
        requireRowVer(c.rowVer());
        try(Connection con=db.requireConnection()){verifyTenant(con,c.shaleClientId());con.setAutoCommit(false);try{
            MaterialRequestDetailDto old=findForDelete(con,c);
            try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.MaterialRequests SET IsDeleted=1,DeletedAt=SYSUTCDATETIME(),DeletedByUserId=?,UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? WHERE ShaleClientId=? AND CaseId=? AND Id=? AND IsDeleted=0 AND RowVer=?")){ps.setInt(1,c.actorUserId());ps.setInt(2,c.actorUserId());ps.setInt(3,c.shaleClientId());ps.setLong(4,c.caseId());ps.setLong(5,c.materialRequestId());ps.setBytes(6,c.rowVer());if(ps.executeUpdate()!=1)throw new IllegalStateException("Material request has changed. Please reload and try again.");}
            phi.auditDelete(con,c.actorUserId(),"MaterialRequests","Title",c.materialRequestId(),old.title());phi.auditDelete(con,c.actorUserId(),"MaterialRequests","Description",c.materialRequestId(),old.description());phi.auditDelete(con,c.actorUserId(),"MaterialRequests","RequestedFromText",c.materialRequestId(),old.requestedFromText());
            touchCase(con,c.caseId(),c.shaleClientId());audit(con,c.shaleClientId(),c.actorUserId(),EntityActionAuditEvent.Action.DELETED,c.materialRequestId(),c.caseId(),null);con.commit();
        }catch(Exception ex){rollback(con);throw ex;}}catch(SQLException e){throw sqlFailure(e);}}
    private MaterialRequestDetailDto findForDelete(Connection con,DeleteMaterialRequestCommand c)throws SQLException{String sql=baseSelect(true)+" WHERE mr.ShaleClientId=? AND mr.CaseId=? AND mr.Id=? AND mr.IsDeleted=0 AND ISNULL(c.IsDeleted,0)=0";try(PreparedStatement ps=con.prepareStatement(sql)){ps.setInt(1,c.shaleClientId());ps.setLong(2,c.caseId());ps.setLong(3,c.materialRequestId());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Material request not found.");return mapDetail(rs);}}}

    public List<MaterialRequestDueNotificationCandidate> listDueNotificationCandidates(int tenant, LocalDate today){
        if(today==null)return List.of();
        String sql="""
            SELECT mr.Id,mr.ShaleClientId,mr.CaseId,mr.Title,mr.AssignedToUserId,mr.RequestedByUserId,mr.ExpectedResponseDate,
                   LOWER(LTRIM(RTRIM(COALESCE(rs.SystemKey,mr.Status)))) AS StatusSystemKey
            FROM dbo.MaterialRequests mr
            OUTER APPLY (SELECT TOP (1) r.SystemKey FROM dbo.RequestStatuses r
                         WHERE (r.ShaleClientId=mr.ShaleClientId OR r.ShaleClientId IS NULL) AND r.IsDeleted=0
                           AND r.SystemKey IS NOT NULL AND (LOWER(LTRIM(RTRIM(r.SystemKey)))=LOWER(LTRIM(RTRIM(mr.Status))) OR LOWER(LTRIM(RTRIM(r.Name)))=LOWER(LTRIM(RTRIM(mr.Status))))
                         ORDER BY CASE WHEN r.ShaleClientId=mr.ShaleClientId THEN 0 ELSE 1 END,r.Id) rs
            WHERE mr.ShaleClientId=? AND mr.IsDeleted=0 AND mr.ExpectedResponseDate IS NOT NULL AND mr.ExpectedResponseDate<=?
              AND LOWER(LTRIM(RTRIM(COALESCE(rs.SystemKey,mr.Status)))) NOT IN ('closed','cancelled')
            ORDER BY mr.ExpectedResponseDate,mr.Id
            """;
        try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement(sql)){verifyTenant(con,tenant);ps.setInt(1,tenant);ps.setDate(2,java.sql.Date.valueOf(today));try(ResultSet rs=ps.executeQuery()){List<MaterialRequestDueNotificationCandidate> out=new ArrayList<>();while(rs.next())out.add(new MaterialRequestDueNotificationCandidate(rs.getLong(1),rs.getInt(2),rs.getLong(3),rs.getString(4),(Integer)rs.getObject(5),(Integer)rs.getObject(6),rs.getDate(7).toLocalDate(),rs.getString(8)));return out;}}catch(SQLException e){throw sqlFailure(e);}
    }

    public List<MaterialRequestFollowUpNotificationCandidate> listFollowUpNotificationCandidates(int tenant,LocalDateTime now){
        if(now==null)return List.of();
        String sql="""
            SELECT mr.Id,mr.ShaleClientId,mr.CaseId,mr.Title,mr.AssignedToUserId,mr.RequestedByUserId,mr.NextFollowUpAt,mr.FollowUpIntervalDays
            FROM dbo.MaterialRequests mr
            OUTER APPLY (SELECT TOP (1) r.SystemKey FROM dbo.RequestStatuses r WHERE (r.ShaleClientId=mr.ShaleClientId OR r.ShaleClientId IS NULL) AND r.IsDeleted=0 AND r.SystemKey IS NOT NULL AND (LOWER(LTRIM(RTRIM(r.SystemKey)))=LOWER(LTRIM(RTRIM(mr.Status))) OR LOWER(LTRIM(RTRIM(r.Name)))=LOWER(LTRIM(RTRIM(mr.Status)))) ORDER BY CASE WHEN r.ShaleClientId=mr.ShaleClientId THEN 0 ELSE 1 END,r.Id) rs
            WHERE mr.ShaleClientId=? AND mr.IsDeleted=0 AND mr.FollowUpIntervalDays IS NOT NULL AND mr.NextFollowUpAt<=?
              AND LOWER(LTRIM(RTRIM(COALESCE(rs.SystemKey,mr.Status)))) NOT IN ('closed','cancelled')
            ORDER BY mr.NextFollowUpAt,mr.Id
            """;
        try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement(sql)){verifyTenant(con,tenant);ps.setInt(1,tenant);setTs(ps,2,now);try(ResultSet rs=ps.executeQuery()){List<MaterialRequestFollowUpNotificationCandidate> out=new ArrayList<>();while(rs.next())out.add(new MaterialRequestFollowUpNotificationCandidate(rs.getLong(1),rs.getInt(2),rs.getLong(3),rs.getString(4),(Integer)rs.getObject(5),(Integer)rs.getObject(6),ldt(rs,"NextFollowUpAt"),rs.getInt(8)));return out;}}catch(SQLException e){throw sqlFailure(e);}
    }
    public record MaterialRequestFollowUpNotificationCandidate(long requestId,int shaleClientId,long caseId,String title,Integer assignedToUserId,Integer requestedByUserId,LocalDateTime nextFollowUpAt,int followUpIntervalDays){public Integer recipientUserId(){return assignedToUserId!=null?assignedToUserId:requestedByUserId;}}

    public record MaterialRequestDueNotificationCandidate(long requestId,int shaleClientId,long caseId,String title,Integer assignedToUserId,Integer requestedByUserId,LocalDate dueAt,String statusSystemKey){
        public Integer recipientUserId(){return assignedToUserId!=null?assignedToUserId:requestedByUserId!=null&&requestedByUserId>0?requestedByUserId:null;}
    }

    public List<MaterialRequestFollowUpDto> listFollowUps(long caseId,long requestId,int tenant){ String sql=""" 
        SELECT f.*, %s AS AttemptedByDisplayName FROM dbo.MaterialRequestFollowUps f JOIN dbo.MaterialRequests mr ON mr.Id=f.MaterialRequestId AND mr.ShaleClientId=f.ShaleClientId AND mr.CaseId=f.CaseId JOIN dbo.Users u ON u.Id=f.AttemptedByUserId AND u.ShaleClientId=f.ShaleClientId WHERE f.ShaleClientId=? AND f.CaseId=? AND f.MaterialRequestId=? AND mr.IsDeleted=0 ORDER BY f.AttemptedAt, f.Id
        """.formatted(userDisplayName("u")); try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement(sql)){verifyTenant(con,tenant);ps.setInt(1,tenant);ps.setLong(2,caseId);ps.setLong(3,requestId);try(ResultSet rs=ps.executeQuery()){List<MaterialRequestFollowUpDto> out=new ArrayList<>();while(rs.next())out.add(mapFollowUp(rs));return out;}}catch(SQLException e){throw sqlFailure(e);} }


    private static String baseSelect(boolean detail){ return """
        SELECT mr.Id,mr.ShaleClientId,mr.CaseId,mr.MaterialTypeId,mt.Name AS MaterialTypeName,mt.SystemKey AS MaterialTypeSystemKey,mt.Color AS MaterialTypeColor,mr.Title,
               %s mr.RequestedByUserId, %s AS RequestedByDisplayName, rbu.Color AS RequestedByUserColor, mr.AssignedToUserId, %s AS AssignedToDisplayName, au.Color AS AssignedToUserColor,
               mr.RequestedFromContactId, %s AS RequestedFromContactDisplayName, mr.RequestedFromOrganizationId, org.Name AS RequestedFromOrganizationName, mr.RequestedFromText,
               mr.RequestMethod,mr.RequestedAt,mr.RequestedRangeStartDate,mr.RequestedRangeEndDate,mr.Status,mr.ExpectedResponseDate,mr.NextFollowUpAt,mr.FollowUpIntervalDays,CAST(NULL AS datetime2) AS LastFollowUpAt,mr.FirstReceivedAt,mr.FullyReceivedAt,mr.ClosedAt,mr.ClosedByUserId,mr.ClosureReason,mr.Notes,mr.CreatedAt,mr.CreatedByUserId, %s AS CreatedByDisplayName,mr.UpdatedAt,mr.UpdatedByUserId,mr.RowVer,mr.IsDeleted
        FROM dbo.MaterialRequests mr JOIN dbo.Cases c ON c.Id=mr.CaseId AND c.ShaleClientId=mr.ShaleClientId
        JOIN dbo.MaterialTypes mt ON mt.Id=mr.MaterialTypeId AND (mt.ShaleClientId=mr.ShaleClientId OR mt.ShaleClientId IS NULL)
        LEFT JOIN dbo.Users rbu ON rbu.Id=mr.RequestedByUserId AND rbu.ShaleClientId=mr.ShaleClientId AND ISNULL(rbu.is_deleted,0)=0
        LEFT JOIN dbo.Users cb ON cb.Id=mr.CreatedByUserId AND cb.ShaleClientId=mr.ShaleClientId
        LEFT JOIN dbo.Users au ON au.Id=mr.AssignedToUserId AND au.ShaleClientId=mr.ShaleClientId AND ISNULL(au.is_deleted,0)=0
        LEFT JOIN dbo.Contacts ct ON ct.Id=mr.RequestedFromContactId AND ct.ShaleClientId=mr.ShaleClientId AND ISNULL(ct.IsDeleted,0)=0
        LEFT JOIN dbo.Organizations org ON org.Id=mr.RequestedFromOrganizationId AND org.ShaleClientId=mr.ShaleClientId AND ISNULL(org.IsDeleted,0)=0
        """.formatted(detail?"mr.Description,":"mr.Description,", userDisplayName("rbu"), userDisplayName("au"), contactDisplayName("ct"), userDisplayName("cb")); }
    private static String userDisplayName(String alias){return "LTRIM(RTRIM(COALESCE("+alias+".name_first, '') + CASE WHEN COALESCE("+alias+".name_first, '') = '' OR COALESCE("+alias+".name_last, '') = '' THEN '' ELSE ' ' END + COALESCE("+alias+".name_last, '')))";}
    private static String contactDisplayName(String alias){return "COALESCE(NULLIF(LTRIM(RTRIM("+alias+".Name)), ''), NULLIF(LTRIM(RTRIM(CONCAT("+alias+".FirstName, ' ', "+alias+".LastName))), ''), NULLIF(LTRIM(RTRIM("+alias+".WorkName)), ''))";}
    private static MaterialRequestSummaryDto mapSummary(ResultSet rs)throws SQLException{return new MaterialRequestSummaryDto(rs.getLong("Id"),rs.getInt("ShaleClientId"),rs.getLong("CaseId"),rs.getInt("MaterialTypeId"),rs.getString("MaterialTypeName"),rs.getString("MaterialTypeSystemKey"),rs.getString("MaterialTypeColor"),rs.getString("Title"),(Integer)rs.getObject("RequestedByUserId"),rs.getString("RequestedByDisplayName"),rs.getString("RequestedByUserColor"),(Integer)rs.getObject("AssignedToUserId"),rs.getString("AssignedToDisplayName"),rs.getString("AssignedToUserColor"),(Integer)rs.getObject("RequestedFromContactId"),rs.getString("RequestedFromContactDisplayName"),(Integer)rs.getObject("RequestedFromOrganizationId"),rs.getString("RequestedFromOrganizationName"),rs.getString("RequestedFromText"),rs.getString("RequestMethod"),ldt(rs,"RequestedAt"),rs.getString("Status"),ldt(rs,"ExpectedResponseDate"),ldt(rs,"NextFollowUpAt"),(Integer)rs.getObject("FollowUpIntervalDays"),ldt(rs,"LastFollowUpAt"),ldt(rs,"UpdatedAt"),rs.getBytes("RowVer"),rs.getString("Description"),rs.getBoolean("IsDeleted"),ld(rs,"RequestedRangeStartDate"),ld(rs,"RequestedRangeEndDate"),(Integer)rs.getObject("CreatedByUserId"),rs.getString("CreatedByDisplayName"));}
    private static MaterialRequestDetailDto mapDetail(ResultSet rs)throws SQLException{return new MaterialRequestDetailDto(rs.getLong("Id"),rs.getInt("ShaleClientId"),rs.getLong("CaseId"),rs.getInt("MaterialTypeId"),rs.getString("MaterialTypeName"),rs.getString("MaterialTypeSystemKey"),rs.getString("Title"),rs.getString("Description"),(Integer)rs.getObject("RequestedByUserId"),rs.getString("RequestedByDisplayName"),(Integer)rs.getObject("AssignedToUserId"),rs.getString("AssignedToDisplayName"),(Integer)rs.getObject("RequestedFromContactId"),rs.getString("RequestedFromContactDisplayName"),(Integer)rs.getObject("RequestedFromOrganizationId"),rs.getString("RequestedFromOrganizationName"),rs.getString("RequestedFromText"),rs.getString("RequestMethod"),ldt(rs,"RequestedAt"),ld(rs,"RequestedRangeStartDate"),ld(rs,"RequestedRangeEndDate"),rs.getString("Status"),ld(rs,"ExpectedResponseDate"),ldt(rs,"NextFollowUpAt"),(Integer)rs.getObject("FollowUpIntervalDays"),ldt(rs,"LastFollowUpAt"),ldt(rs,"FirstReceivedAt"),ldt(rs,"FullyReceivedAt"),ldt(rs,"ClosedAt"),(Integer)rs.getObject("ClosedByUserId"),rs.getString("ClosureReason"),rs.getString("Notes"),ldt(rs,"CreatedAt"),(Integer)rs.getObject("CreatedByUserId"),rs.getString("CreatedByDisplayName"),ldt(rs,"UpdatedAt"),(Integer)rs.getObject("UpdatedByUserId"),rs.getBytes("RowVer"),rs.getBoolean("IsDeleted"));}
        private static MaterialRequestFollowUpDto mapFollowUp(ResultSet rs)throws SQLException{return new MaterialRequestFollowUpDto(rs.getLong("Id"),rs.getInt("ShaleClientId"),rs.getLong("MaterialRequestId"),rs.getLong("CaseId"),ldt(rs,"AttemptedAt"),rs.getInt("AttemptedByUserId"),rs.getString("AttemptedByDisplayName"),rs.getString("Method"),rs.getString("Outcome"),ldt(rs,"NextFollowUpAt"),rs.getString("Notes"),ldt(rs,"CreatedAt"),rs.getInt("CreatedByUserId"),rs.getBytes("RowVer"));}

    private int updateRow(Connection con,UpdateMaterialRequestCommand c,String persistedStatus,ClosureValues closure,LocalDateTime normalizedNext)throws SQLException{try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.MaterialRequests SET MaterialTypeId=?,Title=?,Description=?,RequestedFromContactId=?,RequestedFromOrganizationId=?,RequestedFromText=?,RequestMethod=?,Status=?,RequestedByUserId=?,AssignedToUserId=?,RequestedAt=?,RequestedRangeStartDate=?,RequestedRangeEndDate=?,ExpectedResponseDate=?,NextFollowUpAt=?,FollowUpIntervalDays=?,FirstReceivedAt=?,FullyReceivedAt=?,ClosedAt=?,ClosedByUserId=?,ClosureReason=?,Notes=?,UpdatedByUserId=?,UpdatedAt=SYSUTCDATETIME() WHERE ShaleClientId=? AND CaseId=? AND Id=? AND IsDeleted=0 AND RowVer=?")){int i=1;ps.setInt(i++,c.materialTypeId());ps.setString(i++,blank(c.title()));ps.setString(i++,blank(c.description()));setInt(ps,i++,c.requestedFromContactId());setInt(ps,i++,c.requestedFromOrganizationId());ps.setString(i++,blank(c.requestedFromText()));ps.setString(i++,norm(c.requestMethod()));ps.setString(i++,persistedStatus);setInt(ps,i++,c.requestedByUserId());setInt(ps,i++,c.assignedToUserId());setTs(ps,i++,c.requestedAt());setDate(ps,i++,c.requestedRangeStartDate());setDate(ps,i++,c.requestedRangeEndDate());setDate(ps,i++,c.expectedResponseDate());setTs(ps,i++,normalizedNext);setInt(ps,i++,c.followUpIntervalDays());setTs(ps,i++,c.firstReceivedAt());setTs(ps,i++,c.fullyReceivedAt());setTs(ps,i++,closure.closedAt());setInt(ps,i++,closure.closedByUserId());ps.setString(i++,closure.closureReason());ps.setString(i++,blank(c.notes()));ps.setInt(i++,c.actorUserId());ps.setInt(i++,c.shaleClientId());ps.setLong(i++,c.caseId());ps.setLong(i++,c.materialRequestId());ps.setBytes(i++,c.rowVer());return ps.executeUpdate();}}
    record RecipientSnapshot(Integer requestedByUserId,Integer assignedToUserId) {}
    private RecipientSnapshot findRecipientSnapshot(Connection con,UpdateMaterialRequestCommand c)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT RequestedByUserId,AssignedToUserId FROM dbo.MaterialRequests WHERE ShaleClientId=? AND CaseId=? AND Id=? AND IsDeleted=0")){ps.setInt(1,c.shaleClientId());ps.setLong(2,c.caseId());ps.setLong(3,c.materialRequestId());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Material request not found.");return new RecipientSnapshot((Integer)rs.getObject(1),(Integer)rs.getObject(2));}}}
    private void createRecipientNotifications(Connection con,long requestId,int tenant,long caseId,String requestTitle,int actor,Integer oldRequester,Integer oldAssignee,Integer newRequester,Integer newAssignee,String occurrence)throws SQLException{
        boolean requesterChanged=newRequester!=null&&!Objects.equals(oldRequester,newRequester);
        boolean assigneeChanged=newAssignee!=null&&!Objects.equals(oldAssignee,newAssignee);
        LinkedHashSet<Integer> recipients=new LinkedHashSet<>();if(requesterChanged&&!Objects.equals(newRequester,actor))recipients.add(newRequester);if(assigneeChanged&&newAssignee!=actor)recipients.add(newAssignee);
        for(int recipient:recipients){boolean requester=requesterChanged&&Objects.equals(recipient,newRequester),assignee=assigneeChanged&&Objects.equals(recipient,newAssignee);String message=requester&&assignee?"You were assigned to and designated as the requester for a material request.":assignee?"A material request was assigned to you.":"You were designated as the requester for a material request.";String key="material-request:"+requestId+":recipient:"+occurrence+":"+recipient;notifications.createMaterialRequestNotification(con,tenant,recipient,blank(requestTitle),message,requestId,actor,requester&&assignee?"ASSIGNED_AND_REQUESTER":assignee?"ASSIGNED":"REQUESTER",key);}
    }

    record ScheduleSnapshot(Integer intervalDays,LocalDateTime nextFollowUpAt,String statusSystemKey) {}
    private ScheduleSnapshot findScheduleSnapshot(Connection con,UpdateMaterialRequestCommand c)throws SQLException{
        String sql="SELECT mr.FollowUpIntervalDays,mr.NextFollowUpAt,LOWER(LTRIM(RTRIM(COALESCE(rs.SystemKey,mr.Status)))) StatusSystemKey FROM dbo.MaterialRequests mr OUTER APPLY (SELECT TOP (1) r.SystemKey FROM dbo.RequestStatuses r WHERE (r.ShaleClientId=mr.ShaleClientId OR r.ShaleClientId IS NULL) AND r.IsDeleted=0 AND r.SystemKey IS NOT NULL AND (LOWER(LTRIM(RTRIM(r.SystemKey)))=LOWER(LTRIM(RTRIM(mr.Status))) OR LOWER(LTRIM(RTRIM(r.Name)))=LOWER(LTRIM(RTRIM(mr.Status)))) ORDER BY CASE WHEN r.ShaleClientId=mr.ShaleClientId THEN 0 ELSE 1 END,r.Id) rs WHERE mr.ShaleClientId=? AND mr.CaseId=? AND mr.Id=? AND mr.IsDeleted=0";
        try(PreparedStatement ps=con.prepareStatement(sql)){ps.setInt(1,c.shaleClientId());ps.setLong(2,c.caseId());ps.setLong(3,c.materialRequestId());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Material request not found.");return new ScheduleSnapshot((Integer)rs.getObject(1),ldt(rs,"NextFollowUpAt"),rs.getString(3));}}
    }
    static LocalDateTime normalizeCreateSchedule(LocalDateTime supplied,Integer interval,LocalDateTime now){validateInterval(interval);return interval==null?supplied:Objects.requireNonNull(now).plusDays(interval);}
    static LocalDateTime normalizeUpdateSchedule(LocalDateTime supplied,Integer interval,ScheduleSnapshot existing,String newStatus,LocalDateTime now){
        validateInterval(interval);boolean wasTerminal=isTerminal(existing.statusSystemKey()),terminal=isTerminal(newStatus);
        if(interval==null)return existing.intervalDays()!=null?null:supplied;
        if(!Objects.equals(interval,existing.intervalDays())||(wasTerminal&&!terminal))return Objects.requireNonNull(now).plusDays(interval);
        return supplied;
    }
    private static boolean isTerminal(String key){return key!=null&&Set.of("closed","cancelled").contains(key.trim().toLowerCase(Locale.ROOT));}
    static void validateInterval(Integer days){if(days!=null&&(days<1||days>365))throw new IllegalArgumentException("Follow-up interval must be between 1 and 365 days.");}

    private void validateUpdateRefs(Connection con,UpdateMaterialRequestCommand c)throws SQLException{requireExists(con,"dbo.Cases","Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0",c.caseId(),c.shaleClientId());validateMaterialType(con,c.shaleClientId(),c.materialTypeId());if(c.requestedByUserId()!=null)validateUser(con,c.shaleClientId(),c.requestedByUserId());if(c.assignedToUserId()!=null)validateUser(con,c.shaleClientId(),c.assignedToUserId());if(c.requestedFromContactId()!=null)requireExists(con,"dbo.Contacts","Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0",c.requestedFromContactId(),c.shaleClientId());if(c.requestedFromOrganizationId()!=null)requireExists(con,"dbo.Organizations","Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0",c.requestedFromOrganizationId(),c.shaleClientId());}
    record StatusSemantic(String systemKey,String persistedStatus) {}
    record ClosureValues(LocalDateTime closedAt,Integer closedByUserId,String closureReason) {}
    private StatusSemantic resolveStatusSemantic(Connection con,int tenant,String suppliedStatus)throws SQLException{
        String value=blank(suppliedStatus);
        String sql="""
            WITH effective AS (
              SELECT Id,ShaleClientId,SystemKey,Name,IsActive,IsDeleted,
                     ROW_NUMBER() OVER (PARTITION BY SystemKey ORDER BY CASE WHEN ShaleClientId=? THEN 0 ELSE 1 END,Id) rn
              FROM dbo.RequestStatuses
              WHERE (ShaleClientId=? OR ShaleClientId IS NULL) AND SystemKey IS NOT NULL
            ), choices AS (
              SELECT Id,ShaleClientId,SystemKey,Name FROM effective WHERE rn=1 AND IsActive=1 AND IsDeleted=0
              UNION ALL
              SELECT Id,ShaleClientId,SystemKey,Name FROM dbo.RequestStatuses
              WHERE ShaleClientId=? AND SystemKey IS NULL AND IsActive=1 AND IsDeleted=0
            )
            SELECT TOP (1) SystemKey,Name FROM choices
            WHERE LOWER(LTRIM(RTRIM(Name)))=LOWER(?) OR (SystemKey IS NOT NULL AND LOWER(LTRIM(RTRIM(SystemKey)))=LOWER(?))
            ORDER BY CASE WHEN ShaleClientId=? THEN 0 ELSE 1 END,Id
            """;
        try(PreparedStatement ps=con.prepareStatement(sql)){ps.setInt(1,tenant);ps.setInt(2,tenant);ps.setInt(3,tenant);ps.setString(4,value);ps.setString(5,value);ps.setInt(6,tenant);try(ResultSet rs=ps.executeQuery()){
            if(!rs.next())throw new IllegalArgumentException("Request status is not active for this tenant.");
            String key=blank(rs.getString(1));String name=blank(rs.getString(2));
            return new StatusSemantic(key==null?null:key.toLowerCase(Locale.ROOT),key==null?norm(name):norm(key));
        }}
    }
    private ClosureValues findClosureValues(Connection con,UpdateMaterialRequestCommand c)throws SQLException{
        try(PreparedStatement ps=con.prepareStatement("SELECT ClosedAt,ClosedByUserId,ClosureReason FROM dbo.MaterialRequests WHERE ShaleClientId=? AND CaseId=? AND Id=? AND IsDeleted=0")){ps.setInt(1,c.shaleClientId());ps.setLong(2,c.caseId());ps.setLong(3,c.materialRequestId());try(ResultSet rs=ps.executeQuery()){return rs.next()?new ClosureValues(ldt(rs,"ClosedAt"),(Integer)rs.getObject("ClosedByUserId"),rs.getString("ClosureReason")):new ClosureValues(null,null,null);}}
    }
    static ClosureValues normalizeClosure(String statusSystemKey,LocalDateTime suppliedClosedAt,Integer suppliedClosedBy,String suppliedReason,LocalDateTime existingClosedAt,Integer existingClosedBy,String existingReason,int actorUserId,LocalDateTime now){
        String key=statusSystemKey==null?"":statusSystemKey.trim().toLowerCase(Locale.ROOT);
        if(!Set.of("closed","cancelled").contains(key))return new ClosureValues(null,null,null);
        LocalDateTime closedAt=existingClosedAt!=null?existingClosedAt:suppliedClosedAt!=null?suppliedClosedAt:Objects.requireNonNull(now,"now");
        Integer closedBy=existingClosedBy!=null?existingClosedBy:suppliedClosedBy!=null?suppliedClosedBy:actorUserId;
        String reason=existingReason!=null?existingReason:blank(suppliedReason);
        if(reason==null)reason="Status changed to "+key+".";
        return new ClosureValues(closedAt,closedBy,reason);
    }
    private static void validateUpdate(UpdateMaterialRequestCommand c){if(blank(c.title())==null)throw new IllegalArgumentException("Title is required.");if(blank(c.title()).length()>255)throw new IllegalArgumentException("Title is too long.");if(c.materialTypeId()<=0)throw new IllegalArgumentException("Material Type is required.");if(blank(c.requestMethod())==null)throw new IllegalArgumentException("Request Method is required.");if(blank(c.status())==null)throw new IllegalArgumentException("Status is required.");if(c.requestedByUserId()!=null&&c.requestedByUserId()<=0)throw new IllegalArgumentException("Requested By selection is invalid.");if(c.requestedAt()==null)throw new IllegalArgumentException("Requested At is required.");if(c.rowVer()==null||c.rowVer().length==0)throw new IllegalArgumentException("Request version is required.");validateRequestedFrom(c.requestedFromContactId(),c.requestedFromOrganizationId());validateRequestedRange(c.requestedRangeStartDate(),c.requestedRangeEndDate());if(c.expectedResponseDate()!=null&&c.expectedResponseDate().isBefore(c.requestedAt().toLocalDate()))throw new IllegalArgumentException("Due date cannot be before Requested At.");if(c.nextFollowUpAt()!=null&&c.nextFollowUpAt().isBefore(c.requestedAt()))throw new IllegalArgumentException("Next follow-up cannot be before Requested At.");validateInterval(c.followUpIntervalDays());}
    private static Map<EntityActionAuditEvent.MetadataKey,Object> meta(UpdateMaterialRequestCommand c){var m=new EnumMap<EntityActionAuditEvent.MetadataKey,Object>(EntityActionAuditEvent.MetadataKey.class);m.put(EntityActionAuditEvent.MetadataKey.MATERIAL_TYPE_ID,c.materialTypeId());m.put(EntityActionAuditEvent.MetadataKey.REQUEST_STATUS,norm(c.status()));if(c.assignedToUserId()!=null)m.put(EntityActionAuditEvent.MetadataKey.ASSIGNED_TO_USER_ID,c.assignedToUserId());if(c.requestedFromContactId()!=null)m.put(EntityActionAuditEvent.MetadataKey.CONTACT_ID,c.requestedFromContactId());if(c.requestedFromOrganizationId()!=null)m.put(EntityActionAuditEvent.MetadataKey.ORGANIZATION_ID,c.requestedFromOrganizationId());return m;}
    private long insert(Connection con,CreateMaterialRequestCommand c,String persistedStatus,ClosureValues closure,LocalDateTime normalizedNext)throws SQLException{try(PreparedStatement ps=con.prepareStatement("INSERT dbo.MaterialRequests (ShaleClientId,CaseId,MaterialTypeId,Title,Description,RequestedFromContactId,RequestedFromOrganizationId,RequestedFromText,RequestMethod,Status,RequestedByUserId,AssignedToUserId,RequestedAt,RequestedRangeStartDate,RequestedRangeEndDate,ExpectedResponseDate,NextFollowUpAt,FollowUpIntervalDays,ClosedAt,ClosedByUserId,ClosureReason,CreatedByUserId) OUTPUT INSERTED.Id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){int i=1;ps.setInt(i++,c.shaleClientId());ps.setLong(i++,c.caseId());ps.setInt(i++,c.materialTypeId());ps.setString(i++,blank(c.title()));ps.setString(i++,blank(c.description()));setInt(ps,i++,c.requestedFromContactId());setInt(ps,i++,c.requestedFromOrganizationId());ps.setString(i++,blank(c.requestedFromText()));ps.setString(i++,norm(c.requestMethod()));ps.setString(i++,persistedStatus);setInt(ps,i++,c.requestedByUserId());setInt(ps,i++,c.assignedToUserId());setTs(ps,i++,c.requestedAt());setDate(ps,i++,c.requestedRangeStartDate());setDate(ps,i++,c.requestedRangeEndDate());setDate(ps,i++,c.expectedResponseDate());setTs(ps,i++,normalizedNext);setInt(ps,i++,c.followUpIntervalDays());setTs(ps,i++,closure.closedAt());setInt(ps,i++,closure.closedByUserId());ps.setString(i++,closure.closureReason());ps.setInt(i++,c.actorUserId());try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getLong(1);}}}
    private void validateRefs(Connection con,CreateMaterialRequestCommand c)throws SQLException{requireExists(con,"dbo.Cases","Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0",c.caseId(),c.shaleClientId());validateMaterialType(con,c.shaleClientId(),c.materialTypeId());if(c.requestedByUserId()!=null)validateUser(con,c.shaleClientId(),c.requestedByUserId());if(c.assignedToUserId()!=null)validateUser(con,c.shaleClientId(),c.assignedToUserId());if(c.requestedFromContactId()!=null)requireExists(con,"dbo.Contacts","Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0",c.requestedFromContactId(),c.shaleClientId());if(c.requestedFromOrganizationId()!=null)requireExists(con,"dbo.Organizations","Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0",c.requestedFromOrganizationId(),c.shaleClientId());}
    private void validateMaterialType(Connection con,int t,int id)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT ShaleClientId,SystemKey,IsActive,IsDeleted FROM dbo.MaterialTypes WHERE Id=? AND (ShaleClientId=? OR ShaleClientId IS NULL)")){ps.setInt(1,id);ps.setInt(2,t);try(ResultSet rs=ps.executeQuery()){if(!rs.next()||!rs.getBoolean("IsActive")||rs.getBoolean("IsDeleted"))throw new IllegalArgumentException("Material type is not active for this tenant.");String key=rs.getString("SystemKey");Integer owner=(Integer)rs.getObject("ShaleClientId");if(owner==null&&key!=null)try(PreparedStatement m=con.prepareStatement("SELECT 1 FROM dbo.MaterialTypes WHERE ShaleClientId=? AND SystemKey=?")){m.setInt(1,t);m.setString(2,key);try(ResultSet mr=m.executeQuery()){if(mr.next())throw new IllegalArgumentException("Material type is masked by tenant override.");}}}}}
    private void validateUser(Connection con,int t,int u)throws SQLException{requireExists(con,"dbo.Users","Id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0",u,t);}
    private static void validateRequestedRange(LocalDate start,LocalDate end){if(start!=null&&end!=null&&start.isAfter(end))throw new IllegalArgumentException("Requested Date Start cannot be after Requested Date End.");}
    private static void validateRequestedFrom(Integer contactId,Integer organizationId){if(contactId==null&&organizationId==null)throw new IllegalArgumentException("Requested From is required.");if(contactId!=null&&organizationId!=null)throw new IllegalArgumentException("Choose either a Requested From contact or organization, not both.");if(contactId!=null&&contactId<=0||organizationId!=null&&organizationId<=0)throw new IllegalArgumentException("Requested From selection is invalid.");}
    private static void validateCreate(CreateMaterialRequestCommand c){if(blank(c.title())==null)throw new IllegalArgumentException("Title is required.");if(blank(c.title()).length()>255)throw new IllegalArgumentException("Title is too long.");if(c.materialTypeId()<=0)throw new IllegalArgumentException("Material Type is required.");if(blank(c.requestMethod())==null)throw new IllegalArgumentException("Request Method is required.");if(blank(c.status())==null)throw new IllegalArgumentException("Status is required.");if(c.requestedByUserId()!=null&&c.requestedByUserId()<=0)throw new IllegalArgumentException("Requested By selection is invalid.");if(c.requestedAt()==null)throw new IllegalArgumentException("Requested At is required.");validateRequestedFrom(c.requestedFromContactId(),c.requestedFromOrganizationId());validateRequestedRange(c.requestedRangeStartDate(),c.requestedRangeEndDate());if(c.expectedResponseDate()!=null&&c.expectedResponseDate().isBefore(c.requestedAt().toLocalDate()))throw new IllegalArgumentException("Due date cannot be before Requested At.");if(c.nextFollowUpAt()!=null&&c.nextFollowUpAt().isBefore(c.requestedAt()))throw new IllegalArgumentException("Next follow-up cannot be before Requested At.");validateInterval(c.followUpIntervalDays());}
    private void auditPhiCreate(Connection con,int actor,long id,CreateMaterialRequestCommand c){phi.auditCreate(con,actor,"MaterialRequests","Title",id,c.title());phi.auditCreate(con,actor,"MaterialRequests","Description",id,c.description());phi.auditCreate(con,actor,"MaterialRequests","RequestedFromText",id,c.requestedFromText());}
    private void audit(Connection con,int t,int actor,EntityActionAuditEvent.Action a,long id,long caseId,Map<EntityActionAuditEvent.MetadataKey,?> md)throws SQLException{var m=new EnumMap<EntityActionAuditEvent.MetadataKey,Object>(EntityActionAuditEvent.MetadataKey.class);m.put(EntityActionAuditEvent.MetadataKey.CASE_ID,caseId);if(md!=null)m.putAll(md);actionDao.append(con,EntityActionAuditEvent.now(t,actor,EntityActionAuditEvent.EntityType.MATERIAL_REQUEST,id,a,EntityActionAuditEvent.EntityType.MATERIAL_REQUEST,id,m));}
    private static Map<EntityActionAuditEvent.MetadataKey,Object> meta(CreateMaterialRequestCommand c){var m=new EnumMap<EntityActionAuditEvent.MetadataKey,Object>(EntityActionAuditEvent.MetadataKey.class);m.put(EntityActionAuditEvent.MetadataKey.MATERIAL_TYPE_ID,c.materialTypeId());m.put(EntityActionAuditEvent.MetadataKey.REQUEST_STATUS,norm(c.status()));if(c.assignedToUserId()!=null)m.put(EntityActionAuditEvent.MetadataKey.ASSIGNED_TO_USER_ID,c.assignedToUserId());if(c.requestedFromContactId()!=null)m.put(EntityActionAuditEvent.MetadataKey.CONTACT_ID,c.requestedFromContactId());if(c.requestedFromOrganizationId()!=null)m.put(EntityActionAuditEvent.MetadataKey.ORGANIZATION_ID,c.requestedFromOrganizationId());return m;}
    private static void requireExists(Connection con,String table,String where,Object a,Object b)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM "+table+" WHERE "+where)){ps.setObject(1,a);ps.setObject(2,b);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Referenced record not found.");}}}
    private static void touchCase(Connection con,long caseId,int t)throws SQLException{try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.Cases SET UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND ShaleClientId=?")){ps.setLong(1,caseId);ps.setInt(2,t);ps.executeUpdate();}}
    private static void verifyTenant(Connection con,int t)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT CAST(SESSION_CONTEXT(N'ShaleClientId') AS INT)" );ResultSet rs=ps.executeQuery()){if(!rs.next()||rs.getInt(1)!=t)throw new IllegalStateException("ShaleClientId session context mismatch.");}}
    private static RuntimeException sqlFailure(SQLException e){return new IllegalStateException("Database operation failed.",e);}
    private static void rollback(Connection c){try{c.rollback();}catch(Exception ignored){}}
    private static String norm(String s){return s==null?null:s.trim().toUpperCase(Locale.ROOT);} private static String blank(String s){return s==null||s.trim().isEmpty()?null:s.trim();}
    private static LocalDateTime ldt(ResultSet rs,String c)throws SQLException{Timestamp t=rs.getTimestamp(c);return t==null?null:t.toLocalDateTime();} private static LocalDate ld(ResultSet rs,String c)throws SQLException{java.sql.Date d=rs.getDate(c);return d==null?null:d.toLocalDate();}
    private static void setTs(PreparedStatement ps,int i,LocalDateTime v)throws SQLException{if(v==null)ps.setNull(i,Types.TIMESTAMP);else ps.setTimestamp(i,Timestamp.valueOf(v));} private static void setDate(PreparedStatement ps,int i,LocalDate v)throws SQLException{if(v==null)ps.setNull(i,Types.DATE);else ps.setDate(i,java.sql.Date.valueOf(v));} private static void setInt(PreparedStatement ps,int i,Integer v)throws SQLException{if(v==null)ps.setNull(i,Types.INTEGER);else ps.setInt(i,v);}
    private interface SqlWork<T>{T run(Connection con)throws Exception;} private interface SqlVoid{void run(Connection con)throws Exception;}
    private <T>T mutate(int t,int actor,SqlWork<T>w){try(Connection con=db.requireConnection()){verifyTenant(con,t);con.setAutoCommit(false);try{validateAdmin(con,t,actor);T out=w.run(con);con.commit();return out;}catch(RuntimeException e){rollback(con);throw e;}catch(Exception e){rollback(con);throw new IllegalStateException("Database operation failed.", e);}finally{con.setAutoCommit(true);}}catch(SQLException e){throw sqlFailure(e);}}
    private void mutateVoid(int t,int actor,SqlVoid w){mutate(t,actor,con->{w.run(con);return null;});}
    private void validateAdmin(Connection con,int t,int actor)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo.Users WHERE Id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0 AND is_admin=1")){ps.setInt(1,actor);ps.setInt(2,t);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Actor user is not an active admin for this tenant.");}}}
    private static void requireRowVer(byte[] rv){if(rv==null||rv.length==0)throw new IllegalArgumentException("Request version is required.");}
    private static String nreq(String v,int max){String s=blank(v);if(s==null)throw new IllegalArgumentException("Name is required.");if(s.length()>max)throw new IllegalArgumentException("Name is too long.");return s;}
    private static String nopt(String v,int max){String s=blank(v);if(s!=null&&s.length()>max)throw new IllegalArgumentException("Value is too long.");return s;}
    private static String sk(String v){String s=blank(v);return s==null?null:s.toLowerCase(Locale.ROOT);}
    private static int so(Integer v){return v==null?0:v;}
    private static void assertRowVer(Connection con,String table,int id,byte[] rv,String msg)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM "+table+" WHERE Id=? AND RowVer=?")){ps.setInt(1,id);ps.setBytes(2,rv);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalStateException("Optimistic conflict: "+msg);}}}
    private static void requireAvailable(Object dto,int t,String label){Integer owner=null;if(dto instanceof MaterialTypeDto d)owner=d.shaleClientId();else if(dto instanceof RequestMethodDto d)owner=d.shaleClientId();else if(dto instanceof RequestStatusDto d)owner=d.shaleClientId();if(dto==null||(owner!=null&&owner!=t))throw new IllegalArgumentException(label+" is not available for this tenant.");}
    private List<MaterialTypeDto> listMaterialTypes(String where,int t,int actor){try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement("SELECT Id,ShaleClientId,SystemKey,Name,Description,Color,SortOrder,IsActive,IsDeleted,RowVer FROM dbo.MaterialTypes "+where)){verifyTenant(con,t);validateAdmin(con,t,actor);ps.setInt(1,t);try(ResultSet rs=ps.executeQuery()){List<MaterialTypeDto> out=new ArrayList<>();while(rs.next())out.add(new MaterialTypeDto(rs.getInt(1),(Integer)rs.getObject(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getInt(7),rs.getBoolean(8),rs.getBoolean(9),rs.getBytes(10)));return out;}}catch(SQLException e){throw sqlFailure(e);}}
    private List<RequestMethodDto> listRequestMethods(String where,int t,int actor){try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement("SELECT Id,ShaleClientId,SystemKey,Name,Color,SortOrder,IsActive,IsDeleted,RowVer FROM dbo.RequestMethods "+where)){verifyTenant(con,t);validateAdmin(con,t,actor);ps.setInt(1,t);try(ResultSet rs=ps.executeQuery()){List<RequestMethodDto> out=new ArrayList<>();while(rs.next())out.add(new RequestMethodDto(rs.getInt(1),(Integer)rs.getObject(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getInt(6),rs.getBoolean(7),rs.getBoolean(8),rs.getBytes(9)));return out;}}catch(SQLException e){throw sqlFailure(e);}}
    private List<RequestStatusDto> listRequestStatuses(String where,int t,int actor){try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement("SELECT Id,ShaleClientId,SystemKey,Name,Color,SortOrder,IsActive,IsDeleted,RowVer FROM dbo.RequestStatuses "+where)){verifyTenant(con,t);validateAdmin(con,t,actor);ps.setInt(1,t);try(ResultSet rs=ps.executeQuery()){List<RequestStatusDto> out=new ArrayList<>();while(rs.next())out.add(new RequestStatusDto(rs.getInt(1),(Integer)rs.getObject(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getInt(6),rs.getBoolean(7),rs.getBoolean(8),rs.getBytes(9)));return out;}}catch(SQLException e){throw sqlFailure(e);}}
    private MaterialTypeDto findMaterialType(Connection con,Integer id)throws SQLException{if(id==null)return null;try(PreparedStatement ps=con.prepareStatement("SELECT Id,ShaleClientId,SystemKey,Name,Description,Color,SortOrder,IsActive,IsDeleted,RowVer FROM dbo.MaterialTypes WHERE Id=?")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?new MaterialTypeDto(rs.getInt(1),(Integer)rs.getObject(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getInt(7),rs.getBoolean(8),rs.getBoolean(9),rs.getBytes(10)):null;}}}
    private RequestMethodDto findRequestMethod(Connection con,Integer id)throws SQLException{if(id==null)return null;try(PreparedStatement ps=con.prepareStatement("SELECT Id,ShaleClientId,SystemKey,Name,Color,SortOrder,IsActive,IsDeleted,RowVer FROM dbo.RequestMethods WHERE Id=?")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?new RequestMethodDto(rs.getInt(1),(Integer)rs.getObject(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getInt(6),rs.getBoolean(7),rs.getBoolean(8),rs.getBytes(9)):null;}}}
    private RequestStatusDto findRequestStatus(Connection con,Integer id)throws SQLException{if(id==null)return null;try(PreparedStatement ps=con.prepareStatement("SELECT Id,ShaleClientId,SystemKey,Name,Color,SortOrder,IsActive,IsDeleted,RowVer FROM dbo.RequestStatuses WHERE Id=?")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?new RequestStatusDto(rs.getInt(1),(Integer)rs.getObject(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getInt(6),rs.getBoolean(7),rs.getBoolean(8),rs.getBytes(9)):null;}}}
    private Integer findTenantOverrideId(Connection con,String table,int t,String key)throws SQLException{if(key==null)return null;try(PreparedStatement ps=con.prepareStatement("SELECT Id FROM "+table+" WHERE ShaleClientId=? AND SystemKey=?")){ps.setInt(1,t);ps.setString(2,key);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getInt(1):null;}}}
    private MaterialTypeDto insertMaterialType(Connection con,MaterialTypeCommand c)throws SQLException{try(PreparedStatement ps=con.prepareStatement("INSERT dbo.MaterialTypes(ShaleClientId,SystemKey,Name,Description,Color,SortOrder,IsActive,IsDeleted,CreatedByUserId,UpdatedByUserId,CreatedAt,UpdatedAt) VALUES(?,?,?,?,?,?,?,0,?,?,SYSUTCDATETIME(),SYSUTCDATETIME())",Statement.RETURN_GENERATED_KEYS)){ps.setInt(1,c.shaleClientId());ps.setString(2,sk(c.systemKey()));ps.setString(3,nreq(c.name(),120));ps.setString(4,nopt(c.description(),4000));ps.setString(5,nopt(c.color(),32));ps.setInt(6,so(c.sortOrder()));ps.setBoolean(7,c.active());ps.setInt(8,c.actorUserId());ps.setInt(9,c.actorUserId());ps.executeUpdate();try(ResultSet rs=ps.getGeneratedKeys()){rs.next();return findMaterialType(con,rs.getInt(1));}}}
    private RequestMethodDto insertRequestMethod(Connection con,RequestMethodCommand c)throws SQLException{try(PreparedStatement ps=con.prepareStatement("INSERT dbo.RequestMethods(ShaleClientId,SystemKey,Name,Color,SortOrder,IsActive,IsDeleted,CreatedAt,UpdatedAt) VALUES(?,?,?,?,?,?,0,SYSUTCDATETIME(),SYSUTCDATETIME())",Statement.RETURN_GENERATED_KEYS)){ps.setInt(1,c.shaleClientId());ps.setString(2,sk(c.systemKey()));ps.setString(3,nreq(c.name(),120));ps.setString(4,nopt(c.color(),20));ps.setInt(5,c.sortOrder()==null?nextRequestMethodSortOrder(con,c.shaleClientId()):c.sortOrder());ps.setBoolean(6,c.active());ps.executeUpdate();try(ResultSet rs=ps.getGeneratedKeys()){rs.next();return findRequestMethod(con,rs.getInt(1));}}}
    private RequestStatusDto insertRequestStatus(Connection con,RequestStatusCommand c)throws SQLException{try(PreparedStatement ps=con.prepareStatement("INSERT dbo.RequestStatuses(ShaleClientId,SystemKey,Name,Color,SortOrder,IsActive,IsDeleted,CreatedAt,UpdatedAt) VALUES(?,?,?,?,?,?,0,SYSUTCDATETIME(),SYSUTCDATETIME())",Statement.RETURN_GENERATED_KEYS)){ps.setInt(1,c.shaleClientId());ps.setString(2,sk(c.systemKey()));ps.setString(3,nreq(c.name(),120));ps.setString(4,nopt(c.color(),32));ps.setInt(5,c.sortOrder()==null||c.sortOrder()<=0?nextRequestStatusSortOrder(con,c.shaleClientId()):c.sortOrder());ps.setBoolean(6,c.active());ps.executeUpdate();try(ResultSet rs=ps.getGeneratedKeys()){rs.next();return findRequestStatus(con,rs.getInt(1));}}}
    private MaterialTypeDto upsertMaterialTypeOverride(Connection con,MaterialTypeCommand c,MaterialTypeDto g)throws SQLException{Integer id=findTenantOverrideId(con,"dbo.MaterialTypes",c.shaleClientId(),g.systemKey());return id==null?insertMaterialType(con,new MaterialTypeCommand(null,c.shaleClientId(),c.actorUserId(),c.name(),c.description(),c.color(),c.active(),g.systemKey(),c.sortOrder(),null)):updateMaterialTypeRow(con,new MaterialTypeCommand(id,c.shaleClientId(),c.actorUserId(),c.name(),c.description(),c.color(),c.active(),g.systemKey(),c.sortOrder(),findMaterialType(con,id).rowVer()),id,findMaterialType(con,id).rowVer());}
    private RequestMethodDto upsertRequestMethodOverride(Connection con,RequestMethodCommand c,RequestMethodDto g)throws SQLException{Integer id=findTenantOverrideId(con,"dbo.RequestMethods",c.shaleClientId(),g.systemKey());return id==null?insertRequestMethod(con,new RequestMethodCommand(null,c.shaleClientId(),c.actorUserId(),c.name(),c.color(),c.active(),g.systemKey(),g.sortOrder(),null)):updateRequestMethodRow(con,new RequestMethodCommand(id,c.shaleClientId(),c.actorUserId(),c.name(),c.color(),c.active(),g.systemKey(),g.sortOrder(),findRequestMethod(con,id).rowVer()),id,findRequestMethod(con,id).rowVer());}
    private RequestStatusDto upsertRequestStatusOverride(Connection con,RequestStatusCommand c,RequestStatusDto g)throws SQLException{Integer id=findTenantOverrideId(con,"dbo.RequestStatuses",c.shaleClientId(),g.systemKey());return id==null?insertRequestStatus(con,new RequestStatusCommand(null,c.shaleClientId(),c.actorUserId(),c.name(),c.color(),c.active(),g.systemKey(),c.sortOrder(),null)):updateRequestStatusRow(con,new RequestStatusCommand(id,c.shaleClientId(),c.actorUserId(),c.name(),c.color(),c.active(),g.systemKey(),c.sortOrder(),findRequestStatus(con,id).rowVer()),id,findRequestStatus(con,id).rowVer());}
    private MaterialTypeDto updateMaterialTypeRow(Connection con,MaterialTypeCommand c,int id,byte[] rv)throws SQLException{try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.MaterialTypes SET Name=?,Description=?,Color=?,SortOrder=?,IsActive=?,IsDeleted=0,SystemKey=?,UpdatedByUserId=?,UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND ShaleClientId=? AND RowVer=?")){ps.setString(1,nreq(c.name(),120));ps.setString(2,nopt(c.description(),4000));ps.setString(3,nopt(c.color(),32));ps.setInt(4,so(c.sortOrder()));ps.setBoolean(5,c.active());ps.setString(6,sk(c.systemKey()));ps.setInt(7,c.actorUserId());ps.setInt(8,id);ps.setInt(9,c.shaleClientId());ps.setBytes(10,rv);if(ps.executeUpdate()!=1)throw new IllegalStateException("Optimistic conflict: material type changed.");return findMaterialType(con,id);}}
    private RequestMethodDto updateRequestMethodRow(Connection con,RequestMethodCommand c,int id,byte[] rv)throws SQLException{try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.RequestMethods SET Name=?,Color=?,SortOrder=?,IsActive=?,IsDeleted=0,SystemKey=?,UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND ShaleClientId=? AND RowVer=?")){ps.setString(1,nreq(c.name(),120));ps.setString(2,nopt(c.color(),20));ps.setInt(3,c.sortOrder());ps.setBoolean(4,c.active());ps.setString(5,sk(c.systemKey()));ps.setInt(6,id);ps.setInt(7,c.shaleClientId());ps.setBytes(8,rv);if(ps.executeUpdate()!=1)throw new IllegalStateException("Optimistic conflict: request method changed.");return findRequestMethod(con,id);}}
    private RequestStatusDto updateRequestStatusRow(Connection con,RequestStatusCommand c,int id,byte[] rv)throws SQLException{try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.RequestStatuses SET Name=?,Color=?,SortOrder=?,IsActive=?,IsDeleted=0,SystemKey=?,UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND ShaleClientId=? AND RowVer=?")){ps.setString(1,nreq(c.name(),120));ps.setString(2,nopt(c.color(),32));ps.setInt(3,so(c.sortOrder()));ps.setBoolean(4,c.active());ps.setString(5,sk(c.systemKey()));ps.setInt(6,id);ps.setInt(7,c.shaleClientId());ps.setBytes(8,rv);if(ps.executeUpdate()!=1)throw new IllegalStateException("Optimistic conflict: request status changed.");return findRequestStatus(con,id);}}
    private void softDeleteOverride(Connection con,String table,int id,int t,int actor,String key)throws SQLException{Integer target=id;try(PreparedStatement own=con.prepareStatement("SELECT ShaleClientId,SystemKey FROM "+table+" WHERE Id=?")){own.setInt(1,id);try(ResultSet rs=own.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Lookup value is not available for this tenant.");Integer owner=(Integer)rs.getObject(1);String k=rs.getString(2);if(owner==null)target=findTenantOverrideId(con,table,t,k);else if(owner!=t)throw new IllegalArgumentException("Lookup value is not available for this tenant.");}} if(target==null)return;try(PreparedStatement ps=con.prepareStatement("UPDATE "+table+" SET IsDeleted=1,IsActive=0,UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND ShaleClientId=?")){ps.setInt(1,target);ps.setInt(2,t);if(ps.executeUpdate()!=1)throw new IllegalArgumentException("Lookup value is not available for this tenant.");}}
}
