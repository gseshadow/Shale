package com.shale.data.dao;

import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.shale.core.dto.ApplicationInstanceView;
import com.shale.core.model.ClientType;
import com.shale.core.model.SemanticVersion;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.EndedApplicationInstanceException;

/** Current-principal-only persistence for application process enrollments. */
public final class ApplicationInstanceDao {
	private static final String SELECT = "SELECT Id,MachineId,ClientType,MajorVersion,MinorVersion,BuildVersion,StartedAt,EndedAt,LastHeartbeatAt,LastHumanActivityAt FROM dbo.ApplicationInstances WHERE Id=? AND ShaleClientId=? AND UserId=?";
	private final DbSessionProvider db;
	public ApplicationInstanceDao(DbSessionProvider db) { this.db=Objects.requireNonNull(db,"db"); }

	public ApplicationInstanceView enroll(int tenant, int user, UUID machineId, ClientType type, SemanticVersion version) {
		scope(tenant,user); Objects.requireNonNull(type,"clientType"); Objects.requireNonNull(version,"applicationVersion");
		if (type==ClientType.DESKTOP && machineId==null) throw new IllegalArgumentException("DESKTOP enrollment requires machineId");
		if (type!=ClientType.DESKTOP && machineId!=null) throw new IllegalArgumentException("machineId is only valid for DESKTOP");
		try (Connection c=db.requireConnection()) {
			verifyActor(c,tenant,user);
			try (PreparedStatement p=c.prepareStatement("INSERT dbo.ApplicationInstances(ShaleClientId,UserId,MachineId,ClientType,MajorVersion,MinorVersion,BuildVersion) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,?)")) {
				p.setInt(1,tenant); p.setInt(2,user);
				if(machineId==null)p.setNull(3,Types.OTHER);else p.setObject(3,machineId);
				p.setString(4,type.name());p.setInt(5,version.major());p.setInt(6,version.minor());p.setInt(7,version.build());
				try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Enrollment did not return an id");return load(c,r.getLong(1),tenant,user);}
			}
		} catch(SQLException e){throw new IllegalStateException("Failed to enroll application instance",e);}
	}

	public ApplicationInstanceView end(int tenant,int user,long id){
		scope(tenant,user);if(id<=0)throw new IllegalArgumentException("applicationInstanceId must be positive");
		try(Connection c=db.requireConnection()){
			verifyActor(c,tenant,user);
			try(PreparedStatement p=c.prepareStatement("UPDATE dbo.ApplicationInstances SET EndedAt=COALESCE(EndedAt,SYSUTCDATETIME()),UpdatedAt=CASE WHEN EndedAt IS NULL THEN SYSUTCDATETIME() ELSE UpdatedAt END WHERE Id=? AND ShaleClientId=? AND UserId=?")){
				p.setLong(1,id);p.setInt(2,tenant);p.setInt(3,user);if(p.executeUpdate()!=1)throw new SecurityException("Application instance is unavailable to the authenticated owner.");
			}
			return load(c,id,tenant,user);
		}catch(SQLException e){throw new IllegalStateException("Failed to end application instance",e);}
	}

	public ApplicationInstanceView heartbeat(int tenant,int user,long id,SemanticVersion version,Instant activity){
		scope(tenant,user);if(id<=0)throw new IllegalArgumentException("applicationInstanceId must be positive");Objects.requireNonNull(version,"applicationVersion");
		try(Connection c=db.requireConnection()){
			verifyActor(c,tenant,user);
			String sql="UPDATE dbo.ApplicationInstances SET LastHeartbeatAt=SYSUTCDATETIME(),LastHumanActivityAt=CASE WHEN ? IS NOT NULL AND (LastHumanActivityAt IS NULL OR LastHumanActivityAt<?) THEN ? ELSE LastHumanActivityAt END,MajorVersion=?,MinorVersion=?,BuildVersion=?,UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND ShaleClientId=? AND UserId=? AND EndedAt IS NULL";
			try(PreparedStatement p=c.prepareStatement(sql)){Timestamp a=activity==null?null:Timestamp.from(activity);p.setTimestamp(1,a);p.setTimestamp(2,a);p.setTimestamp(3,a);p.setInt(4,version.major());p.setInt(5,version.minor());p.setInt(6,version.build());p.setLong(7,id);p.setInt(8,tenant);p.setInt(9,user);if(p.executeUpdate()!=1){ApplicationInstanceView existing=load(c,id,tenant,user);if(existing.endedAt()!=null)throw new EndedApplicationInstanceException();throw new SecurityException("Application instance is unavailable to the authenticated owner.");}}
			return load(c,id,tenant,user);
		}catch(SQLException e){throw new IllegalStateException("Failed to heartbeat application instance",e);}
	}
	private static ApplicationInstanceView load(Connection c,long id,int tenant,int user)throws SQLException{try(PreparedStatement p=c.prepareStatement(SELECT)){p.setLong(1,id);p.setInt(2,tenant);p.setInt(3,user);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SecurityException("Application instance is unavailable to the authenticated owner.");String rawMachine=r.getString(2);UUID machine=rawMachine==null?null:UUID.fromString(rawMachine);Timestamp ended=r.getTimestamp(8),heartbeat=r.getTimestamp(9),activity=r.getTimestamp(10);return new ApplicationInstanceView(r.getLong(1),machine,ClientType.valueOf(r.getString(3)),new SemanticVersion(r.getInt(4),r.getInt(5),r.getInt(6)),r.getTimestamp(7).toInstant(),instant(ended),instant(heartbeat),instant(activity));}}}
	private static Instant instant(Timestamp value){return value==null?null:value.toInstant();}
	private static void verifyActor(Connection c,int tenant,int user)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM dbo.Users WHERE id=? AND ShaleClientId=? AND COALESCE(is_deleted,0)=0 AND COALESCE(IsRemoved,0)=0 AND CAST(SESSION_CONTEXT(N'ShaleClientId') AS int)=? AND CAST(SESSION_CONTEXT(N'PrincipalUserId') AS int)=?")){p.setInt(1,user);p.setInt(2,tenant);p.setInt(3,tenant);p.setInt(4,user);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SecurityException("An authenticated active same-tenant user is required.");}}}
	private static void scope(int tenant,int user){if(tenant<=0||user<=0)throw new SecurityException("An authenticated tenant user is required.");}
}
