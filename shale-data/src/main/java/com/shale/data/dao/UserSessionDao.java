package com.shale.data.dao;

import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.shale.core.dto.UserSessionView;
import com.shale.core.model.ClientType;
import com.shale.core.runtime.DbSessionProvider;

/** Tenant- and owner-qualified persistence for durable user sessions. */
public final class UserSessionDao {
	private static final String SELECT="SELECT Id,SessionId,ClientType,ApplicationInstanceId,CurrentAccessJti,IssuedAt,ExpiresAt,RevokedAt,RevocationReason FROM dbo.UserSessions WHERE SessionId=? AND ShaleClientId=? AND UserId=?";
	private final DbSessionProvider db;
	public UserSessionDao(DbSessionProvider db){this.db=Objects.requireNonNull(db,"db");}
	public UserSessionView create(int tenant,int user,ClientType type,Long instance,UUID sessionId,UUID jti,Instant expiresAt){
		try(Connection c=db.requireConnection()){verifyOwner(c,tenant,user,type,instance);try(PreparedStatement p=c.prepareStatement("INSERT dbo.UserSessions(ShaleClientId,UserId,SessionId,ApplicationInstanceId,ClientType,CurrentAccessJti,ExpiresAt) VALUES(?,?,?,?,?,?,?)")){p.setInt(1,tenant);p.setInt(2,user);p.setObject(3,sessionId);if(instance==null)p.setNull(4,Types.BIGINT);else p.setLong(4,instance);p.setString(5,type.name());p.setObject(6,jti);p.setTimestamp(7,Timestamp.from(expiresAt));p.executeUpdate();}return load(c,tenant,user,sessionId);}
		catch(SQLException e){throw new IllegalStateException("Failed to create durable user session",e);}}
	public Optional<UserSessionView> find(int tenant,int user,UUID sessionId){try(Connection c=db.requireConnection()){return find(c,tenant,user,sessionId);}catch(SQLException e){throw new IllegalStateException("Failed to find durable user session",e);}}
	public UserSessionView revoke(int tenant,int user,UUID sessionId,String reason){try(Connection c=db.requireConnection()){try(PreparedStatement p=c.prepareStatement("UPDATE dbo.UserSessions SET RevokedAt=COALESCE(RevokedAt,SYSUTCDATETIME()),RevocationReason=COALESCE(RevocationReason,?),UpdatedAt=CASE WHEN RevokedAt IS NULL THEN SYSUTCDATETIME() ELSE UpdatedAt END WHERE SessionId=? AND ShaleClientId=? AND UserId=?")){p.setString(1,reason);p.setObject(2,sessionId);p.setInt(3,tenant);p.setInt(4,user);if(p.executeUpdate()!=1)throw unavailable();}return load(c,tenant,user,sessionId);}catch(SQLException e){throw new IllegalStateException("Failed to revoke durable user session",e);}}
	public UserSessionView rotate(int tenant,int user,UUID sessionId,UUID expected,UUID replacement,Instant expiry){try(Connection c=db.requireConnection()){try(PreparedStatement p=c.prepareStatement("UPDATE dbo.UserSessions SET CurrentAccessJti=?,ExpiresAt=?,LastRefreshedAt=SYSUTCDATETIME(),UpdatedAt=SYSUTCDATETIME() WHERE SessionId=? AND ShaleClientId=? AND UserId=? AND CurrentAccessJti=? AND RevokedAt IS NULL")){p.setObject(1,replacement);p.setTimestamp(2,Timestamp.from(expiry));p.setObject(3,sessionId);p.setInt(4,tenant);p.setInt(5,user);p.setObject(6,expected);if(p.executeUpdate()!=1)throw new IllegalStateException("Stale, revoked, or unavailable durable user session.");}return load(c,tenant,user,sessionId);}catch(SQLException e){throw new IllegalStateException("Failed to rotate durable session credential",e);}}
	private static void verifyOwner(Connection c,int tenant,int user,ClientType type,Long instance)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM dbo.Users u WHERE u.Id=? AND u.ShaleClientId=? AND COALESCE(u.is_deleted,0)=0 AND COALESCE(u.IsRemoved,0)=0 AND CAST(SESSION_CONTEXT(N'ShaleClientId') AS int)=? AND CAST(SESSION_CONTEXT(N'PrincipalUserId') AS int)=? AND (? IS NULL OR (?='DESKTOP' AND EXISTS(SELECT 1 FROM dbo.ApplicationInstances ai WHERE ai.Id=? AND ai.ShaleClientId=? AND ai.UserId=?)))")){p.setInt(1,user);p.setInt(2,tenant);p.setInt(3,tenant);p.setInt(4,user);if(instance==null)p.setNull(5,Types.BIGINT);else p.setLong(5,instance);p.setString(6,type.name());if(instance==null)p.setNull(7,Types.BIGINT);else p.setLong(7,instance);p.setInt(8,tenant);p.setInt(9,user);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SecurityException("An active same-tenant owner and compatible application instance are required.");}}}
	private static Optional<UserSessionView> find(Connection c,int tenant,int user,UUID id)throws SQLException{try(PreparedStatement p=c.prepareStatement(SELECT)){p.setObject(1,id);p.setInt(2,tenant);p.setInt(3,user);try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(map(r)):Optional.empty();}}}
	private static UserSessionView load(Connection c,int tenant,int user,UUID id)throws SQLException{return find(c,tenant,user,id).orElseThrow(UserSessionDao::unavailable);}
	private static UserSessionView map(ResultSet r)throws SQLException{Timestamp refreshed=null,revoked=r.getTimestamp(8);long instance=r.getLong(4);return new UserSessionView(r.getLong(1),UUID.fromString(r.getString(2)),ClientType.valueOf(r.getString(3)),r.wasNull()?null:instance,UUID.fromString(r.getString(5)),r.getTimestamp(6).toInstant(),r.getTimestamp(7).toInstant(),revoked==null?null:revoked.toInstant(),r.getString(9));}
	private static SecurityException unavailable(){return new SecurityException("Durable user session is unavailable to the authenticated owner.");}
}
