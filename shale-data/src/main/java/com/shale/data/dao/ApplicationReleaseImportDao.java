package com.shale.data.dao;

import java.sql.*;
import java.time.*;
import java.util.*;

import com.shale.core.dto.ApplicationReleaseImport;
import com.shale.core.dto.ApplicationReleaseImportResult;
import com.shale.core.runtime.DbSessionProvider;

/** Owns the atomic global release-catalog import and its global audit append. */
public final class ApplicationReleaseImportDao {
	private final DbSessionProvider db;
	public ApplicationReleaseImportDao(DbSessionProvider db) { this.db = Objects.requireNonNull(db); }

	public ApplicationReleaseImportResult importProductionRelease(ApplicationReleaseImport source,
			boolean allowUpdate, String operatorId) {
		validate(source, allowUpdate, operatorId);
		try (Connection c = db.requireConnection()) {
			boolean auto = c.getAutoCommit(); c.setAutoCommit(false);
			try {
				Existing existing = findForUpdate(c, source);
				ApplicationReleaseImportResult result;
				if (existing == null) result = create(c, source, operatorId);
				else if (same(existing, source)) result = new ApplicationReleaseImportResult(existing.id,
						source.version().toString(), ApplicationReleaseImportResult.Outcome.UNCHANGED, existing.rowVersion);
				else {
					if (!allowUpdate) throw new ReleaseImportConflictException("Release catalog content differs from the authored source.");
					if (!Arrays.equals(existing.rowVersion, source.expectedRowVersion()))
						throw new ReleaseImportConflictException("Release catalog RowVer changed; reload before controlled update.");
					result = update(c, existing.id, source, operatorId);
				}
				c.commit(); return result;
			} catch (RuntimeException | SQLException ex) { c.rollback(); throw ex; }
			finally { c.setAutoCommit(auto); }
		} catch (SQLException ex) { throw new RuntimeException("Release catalog import failed.", ex); }
	}

	private static Existing findForUpdate(Connection c, ApplicationReleaseImport s) throws SQLException {
		String sql="SELECT Id,Title,ReleaseDate,Summary,RowVer FROM dbo.ApplicationReleases WITH(UPDLOCK,HOLDLOCK) WHERE ReleaseChannel='PRODUCTION' AND MajorVersion=? AND MinorVersion=? AND BuildVersion=?";
		try(PreparedStatement p=c.prepareStatement(sql)){p.setInt(1,s.version().major());p.setInt(2,s.version().minor());p.setInt(3,s.version().build());try(ResultSet r=p.executeQuery()){
			if(!r.next())return null; long id=r.getLong(1); List<ApplicationReleaseImport.Item> items=new ArrayList<>();
			try(PreparedStatement q=c.prepareStatement("SELECT SortOrder,Title,ItemType,Body FROM dbo.ApplicationReleaseItems WITH(UPDLOCK,HOLDLOCK) WHERE ApplicationReleaseId=? AND IsActive=1 ORDER BY SortOrder,Id")){q.setLong(1,id);try(ResultSet x=q.executeQuery()){while(x.next())items.add(new ApplicationReleaseImport.Item(x.getInt(1),x.getString(2),com.shale.core.model.ReleaseItemType.valueOf(x.getString(3)),x.getString(4)));}}
			java.sql.Date d=r.getDate(3);return new Existing(id,r.getString(2),d==null?null:d.toLocalDate(),r.getString(4),r.getBytes(5),items);
		}}
	}
	private static ApplicationReleaseImportResult create(Connection c,ApplicationReleaseImport s,String operator)throws SQLException{
		String sql="INSERT dbo.ApplicationReleases(MajorVersion,MinorVersion,BuildVersion,ReleaseChannel,PublicationStatus,PublishedAt,Title,ReleaseDate,Summary) OUTPUT INSERTED.Id VALUES(?,?,?,'PRODUCTION','PUBLISHED',SYSUTCDATETIME(),?,?,?)";
		long id;try(PreparedStatement p=c.prepareStatement(sql)){p.setInt(1,s.version().major());p.setInt(2,s.version().minor());p.setInt(3,s.version().build());p.setString(4,s.title());setDate(p,5,s.releaseDate());p.setString(6,s.summary());try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Release insert returned no identity.");id=r.getLong(1);}}
		insertItems(c,id,s);audit(c,operator,id,"RELEASE_CATALOG_IMPORTED",s.version().toString(),s.items().size());return result(c,id,s,ApplicationReleaseImportResult.Outcome.CREATED);
	}
	private static ApplicationReleaseImportResult update(Connection c,long id,ApplicationReleaseImport s,String operator)throws SQLException{
		try(PreparedStatement p=c.prepareStatement("UPDATE dbo.ApplicationReleases SET Title=?,ReleaseDate=?,Summary=?,UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND RowVer=?")){p.setString(1,s.title());setDate(p,2,s.releaseDate());p.setString(3,s.summary());p.setLong(4,id);p.setBytes(5,s.expectedRowVersion());if(p.executeUpdate()!=1)throw new ReleaseImportConflictException("Release catalog RowVer changed; controlled update was rejected.");}
		try(PreparedStatement p=c.prepareStatement("DELETE dbo.ApplicationReleaseItems WHERE ApplicationReleaseId=?")){p.setLong(1,id);p.executeUpdate();}insertItems(c,id,s);audit(c,operator,id,"RELEASE_CATALOG_UPDATED",s.version().toString(),s.items().size());return result(c,id,s,ApplicationReleaseImportResult.Outcome.UPDATED);
	}
	private static void insertItems(Connection c,long id,ApplicationReleaseImport s)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT dbo.ApplicationReleaseItems(ApplicationReleaseId,SortOrder,ItemType,Title,Body,IsActive) VALUES(?,?,?,?,?,1)")){for(var i:s.items()){p.setLong(1,id);p.setInt(2,i.sortOrder());p.setString(3,i.type().name());p.setString(4,i.group());p.setString(5,i.body());p.addBatch();}p.executeBatch();}}
	private static void audit(Connection c,String operator,long id,String action,String version,int count)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT dbo.GlobalControlPlaneAuditLog(OperatorId,EntityType,EntityId,Action,Metadata) VALUES(?,'APPLICATION_RELEASE',?,?,?)")){p.setString(1,operator);p.setLong(2,id);p.setString(3,action);p.setString(4,"{\"VERSION\":\""+version+"\",\"ITEM_COUNT\":"+count+"}");p.executeUpdate();}}
	private static ApplicationReleaseImportResult result(Connection c,long id,ApplicationReleaseImport s,ApplicationReleaseImportResult.Outcome o)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT RowVer FROM dbo.ApplicationReleases WHERE Id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Imported release disappeared.");return new ApplicationReleaseImportResult(id,s.version().toString(),o,r.getBytes(1));}}}
	private static boolean same(Existing e,ApplicationReleaseImport s){return Objects.equals(e.title,s.title())&&Objects.equals(e.releaseDate,s.releaseDate())&&Objects.equals(e.summary,s.summary())&&e.items.equals(s.items());}
	private static void validate(ApplicationReleaseImport s,boolean allow,String operator){Objects.requireNonNull(s,"release");if(operator==null||operator.isBlank()||operator.length()>128)throw new IllegalArgumentException("operatorId is required");if(s.title()==null||s.title().isBlank()||s.title().length()>200)throw new IllegalArgumentException("title is invalid");if(s.summary()==null||s.summary().isBlank()||s.summary().length()>510)throw new IllegalArgumentException("summary is invalid");if(s.items().isEmpty())throw new IllegalArgumentException("release items are required");for(int n=0;n<s.items().size();n++){var i=s.items().get(n);if(i.sortOrder()!=n)throw new IllegalArgumentException("release item order must be contiguous");if(i.body()==null||i.body().isBlank()||i.body().length()>2000)throw new IllegalArgumentException("release item is invalid");}if(allow&&s.expectedRowVersion()==null)throw new IllegalArgumentException("expectedRowVersion is required for controlled update");}
	private static void setDate(PreparedStatement p,int n,LocalDate d)throws SQLException{if(d==null)p.setNull(n,Types.DATE);else p.setDate(n,java.sql.Date.valueOf(d));}
	private record Existing(long id,String title,LocalDate releaseDate,String summary,byte[] rowVersion,List<ApplicationReleaseImport.Item>items){}
	public static final class ReleaseImportConflictException extends RuntimeException{public ReleaseImportConflictException(String message){super(message);}}
}
