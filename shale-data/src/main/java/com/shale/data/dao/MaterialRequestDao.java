package com.shale.data.dao;

import com.shale.core.dto.*;
import com.shale.core.runtime.DbSessionProvider;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class MaterialRequestDao {
    private final DbSessionProvider db;
    public MaterialRequestDao(DbSessionProvider db) { this.db = Objects.requireNonNull(db); }

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

    public List<MaterialRequestSummaryDto> listMaterialRequests(long caseId, int tenant) {
        String sql = baseSelect(false) + " WHERE mr.ShaleClientId=? AND mr.CaseId=? AND mr.IsDeleted=0 AND ISNULL(c.IsDeleted,0)=0 ORDER BY mr.NextFollowUpAt, mr.RequestedAt DESC, mr.Id DESC";
        try (Connection con=db.requireConnection(); PreparedStatement ps=con.prepareStatement(sql)) { verifyTenant(con, tenant); ps.setInt(1,tenant); ps.setLong(2,caseId); try(ResultSet rs=ps.executeQuery()){List<MaterialRequestSummaryDto> out=new ArrayList<>(); while(rs.next()) out.add(mapSummary(rs)); return out;}} catch(SQLException e){throw sqlFailure(e);} }

    public MaterialRequestDetailDto findMaterialRequest(long caseId,long id,int tenant){
        String sql = baseSelect(true) + " WHERE mr.ShaleClientId=? AND mr.CaseId=? AND mr.Id=? AND mr.IsDeleted=0 AND ISNULL(c.IsDeleted,0)=0";
        try(Connection con=db.requireConnection(); PreparedStatement ps=con.prepareStatement(sql)){ verifyTenant(con,tenant); ps.setInt(1,tenant); ps.setLong(2,caseId); ps.setLong(3,id); try(ResultSet rs=ps.executeQuery()){return rs.next()?mapDetail(rs):null;}} catch(SQLException e){throw sqlFailure(e);} }



    public List<MaterialRequestFollowUpDto> listFollowUps(long caseId,long requestId,int tenant){ String sql=""" 
        SELECT f.*, %s AS AttemptedByDisplayName FROM dbo.MaterialRequestFollowUps f JOIN dbo.MaterialRequests mr ON mr.Id=f.MaterialRequestId AND mr.ShaleClientId=f.ShaleClientId AND mr.CaseId=f.CaseId JOIN dbo.Users u ON u.Id=f.AttemptedByUserId AND u.ShaleClientId=f.ShaleClientId WHERE f.ShaleClientId=? AND f.CaseId=? AND f.MaterialRequestId=? AND mr.IsDeleted=0 ORDER BY f.AttemptedAt, f.Id
        """.formatted(userDisplayName("u")); try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement(sql)){verifyTenant(con,tenant);ps.setInt(1,tenant);ps.setLong(2,caseId);ps.setLong(3,requestId);try(ResultSet rs=ps.executeQuery()){List<MaterialRequestFollowUpDto> out=new ArrayList<>();while(rs.next())out.add(mapFollowUp(rs));return out;}}catch(SQLException e){throw sqlFailure(e);} }


    private static String baseSelect(boolean detail){ return """
        SELECT mr.Id,mr.ShaleClientId,mr.CaseId,mr.MaterialTypeId,mt.Name AS MaterialTypeName,mt.SystemKey AS MaterialTypeSystemKey,mr.Title,
               %s mr.RequestedByUserId, %s AS RequestedByDisplayName, mr.AssignedToUserId, %s AS AssignedToDisplayName,
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
    private static MaterialRequestSummaryDto mapSummary(ResultSet rs)throws SQLException{return new MaterialRequestSummaryDto(rs.getLong("Id"),rs.getInt("ShaleClientId"),rs.getLong("CaseId"),rs.getInt("MaterialTypeId"),rs.getString("MaterialTypeName"),rs.getString("MaterialTypeSystemKey"),rs.getString("Title"),rs.getInt("RequestedByUserId"),rs.getString("RequestedByDisplayName"),(Integer)rs.getObject("AssignedToUserId"),rs.getString("AssignedToDisplayName"),(Integer)rs.getObject("RequestedFromContactId"),rs.getString("RequestedFromContactDisplayName"),(Integer)rs.getObject("RequestedFromOrganizationId"),rs.getString("RequestedFromOrganizationName"),rs.getString("RequestedFromText"),rs.getString("RequestMethod"),ldt(rs,"RequestedAt"),rs.getString("Status"),ldt(rs,"ExpectedResponseDate"),ldt(rs,"NextFollowUpAt"),ldt(rs,"LastFollowUpAt"),ldt(rs,"UpdatedAt"),rs.getBytes("RowVer"));}
    private static MaterialRequestDetailDto mapDetail(ResultSet rs)throws SQLException{return new MaterialRequestDetailDto(rs.getLong("Id"),rs.getInt("ShaleClientId"),rs.getLong("CaseId"),rs.getInt("MaterialTypeId"),rs.getString("MaterialTypeName"),rs.getString("MaterialTypeSystemKey"),rs.getString("Title"),rs.getString("Description"),rs.getInt("RequestedByUserId"),rs.getString("RequestedByDisplayName"),(Integer)rs.getObject("AssignedToUserId"),rs.getString("AssignedToDisplayName"),(Integer)rs.getObject("RequestedFromContactId"),rs.getString("RequestedFromContactDisplayName"),(Integer)rs.getObject("RequestedFromOrganizationId"),rs.getString("RequestedFromOrganizationName"),rs.getString("RequestedFromText"),rs.getString("RequestMethod"),ldt(rs,"RequestedAt"),ld(rs,"RelevantStartDate"),ld(rs,"RelevantEndDate"),rs.getString("Status"),ld(rs,"ExpectedResponseDate"),ldt(rs,"NextFollowUpAt"),ldt(rs,"LastFollowUpAt"),ldt(rs,"FirstReceivedAt"),ldt(rs,"FullyReceivedAt"),ldt(rs,"ClosedAt"),(Integer)rs.getObject("ClosedByUserId"),rs.getString("ClosureReason"),rs.getString("Notes"),ldt(rs,"CreatedAt"),rs.getInt("CreatedByUserId"),ldt(rs,"UpdatedAt"),(Integer)rs.getObject("UpdatedByUserId"),rs.getBytes("RowVer"));}
    private static MaterialRequestFollowUpDto mapFollowUp(ResultSet rs)throws SQLException{return new MaterialRequestFollowUpDto(rs.getLong("Id"),rs.getInt("ShaleClientId"),rs.getLong("MaterialRequestId"),rs.getLong("CaseId"),ldt(rs,"AttemptedAt"),rs.getInt("AttemptedByUserId"),rs.getString("AttemptedByDisplayName"),rs.getString("Method"),rs.getString("Outcome"),ldt(rs,"NextFollowUpAt"),rs.getString("Notes"),ldt(rs,"CreatedAt"),rs.getInt("CreatedByUserId"),rs.getBytes("RowVer"));}
    private static void verifyTenant(Connection con,int t)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT CAST(SESSION_CONTEXT(N'ShaleClientId') AS INT)" );ResultSet rs=ps.executeQuery()){if(!rs.next()||rs.getInt(1)!=t)throw new IllegalStateException("ShaleClientId session context mismatch.");}}
    private static RuntimeException sqlFailure(SQLException e){return new IllegalStateException("Database operation failed.",e);}
    private static LocalDateTime ldt(ResultSet rs,String c)throws SQLException{Timestamp t=rs.getTimestamp(c);return t==null?null:t.toLocalDateTime();} private static LocalDate ld(ResultSet rs,String c)throws SQLException{java.sql.Date d=rs.getDate(c);return d==null?null:d.toLocalDate();}
}
