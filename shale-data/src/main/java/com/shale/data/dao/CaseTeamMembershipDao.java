package com.shale.data.dao;

import static com.shale.data.dao.EntityActionAuditEvent.Action.*;
import static com.shale.data.dao.EntityActionAuditEvent.EntityType.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;

import com.shale.core.dto.CaseTeamMemberRoleDto;
import com.shale.core.dto.CaseTeamMembershipDto;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.CaseServicePort.*;

/** Owns atomic membership/role mutations. CaseUsers remains membership authority. */
public final class CaseTeamMembershipDao {
	private static final String RESPONSIBLE_ATTORNEY = "responsible_attorney";
	private final DbSessionProvider db;
	private final EntityActionAuditDao audit = new EntityActionAuditDao();
	public CaseTeamMembershipDao(DbSessionProvider db) { this.db=Objects.requireNonNull(db); }

	public List<CaseTeamMembershipDto> list(int tenant,int actor,long caseId) {
		try(Connection c=db.requireConnection()) { requireContext(c,tenant); requireActor(c,tenant,actor); requireCase(c,tenant,caseId);
			var members=new LinkedHashMap<Long,Mutable>();
			try(var p=c.prepareStatement("""
				SELECT cu.Id,cu.CaseId,cu.UserId,cu.RoleId,cu.IsPrimary,cu.RowVer,
				 LTRIM(RTRIM(CONCAT(u.name_first,' ',u.name_last))) DisplayName
				FROM dbo.CaseUsers cu JOIN dbo.Users u ON u.id=cu.UserId AND u.ShaleClientId=cu.ShaleClientId
				WHERE cu.ShaleClientId=? AND cu.CaseId=? ORDER BY cu.Id""")) {
				p.setInt(1,tenant);p.setLong(2,caseId);try(var r=p.executeQuery()){while(r.next()){long id=r.getLong("Id");members.put(id,new Mutable(id,r.getLong("CaseId"),r.getInt("UserId"),r.getString("DisplayName"),(Integer)r.getObject("RoleId"),r.getBoolean("IsPrimary"),r.getBytes("RowVer")));}}
			}
			if(!members.isEmpty())try(var p=c.prepareStatement("""
				SELECT a.Id,a.CaseUserId,a.CaseTeamRoleDefinitionId,d.SystemKey,d.Name,d.IsActive,d.IsDeleted,
				 a.IsDeleted,a.CreatedAt,a.UpdatedAt,a.RowVer
				FROM dbo.CaseTeamMemberRoles a JOIN dbo.CaseTeamRoleDefinitions d ON d.Id=a.CaseTeamRoleDefinitionId
				WHERE a.ShaleClientId=? AND a.CaseId=? ORDER BY d.SortOrder,d.Id,a.Id""")) {
				p.setInt(1,tenant);p.setLong(2,caseId);try(var r=p.executeQuery()){while(r.next()){var m=members.get(r.getLong("CaseUserId"));if(m!=null)m.roles.add(new CaseTeamMemberRoleDto(r.getLong("Id"),r.getInt("CaseTeamRoleDefinitionId"),r.getString("SystemKey"),r.getString("Name"),r.getBoolean(6),r.getBoolean(7),r.getBoolean(8),instant(r,"CreatedAt"),instant(r,"UpdatedAt"),r.getBytes("RowVer")));}}
			}
			return members.values().stream().map(Mutable::dto).toList();
		}catch(SQLException e){throw failure("list case team",e);}
	}

	public CaseTeamMembershipDto add(CaseTeamMemberCommand x){long membershipId=tx(x.tenantId(),c->{requireActor(c,x.tenantId(),x.actorUserId());requireCase(c,x.tenantId(),x.caseId());requireUser(c,x.tenantId(),x.userId());try(var p=c.prepareStatement("SELECT 1 FROM dbo.CaseUsers WHERE ShaleClientId=? AND CaseId=? AND UserId=?")){p.setInt(1,x.tenantId());p.setLong(2,x.caseId());p.setInt(3,x.userId());try(var r=p.executeQuery()){if(r.next())throw new IllegalStateException("User is already a case-team member.");}}
		try(var p=c.prepareStatement("INSERT dbo.CaseUsers(CaseId,UserId,RoleId,IsPrimary,Notes,CreatedAt,UpdatedAt,ShaleClientId) OUTPUT inserted.Id VALUES(?,?,NULL,0,NULL,SYSUTCDATETIME(),SYSUTCDATETIME(),?)")){p.setLong(1,x.caseId());p.setInt(2,x.userId());p.setInt(3,x.tenantId());try(var r=p.executeQuery()){r.next();long id=r.getLong(1);event(c,x.tenantId(),x.actorUserId(),CASE_TEAM_MEMBER,id,ADDED,x.caseId());timeline(c,x.caseId(),x.tenantId(),x.actorUserId(),CaseTimelineWriter.CASE_TEAM_MEMBER_ADDED,"Team member added");}}
		try(var p=c.prepareStatement("SELECT Id FROM dbo.CaseUsers WHERE ShaleClientId=? AND CaseId=? AND UserId=?")){p.setInt(1,x.tenantId());p.setLong(2,x.caseId());p.setInt(3,x.userId());try(var r=p.executeQuery()){r.next();return r.getLong(1);}}});return list(x.tenantId(),x.actorUserId(),x.caseId()).stream().filter(m->m.membershipId()==membershipId).findFirst().orElseThrow();}

	public void assign(CaseTeamMemberRoleCommand x){tx(x.tenantId(),c->{requireActor(c,x.tenantId(),x.actorUserId());membership(c,x.tenantId(),x.caseId(),x.membershipId(),null);Def d=effectiveDefinition(c,x.tenantId(),x.roleDefinitionId());
			if(RESPONSIBLE_ATTORNEY.equals(d.key)){lockResponsible(c,x.tenantId(),x.caseId());clearResponsible(c,x.tenantId(),x.caseId(),x.membershipId(),x.actorUserId());}
			Long deleted=null;try(var p=c.prepareStatement("SELECT Id FROM dbo.CaseTeamMemberRoles WHERE ShaleClientId=? AND CaseUserId=? AND CaseTeamRoleDefinitionId=?")){p.setInt(1,x.tenantId());p.setLong(2,x.membershipId());p.setInt(3,x.roleDefinitionId());try(var r=p.executeQuery()){if(r.next())deleted=r.getLong(1);}}
			long id;if(deleted!=null){try(var p=c.prepareStatement("UPDATE dbo.CaseTeamMemberRoles SET IsDeleted=0,DeletedAt=NULL,DeletedByUserId=NULL,UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? WHERE Id=? AND IsDeleted=1")){p.setInt(1,x.actorUserId());p.setLong(2,deleted);if(p.executeUpdate()!=1)throw new IllegalStateException("Role is already assigned.");}id=deleted;event(c,x.tenantId(),x.actorUserId(),CASE_TEAM_MEMBER_ROLE,id,RESTORED,x.caseId());}
			else try(var p=c.prepareStatement("INSERT dbo.CaseTeamMemberRoles(ShaleClientId,CaseId,CaseUserId,CaseTeamRoleDefinitionId,RoleDefinitionTenantKey,CreatedByUserId) OUTPUT inserted.Id VALUES(?,?,?,?,?,?)")){p.setInt(1,x.tenantId());p.setLong(2,x.caseId());p.setLong(3,x.membershipId());p.setInt(4,x.roleDefinitionId());p.setInt(5,d.owner==null?0:d.owner);p.setInt(6,x.actorUserId());try(var r=p.executeQuery()){r.next();id=r.getLong(1);}event(c,x.tenantId(),x.actorUserId(),CASE_TEAM_MEMBER_ROLE,id,ADDED,x.caseId());}
			if(d.legacy!=null)syncLegacyOnAssign(c,x.membershipId(),d.legacy,RESPONSIBLE_ATTORNEY.equals(d.key));
			timeline(c,x.caseId(),x.tenantId(),x.actorUserId(),RESPONSIBLE_ATTORNEY.equals(d.key)?CaseTimelineWriter.RESPONSIBLE_ATTORNEY_CHANGED:CaseTimelineWriter.CASE_TEAM_ROLE_ASSIGNED,RESPONSIBLE_ATTORNEY.equals(d.key)?"Responsible Attorney changed":"Team role assigned");return null;});}

	public void removeRole(CaseTeamMemberRoleLifecycleCommand x){tx(x.tenantId(),c->{requireActor(c,x.tenantId(),x.actorUserId());membership(c,x.tenantId(),x.caseId(),x.membershipId(),null);String key;Integer legacy;
		try(var p=c.prepareStatement("SELECT d.SystemKey,d.LegacyRoleId FROM dbo.CaseTeamMemberRoles a JOIN dbo.CaseTeamRoleDefinitions d ON d.Id=a.CaseTeamRoleDefinitionId WHERE a.Id=? AND a.ShaleClientId=? AND a.CaseUserId=? AND a.IsDeleted=0 AND a.RowVer=?")){p.setLong(1,x.assignmentId());p.setInt(2,x.tenantId());p.setLong(3,x.membershipId());p.setBytes(4,x.rowVer());try(var r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Role assignment changed or was removed.");key=r.getString(1);legacy=(Integer)r.getObject(2);}}
		try(var p=c.prepareStatement("UPDATE dbo.CaseTeamMemberRoles SET IsDeleted=1,DeletedAt=SYSUTCDATETIME(),DeletedByUserId=?,UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? WHERE Id=? AND RowVer=?")){p.setInt(1,x.actorUserId());p.setInt(2,x.actorUserId());p.setLong(3,x.assignmentId());p.setBytes(4,x.rowVer());if(p.executeUpdate()!=1)throw new IllegalStateException("Role assignment changed or was removed.");}
		if(legacy!=null)try(var p=c.prepareStatement("UPDATE dbo.CaseUsers SET RoleId=NULL,IsPrimary=0,UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND RoleId=?")){p.setLong(1,x.membershipId());p.setInt(2,legacy);p.executeUpdate();}
		event(c,x.tenantId(),x.actorUserId(),CASE_TEAM_MEMBER_ROLE,x.assignmentId(),REMOVED,x.caseId());timeline(c,x.caseId(),x.tenantId(),x.actorUserId(),RESPONSIBLE_ATTORNEY.equals(key)?CaseTimelineWriter.RESPONSIBLE_ATTORNEY_CHANGED:CaseTimelineWriter.CASE_TEAM_ROLE_REMOVED,RESPONSIBLE_ATTORNEY.equals(key)?"Responsible Attorney changed":"Team role removed");return null;});}

	public void removeMember(CaseTeamMemberLifecycleCommand x){tx(x.tenantId(),c->{requireActor(c,x.tenantId(),x.actorUserId());membership(c,x.tenantId(),x.caseId(),x.membershipId(),x.rowVer());try(var p=c.prepareStatement("DELETE dbo.CaseTeamMemberRoles WHERE ShaleClientId=? AND CaseUserId=?")){p.setInt(1,x.tenantId());p.setLong(2,x.membershipId());p.executeUpdate();}try(var p=c.prepareStatement("DELETE dbo.CaseUsers WHERE Id=? AND ShaleClientId=? AND RowVer=?")){p.setLong(1,x.membershipId());p.setInt(2,x.tenantId());p.setBytes(3,x.rowVer());if(p.executeUpdate()!=1)throw new IllegalStateException("Team membership changed.");}event(c,x.tenantId(),x.actorUserId(),CASE_TEAM_MEMBER,x.membershipId(),REMOVED,x.caseId());timeline(c,x.caseId(),x.tenantId(),x.actorUserId(),CaseTimelineWriter.CASE_TEAM_MEMBER_REMOVED,"Team member removed");return null;});}

	private void clearResponsible(Connection c,int t,long caseId,long keep,int actor)throws SQLException{try(var p=c.prepareStatement("""
		UPDATE a SET IsDeleted=1,DeletedAt=SYSUTCDATETIME(),DeletedByUserId=?,UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=?
		FROM dbo.CaseTeamMemberRoles a JOIN dbo.CaseTeamRoleDefinitions d ON d.Id=a.CaseTeamRoleDefinitionId
		WHERE a.ShaleClientId=? AND a.CaseId=? AND a.CaseUserId<>? AND a.IsDeleted=0 AND d.SystemKey='responsible_attorney'""")){p.setInt(1,actor);p.setInt(2,actor);p.setInt(3,t);p.setLong(4,caseId);p.setLong(5,keep);p.executeUpdate();}try(var p=c.prepareStatement("UPDATE dbo.CaseUsers SET RoleId=NULL,IsPrimary=0,UpdatedAt=SYSUTCDATETIME() WHERE ShaleClientId=? AND CaseId=? AND Id<>? AND RoleId=4")){p.setInt(1,t);p.setLong(2,caseId);p.setLong(3,keep);p.executeUpdate();}}
	private static void lockResponsible(Connection c,int tenant,long caseId)throws SQLException{try(var p=c.prepareStatement("DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=?,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000; IF @r<0 THROW 56641,'Responsible Attorney assignment is busy.',1;")){p.setString(1,"CASE_RESPONSIBLE_ATTORNEY:"+tenant+":"+caseId);p.executeUpdate();}}
	private static void syncLegacyOnAssign(Connection c,long member,int role,boolean primary)throws SQLException{try(var p=c.prepareStatement("UPDATE dbo.CaseUsers SET RoleId=CASE WHEN ?=1 OR RoleId IS NULL OR RoleId=? THEN ? ELSE RoleId END,IsPrimary=CASE WHEN ?=1 THEN 1 ELSE IsPrimary END,UpdatedAt=SYSUTCDATETIME() WHERE Id=?")){p.setBoolean(1,primary);p.setInt(2,role);p.setInt(3,role);p.setBoolean(4,primary);p.setLong(5,member);p.executeUpdate();}}
	private Def effectiveDefinition(Connection c,int t,int id)throws SQLException{try(var p=c.prepareStatement("""
		SELECT d.ShaleClientId,d.SystemKey,d.LegacyRoleId FROM dbo.CaseTeamRoleDefinitions d WHERE d.Id=? AND (d.ShaleClientId=? OR d.ShaleClientId IS NULL) AND d.IsActive=1 AND d.IsDeleted=0
		AND (d.ShaleClientId IS NOT NULL OR NOT EXISTS(SELECT 1 FROM dbo.CaseTeamRoleDefinitions o WHERE o.ShaleClientId=? AND o.SystemKey=d.SystemKey AND o.IsDeleted=0))""")){p.setInt(1,id);p.setInt(2,t);p.setInt(3,t);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Role definition is inactive, deleted, inaccessible, or overridden.");return new Def((Integer)r.getObject(1),r.getString(2),(Integer)r.getObject(3));}}}
	private static void membership(Connection c,int t,long caseId,long id,byte[] ver)throws SQLException{String sql="SELECT 1 FROM dbo.CaseUsers WHERE Id=? AND ShaleClientId=? AND CaseId=?"+(ver==null?"":" AND RowVer=?");try(var p=c.prepareStatement(sql)){p.setLong(1,id);p.setInt(2,t);p.setLong(3,caseId);if(ver!=null)p.setBytes(4,ver);try(var r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Team membership was not found or changed.");}}}
	private static void requireContext(Connection c,int t)throws SQLException{try(var p=c.prepareStatement("SELECT CAST(SESSION_CONTEXT(N'ShaleClientId') AS int)" );var r=p.executeQuery()){if(!r.next()||r.getInt(1)!=t)throw new IllegalStateException("Requested tenant does not match session context.");}}
	private static void requireActor(Connection c,int t,int a)throws SQLException{try(var p=c.prepareStatement("SELECT 1 FROM dbo.Users WHERE id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0")){p.setInt(1,a);p.setInt(2,t);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Actor is not an active tenant user.");}}}
	private static void requireUser(Connection c,int t,int u)throws SQLException{requireActor(c,t,u);}
	private static void requireCase(Connection c,int t,long id)throws SQLException{try(var p=c.prepareStatement("SELECT 1 FROM dbo.Cases WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0")){p.setLong(1,id);p.setInt(2,t);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Case is unavailable for tenant.");}}}
	private void event(Connection c,int t,int actor,EntityActionAuditEvent.EntityType type,long id,EntityActionAuditEvent.Action action,long caseId)throws SQLException{audit.append(c,EntityActionAuditEvent.now(t,actor,type,id,action,CASE,caseId,Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID,caseId)));}
	private static void timeline(Connection c,long id,int t,int a,String type,String title)throws SQLException{CaseTimelineWriter.append(c,id,t,a,type,title,null);}
	private <T>T tx(int tenant,SqlWork<T>w){try(Connection c=db.requireConnection()){requireContext(c,tenant);c.setAutoCommit(false);try{T out=w.run(c);c.commit();return out;}catch(SQLException e){c.rollback();throw e;}catch(RuntimeException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}catch(SQLException e){throw failure("change case team",e);}}
	private static RuntimeException failure(String operation,SQLException e){return new RuntimeException("Failed to "+operation,e);}
	private static Instant instant(ResultSet r,String n)throws SQLException{Timestamp t=r.getTimestamp(n);return t==null?null:t.toInstant();}
	private interface SqlWork<T>{T run(Connection c)throws SQLException;}
	private record Def(Integer owner,String key,Integer legacy){}
	private static final class Mutable{final long id,caseId;final int user;final String name;final Integer legacy;final boolean primary;final byte[] version;final List<CaseTeamMemberRoleDto> roles=new ArrayList<>();Mutable(long i,long c,int u,String n,Integer l,boolean p,byte[]v){id=i;caseId=c;user=u;name=n;legacy=l;primary=p;version=v;}CaseTeamMembershipDto dto(){return new CaseTeamMembershipDto(id,caseId,user,name,legacy,primary,version,roles);}}
}
