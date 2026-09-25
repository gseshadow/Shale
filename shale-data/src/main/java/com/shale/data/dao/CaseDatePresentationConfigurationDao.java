package com.shale.data.dao;

import com.shale.core.dto.*;
import com.shale.core.model.CaseDatePresentationPurpose;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.CaseServicePort.ReplaceCaseDatePresentationConfigurationCommand;
import java.sql.*;
import java.util.*;

/** Transaction owner for tenant Case Date presentation defaults. */
public final class CaseDatePresentationConfigurationDao {
    private final DbSessionProvider db;
    private final EntityActionAuditDao audits;
    public CaseDatePresentationConfigurationDao(DbSessionProvider db){this(db,new EntityActionAuditDao());}
    CaseDatePresentationConfigurationDao(DbSessionProvider db,EntityActionAuditDao audits){this.db=Objects.requireNonNull(db);this.audits=Objects.requireNonNull(audits);}

    public CaseDatePresentationConfigurationDto get(int tenant,int actor,CaseDatePresentationPurpose purpose){
        Objects.requireNonNull(purpose,"purpose");
        try(Connection con=db.requireConnection()){verifySession(con,tenant,actor,false);return read(con,tenant,purpose);}
        catch(SQLException e){throw new IllegalStateException("Case Date presentation configuration could not be loaded.",e);}
    }

    public CaseDatePresentationConfigurationDto replace(ReplaceCaseDatePresentationConfigurationCommand c){
        Objects.requireNonNull(c,"command");Objects.requireNonNull(c.purpose(),"purpose");
        List<String> identities=normalizeDistinct(c.orderedSelectionIdentities());requireRowVer(c.expectedRowVer());
        try(Connection con=db.requireConnection()){verifySession(con,c.shaleClientId(),c.actorUserId(),true);con.setAutoCommit(false);try{
            Config current=lock(con,c.shaleClientId(),c.purpose());
            if(current==null||!Arrays.equals(current.rowVer,c.expectedRowVer()))throw stale();
            for(String identity:identities) requireEligibleSelection(con,c.shaleClientId(),identity);
            try(PreparedStatement ps=con.prepareStatement("DELETE FROM dbo.CaseDatePresentationSelections WHERE ShaleClientId=? AND CaseDatePresentationConfigurationId=?")){ps.setInt(1,c.shaleClientId());ps.setLong(2,current.id);ps.executeUpdate();}
            try(PreparedStatement ps=con.prepareStatement("INSERT dbo.CaseDatePresentationSelections(ShaleClientId,CaseDatePresentationConfigurationId,SelectionIdentity,SortOrder,CreatedByUserId) VALUES(?,?,?,?,?)")){
                for(int i=0;i<identities.size();i++){ps.setInt(1,c.shaleClientId());ps.setLong(2,current.id);ps.setString(3,identities.get(i));ps.setInt(4,i);ps.setInt(5,c.actorUserId());ps.addBatch();}ps.executeBatch();}
            try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.CaseDatePresentationConfigurations SET UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? WHERE Id=? AND ShaleClientId=? AND RowVer=?")){ps.setInt(1,c.actorUserId());ps.setLong(2,current.id);ps.setInt(3,c.shaleClientId());ps.setBytes(4,c.expectedRowVer());if(ps.executeUpdate()!=1)throw stale();}
            audits.append(con,EntityActionAuditEvent.now(c.shaleClientId(),c.actorUserId(),EntityActionAuditEvent.EntityType.CASE_DATE_PRESENTATION_CONFIGURATION,current.id,EntityActionAuditEvent.Action.UPDATED,null,null,Map.of(EntityActionAuditEvent.MetadataKey.ORDERING_COUNT,identities.size(),EntityActionAuditEvent.MetadataKey.KIND,c.purpose().name())));
            CaseDatePresentationConfigurationDto out=read(con,c.shaleClientId(),c.purpose());con.commit();return out;
        }catch(Exception e){con.rollback();if(e instanceof RuntimeException r)throw r;throw new IllegalStateException("Case Date presentation configuration mutation failed.",e);}finally{con.setAutoCommit(true);}}
        catch(SQLException e){throw new IllegalStateException("Case Date presentation configuration mutation failed.",e);}
    }

    public List<SelectedCaseDateOccurrenceDto> resolve(long caseId,int tenant,int actor,CaseDatePresentationPurpose purpose){
        return resolveForCases(List.of(caseId),tenant,actor,purpose).getOrDefault(caseId,List.of());
    }

    /** Resolves a whole card page/collection in one set-based query (never one query per card). */
    public Map<Long,List<SelectedCaseDateOccurrenceDto>> resolveForCases(Collection<? extends Number> caseIds,int tenant,int actor,CaseDatePresentationPurpose purpose){
        Objects.requireNonNull(purpose,"purpose");if(caseIds==null||caseIds.isEmpty())return Map.of();
        List<Long> ids=caseIds.stream().map(Number::longValue).distinct().toList();
        String values=String.join(",",Collections.nCopies(ids.size(),"(?)"));String sql="""
          WITH requested(CaseId) AS (SELECT v.CaseId FROM (VALUES %s) v(CaseId)), selections AS (
            SELECT r.CaseId,s.SelectionIdentity,s.SortOrder,s.Id TieId
            FROM requested r JOIN dbo.CaseDatePresentationConfigurations c ON c.ShaleClientId=? AND c.Purpose=?
            JOIN dbo.CaseDatePresentationSelections s ON s.ShaleClientId=c.ShaleClientId AND s.CaseDatePresentationConfigurationId=c.Id
            WHERE ?='CASE_CARD' OR NOT EXISTS(SELECT 1 FROM dbo.CaseOverviewConfigurations o WHERE o.ShaleClientId=? AND o.CaseId=r.CaseId)
            UNION ALL
            SELECT r.CaseId,CASE WHEN NULLIF(LTRIM(RTRIM(t.SystemKey)),'') IS NULL THEN 'TYPE:'+CONVERT(varchar(20),t.Id)
                  ELSE 'SYSTEM:'+LOWER(LTRIM(RTRIM(t.SystemKey))) END,s.SortOrder,s.Id
            FROM requested r JOIN dbo.CaseOverviewConfigurations o ON o.ShaleClientId=? AND o.CaseId=r.CaseId
            JOIN dbo.CaseOverviewDateSelections s ON s.ShaleClientId=o.ShaleClientId AND s.CaseOverviewConfigurationId=o.Id
            JOIN dbo.CaseDateTypes t ON t.Id=s.CaseDateTypeId AND (t.ShaleClientId=o.ShaleClientId OR t.ShaleClientId IS NULL)
            WHERE ?='CASE_OVERVIEW')
          SELECT s.CaseId,s.SelectionIdentity,s.SortOrder,picked.Id,picked.CaseDateTypeId,picked.StartsAt,picked.EndsAt,picked.AllDay,
                 COALESCE(displayType.Id,picked.CaseDateTypeId),COALESCE(displayType.Name,picked.StoredName),
                 COALESCE(displayType.Color,picked.StoredColor),COALESCE(displayType.SystemKey,picked.StoredSystemKey),
                 COALESCE(displayType.SupportsTime,picked.StoredSupportsTime,0),
                 CASE WHEN EXISTS(SELECT 1 FROM dbo.CaseDateConfirmationTargets ct
                   JOIN dbo.SavedValueConfirmationRequirements cr ON cr.Id=ct.ConfirmationRequirementId AND cr.ShaleClientId=ct.ShaleClientId
                   LEFT JOIN dbo.SavedValueConfirmations cc ON cc.ConfirmationRequirementId=cr.Id AND cc.ShaleClientId=cr.ShaleClientId
                   WHERE ct.ShaleClientId=? AND ct.CaseDateId=picked.Id AND ct.BusinessValueRevision=picked.ValueRevision
                     AND cc.Id IS NULL) THEN 1 ELSE 0 END
          FROM selections s
          OUTER APPLY (SELECT TOP(1) cd.Id,cd.CaseDateTypeId,cd.StartsAt,cd.EndsAt,cd.AllDay,
                         stored.Name StoredName,stored.Color StoredColor,stored.SystemKey StoredSystemKey,stored.SupportsTime StoredSupportsTime,cd.ValueRevision
            FROM dbo.CaseDates cd JOIN dbo.CaseDateTypes stored ON stored.Id=cd.CaseDateTypeId
            WHERE cd.ShaleClientId=? AND cd.CaseId=s.CaseId AND cd.IsDeleted=0 AND
             ((s.SelectionIdentity LIKE 'SYSTEM:%%' AND stored.SystemKey IS NOT NULL
               AND LOWER(LTRIM(RTRIM(stored.SystemKey)))=SUBSTRING(s.SelectionIdentity,8,160)
               AND (stored.ShaleClientId=? OR stored.ShaleClientId IS NULL))
              OR (s.SelectionIdentity LIKE 'TYPE:%%' AND stored.Id=TRY_CONVERT(int,SUBSTRING(s.SelectionIdentity,6,20)) AND stored.ShaleClientId=?))
            ORDER BY cd.StartsAt,cd.Id) picked
          OUTER APPLY (SELECT TOP(1) x.Id,x.Name,x.Color,x.SystemKey,x.SupportsTime FROM dbo.CaseDateTypes x
            WHERE x.IsActive=1 AND x.IsDeleted=0 AND
             ((s.SelectionIdentity LIKE 'SYSTEM:%%' AND x.SystemKey IS NOT NULL AND LOWER(LTRIM(RTRIM(x.SystemKey)))=SUBSTRING(s.SelectionIdentity,8,160)
                AND (x.ShaleClientId=? OR x.ShaleClientId IS NULL))
              OR (s.SelectionIdentity LIKE 'TYPE:%%' AND x.Id=TRY_CONVERT(int,SUBSTRING(s.SelectionIdentity,6,20)) AND x.ShaleClientId=?))
            ORDER BY CASE WHEN x.ShaleClientId=? THEN 0 ELSE 1 END,x.Id) displayType
          ORDER BY s.CaseId,s.SortOrder,s.TieId
          """.formatted(values);
        try(Connection con=db.requireConnection()){verifySession(con,tenant,actor,false);try(PreparedStatement ps=con.prepareStatement(sql)){int p=1;for(long id:ids)ps.setLong(p++,id);ps.setInt(p++,tenant);ps.setString(p++,purpose.name());ps.setString(p++,purpose.name());ps.setInt(p++,tenant);ps.setInt(p++,tenant);ps.setString(p++,purpose.name());ps.setInt(p++,tenant);ps.setInt(p++,tenant);ps.setInt(p++,tenant);ps.setInt(p++,tenant);ps.setInt(p++,tenant);ps.setInt(p++,tenant);ps.setInt(p,tenant);try(ResultSet rs=ps.executeQuery()){Map<Long,List<SelectedCaseDateOccurrenceDto>> mutable=new LinkedHashMap<>();for(long id:ids)mutable.put(id,new ArrayList<>());while(rs.next()){long caseId=rs.getLong(1);Long id=(Long)rs.getObject(4);Integer type=(Integer)rs.getObject(5);Timestamp start=rs.getTimestamp(6),end=rs.getTimestamp(7);mutable.computeIfAbsent(caseId,k->new ArrayList<>()).add(new SelectedCaseDateOccurrenceDto(rs.getString(2),rs.getInt(3),id,type,start==null?null:start.toLocalDateTime(),end==null?null:end.toLocalDateTime(),id==null?null:rs.getBoolean(8),(Integer)rs.getObject(9),rs.getString(10),rs.getString(11),rs.getString(12),rs.getBoolean(13),rs.getBoolean(14)));}Map<Long,List<SelectedCaseDateOccurrenceDto>> out=new LinkedHashMap<>();mutable.forEach((k,v)->out.put(k,List.copyOf(v)));return Map.copyOf(out);}}}
        catch(SQLException e){throw new IllegalStateException("Case Date presentation could not be resolved.",e);}
    }

    static String normalizeIdentity(String raw){
        if(raw==null)throw invalid();String value=raw.trim();int split=value.indexOf(':');if(split<=0)throw invalid();String prefix=value.substring(0,split).toUpperCase(Locale.ROOT),suffix=value.substring(split+1).trim();
        if(prefix.equals("SYSTEM")&&!suffix.isBlank())return "SYSTEM:"+suffix.toLowerCase(Locale.ROOT);
        if(prefix.equals("TYPE")){try{int id=Integer.parseInt(suffix);if(id>0)return "TYPE:"+id;}catch(NumberFormatException ignored){}}
        throw invalid();
    }
    static List<String> normalizeDistinct(List<String> values){if(values==null)throw invalid();List<String> out=new ArrayList<>();Set<String> seen=new HashSet<>();for(String v:values){String n=normalizeIdentity(v);if(!seen.add(n))throw new IllegalArgumentException("Duplicate Case Date presentation selections are not allowed.");out.add(n);}return List.copyOf(out);}

    private static CaseDatePresentationConfigurationDto read(Connection con,int tenant,CaseDatePresentationPurpose purpose)throws SQLException{
        Config config=find(con,tenant,purpose);if(config==null)throw new IllegalStateException("Case Date presentation defaults have not been seeded for this tenant.");
        String sql="""
          SELECT s.SelectionIdentity,s.SortOrder,t.Id,t.ShaleClientId,t.SystemKey,t.Name,t.Description,t.CalendarCategory,t.Color,t.SupportsTime,t.SortOrder,t.IsActive,t.IsDeleted,t.RowVer,
           CASE WHEN t.IsActive=1 AND t.IsDeleted=0 THEN 0 ELSE 1 END Historical
          FROM dbo.CaseDatePresentationSelections s
          OUTER APPLY (SELECT TOP(1) x.* FROM dbo.CaseDateTypes x WHERE
            (s.SelectionIdentity LIKE 'SYSTEM:%' AND x.SystemKey IS NOT NULL AND LOWER(LTRIM(RTRIM(x.SystemKey)))=SUBSTRING(s.SelectionIdentity,8,160) AND (x.ShaleClientId=s.ShaleClientId OR x.ShaleClientId IS NULL)) OR
            (s.SelectionIdentity LIKE 'TYPE:%' AND x.Id=TRY_CONVERT(int,SUBSTRING(s.SelectionIdentity,6,20)) AND x.ShaleClientId=s.ShaleClientId)
            ORDER BY CASE WHEN x.ShaleClientId=s.ShaleClientId AND x.IsActive=1 AND x.IsDeleted=0 THEN 0
                          WHEN x.ShaleClientId IS NULL AND x.IsActive=1 AND x.IsDeleted=0 THEN 1 ELSE 2 END,x.Id) t
          WHERE s.ShaleClientId=? AND s.CaseDatePresentationConfigurationId=? ORDER BY s.SortOrder,s.Id
          """;
        try(PreparedStatement ps=con.prepareStatement(sql)){ps.setInt(1,tenant);ps.setLong(2,config.id);try(ResultSet rs=ps.executeQuery()){List<CaseDatePresentationSelectionDto> out=new ArrayList<>();while(rs.next()){if(rs.getObject(3)==null)throw new IllegalStateException("A historical Case Date presentation selection is no longer readable.");Integer owner=(Integer)rs.getObject(4);var type=new EffectiveCaseDateTypeDto(rs.getInt(3),owner,rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getBoolean(10),rs.getInt(11),rs.getBoolean(12),rs.getBoolean(13),owner==null?EffectiveCaseDateTypeDto.Origin.GLOBAL:EffectiveCaseDateTypeDto.Origin.TENANT_CREATED,rs.getBytes(14));out.add(new CaseDatePresentationSelectionDto(rs.getString(1),rs.getInt(2),type,rs.getBoolean(15)));}return new CaseDatePresentationConfigurationDto(config.id,tenant,purpose,out,config.rowVer);}}
    }
    private static void requireEligibleSelection(Connection con,int tenant,String identity)throws SQLException{String sql="""
      WITH visible AS (SELECT x.Id,x.SystemKey,x.IsActive,x.IsDeleted,ROW_NUMBER() OVER(PARTITION BY x.SystemKey ORDER BY CASE WHEN x.ShaleClientId=? AND x.IsDeleted=0 THEN 0 ELSE 1 END,x.Id) rn FROM dbo.CaseDateTypes x WHERE (x.ShaleClientId=? OR x.ShaleClientId IS NULL) AND x.SystemKey IS NOT NULL)
      SELECT 1 FROM visible WHERE ?='SYSTEM:'+LOWER(LTRIM(RTRIM(SystemKey))) AND rn=1 AND IsActive=1 AND IsDeleted=0
      UNION ALL SELECT 1 FROM dbo.CaseDateTypes WHERE ?='TYPE:'+CONVERT(varchar(20),Id) AND ShaleClientId=? AND SystemKey IS NULL AND IsActive=1 AND IsDeleted=0
      """;try(PreparedStatement ps=con.prepareStatement(sql)){ps.setInt(1,tenant);ps.setInt(2,tenant);ps.setString(3,identity);ps.setString(4,identity);ps.setInt(5,tenant);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("A selected Case Date Type is not active and tenant-effective.");if(rs.next())throw new IllegalStateException("A Case Date presentation identity is ambiguous.");}}}
    private static Config find(Connection c,int t,CaseDatePresentationPurpose p)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT Id,RowVer FROM dbo.CaseDatePresentationConfigurations WHERE ShaleClientId=? AND Purpose=?")){ps.setInt(1,t);ps.setString(2,p.name());try(ResultSet r=ps.executeQuery()){return r.next()?new Config(r.getLong(1),r.getBytes(2)):null;}}}
    private static Config lock(Connection c,int t,CaseDatePresentationPurpose p)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT Id,RowVer FROM dbo.CaseDatePresentationConfigurations WITH(UPDLOCK,HOLDLOCK) WHERE ShaleClientId=? AND Purpose=?")){ps.setInt(1,t);ps.setString(2,p.name());try(ResultSet r=ps.executeQuery()){return r.next()?new Config(r.getLong(1),r.getBytes(2)):null;}}}
    private static void validateCase(Connection c,int t,long id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM dbo.Cases WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0")){p.setLong(1,id);p.setInt(2,t);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Case is not available for this tenant.");}}}
    private static void verifySession(Connection c,int t,int a,boolean admin)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM dbo.Users WHERE id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0 AND ISNULL(IsRemoved,0)=0 AND (?=0 OR ISNULL(is_admin,0)=1) AND CAST(SESSION_CONTEXT(N'ShaleClientId') AS int)=? AND CAST(SESSION_CONTEXT(N'PrincipalUserId') AS int)=?")){p.setInt(1,a);p.setInt(2,t);p.setBoolean(3,admin);p.setInt(4,t);p.setInt(5,a);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException(admin?"An active same-tenant administrator is required.":"An active same-tenant actor is required.");}}}
    private static void requireRowVer(byte[] value){if(value==null||value.length==0)throw new IllegalArgumentException("expectedRowVer is required.");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("A valid Case Date presentation selection identity is required.");}
    private static IllegalStateException stale(){return new IllegalStateException("Case Date presentation configuration changed; reload before saving.");}
    private record Config(long id,byte[] rowVer){}
}
