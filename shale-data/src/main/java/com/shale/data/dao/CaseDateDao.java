package com.shale.data.dao;

import com.shale.core.dto.CaseDateDto;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.runtime.DbSessionProvider;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class CaseDateDao {
    private final DbSessionProvider db;
    public CaseDateDao(DbSessionProvider db) { this.db = Objects.requireNonNull(db, "db"); }

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
            ps.setInt(1, tenant); ps.setLong(2, caseId); ps.setInt(3, tenant);
            try (ResultSet rs = ps.executeQuery()) { List<CaseDateDto> out = new ArrayList<>(); while (rs.next()) out.add(mapDate(rs)); return List.copyOf(out); }
        } catch (SQLException e) { throw fail(e); }
    }

    public Optional<CaseDateDto> getCaseDate(long id, int tenant, int actor) {
        String sql = occurrenceSql("cd.Id = ? AND cd.ShaleClientId = ? AND cd.IsDeleted = 0");
        try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            verifyTenant(con, tenant); validateActor(con, tenant, actor);
            ps.setInt(1, tenant); ps.setLong(2, id); ps.setInt(3, tenant);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(mapDate(rs)) : Optional.empty(); }
        } catch (SQLException e) { throw fail(e); }
    }

    static String occurrenceSql(String where) { return """
            SELECT cd.Id, cd.ShaleClientId, cd.CaseId, cd.CaseDateTypeId,
                   COALESCE(eff.SystemKey, st.SystemKey) AS TypeSystemKey,
                   COALESCE(eff.Name, st.Name) AS TypeName,
                   COALESCE(eff.Description, st.Description) AS TypeDescription,
                   COALESCE(eff.CalendarCategory, st.CalendarCategory) AS CalendarCategory,
                   COALESCE(eff.Color, st.Color) AS Color,
                   COALESCE(eff.SupportsTime, st.SupportsTime) AS SupportsTime,
                   cd.StartsAt, cd.EndsAt, cd.AllDay, cd.Notes, cd.CreatedAt, cd.CreatedByUserId,
                   COALESCE(cu.DisplayName, cu.Name, CONCAT(cu.first_name, ' ', cu.last_name), CONCAT('User #', cd.CreatedByUserId)) AS CreatedByDisplayName,
                   cd.UpdatedAt, cd.UpdatedByUserId,
                   CASE WHEN cd.UpdatedByUserId IS NULL THEN NULL ELSE COALESCE(uu.DisplayName, uu.Name, CONCAT(uu.first_name, ' ', uu.last_name), CONCAT('User #', cd.UpdatedByUserId)) END AS UpdatedByDisplayName,
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

    private static EffectiveCaseDateTypeDto mapType(ResultSet rs) throws SQLException { return new EffectiveCaseDateTypeDto(rs.getInt(1),(Integer)rs.getObject(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getBoolean(8),rs.getInt(9),rs.getBoolean(10),rs.getBoolean(11),EffectiveCaseDateTypeDto.Origin.valueOf(rs.getString(12)),rs.getBytes(13)); }
    private static CaseDateDto mapDate(ResultSet rs) throws SQLException { return new CaseDateDto(rs.getLong("Id"),rs.getInt("ShaleClientId"),rs.getLong("CaseId"),rs.getInt("CaseDateTypeId"),rs.getString("TypeSystemKey"),rs.getString("TypeName"),rs.getString("TypeDescription"),rs.getString("CalendarCategory"),rs.getString("Color"),rs.getBoolean("SupportsTime"),ldt(rs,"StartsAt"),ldt(rs,"EndsAt"),rs.getBoolean("AllDay"),rs.getString("Notes"),ldt(rs,"CreatedAt"),rs.getInt("CreatedByUserId"),rs.getString("CreatedByDisplayName"),ldt(rs,"UpdatedAt"),(Integer)rs.getObject("UpdatedByUserId"),rs.getString("UpdatedByDisplayName"),rs.getBytes("RowVer")); }
    private static LocalDateTime ldt(ResultSet rs, String c) throws SQLException { Timestamp ts = rs.getTimestamp(c); return ts == null ? null : ts.toLocalDateTime(); }
    private static void verifyTenant(Connection con,int t)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT CAST(SESSION_CONTEXT(N'ShaleClientId') AS INT)");ResultSet rs=ps.executeQuery()){if(!rs.next()||rs.getInt(1)!=t)throw new IllegalStateException("ShaleClientId session context mismatch.");}}
    private static void validateActor(Connection con,int t,int u)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo.Users WHERE id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0")){ps.setInt(1,u);ps.setInt(2,t);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Actor user is not available for this tenant.");}}}
    private static void validateCase(Connection con,int t,long c)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo.Cases WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0")){ps.setLong(1,c);ps.setInt(2,t);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Case is not available for this tenant.");}}}
    private static RuntimeException fail(SQLException e){return new IllegalStateException("Database operation failed.", e);}
}
