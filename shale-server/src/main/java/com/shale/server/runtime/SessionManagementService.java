package com.shale.server.runtime;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import com.shale.core.model.ClientType;
import com.shale.server.dto.UserSessionResponse;
import com.shale.server.live.InvalidationPublisher;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;

/** Authoritative, tenant-qualified session management. Authorization is repeated at this service boundary. */
public final class SessionManagementService {
	private static final Logger LOG=LoggerFactory.getLogger(SessionManagementService.class);
	private final RuntimeConnectionProvider connections;
	private final InvalidationPublisher invalidations;
	public SessionManagementService(RuntimeConnectionProvider connections){this(connections,InvalidationPublisher.disabled());}
	public SessionManagementService(RuntimeConnectionProvider connections,InvalidationPublisher invalidations){this.connections=Objects.requireNonNull(connections);this.invalidations=Objects.requireNonNull(invalidations);}

	public List<UserSessionResponse> listOwnSessions(ServerPrincipal actor,UUID currentSid){return query(actor,currentSid,false,null,null,false,0,100);}
	public List<UserSessionResponse> listTenantSessionsAsAdmin(ServerPrincipal actor,UUID currentSid,Integer userId,ClientType type,boolean activeOnly,Instant since,int page,int size){
		if(page<0||size<1||size>100)throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100.");
		return query(actor,currentSid,true,userId,type,activeOnly,page,size,since);
	}
	private List<UserSessionResponse> query(ServerPrincipal actor,UUID sid,boolean admin,Integer userId,ClientType type,boolean activeOnly,int page,int size){return query(actor,sid,admin,userId,type,activeOnly,page,size,null);}
	private List<UserSessionResponse> query(ServerPrincipal actor,UUID sid,boolean admin,Integer userId,ClientType type,boolean activeOnly,int page,int size,Instant since){
		try(Connection c=connections.openConnection(actor)){c.setAutoCommit(false);if(admin)requireAdmin(c,actor);else requireEligibleActor(c,actor);
			StringBuilder q=new StringBuilder("SELECT Id,SessionId,UserId,ClientType,IssuedAt,ExpiresAt,LastRefreshedAt,RevokedAt,RevocationReason FROM dbo.UserSessions WHERE ShaleClientId=?");
			if(!admin)q.append(" AND UserId=?");else if(userId!=null)q.append(" AND UserId=?");if(type!=null)q.append(" AND ClientType=?");if(activeOnly)q.append(" AND RevokedAt IS NULL AND ExpiresAt>SYSUTCDATETIME()");if(since!=null)q.append(" AND IssuedAt>=?");q.append(" ORDER BY IssuedAt DESC,Id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
			try(PreparedStatement p=c.prepareStatement(q.toString())){int i=1;p.setInt(i++,actor.shaleClientId());if(!admin)p.setInt(i++,actor.userId());else if(userId!=null)p.setInt(i++,userId);if(type!=null)p.setString(i++,type.name());if(since!=null)p.setTimestamp(i++,Timestamp.from(since));p.setInt(i++,Math.multiplyExact(page,size));p.setInt(i,size);List<UserSessionResponse> out=new ArrayList<>();try(ResultSet r=p.executeQuery()){while(r.next()){UUID id=UUID.fromString(r.getString("SessionId"));out.add(new UserSessionResponse(id,r.getInt("UserId"),ClientType.valueOf(r.getString("ClientType")),instant(r,"IssuedAt"),instant(r,"ExpiresAt"),nullable(r,"LastRefreshedAt"),nullable(r,"RevokedAt"),r.getString("RevocationReason"),id.equals(sid)));}}
				if(admin)appendAudit(c,actor,"ADMIN_SESSION_LIST",null,userId,out.size(),"ADMIN_READ");c.commit();return List.copyOf(out);}
		}catch(SQLException e){throw new IllegalStateException("Failed to complete session management operation",e);}
	}
	public void revokeOwnSession(ServerPrincipal actor,UUID currentSid,UUID target){publishAfterCommit(actor,mutate(actor,currentSid,target,false,false,"USER_REVOKED","SELF_REVOKE"));}
	/** Current-client logout clears local state immediately, so it deliberately avoids a redundant self notification. */
	public void revokeCurrentSession(ServerPrincipal actor,UUID currentSid){mutate(actor,currentSid,currentSid,false,false,"USER_LOGOUT","SELF_LOGOUT");}
	public void revokeTenantSessionAsAdmin(ServerPrincipal actor,UUID currentSid,UUID target){publishAfterCommit(actor,mutate(actor,currentSid,target,true,false,"ADMIN_REVOKED","ADMIN_REVOKE"));}
	public int revokeOtherOwnSessions(ServerPrincipal actor,UUID currentSid){List<UUID> changed=mutate(actor,currentSid,null,false,true,"USER_REVOKED","SELF_REVOKE_OTHERS");publishAfterCommit(actor,changed);return changed.size();}
	private List<UUID> mutate(ServerPrincipal actor,UUID currentSid,UUID target,boolean admin,boolean others,String reason,String event){
		try(Connection c=connections.openConnection(actor)){c.setAutoCommit(false);try{if(admin)requireAdmin(c,actor);else requireEligibleActor(c,actor);String sql=others?"UPDATE dbo.UserSessions SET RevokedAt=SYSUTCDATETIME(),RevocationReason=?,UpdatedAt=SYSUTCDATETIME() OUTPUT INSERTED.SessionId WHERE ShaleClientId=? AND UserId=? AND SessionId<>? AND RevokedAt IS NULL":"UPDATE dbo.UserSessions SET RevokedAt=COALESCE(RevokedAt,SYSUTCDATETIME()),RevocationReason=COALESCE(RevocationReason,?),UpdatedAt=CASE WHEN RevokedAt IS NULL THEN SYSUTCDATETIME() ELSE UpdatedAt END OUTPUT INSERTED.SessionId WHERE ShaleClientId=? "+(admin?"":"AND UserId=? ")+"AND SessionId=?";List<UUID> changed=new ArrayList<>();Integer targetUser=null;try(PreparedStatement p=c.prepareStatement(sql)){int i=1;p.setString(i++,reason);p.setInt(i++,actor.shaleClientId());if(!admin)p.setInt(i++,actor.userId());p.setObject(i,target==null?currentSid:target);try(ResultSet r=p.executeQuery()){while(r.next())changed.add(UUID.fromString(r.getString(1)));}}if(!others&&changed.isEmpty())throw new NoSuchElementException("Session unavailable");if(admin)targetUser=targetUser(c,actor.shaleClientId(),target);appendAudit(c,actor,event,target,targetUser,changed.size(),reason);c.commit();return List.copyOf(changed);}catch(Exception e){c.rollback();throw e;}}
		catch(NoSuchElementException|SecurityException e){throw e;}catch(Exception e){throw new IllegalStateException("Failed to revoke durable session",e);}
	}
	private void publishAfterCommit(ServerPrincipal actor,List<UUID> sessions){for(UUID session:sessions){try{invalidations.sessionInvalidated(actor.shaleClientId(),session);LOG.debug("Session invalidation published.");}catch(RuntimeException failure){LOG.warn("Session invalidation publish failed after authoritative commit ({}).",failure.getClass().getSimpleName());}}}
	private static Integer targetUser(Connection c,int tenant,UUID sid)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT UserId FROM dbo.UserSessions WHERE ShaleClientId=? AND SessionId=?")){p.setInt(1,tenant);p.setObject(2,sid);try(ResultSet r=p.executeQuery()){return r.next()?r.getInt(1):null;}}}
	private static void requireEligibleActor(Connection c,ServerPrincipal a)throws SQLException{verify(c,a,false);}
	private static void requireAdmin(Connection c,ServerPrincipal a)throws SQLException{verify(c,a,true);}
	private static void verify(Connection c,ServerPrincipal a,boolean admin)throws SQLException{String sql="SELECT 1 FROM dbo.Users WHERE Id=? AND ShaleClientId=? AND COALESCE(is_deleted,0)=0 AND COALESCE(IsRemoved,0)=0"+(admin?" AND COALESCE(is_admin,0)=1":"")+" AND CAST(SESSION_CONTEXT(N'ShaleClientId') AS int)=? AND CAST(SESSION_CONTEXT(N'PrincipalUserId') AS int)=?";try(PreparedStatement p=c.prepareStatement(sql)){p.setInt(1,a.userId());p.setInt(2,a.shaleClientId());p.setInt(3,a.shaleClientId());p.setInt(4,a.userId());try(ResultSet r=p.executeQuery()){if(!r.next())throw new SecurityException(admin?"Administrator access is required.":"Active account is required.");}}}
	private static void appendAudit(Connection c,ServerPrincipal a,String type,UUID target,Integer targetUser,int count,String reason)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT dbo.SessionSecurityAuditLog(ShaleClientId,ActorUserId,EventType,TargetSessionId,TargetUserId,AffectedCount,ReasonCode) VALUES(?,?,?,?,?,?,?)")){p.setInt(1,a.shaleClientId());p.setInt(2,a.userId());p.setString(3,type);if(target==null)p.setNull(4,Types.VARCHAR);else p.setObject(4,target);if(targetUser==null)p.setNull(5,Types.INTEGER);else p.setInt(5,targetUser);p.setInt(6,count);p.setString(7,reason);p.executeUpdate();}}
	private static Instant instant(ResultSet r,String n)throws SQLException{return r.getTimestamp(n).toInstant();}private static Instant nullable(ResultSet r,String n)throws SQLException{Timestamp t=r.getTimestamp(n);return t==null?null:t.toInstant();}
}
