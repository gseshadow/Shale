package com.shale.data.dao;

import com.shale.core.dto.*;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.MaterialRequestServicePort.CreateMaterialRequestCommand;
import com.shale.core.service.MaterialRequestServicePort.UpdateMaterialRequestCommand;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class MaterialRequestDao {
    private final DbSessionProvider db; private final EntityActionAuditDao actionDao=new EntityActionAuditDao(); private final PhiAuditService phi;
    public MaterialRequestDao(DbSessionProvider db) { this.db = Objects.requireNonNull(db); this.phi=new PhiAuditService(new AuditLogDao(db)); }

    public List<MaterialTypeDto> listEffectiveMaterialTypes(int shaleClientId) {
        String sql = """
                WITH visible AS (
                  SELECT Id, ShaleClientId, SystemKey, Name, Description, Color, SortOrder, IsActive, IsDeleted,
                         ROW_NUMBER() OVER (PARTITION BY SystemKey ORDER BY CASE WHEN ShaleClientId = ? THEN 0 ELSE 1 END, Id) AS rn
                  FROM dbo.MaterialTypes
                  WHERE (ShaleClientId = ? OR ShaleClientId IS NULL) AND SystemKey IS NOT NULL
                )
                SELECT Id, ShaleClientId, SystemKey, Name, Description, Color, SortOrder FROM visible
                WHERE rn = 1 AND IsDeleted = 0 AND IsActive = 1
                UNION ALL
                SELECT Id, ShaleClientId, SystemKey, Name, Description, Color, SortOrder
                FROM dbo.MaterialTypes
                WHERE ShaleClientId = ? AND SystemKey IS NULL AND IsDeleted = 0 AND IsActive = 1
                ORDER BY SortOrder, Name, Id
                """;
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            verifyTenant(con, shaleClientId); ps.setInt(1, shaleClientId); ps.setInt(2, shaleClientId); ps.setInt(3, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) { List<MaterialTypeDto> out = new ArrayList<>(); while (rs.next()) out.add(new MaterialTypeDto(rs.getInt(1), (Integer)rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7))); return out; }
        } catch (SQLException e) { throw sqlFailure(e); }
    }

    public List<RequestMethodDto> listEffectiveRequestMethods(int shaleClientId) {
        String sql = effectiveRequestLookupSql("dbo.RequestMethods");
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            verifyTenant(con, shaleClientId);
            ps.setInt(1, shaleClientId); ps.setInt(2, shaleClientId); ps.setInt(3, shaleClientId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RequestMethodDto> out = new ArrayList<>();
                while (rs.next()) out.add(new RequestMethodDto(rs.getInt(1), (Integer) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getBoolean(6), rs.getBoolean(7)));
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
                while (rs.next()) out.add(new RequestStatusDto(rs.getInt(1), (Integer) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getBoolean(6), rs.getBoolean(7)));
                return out;
            }
        } catch (SQLException e) { throw sqlFailure(e); }
    }

    private static String effectiveRequestLookupSql(String tableName) {
        return """
                WITH visible AS (
                  SELECT Id, ShaleClientId, SystemKey, Name, SortOrder, IsActive, IsDeleted,
                         ROW_NUMBER() OVER (PARTITION BY SystemKey ORDER BY CASE WHEN ShaleClientId = ? THEN 0 ELSE 1 END, Id) AS rn
                  FROM %s
                  WHERE (ShaleClientId = ? OR ShaleClientId IS NULL) AND SystemKey IS NOT NULL
                )
                SELECT Id, ShaleClientId, SystemKey, Name, SortOrder, IsActive, IsDeleted FROM visible
                WHERE rn = 1 AND IsDeleted = 0 AND IsActive = 1
                UNION ALL
                SELECT Id, ShaleClientId, SystemKey, Name, SortOrder, IsActive, IsDeleted
                FROM %s
                WHERE ShaleClientId = ? AND SystemKey IS NULL AND IsDeleted = 0 AND IsActive = 1
                ORDER BY SortOrder, Name, Id
                """.formatted(tableName, tableName);
    }

    public List<MaterialRequestSummaryDto> listMaterialRequests(long caseId, int tenant) {
        String sql = baseSelect(false) + " WHERE mr.ShaleClientId=? AND mr.CaseId=? AND mr.IsDeleted=0 AND ISNULL(c.IsDeleted,0)=0 ORDER BY mr.NextFollowUpAt, mr.RequestedAt DESC, mr.Id DESC";
        try (Connection con=db.requireConnection(); PreparedStatement ps=con.prepareStatement(sql)) { verifyTenant(con, tenant); ps.setInt(1,tenant); ps.setLong(2,caseId); try(ResultSet rs=ps.executeQuery()){List<MaterialRequestSummaryDto> out=new ArrayList<>(); while(rs.next()) out.add(mapSummary(rs)); return out;}} catch(SQLException e){throw sqlFailure(e);} }

    public MaterialRequestDetailDto findMaterialRequest(long caseId,long id,int tenant){
        String sql = baseSelect(true) + " WHERE mr.ShaleClientId=? AND mr.CaseId=? AND mr.Id=? AND mr.IsDeleted=0 AND ISNULL(c.IsDeleted,0)=0";
        try(Connection con=db.requireConnection(); PreparedStatement ps=con.prepareStatement(sql)){ verifyTenant(con,tenant); ps.setInt(1,tenant); ps.setLong(2,caseId); ps.setLong(3,id); try(ResultSet rs=ps.executeQuery()){return rs.next()?mapDetail(rs):null;}} catch(SQLException e){throw sqlFailure(e);} }

    public MaterialRequestDetailDto create(CreateMaterialRequestCommand c){validateCreate(c);try(Connection con=db.requireConnection()){verifyTenant(con,c.shaleClientId());con.setAutoCommit(false);try{validateRefs(con,c);long id=insert(con,c);touchCase(con,c.caseId(),c.shaleClientId());auditPhiCreate(con,c.actorUserId(),id,c);audit(con,c.shaleClientId(),c.actorUserId(),EntityActionAuditEvent.Action.CREATED,id,c.caseId(),meta(c));con.commit();return findMaterialRequest(c.caseId(),id,c.shaleClientId());}catch(Exception ex){rollback(con);throw ex;}}catch(SQLException e){throw sqlFailure(e);}}

    public MaterialRequestDetailDto update(UpdateMaterialRequestCommand c){validateUpdate(c);try(Connection con=db.requireConnection()){verifyTenant(con,c.shaleClientId());con.setAutoCommit(false);try{validateUpdateRefs(con,c);int rows=updateRow(con,c);if(rows==0)throw new IllegalStateException("Material request has changed. Please reload and try again.");touchCase(con,c.caseId(),c.shaleClientId());audit(con,c.shaleClientId(),c.actorUserId(),EntityActionAuditEvent.Action.UPDATED,c.materialRequestId(),c.caseId(),meta(c));con.commit();return findMaterialRequest(c.caseId(),c.materialRequestId(),c.shaleClientId());}catch(Exception ex){rollback(con);throw ex;}}catch(SQLException e){throw sqlFailure(e);}}

    public List<MaterialRequestFollowUpDto> listFollowUps(long caseId,long requestId,int tenant){ String sql=""" 
        SELECT f.*, %s AS AttemptedByDisplayName FROM dbo.MaterialRequestFollowUps f JOIN dbo.MaterialRequests mr ON mr.Id=f.MaterialRequestId AND mr.ShaleClientId=f.ShaleClientId AND mr.CaseId=f.CaseId JOIN dbo.Users u ON u.Id=f.AttemptedByUserId AND u.ShaleClientId=f.ShaleClientId WHERE f.ShaleClientId=? AND f.CaseId=? AND f.MaterialRequestId=? AND mr.IsDeleted=0 ORDER BY f.AttemptedAt, f.Id
        """.formatted(userDisplayName("u")); try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement(sql)){verifyTenant(con,tenant);ps.setInt(1,tenant);ps.setLong(2,caseId);ps.setLong(3,requestId);try(ResultSet rs=ps.executeQuery()){List<MaterialRequestFollowUpDto> out=new ArrayList<>();while(rs.next())out.add(mapFollowUp(rs));return out;}}catch(SQLException e){throw sqlFailure(e);} }


    private static String baseSelect(boolean detail){ return """
        SELECT mr.Id,mr.ShaleClientId,mr.CaseId,mr.MaterialTypeId,mt.Name AS MaterialTypeName,mt.SystemKey AS MaterialTypeSystemKey,mt.Color AS MaterialTypeColor,mr.Title,
               %s mr.RequestedByUserId, %s AS RequestedByDisplayName, rbu.Color AS RequestedByUserColor, mr.AssignedToUserId, %s AS AssignedToDisplayName, au.Color AS AssignedToUserColor,
               mr.RequestedFromContactId, %s AS RequestedFromContactDisplayName, mr.RequestedFromOrganizationId, org.Name AS RequestedFromOrganizationName, mr.RequestedFromText,
               mr.RequestMethod,mr.RequestedAt,mr.RelevantStartDate,mr.RelevantEndDate,mr.Status,mr.ExpectedResponseDate,mr.NextFollowUpAt,CAST(NULL AS datetime2) AS LastFollowUpAt,mr.FirstReceivedAt,mr.FullyReceivedAt,mr.ClosedAt,mr.ClosedByUserId,mr.ClosureReason,mr.Notes,mr.CreatedAt,mr.CreatedByUserId,mr.UpdatedAt,mr.UpdatedByUserId,mr.RowVer
        FROM dbo.MaterialRequests mr JOIN dbo.Cases c ON c.Id=mr.CaseId AND c.ShaleClientId=mr.ShaleClientId
        JOIN dbo.MaterialTypes mt ON mt.Id=mr.MaterialTypeId AND (mt.ShaleClientId=mr.ShaleClientId OR mt.ShaleClientId IS NULL)
        JOIN dbo.Users rbu ON rbu.Id=mr.RequestedByUserId AND rbu.ShaleClientId=mr.ShaleClientId AND ISNULL(rbu.is_deleted,0)=0
        LEFT JOIN dbo.Users au ON au.Id=mr.AssignedToUserId AND au.ShaleClientId=mr.ShaleClientId AND ISNULL(au.is_deleted,0)=0
        LEFT JOIN dbo.Contacts ct ON ct.Id=mr.RequestedFromContactId AND ct.ShaleClientId=mr.ShaleClientId AND ISNULL(ct.IsDeleted,0)=0
        LEFT JOIN dbo.Organizations org ON org.Id=mr.RequestedFromOrganizationId AND org.ShaleClientId=mr.ShaleClientId AND ISNULL(org.IsDeleted,0)=0
        """.formatted(detail?"mr.Description,":"CAST(NULL AS nvarchar(max)) AS Description,", userDisplayName("rbu"), userDisplayName("au"), contactDisplayName("ct")); }
    private static String userDisplayName(String alias){return "LTRIM(RTRIM(COALESCE("+alias+".name_first, '') + CASE WHEN COALESCE("+alias+".name_first, '') = '' OR COALESCE("+alias+".name_last, '') = '' THEN '' ELSE ' ' END + COALESCE("+alias+".name_last, '')))";}
    private static String contactDisplayName(String alias){return "COALESCE(NULLIF(LTRIM(RTRIM("+alias+".Name)), ''), NULLIF(LTRIM(RTRIM(CONCAT("+alias+".FirstName, ' ', "+alias+".LastName))), ''), NULLIF(LTRIM(RTRIM("+alias+".WorkName)), ''))";}
    private static MaterialRequestSummaryDto mapSummary(ResultSet rs)throws SQLException{return new MaterialRequestSummaryDto(rs.getLong("Id"),rs.getInt("ShaleClientId"),rs.getLong("CaseId"),rs.getInt("MaterialTypeId"),rs.getString("MaterialTypeName"),rs.getString("MaterialTypeSystemKey"),rs.getString("MaterialTypeColor"),rs.getString("Title"),rs.getInt("RequestedByUserId"),rs.getString("RequestedByDisplayName"),rs.getString("RequestedByUserColor"),(Integer)rs.getObject("AssignedToUserId"),rs.getString("AssignedToDisplayName"),rs.getString("AssignedToUserColor"),(Integer)rs.getObject("RequestedFromContactId"),rs.getString("RequestedFromContactDisplayName"),(Integer)rs.getObject("RequestedFromOrganizationId"),rs.getString("RequestedFromOrganizationName"),rs.getString("RequestedFromText"),rs.getString("RequestMethod"),ldt(rs,"RequestedAt"),rs.getString("Status"),ldt(rs,"ExpectedResponseDate"),ldt(rs,"NextFollowUpAt"),ldt(rs,"LastFollowUpAt"),ldt(rs,"UpdatedAt"),rs.getBytes("RowVer"));}
    private static MaterialRequestDetailDto mapDetail(ResultSet rs)throws SQLException{return new MaterialRequestDetailDto(rs.getLong("Id"),rs.getInt("ShaleClientId"),rs.getLong("CaseId"),rs.getInt("MaterialTypeId"),rs.getString("MaterialTypeName"),rs.getString("MaterialTypeSystemKey"),rs.getString("Title"),rs.getString("Description"),rs.getInt("RequestedByUserId"),rs.getString("RequestedByDisplayName"),(Integer)rs.getObject("AssignedToUserId"),rs.getString("AssignedToDisplayName"),(Integer)rs.getObject("RequestedFromContactId"),rs.getString("RequestedFromContactDisplayName"),(Integer)rs.getObject("RequestedFromOrganizationId"),rs.getString("RequestedFromOrganizationName"),rs.getString("RequestedFromText"),rs.getString("RequestMethod"),ldt(rs,"RequestedAt"),ld(rs,"RelevantStartDate"),ld(rs,"RelevantEndDate"),rs.getString("Status"),ld(rs,"ExpectedResponseDate"),ldt(rs,"NextFollowUpAt"),ldt(rs,"LastFollowUpAt"),ldt(rs,"FirstReceivedAt"),ldt(rs,"FullyReceivedAt"),ldt(rs,"ClosedAt"),(Integer)rs.getObject("ClosedByUserId"),rs.getString("ClosureReason"),rs.getString("Notes"),ldt(rs,"CreatedAt"),rs.getInt("CreatedByUserId"),ldt(rs,"UpdatedAt"),(Integer)rs.getObject("UpdatedByUserId"),rs.getBytes("RowVer"));}
        private static MaterialRequestFollowUpDto mapFollowUp(ResultSet rs)throws SQLException{return new MaterialRequestFollowUpDto(rs.getLong("Id"),rs.getInt("ShaleClientId"),rs.getLong("MaterialRequestId"),rs.getLong("CaseId"),ldt(rs,"AttemptedAt"),rs.getInt("AttemptedByUserId"),rs.getString("AttemptedByDisplayName"),rs.getString("Method"),rs.getString("Outcome"),ldt(rs,"NextFollowUpAt"),rs.getString("Notes"),ldt(rs,"CreatedAt"),rs.getInt("CreatedByUserId"),rs.getBytes("RowVer"));}

    private int updateRow(Connection con,UpdateMaterialRequestCommand c)throws SQLException{try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.MaterialRequests SET MaterialTypeId=?,Title=?,Description=?,RequestedFromContactId=?,RequestedFromOrganizationId=?,RequestedFromText=?,RequestMethod=?,Status=?,RequestedByUserId=?,AssignedToUserId=?,RequestedAt=?,RelevantStartDate=?,RelevantEndDate=?,ExpectedResponseDate=?,NextFollowUpAt=?,FirstReceivedAt=?,FullyReceivedAt=?,ClosedAt=?,ClosedByUserId=?,ClosureReason=?,Notes=?,UpdatedByUserId=?,UpdatedAt=SYSUTCDATETIME() WHERE ShaleClientId=? AND CaseId=? AND Id=? AND IsDeleted=0 AND RowVer=?")){int i=1;ps.setInt(i++,c.materialTypeId());ps.setString(i++,blank(c.title()));ps.setString(i++,blank(c.description()));setInt(ps,i++,c.requestedFromContactId());setInt(ps,i++,c.requestedFromOrganizationId());ps.setString(i++,blank(c.requestedFromText()));ps.setString(i++,norm(c.requestMethod()));ps.setString(i++,norm(c.status()));ps.setInt(i++,c.requestedByUserId());setInt(ps,i++,c.assignedToUserId());setTs(ps,i++,c.requestedAt());setDate(ps,i++,c.relevantStartDate());setDate(ps,i++,c.relevantEndDate());setDate(ps,i++,c.expectedResponseDate());setTs(ps,i++,c.nextFollowUpAt());setTs(ps,i++,c.firstReceivedAt());setTs(ps,i++,c.fullyReceivedAt());setTs(ps,i++,c.closedAt());setInt(ps,i++,c.closedByUserId());ps.setString(i++,blank(c.closureReason()));ps.setString(i++,blank(c.notes()));ps.setInt(i++,c.actorUserId());ps.setInt(i++,c.shaleClientId());ps.setLong(i++,c.caseId());ps.setLong(i++,c.materialRequestId());ps.setBytes(i++,c.rowVer());return ps.executeUpdate();}}
    private void validateUpdateRefs(Connection con,UpdateMaterialRequestCommand c)throws SQLException{requireExists(con,"dbo.Cases","Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0",c.caseId(),c.shaleClientId());validateMaterialType(con,c.shaleClientId(),c.materialTypeId());validateUser(con,c.shaleClientId(),c.requestedByUserId());if(c.assignedToUserId()!=null)validateUser(con,c.shaleClientId(),c.assignedToUserId());if(c.requestedFromContactId()!=null)requireExists(con,"dbo.Contacts","Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0",c.requestedFromContactId(),c.shaleClientId());if(c.requestedFromOrganizationId()!=null)requireExists(con,"dbo.Organizations","Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0",c.requestedFromOrganizationId(),c.shaleClientId());}
    private static void validateUpdate(UpdateMaterialRequestCommand c){if(blank(c.title())==null)throw new IllegalArgumentException("Title is required.");if(blank(c.title()).length()>255)throw new IllegalArgumentException("Title is too long.");if(c.materialTypeId()<=0)throw new IllegalArgumentException("Material Type is required.");if(blank(c.requestMethod())==null)throw new IllegalArgumentException("Request Method is required.");if(blank(c.status())==null)throw new IllegalArgumentException("Status is required.");if(c.requestedByUserId()<=0)throw new IllegalArgumentException("Requested By is required.");if(c.requestedAt()==null)throw new IllegalArgumentException("Requested At is required.");if(c.rowVer()==null||c.rowVer().length==0)throw new IllegalArgumentException("Request version is required.");if(c.requestedFromContactId()!=null&&c.requestedFromOrganizationId()!=null)throw new IllegalArgumentException("Choose either a contact or an organization, not both.");if(c.expectedResponseDate()!=null&&c.expectedResponseDate().isBefore(c.requestedAt().toLocalDate()))throw new IllegalArgumentException("Due date cannot be before Requested At.");if(c.nextFollowUpAt()!=null&&c.nextFollowUpAt().isBefore(c.requestedAt()))throw new IllegalArgumentException("Next follow-up cannot be before Requested At.");}
    private static Map<EntityActionAuditEvent.MetadataKey,Object> meta(UpdateMaterialRequestCommand c){var m=new EnumMap<EntityActionAuditEvent.MetadataKey,Object>(EntityActionAuditEvent.MetadataKey.class);m.put(EntityActionAuditEvent.MetadataKey.MATERIAL_TYPE_ID,c.materialTypeId());m.put(EntityActionAuditEvent.MetadataKey.REQUEST_STATUS,norm(c.status()));if(c.assignedToUserId()!=null)m.put(EntityActionAuditEvent.MetadataKey.ASSIGNED_TO_USER_ID,c.assignedToUserId());if(c.requestedFromContactId()!=null)m.put(EntityActionAuditEvent.MetadataKey.CONTACT_ID,c.requestedFromContactId());if(c.requestedFromOrganizationId()!=null)m.put(EntityActionAuditEvent.MetadataKey.ORGANIZATION_ID,c.requestedFromOrganizationId());return m;}
    private long insert(Connection con,CreateMaterialRequestCommand c)throws SQLException{try(PreparedStatement ps=con.prepareStatement("INSERT dbo.MaterialRequests (ShaleClientId,CaseId,MaterialTypeId,Title,Description,RequestedFromContactId,RequestedFromOrganizationId,RequestedFromText,RequestMethod,Status,RequestedByUserId,AssignedToUserId,RequestedAt,ExpectedResponseDate,NextFollowUpAt,CreatedByUserId) OUTPUT INSERTED.Id VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){int i=1;ps.setInt(i++,c.shaleClientId());ps.setLong(i++,c.caseId());ps.setInt(i++,c.materialTypeId());ps.setString(i++,blank(c.title()));ps.setString(i++,blank(c.description()));setInt(ps,i++,c.requestedFromContactId());setInt(ps,i++,c.requestedFromOrganizationId());ps.setString(i++,blank(c.requestedFromText()));ps.setString(i++,norm(c.requestMethod()));ps.setString(i++,norm(c.status()));ps.setInt(i++,c.requestedByUserId());setInt(ps,i++,c.assignedToUserId());setTs(ps,i++,c.requestedAt());setDate(ps,i++,c.expectedResponseDate());setTs(ps,i++,c.nextFollowUpAt());ps.setInt(i++,c.actorUserId());try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getLong(1);}}}
    private void validateRefs(Connection con,CreateMaterialRequestCommand c)throws SQLException{requireExists(con,"dbo.Cases","Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0",c.caseId(),c.shaleClientId());validateMaterialType(con,c.shaleClientId(),c.materialTypeId());validateUser(con,c.shaleClientId(),c.requestedByUserId());if(c.assignedToUserId()!=null)validateUser(con,c.shaleClientId(),c.assignedToUserId());if(c.requestedFromContactId()!=null)requireExists(con,"dbo.Contacts","Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0",c.requestedFromContactId(),c.shaleClientId());if(c.requestedFromOrganizationId()!=null)requireExists(con,"dbo.Organizations","Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0",c.requestedFromOrganizationId(),c.shaleClientId());}
    private void validateMaterialType(Connection con,int t,int id)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT ShaleClientId,SystemKey,IsActive,IsDeleted FROM dbo.MaterialTypes WHERE Id=? AND (ShaleClientId=? OR ShaleClientId IS NULL)")){ps.setInt(1,id);ps.setInt(2,t);try(ResultSet rs=ps.executeQuery()){if(!rs.next()||!rs.getBoolean("IsActive")||rs.getBoolean("IsDeleted"))throw new IllegalArgumentException("Material type is not active for this tenant.");String key=rs.getString("SystemKey");Integer owner=(Integer)rs.getObject("ShaleClientId");if(owner==null&&key!=null)try(PreparedStatement m=con.prepareStatement("SELECT 1 FROM dbo.MaterialTypes WHERE ShaleClientId=? AND SystemKey=?")){m.setInt(1,t);m.setString(2,key);try(ResultSet mr=m.executeQuery()){if(mr.next())throw new IllegalArgumentException("Material type is masked by tenant override.");}}}}}
    private void validateUser(Connection con,int t,int u)throws SQLException{requireExists(con,"dbo.Users","Id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0",u,t);}
    private static void validateCreate(CreateMaterialRequestCommand c){if(blank(c.title())==null)throw new IllegalArgumentException("Title is required.");if(blank(c.title()).length()>255)throw new IllegalArgumentException("Title is too long.");if(c.materialTypeId()<=0)throw new IllegalArgumentException("Material Type is required.");if(blank(c.requestMethod())==null)throw new IllegalArgumentException("Request Method is required.");if(blank(c.status())==null)throw new IllegalArgumentException("Status is required.");if(c.requestedByUserId()<=0)throw new IllegalArgumentException("Requested By is required.");if(c.requestedAt()==null)throw new IllegalArgumentException("Requested At is required.");if(c.requestedFromContactId()!=null&&c.requestedFromOrganizationId()!=null)throw new IllegalArgumentException("Choose either a contact or an organization, not both.");if(c.expectedResponseDate()!=null&&c.expectedResponseDate().isBefore(c.requestedAt().toLocalDate()))throw new IllegalArgumentException("Due date cannot be before Requested At.");if(c.nextFollowUpAt()!=null&&c.nextFollowUpAt().isBefore(c.requestedAt()))throw new IllegalArgumentException("Next follow-up cannot be before Requested At.");}
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
}
