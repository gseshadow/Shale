package com.shale.data.dao;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import com.shale.core.dto.*;
import com.shale.core.model.*;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.ApplicationInstanceAdminReadServicePort.Filter;

/** Set-based, explicitly tenant-qualified administrative reads. */
public final class ApplicationInstanceAdminReadDao {
	private final DbSessionProvider db; private final AdministrativeReadAuditDao audit;
	public ApplicationInstanceAdminReadDao(DbSessionProvider db) { this(db,new AdministrativeReadAuditDao()); }
	ApplicationInstanceAdminReadDao(DbSessionProvider db,AdministrativeReadAuditDao audit) { this.db=Objects.requireNonNull(db,"db");this.audit=Objects.requireNonNull(audit,"audit"); }

	public ApplicationInstanceAdminPage listRecent(int tenant,int actor,Filter filter,int page,int size) {
		try(Connection c=db.requireConnection()) {
			c.setAutoCommit(false);
			verifyAdmin(c,tenant,actor);
			StringBuilder sql=new StringBuilder("""
				SELECT ai.Id,ai.UserId,
				 LTRIM(RTRIM(COALESCE(u.name_first,'')+CASE WHEN COALESCE(u.name_first,'')='' OR COALESCE(u.name_last,'')='' THEN '' ELSE ' ' END+COALESCE(u.name_last,''))) UserDisplayName,
				 COALESCE(u.email,'') UserEmail,ai.MachineId,ai.ClientType,ai.MajorVersion,ai.MinorVersion,ai.BuildVersion,
				 ai.StartedAt,ai.EndedAt,ai.LastHeartbeatAt,ai.LastHumanActivityAt
				FROM dbo.ApplicationInstances ai
				JOIN dbo.Users u ON u.Id=ai.UserId AND u.ShaleClientId=ai.ShaleClientId
				WHERE ai.ShaleClientId=? AND ai.StartedAt>=?
				""");
			if(filter.clientType()!=null)sql.append(" AND ai.ClientType=?");
			if(filter.applicationVersion()!=null)sql.append(" AND ai.MajorVersion=? AND ai.MinorVersion=? AND ai.BuildVersion=?");
			if(filter.userId()!=null)sql.append(" AND ai.UserId=?");
			if(filter.activeOnly())sql.append(" AND ai.EndedAt IS NULL");
			sql.append(" ORDER BY ai.StartedAt DESC,ai.Id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
			try(PreparedStatement p=c.prepareStatement(sql.toString())) {
				int i=1;p.setInt(i++,tenant);p.setTimestamp(i++,Timestamp.from(filter.startedSince()));
				if(filter.clientType()!=null)p.setString(i++,filter.clientType().name());
				if(filter.applicationVersion()!=null){p.setInt(i++,filter.applicationVersion().major());p.setInt(i++,filter.applicationVersion().minor());p.setInt(i++,filter.applicationVersion().build());}
				if(filter.userId()!=null)p.setInt(i++,filter.userId());
				p.setInt(i++,Math.multiplyExact(page,size));p.setInt(i,size);
				try(ResultSet r=p.executeQuery()){List<AdminApplicationInstanceView> out=new ArrayList<>();while(r.next())out.add(instance(r));var result=new ApplicationInstanceAdminPage(out,page,size);audit.append(c,AdministrativeReadAuditEvent.recentList(tenant,actor,out.size(),page,size,filter.clientType()==null?null:filter.clientType().name(),filter.applicationVersion()==null?null:filter.applicationVersion().toString(),filter.userId()!=null,filter.activeOnly(),filter.startedSince()));c.commit();return result;}
			}
		}catch(SQLException e){throw new IllegalStateException("Failed to complete audited application-instance read",e);}
	}

	public List<ApplicationVersionDistributionView> versionDistribution(int tenant,int actor,Instant since) {
		try(Connection c=db.requireConnection()) {
			c.setAutoCommit(false);
			verifyAdmin(c,tenant,actor);
			String sql="""
				SELECT MajorVersion,MinorVersion,BuildVersion,COUNT_BIG(*) InstanceCount,
				 COUNT_BIG(DISTINCT UserId) DistinctUserCount,MAX(LastHeartbeatAt) LatestHeartbeatAt
				FROM dbo.ApplicationInstances WHERE ShaleClientId=? AND StartedAt>=?
				GROUP BY MajorVersion,MinorVersion,BuildVersion
				ORDER BY MajorVersion DESC,MinorVersion DESC,BuildVersion DESC
				""";
			try(PreparedStatement p=c.prepareStatement(sql)){p.setInt(1,tenant);p.setTimestamp(2,Timestamp.from(since));try(ResultSet r=p.executeQuery()){List<ApplicationVersionDistributionView> out=new ArrayList<>();while(r.next()){Timestamp heartbeat=r.getTimestamp("LatestHeartbeatAt");out.add(new ApplicationVersionDistributionView(new SemanticVersion(r.getInt("MajorVersion"),r.getInt("MinorVersion"),r.getInt("BuildVersion")),r.getLong("InstanceCount"),r.getLong("DistinctUserCount"),heartbeat==null?null:heartbeat.toInstant()));}List<ApplicationVersionDistributionView> result=List.copyOf(out);audit.append(c,AdministrativeReadAuditEvent.versionDistribution(tenant,actor,result.size(),since));c.commit();return result;}}
		}catch(SQLException e){throw new IllegalStateException("Failed to complete audited application-instance version distribution",e);}
	}

	private static AdminApplicationInstanceView instance(ResultSet r)throws SQLException{
		String machine=r.getString("MachineId");Timestamp ended=r.getTimestamp("EndedAt"),heartbeat=r.getTimestamp("LastHeartbeatAt"),activity=r.getTimestamp("LastHumanActivityAt");
		return new AdminApplicationInstanceView(r.getLong("Id"),r.getInt("UserId"),r.getString("UserDisplayName"),r.getString("UserEmail"),machine==null?null:UUID.fromString(machine),ClientType.valueOf(r.getString("ClientType")),new SemanticVersion(r.getInt("MajorVersion"),r.getInt("MinorVersion"),r.getInt("BuildVersion")),r.getTimestamp("StartedAt").toInstant(),instant(ended),instant(heartbeat),instant(activity));
	}
	private static Instant instant(Timestamp t){return t==null?null:t.toInstant();}
	private static void verifyAdmin(Connection c,int tenant,int actor)throws SQLException{
		if(tenant<=0||actor<=0)throw new SecurityException("An authenticated tenant administrator is required.");
		String sql="SELECT 1 FROM dbo.Users WHERE Id=? AND ShaleClientId=? AND COALESCE(is_admin,0)=1 AND COALESCE(is_deleted,0)=0 AND COALESCE(IsRemoved,0)=0 AND CAST(SESSION_CONTEXT(N'ShaleClientId') AS int)=? AND CAST(SESSION_CONTEXT(N'PrincipalUserId') AS int)=?";
		try(PreparedStatement p=c.prepareStatement(sql)){p.setInt(1,actor);p.setInt(2,tenant);p.setInt(3,tenant);p.setInt(4,actor);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SecurityException("Administrator access is required.");}}
	}
}
