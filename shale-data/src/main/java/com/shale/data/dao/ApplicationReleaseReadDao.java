package com.shale.data.dao;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import com.shale.core.dto.*;
import com.shale.core.dto.ApplicationPolicyView.ReleaseReference;
import com.shale.core.model.*;
import com.shale.core.runtime.DbSessionProvider;

/** Global, read-only access to the release catalog and effective policy. */
public final class ApplicationReleaseReadDao {
	private static final String RELEASES_AFTER_SQL = """
		SELECT Id,MajorVersion,MinorVersion,BuildVersion,ReleaseChannel,PublicationStatus,PublishedAt,Summary,RowVer
		FROM dbo.ApplicationReleases
		WHERE ReleaseChannel=? AND PublicationStatus='PUBLISHED'
		AND (MajorVersion>? OR (MajorVersion=? AND MinorVersion>?) OR (MajorVersion=? AND MinorVersion=? AND BuildVersion>?))
		ORDER BY MajorVersion ASC,MinorVersion ASC,BuildVersion ASC""";
	private static final String ITEMS_SQL = """
		SELECT Id,ApplicationReleaseId,SortOrder,ItemType,Title,Body,ResourceUrl,IsActive
		FROM dbo.ApplicationReleaseItems WHERE ApplicationReleaseId=? AND IsActive=1 ORDER BY SortOrder ASC,Id ASC""";
	private static final String POLICY_SQL = """
		SELECT p.Id,p.ReleaseChannel,p.RevisionNumber,p.RequiredUpdateDeadline,p.AccessMode,p.PublishedAt,p.RowVer,
		 l.Id,l.MajorVersion,l.MinorVersion,l.BuildVersion,l.ReleaseChannel,l.PublicationStatus,
		 r.Id,r.MajorVersion,r.MinorVersion,r.BuildVersion,r.ReleaseChannel,r.PublicationStatus,
		 a.Id,a.MajorVersion,a.MinorVersion,a.BuildVersion,a.ReleaseChannel,a.PublicationStatus,SYSUTCDATETIME() AS ServerTime
		FROM dbo.ApplicationPolicy p
		LEFT JOIN dbo.ApplicationReleases l ON l.Id=p.LatestReleaseId
		LEFT JOIN dbo.ApplicationReleases r ON r.Id=p.MinimumRecommendedReleaseId
		LEFT JOIN dbo.ApplicationReleases a ON a.Id=p.MinimumAllowedReleaseId
		WHERE p.ReleaseChannel=? AND p.IsCurrent=1""";
	private final DbSessionProvider db;
	public ApplicationReleaseReadDao(DbSessionProvider db) { this.db=Objects.requireNonNull(db,"db"); }

	public Optional<ApplicationPolicyView> findCurrentPolicy(ReleaseChannel channel) {
		Objects.requireNonNull(channel,"channel");
		try(Connection c=db.requireConnection(); PreparedStatement p=c.prepareStatement(POLICY_SQL)) {
			p.setString(1,channel.name()); try(ResultSet rs=p.executeQuery()) {
				if(!rs.next()) return Optional.empty();
				ApplicationPolicyView value=mapPolicy(rs);
				if(rs.next()) throw new IllegalStateException("Multiple current application policies for channel " + channel);
				validatePolicy(value); return Optional.of(value);
			}
		} catch(SQLException ex) { throw new IllegalStateException("Failed to read current application policy",ex); }
	}

	public List<ApplicationReleaseView> listPublishedReleasesAfter(ReleaseChannel channel, SemanticVersion version) {
		Objects.requireNonNull(channel,"channel"); Objects.requireNonNull(version,"version");
		try(Connection c=db.requireConnection(); PreparedStatement p=c.prepareStatement(RELEASES_AFTER_SQL)) {
			p.setString(1,channel.name()); p.setInt(2,version.major()); p.setInt(3,version.major()); p.setInt(4,version.minor());
			p.setInt(5,version.major()); p.setInt(6,version.minor()); p.setInt(7,version.build());
			try(ResultSet rs=p.executeQuery()){ List<ApplicationReleaseView> out=new ArrayList<>(); while(rs.next()) out.add(mapRelease(rs)); return List.copyOf(out); }
		} catch(SQLException ex){ throw new IllegalStateException("Failed to read published application releases",ex); }
	}

	public List<ApplicationReleaseItemView> listReleaseItems(long releaseId) {
		if(releaseId<=0) throw new IllegalArgumentException("releaseId must be positive");
		try(Connection c=db.requireConnection(); PreparedStatement p=c.prepareStatement(ITEMS_SQL)) {
			p.setLong(1,releaseId); try(ResultSet rs=p.executeQuery()){List<ApplicationReleaseItemView> out=new ArrayList<>(); while(rs.next()) out.add(new ApplicationReleaseItemView(rs.getLong(1),rs.getLong(2),rs.getInt(3),enumValue(ReleaseItemType.class,rs.getString(4)),rs.getString(5),rs.getString(6),rs.getString(7),rs.getBoolean(8)));return List.copyOf(out);}
		} catch(SQLException ex){throw new IllegalStateException("Failed to read application release items",ex);}
	}

	private static ApplicationReleaseView mapRelease(ResultSet r)throws SQLException{return new ApplicationReleaseView(r.getLong(1),new SemanticVersion(r.getInt(2),r.getInt(3),r.getInt(4)),enumValue(ReleaseChannel.class,r.getString(5)),enumValue(PublicationStatus.class,r.getString(6)),instant(r.getTimestamp(7)),r.getString(8),r.getBytes(9));}
	private static ApplicationPolicyView mapPolicy(ResultSet r)throws SQLException{ReleaseChannel channel=enumValue(ReleaseChannel.class,r.getString(2));return new ApplicationPolicyView(r.getLong(1),channel,r.getLong(3),reference(r,8,channel),reference(r,14,channel),reference(r,20,channel),instant(r.getTimestamp(4)),enumValue(ApplicationAccessMode.class,r.getString(5)),instant(r.getTimestamp(6)),instant(r.getTimestamp(26)),r.getBytes(7));}
	private static ReleaseReference reference(ResultSet r,int start,ReleaseChannel policyChannel)throws SQLException{
		Long id=(Long)r.getObject(start); if(id==null)return null;
		ReleaseChannel releaseChannel=enumValue(ReleaseChannel.class,r.getString(start+4));
		PublicationStatus status=enumValue(PublicationStatus.class,r.getString(start+5));
		if(releaseChannel!=policyChannel)throw new IllegalStateException("Invalid current application policy: referenced release has a different channel");
		if(status!=PublicationStatus.PUBLISHED)throw new IllegalStateException("Invalid current application policy: referenced release is not published");
		return new ReleaseReference(id,new SemanticVersion(r.getInt(start+1),r.getInt(start+2),r.getInt(start+3)));
	}
	private static void validatePolicy(ApplicationPolicyView p){
		compare(p.minimumAllowed(),p.minimumRecommended(),"minimum allowed exceeds minimum recommended");
		compare(p.minimumAllowed(),p.latest(),"minimum allowed exceeds latest");
		compare(p.minimumRecommended(),p.latest(),"minimum recommended exceeds latest");
	}
	private static void compare(ReleaseReference low,ReleaseReference high,String message){if(low!=null&&high!=null&&low.version().compareTo(high.version())>0)throw new IllegalStateException("Invalid current application policy: "+message);}
	private static Instant instant(Timestamp value){return value==null?null:value.toInstant();}
	private static <E extends Enum<E>> E enumValue(Class<E> type,String value){try{return Enum.valueOf(type,value);}catch(RuntimeException ex){throw new IllegalStateException("Unknown "+type.getSimpleName()+" database value: "+value,ex);}}
}
