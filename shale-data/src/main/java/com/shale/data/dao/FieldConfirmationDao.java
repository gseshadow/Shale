package com.shale.data.dao;

import com.shale.core.dto.FieldConfirmationPolicyDto;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.CaseServicePort.ConfirmCaseDateCommand;
import com.shale.core.service.CaseServicePort.SetFieldConfirmationPolicyCommand;
import java.sql.*;
import java.util.*;

/** Transaction owner for policy administration/manual confirmation and participant for Case Date writes. */
public final class FieldConfirmationDao {
    private final DbSessionProvider db;
    private final EntityActionAuditDao audit;
    public FieldConfirmationDao(DbSessionProvider db){this(db,new EntityActionAuditDao());}
    FieldConfirmationDao(DbSessionProvider db,EntityActionAuditDao audit){this.db=Objects.requireNonNull(db);this.audit=Objects.requireNonNull(audit);}

    public List<FieldConfirmationPolicyDto> listPolicies(int tenant,int actor){
        String sql="SELECT Id,ShaleClientId,CaseDateTypePolicyKey,PolicyRevision,RequiresConfirmation,RequiredFirmWideRoleDefinitionId,RowVer FROM dbo.FieldConfirmationPolicies WHERE ShaleClientId=? AND CaseDateTypePolicyKey IS NOT NULL AND SupersededAt IS NULL ORDER BY CaseDateTypePolicyKey";
        try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement(sql)){verifySession(con,tenant,actor,false);ps.setInt(1,tenant);try(ResultSet rs=ps.executeQuery()){List<FieldConfirmationPolicyDto> out=new ArrayList<>();while(rs.next())out.add(dto(rs));return List.copyOf(out);}}
        catch(SQLException e){throw new IllegalStateException("Case Date Type confirmation policies could not be loaded.",e);}
    }

    public List<com.shale.core.service.CaseServicePort.ConfirmationRole> listRoles(int tenant,int actor){
        String sql="SELECT Id,Name FROM dbo.FirmWideRoleDefinitions WHERE ShaleClientId=? AND IsActive=1 AND IsDeleted=0 ORDER BY Name,Id";
        try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement(sql)){verifySession(con,tenant,actor,false);ps.setInt(1,tenant);try(ResultSet rs=ps.executeQuery()){List<com.shale.core.service.CaseServicePort.ConfirmationRole> out=new ArrayList<>();while(rs.next())out.add(new com.shale.core.service.CaseServicePort.ConfirmationRole(rs.getInt(1),rs.getString(2)));return List.copyOf(out);}}
        catch(SQLException e){throw new IllegalStateException("Firm-wide confirmation roles could not be loaded.",e);}
    }

    public boolean actorHasRole(int tenant,int actor,int role){try(Connection con=db.requireConnection()){verifySession(con,tenant,actor,false);return hasRole(con,tenant,actor,role);}catch(SQLException e){throw new IllegalStateException("Confirmation eligibility could not be checked.",e);}}

    public FieldConfirmationPolicyDto setPolicy(SetFieldConfirmationPolicyCommand c){
        if(c.caseDateTypeId()<=0)throw new IllegalArgumentException("Case Date Type is required.");
        if(c.requiresConfirmation()&&c.requiredFirmWideRoleDefinitionId()==null)throw new IllegalArgumentException("An active firm-wide role is required.");
        if(!c.requiresConfirmation()&&c.requiredFirmWideRoleDefinitionId()!=null)throw new IllegalArgumentException("Disabled confirmation cannot select a role.");
        try(Connection con=db.requireConnection()){verifySession(con,c.shaleClientId(),c.actorUserId(),true);con.setAutoCommit(false);try{
            String key=requireEffectiveTypePolicyKey(con,c.shaleClientId(),c.caseDateTypeId());
            Policy current=lockPolicy(con,c.shaleClientId(),key);
            if(current==null){if(c.expectedPolicyId()!=null||has(c.expectedPolicyRowVer()))throw stalePolicy();}
            else if(!Objects.equals(c.expectedPolicyId(),current.id)||!Arrays.equals(c.expectedPolicyRowVer(),current.rowVer))throw stalePolicy();
            if(c.requiresConfirmation())requireActiveRole(con,c.shaleClientId(),c.requiredFirmWideRoleDefinitionId());
            long revision=current==null?1:current.revision+1;
            if(current!=null)try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.FieldConfirmationPolicies SET SupersededAt=SYSUTCDATETIME(),SupersededByUserId=? WHERE Id=? AND ShaleClientId=? AND RowVer=? AND SupersededAt IS NULL")){ps.setInt(1,c.actorUserId());ps.setLong(2,current.id);ps.setInt(3,c.shaleClientId());ps.setBytes(4,c.expectedPolicyRowVer());if(ps.executeUpdate()!=1)throw stalePolicy();}
            long id;try(PreparedStatement ps=con.prepareStatement("INSERT dbo.FieldConfirmationPolicies(ShaleClientId,CaseDateTypePolicyKey,PolicyRevision,RequiresConfirmation,RequiredFirmWideRoleDefinitionId,CreatedByUserId) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?)")){ps.setInt(1,c.shaleClientId());ps.setString(2,key);ps.setLong(3,revision);ps.setBoolean(4,c.requiresConfirmation());if(c.requiredFirmWideRoleDefinitionId()==null)ps.setNull(5,Types.INTEGER);else ps.setInt(5,c.requiredFirmWideRoleDefinitionId());ps.setInt(6,c.actorUserId());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalStateException("Policy was not saved.");id=rs.getLong(1);}}
            audit.append(con,EntityActionAuditEvent.now(c.shaleClientId(),c.actorUserId(),EntityActionAuditEvent.EntityType.FIELD_CONFIRMATION_POLICY,id,current==null?EntityActionAuditEvent.Action.CREATED:EntityActionAuditEvent.Action.UPDATED,null,null,Map.of(EntityActionAuditEvent.MetadataKey.ACTIVE,c.requiresConfirmation(),EntityActionAuditEvent.MetadataKey.DEFINITION_ID,c.requiredFirmWideRoleDefinitionId()==null?0:c.requiredFirmWideRoleDefinitionId())));
            FieldConfirmationPolicyDto out=find(con,id);con.commit();return out;
        }catch(Exception e){con.rollback();if(e instanceof RuntimeException r)throw r;throw new IllegalStateException("Policy mutation failed.",e);}finally{con.setAutoCommit(true);}}
        catch(SQLException e){throw new IllegalStateException("Database operation failed.",e);}
    }

    public void confirmCaseDate(ConfirmCaseDateCommand c){
        requireToken(c.expectedCaseDateRowVer(),"expectedCaseDateRowVer");requireToken(c.expectedRequirementRowVer(),"expectedRequirementRowVer");
        try(Connection con=db.requireConnection()){verifySession(con,c.shaleClientId(),c.actorUserId(),false);con.setAutoCommit(false);try{
            String sql="""
              SELECT cd.ValueRevision,cd.RowVer,r.RequiredFirmWideRoleDefinitionId,r.RowVer
              FROM dbo.CaseDates cd WITH(UPDLOCK,HOLDLOCK)
              JOIN dbo.CaseDateConfirmationTargets t ON t.ShaleClientId=cd.ShaleClientId AND t.CaseDateId=cd.Id AND t.BusinessValueRevision=cd.ValueRevision
              JOIN dbo.SavedValueConfirmationRequirements r ON r.ShaleClientId=t.ShaleClientId AND r.Id=t.ConfirmationRequirementId
              WHERE cd.Id=? AND cd.CaseId=? AND cd.ShaleClientId=? AND cd.IsDeleted=0 AND r.Id=?
              """;
            long revision;int role;byte[] dateRv,reqRv;try(PreparedStatement ps=con.prepareStatement(sql)){ps.setLong(1,c.caseDateId());ps.setLong(2,c.caseId());ps.setInt(3,c.shaleClientId());ps.setLong(4,c.requirementId());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw staleConfirmation();revision=rs.getLong(1);dateRv=rs.getBytes(2);role=rs.getInt(3);reqRv=rs.getBytes(4);}}
            if(revision!=c.expectedBusinessValueRevision()||!Arrays.equals(dateRv,c.expectedCaseDateRowVer())||!Arrays.equals(reqRv,c.expectedRequirementRowVer()))throw staleConfirmation();
            if(!hasRole(con,c.shaleClientId(),c.actorUserId(),role))throw new IllegalArgumentException("The actor does not hold the required firm-wide role.");
            try(PreparedStatement ps=con.prepareStatement("INSERT dbo.SavedValueConfirmations(ShaleClientId,ConfirmationRequirementId,ConfirmedByUserId,ConfirmedAsFirmWideRoleDefinitionId) VALUES(?,?,?,?)")){ps.setInt(1,c.shaleClientId());ps.setLong(2,c.requirementId());ps.setInt(3,c.actorUserId());ps.setInt(4,role);ps.executeUpdate();}
            audit.append(con,EntityActionAuditEvent.now(c.shaleClientId(),c.actorUserId(),EntityActionAuditEvent.EntityType.SAVED_VALUE_CONFIRMATION,c.requirementId(),EntityActionAuditEvent.Action.CONFIRMED,EntityActionAuditEvent.EntityType.CASE_DATE,c.caseDateId(),Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID,c.caseId())));
            con.commit();
        }catch(Exception e){con.rollback();if(e instanceof RuntimeException r)throw r;throw new IllegalStateException("Confirmation failed.",e);}finally{con.setAutoCommit(true);}}
        catch(SQLException e){throw new IllegalStateException("Database operation failed.",e);}
    }

    /** Called only inside the owning Case Date transaction after its business revision is final. */
    public void evaluateCaseDate(Connection con,int tenant,int actor,long caseDateId,int caseDateTypeId,long revision)throws SQLException{
        Policy p=applicablePolicy(con,tenant,caseDateTypeId);if(p==null||!p.required)return;
        long requirement;try(PreparedStatement ps=con.prepareStatement("INSERT dbo.SavedValueConfirmationRequirements(ShaleClientId,TargetType,BusinessValueRevision,FieldConfirmationPolicyId,PolicyRevisionSnapshot,FormKeySnapshot,FieldKeySnapshot,RequiredFirmWideRoleDefinitionId,EnteredByUserId) OUTPUT INSERTED.Id VALUES(?,'CASE_DATE',?,?,?,?,?,?,?)")){ps.setInt(1,tenant);ps.setLong(2,revision);ps.setLong(3,p.id);ps.setLong(4,p.revision);ps.setString(5,"CASE_DATE_TYPE");ps.setString(6,p.policyKey);ps.setInt(7,p.roleId);ps.setInt(8,actor);try(ResultSet rs=ps.executeQuery()){rs.next();requirement=rs.getLong(1);}}
        try(PreparedStatement ps=con.prepareStatement("INSERT dbo.CaseDateConfirmationTargets(ShaleClientId,ConfirmationRequirementId,CaseDateId,BusinessValueRevision) VALUES(?,?,?,?)")){ps.setInt(1,tenant);ps.setLong(2,requirement);ps.setLong(3,caseDateId);ps.setLong(4,revision);ps.executeUpdate();}
        if(hasRole(con,tenant,actor,p.roleId))try(PreparedStatement ps=con.prepareStatement("INSERT dbo.SavedValueConfirmations(ShaleClientId,ConfirmationRequirementId,ConfirmedByUserId,ConfirmedAsFirmWideRoleDefinitionId) VALUES(?,?,?,?)")){ps.setInt(1,tenant);ps.setLong(2,requirement);ps.setInt(3,actor);ps.setInt(4,p.roleId);ps.executeUpdate();}
    }

    private static Policy applicablePolicy(Connection con,int tenant,int type)throws SQLException{
        String key=requireEffectiveTypePolicyKey(con,tenant,type);
        try(PreparedStatement ps=con.prepareStatement("SELECT Id,PolicyRevision,CaseDateTypePolicyKey,RequiresConfirmation,RequiredFirmWideRoleDefinitionId,RowVer FROM dbo.FieldConfirmationPolicies WHERE ShaleClientId=? AND CaseDateTypePolicyKey=? AND SupersededAt IS NULL")){ps.setInt(1,tenant);ps.setString(2,key);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return null;Policy p=policy(rs);if(rs.next())throw new IllegalStateException("Multiple confirmation policies apply to this Case Date Type.");return p;}}
    }
    private static String requireEffectiveTypePolicyKey(Connection con,int tenant,int type)throws SQLException{
        String sql="""
          WITH candidates AS (SELECT t.Id,t.SystemKey,t.IsActive,t.IsDeleted,ROW_NUMBER() OVER(PARTITION BY t.SystemKey ORDER BY CASE WHEN t.ShaleClientId=? AND t.IsDeleted=0 THEN 0 WHEN t.ShaleClientId IS NULL THEN 1 ELSE 2 END,t.Id) rn FROM dbo.CaseDateTypes t WHERE t.SystemKey IS NOT NULL AND (t.ShaleClientId=? OR t.ShaleClientId IS NULL))
          SELECT Id,SystemKey FROM candidates WHERE Id=? AND rn=1 AND IsActive=1 AND IsDeleted=0
          UNION ALL SELECT Id,SystemKey FROM dbo.CaseDateTypes WHERE Id=? AND ShaleClientId=? AND SystemKey IS NULL AND IsActive=1 AND IsDeleted=0
          """;
        try(PreparedStatement ps=con.prepareStatement(sql)){ps.setInt(1,tenant);ps.setInt(2,tenant);ps.setInt(3,type);ps.setInt(4,type);ps.setInt(5,tenant);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("The resulting Case Date Type is not tenant-effective and active.");int id=rs.getInt(1);String systemKey=rs.getString(2);if(rs.next())throw new IllegalStateException("Ambiguous tenant-effective Case Date Type.");return systemKey==null?"TYPE:"+id:"SYSTEM:"+systemKey.trim().toLowerCase(Locale.ROOT);}}
    }

    private static boolean hasRole(Connection con,int tenant,int actor,int role)throws SQLException{String sql="""
      SELECT 1 FROM dbo.Users u JOIN dbo.FirmWideRoleDefinitions r ON r.ShaleClientId=u.ShaleClientId AND r.Id=? AND r.IsActive=1 AND r.IsDeleted=0
      WHERE u.id=? AND u.ShaleClientId=? AND ISNULL(u.is_deleted,0)=0 AND ISNULL(u.IsRemoved,0)=0 AND
       ((r.SystemKey='ADMIN' AND u.is_admin=1) OR (r.SystemKey='ATTORNEY' AND u.is_attorney=1) OR (r.SystemKey NOT IN('ADMIN','ATTORNEY') AND EXISTS(SELECT 1 FROM dbo.UserFirmWideRoleAssignments a WHERE a.ShaleClientId=u.ShaleClientId AND a.UserId=u.id AND a.FirmWideRoleDefinitionId=r.Id AND a.IsActive=1 AND a.IsDeleted=0)))
      """;try(PreparedStatement ps=con.prepareStatement(sql)){ps.setInt(1,role);ps.setInt(2,actor);ps.setInt(3,tenant);try(ResultSet rs=ps.executeQuery()){return rs.next();}}}
    private static void verifySession(Connection con,int tenant,int actor,boolean admin)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo.Users WHERE id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0 AND ISNULL(IsRemoved,0)=0 AND (?=0 OR is_admin=1) AND CAST(SESSION_CONTEXT(N'ShaleClientId') AS int)=? AND CAST(SESSION_CONTEXT(N'PrincipalUserId') AS int)=?")){ps.setInt(1,actor);ps.setInt(2,tenant);ps.setBoolean(3,admin);ps.setInt(4,tenant);ps.setInt(5,actor);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException(admin?"An active same-tenant administrator is required.":"An active same-tenant actor is required.");}}}

    private static void requireActiveRole(Connection con,int t,int r)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo.FirmWideRoleDefinitions WHERE Id=? AND ShaleClientId=? AND IsActive=1 AND IsDeleted=0")){ps.setInt(1,r);ps.setInt(2,t);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("The selected firm-wide role is inactive or belongs to another tenant.");}}}
    private static Policy lockPolicy(Connection con,int t,String key)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT Id,PolicyRevision,CaseDateTypePolicyKey,RequiresConfirmation,RequiredFirmWideRoleDefinitionId,RowVer FROM dbo.FieldConfirmationPolicies WITH(UPDLOCK,HOLDLOCK) WHERE ShaleClientId=? AND CaseDateTypePolicyKey=? AND SupersededAt IS NULL")){ps.setInt(1,t);ps.setString(2,key);try(ResultSet rs=ps.executeQuery()){return rs.next()?policy(rs):null;}}}
    private static Policy policy(ResultSet rs)throws SQLException{return new Policy(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getBoolean(4),(Integer)rs.getObject(5),rs.getBytes(6));}
    private static FieldConfirmationPolicyDto find(Connection con,long id)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT Id,ShaleClientId,CaseDateTypePolicyKey,PolicyRevision,RequiresConfirmation,RequiredFirmWideRoleDefinitionId,RowVer FROM dbo.FieldConfirmationPolicies WHERE Id=?")){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){rs.next();return dto(rs);}}}
    private static FieldConfirmationPolicyDto dto(ResultSet rs)throws SQLException{return new FieldConfirmationPolicyDto(rs.getLong(1),rs.getInt(2),rs.getString(3),rs.getLong(4),rs.getBoolean(5),(Integer)rs.getObject(6),rs.getBytes(7));}
    private record Policy(long id,long revision,String policyKey,boolean required,Integer roleId,byte[] rowVer){}
    private static boolean has(byte[] b){return b!=null&&b.length>0;}private static void requireToken(byte[] b,String n){if(!has(b))throw new IllegalArgumentException(n+" is required.");}
    private static void requireKey(String s,String n){if(s==null||s.isBlank())throw new IllegalArgumentException(n+" is required.");}
    private static IllegalStateException stalePolicy(){return new IllegalStateException("Confirmation policy changed; reload before saving.");}
    private static IllegalStateException staleConfirmation(){return new IllegalStateException("Case Date confirmation is stale; reload before confirming.");}
}
