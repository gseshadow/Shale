package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.semantics.RoleSemantics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NotificationDao {
	private static final int ROLE_RESPONSIBLE_ATTORNEY = RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY;

	private final DbSessionProvider db;

	public NotificationDao(DbSessionProvider db) {
		this.db = Objects.requireNonNull(db, "db");
	}

	public Long createTaskAssignedNotification(
			int shaleClientId,
			int userId,
			String title,
			String message,
			long entityId,
			int createdByUserId,
			String eventKey) {
		if (shaleClientId <= 0 || userId <= 0 || entityId <= 0 || eventKey == null || eventKey.isBlank()) {
			return null;
		}
		try (Connection con = db.requireConnection()) {
			return createIfAbsent(
					con,
					shaleClientId,
					userId,
					title,
					message,
					entityId,
					createdByUserId,
					"TASK",
					"Task",
					"ASSIGNED",
					"INFO",
					eventKey);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to create notification", e);
		}
	}

	public Long createTaskNoteAddedNotification(
			int shaleClientId,
			int userId,
			String title,
			String message,
			long entityId,
			int createdByUserId,
			String eventKey) {
		if (eventKey == null || eventKey.isBlank()) {
			return null;
		}
		try (Connection con = db.requireConnection()) {
			return createIfAbsent(
					con,
					shaleClientId,
					userId,
					title,
					message,
					entityId,
					createdByUserId,
					"TASK",
					"Task",
					"NOTE_ADDED",
					"INFO",
					eventKey);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to create task-note notification", e);
		}
	}

	public Long createTaskDueDateNotification(
			int shaleClientId,
			int userId,
			String title,
			String message,
			long entityId,
			int createdByUserId,
			String actionType,
			String severity,
			String eventKey) {
		if (eventKey == null || eventKey.isBlank()) {
			return null;
		}
		try (Connection con = db.requireConnection()) {
			return createIfAbsent(
					con,
					shaleClientId,
					userId,
					title,
					message,
					entityId,
					createdByUserId,
					"TASK",
					"Task",
					actionType,
					severity,
					eventKey);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to create due-date notification", e);
		}
	}

	public Long createTaskActionNotification(
			int shaleClientId,
			int userId,
			String title,
			String message,
			long entityId,
			int createdByUserId,
			String actionType,
			String eventKey) {
		if (eventKey == null || eventKey.isBlank()) {
			return null;
		}
		try (Connection con = db.requireConnection()) {
			return createIfAbsent(
					con,
					shaleClientId,
					userId,
					title,
					message,
					entityId,
					createdByUserId,
					"TASK",
					"Task",
					actionType,
					"INFO",
					eventKey);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to create task-action notification", e);
		}
	}

	public Long createCalendarEventAssignedNotification(
			int shaleClientId,
			int userId,
			String title,
			String message,
			long entityId,
			int createdByUserId,
			String actionType,
			String eventKey) {
		if (eventKey == null || eventKey.isBlank()) {
			return null;
		}
		try (Connection con = db.requireConnection()) {
			return createIfAbsent(
					con,
					shaleClientId,
					userId,
					title,
					message,
					entityId,
					createdByUserId,
					"CALENDAR",
					"CalendarEvent",
					actionType,
					"INFO",
					eventKey);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to create calendar assignment notification", e);
		}
	}

	/** Writes a material-request notification on the caller's transaction. */
	public Long createMaterialRequestNotification(Connection con, int shaleClientId, int userId,
			String title, String message, long materialRequestId, int createdByUserId,
			String actionType, String eventKey) throws SQLException {
		Objects.requireNonNull(con, "con");
		return createIfAbsent(con, shaleClientId, userId, title, message, materialRequestId,
				createdByUserId, "CASE", "MaterialRequest", actionType, "INFO", eventKey);
	}

	/** Writes a scheduler-generated material-request notification. */
	public Long createMaterialRequestDueNotification(int shaleClientId, int userId, String title,
			String message, long materialRequestId, String eventKey) {
		try (Connection con = db.requireConnection()) {
			return createIfAbsent(con, shaleClientId, userId, title, message, materialRequestId,
					0, "CASE", "MaterialRequest", "DUE", "INFO", eventKey);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to create material-request due notification", e);
		}
	}

	public Long createMaterialRequestFollowUpNotification(int shaleClientId,int userId,String title,String message,long materialRequestId,String eventKey) {
		try(Connection con=db.requireConnection()){return createIfAbsent(con,shaleClientId,userId,title,message,materialRequestId,0,"CASE","MaterialRequest","FOLLOW_UP_DUE","INFO",eventKey);}catch(SQLException e){throw new RuntimeException("Failed to create material-request follow-up notification",e);}
	}

	public int countUnreadNotificationsForUser(int shaleClientId, int userId) {
		if (shaleClientId <= 0 || userId <= 0) {
			return 0;
		}
		String sql = """
				SELECT COUNT_BIG(1) AS UnreadCount
				FROM dbo.Notifications n
				WHERE n.ShaleClientId = ?
				  AND n.UserId = ?
				  AND ISNULL(n.IsDismissed, 0) = 0
				  AND ISNULL(n.IsRead, 0) = 0
				  AND (n.ExpiresAt IS NULL OR n.ExpiresAt > SYSUTCDATETIME())
				""";
		try (Connection con = db.requireConnection();
		     PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId);
			ps.setInt(2, userId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? Math.toIntExact(rs.getLong("UnreadCount")) : 0;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to count unread notifications", e);
		}
	}

	public NotificationPageRow listNotificationsForUser(int shaleClientId, int userId, long afterNotificationId, int requestedLimit) {
		if (shaleClientId <= 0 || userId <= 0 || afterNotificationId < 0) return new NotificationPageRow(List.of(), false);
		int limit = Math.max(1, Math.min(requestedLimit, 100));
		String sql = """
				SELECT TOP (?) n.Id,n.Category,n.Title,n.Message,n.CreatedAt,n.IsRead,n.EntityType
				FROM dbo.Notifications n
				WHERE n.ShaleClientId=? AND n.UserId=? AND n.Id>?
				  AND ISNULL(n.IsDismissed,0)=0
				  AND (n.ExpiresAt IS NULL OR n.ExpiresAt>SYSUTCDATETIME())
				ORDER BY n.Id ASC
				""";
		try (Connection con=db.requireConnection(); PreparedStatement ps=con.prepareStatement(sql)) {
			ps.setInt(1, limit + 1); ps.setInt(2, shaleClientId); ps.setInt(3, userId); ps.setLong(4, afterNotificationId);
			try (ResultSet rs=ps.executeQuery()) {
				List<NotificationCursorRow> rows=new ArrayList<>();
				while(rs.next()) rows.add(new NotificationCursorRow(rs.getLong("Id"),rs.getString("Category"),rs.getString("Title"),rs.getString("Message"),toInstant(rs.getTimestamp("CreatedAt")),rs.getBoolean("IsRead"),rs.getString("EntityType")));
				boolean more=rows.size()>limit;
				if(more) rows=new ArrayList<>(rows.subList(0,limit));
				return new NotificationPageRow(List.copyOf(rows),more);
			}
		} catch(SQLException e){throw new RuntimeException("Failed to list notifications",e);}
	}

	public long notificationHighWaterMark(int shaleClientId, int userId) {
		if (shaleClientId <= 0 || userId <= 0) return 0;
		String sql = "SELECT COALESCE(MAX(n.Id),0) FROM dbo.Notifications n WHERE n.ShaleClientId=? AND n.UserId=?";
		try (Connection con = db.requireConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId);
			ps.setInt(2, userId);
			try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0; }
		} catch (SQLException e) {
			throw new RuntimeException("Failed to read notification high-water mark", e);
		}
	}

	public java.util.Optional<NotificationActivationRow> findActivationTarget(int shaleClientId,int userId,long notificationId) {
		if(shaleClientId<=0||userId<=0||notificationId<=0)return java.util.Optional.empty();
		String sql="""
				SELECT n.Id,n.EntityType,n.EntityId,
				 CASE WHEN UPPER(ISNULL(n.EntityType,''))='TASK' THEN t.CaseId
				      WHEN UPPER(ISNULL(n.EntityType,''))='MATERIALREQUEST' THEN mr.CaseId
				      WHEN UPPER(ISNULL(n.EntityType,''))='CASE' THEN n.EntityId END AS ParentCaseId,
				 n.ActionType
				FROM dbo.Notifications n
				LEFT JOIN dbo.Tasks t ON UPPER(ISNULL(n.EntityType,''))='TASK' AND t.Id=n.EntityId AND t.ShaleClientId=n.ShaleClientId AND ISNULL(t.IsDeleted,0)=0
				LEFT JOIN dbo.MaterialRequests mr ON UPPER(ISNULL(n.EntityType,''))='MATERIALREQUEST' AND mr.Id=n.EntityId AND mr.ShaleClientId=n.ShaleClientId AND mr.IsDeleted=0
				WHERE n.Id=? AND n.ShaleClientId=? AND n.UserId=? AND n.EntityId IS NOT NULL
				 AND ISNULL(n.IsDismissed,0)=0 AND (n.ExpiresAt IS NULL OR n.ExpiresAt>SYSUTCDATETIME())
				 AND (UPPER(ISNULL(n.EntityType,'')) NOT IN ('TASK','MATERIALREQUEST') OR t.Id IS NOT NULL OR mr.Id IS NOT NULL)
				""";
		try(Connection con=db.requireConnection();PreparedStatement ps=con.prepareStatement(sql)){ps.setLong(1,notificationId);ps.setInt(2,shaleClientId);ps.setInt(3,userId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return java.util.Optional.empty();Long parent=(Long)rs.getObject("ParentCaseId");return java.util.Optional.of(new NotificationActivationRow(rs.getLong("Id"),rs.getString("EntityType"),rs.getLong("EntityId"),parent,rs.getString("ActionType")));}}catch(SQLException e){throw new RuntimeException("Failed to resolve notification activation target",e);}
	}

	public List<NotificationRow> listUnreadNotificationsForUser(int shaleClientId, int userId) {
		if (shaleClientId <= 0 || userId <= 0) {
			return List.of();
		}
		String sql = """
				SELECT n.Id,
				       n.Category,
				       n.Severity,
				       n.Title,
				       n.Message,
				       n.EntityType,
				       n.EntityId,
				       n.ActionType,
				       n.CreatedByUserId,
				       LTRIM(RTRIM(
				         COALESCE(actor.name_first, '') +
				         CASE WHEN COALESCE(actor.name_first, '') = '' OR COALESCE(actor.name_last, '') = '' THEN '' ELSE ' ' END +
				         COALESCE(actor.name_last, '')
				       )) AS ActorDisplayName,
				       CASE
				         WHEN UPPER(ISNULL(n.EntityType, '')) = 'TASK' THEN t.Title
                         WHEN UPPER(ISNULL(n.EntityType, '')) = 'MATERIALREQUEST' THEN mr.Title
				         ELSE NULL
				       END AS EntityTitle,
				       CASE
				         WHEN UPPER(ISNULL(n.EntityType, '')) = 'TASK' THEN t.CaseId
                         WHEN UPPER(ISNULL(n.EntityType, '')) = 'MATERIALREQUEST' THEN mr.CaseId
				         ELSE NULL
				       END AS CaseId,
				       CASE
				         WHEN UPPER(ISNULL(n.EntityType, '')) IN ('TASK','MATERIALREQUEST') THEN c.Name
				         ELSE NULL
				       END AS CaseName,
				       CASE
				         WHEN UPPER(ISNULL(n.EntityType, '')) IN ('TASK','MATERIALREQUEST') THEN caseAttorney.DisplayName
				         ELSE NULL
				       END AS CaseResponsibleAttorney,
				       CASE
				         WHEN UPPER(ISNULL(n.EntityType, '')) IN ('TASK','MATERIALREQUEST') THEN caseAttorney.Color
				         ELSE NULL
				       END AS CaseResponsibleAttorneyColor,
				       CASE
				         WHEN UPPER(ISNULL(n.EntityType, '')) IN ('TASK','MATERIALREQUEST') THEN c.NonEngagementLetterSent
				         ELSE NULL
				       END AS CaseNonEngagementLetterSent,
				       CASE
				         WHEN UPPER(ISNULL(n.EntityType, '')) IN ('TASK','MATERIALREQUEST') THEN current_status.CurrentStatusName
				         ELSE NULL
				       END AS CasePrimaryStatusName,
				       CASE
				         WHEN UPPER(ISNULL(n.EntityType, '')) IN ('TASK','MATERIALREQUEST') THEN current_status.PrimaryStatusColor
				         ELSE NULL
				       END AS CasePrimaryStatusColor,
				       CASE
				         WHEN UPPER(ISNULL(n.EntityType, '')) IN ('TASK','MATERIALREQUEST') THEN pa.Color
				         ELSE NULL
				       END AS CasePracticeAreaColor,
				       n.IsRead AS IsRead,
				       n.CreatedAt AS CreatedAt,
				       n.EventKey AS EventKey
				FROM dbo.Notifications n
				LEFT JOIN dbo.Users actor
				  ON actor.id = n.CreatedByUserId
				 AND actor.ShaleClientId = n.ShaleClientId
				LEFT JOIN dbo.Tasks t
				  ON UPPER(ISNULL(n.EntityType, '')) = 'TASK'
				 AND t.Id = n.EntityId
				 AND t.ShaleClientId = n.ShaleClientId
				 AND ISNULL(t.IsDeleted, 0) = 0
				LEFT JOIN dbo.MaterialRequests mr
                  ON UPPER(ISNULL(n.EntityType, '')) = 'MATERIALREQUEST'
                 AND mr.Id = n.EntityId AND mr.ShaleClientId = n.ShaleClientId AND mr.IsDeleted = 0
                LEFT JOIN dbo.Cases c
                  ON c.Id = COALESCE(t.CaseId, mr.CaseId)
				 AND c.ShaleClientId = n.ShaleClientId
				LEFT JOIN dbo.PracticeAreas pa
				  ON pa.Id = c.PracticeAreaId
				OUTER APPLY (
				  SELECT TOP (1) s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
				  FROM dbo.CaseStatuses cs
				  INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId
				  WHERE cs.CaseId = c.Id AND cs.IsPrimary = 1
				  ORDER BY cs.UpdatedAt DESC, cs.CreatedAt DESC, cs.Id DESC
				) current_status
				OUTER APPLY (
				  SELECT TOP (1)
				    LTRIM(RTRIM(
				      COALESCE(u.name_first, '') +
				      CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				      COALESCE(u.name_last, '')
				    )) AS DisplayName,
				    u.Color
				  FROM dbo.CaseUsers cu
				  INNER JOIN dbo.Users u
				    ON u.id = cu.UserId
				   AND u.ShaleClientId = c.ShaleClientId
				  WHERE cu.CaseId = c.Id
				    AND cu.RoleId = ?
				    AND cu.IsPrimary = 1
				  ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
				) caseAttorney
				WHERE n.ShaleClientId = ?
				  AND n.UserId = ?
				  AND ISNULL(n.IsDismissed, 0) = 0
				  AND ISNULL(n.IsRead, 0) = 0
				  AND (n.ExpiresAt IS NULL OR n.ExpiresAt > SYSUTCDATETIME())
				ORDER BY n.CreatedAt DESC, n.Id DESC;
				""";
		try (Connection con = db.requireConnection();
		     PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, ROLE_RESPONSIBLE_ATTORNEY);
			ps.setInt(2, shaleClientId);
			ps.setInt(3, userId);
			try (ResultSet rs = ps.executeQuery()) {
				List<NotificationRow> rows = new ArrayList<>();
				while (rs.next()) {
					rows.add(new NotificationRow(
							rs.getLong("Id"),
							rs.getString("Category"),
							rs.getString("Severity"),
							rs.getString("Title"),
							rs.getString("Message"),
							rs.getString("EntityType"),
							rs.getObject("EntityId") == null ? null : rs.getLong("EntityId"),
							rs.getString("ActionType"),
							safeUserDisplayName(rs.getString("ActorDisplayName")),
							rs.getString("EntityTitle"),
							rs.getObject("CaseId") == null ? null : rs.getLong("CaseId"),
							rs.getString("CaseName"),
							rs.getString("CaseResponsibleAttorney"),
							rs.getString("CaseResponsibleAttorneyColor"),
							rs.getObject("CaseNonEngagementLetterSent") == null ? null : rs.getBoolean("CaseNonEngagementLetterSent"),
							rs.getString("CasePrimaryStatusName"),
							rs.getString("CasePrimaryStatusColor"),
							rs.getString("CasePracticeAreaColor"),
							rs.getBoolean("IsRead"),
							toInstant(rs.getTimestamp("CreatedAt")),
							rs.getString("EventKey")));
				}
				return rows;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list unread notifications", e);
		}
	}

	public void markNotificationRead(int shaleClientId, int userId, long notificationId) {
		if (notificationId <= 0) {
			return;
		}
		markNotificationsRead(shaleClientId, userId, List.of(notificationId));
	}

	public void markNotificationsRead(int shaleClientId, int userId, List<Long> notificationIds) {
		if (shaleClientId <= 0 || userId <= 0 || notificationIds == null || notificationIds.isEmpty()) {
			return;
		}
		String sql = """
				UPDATE dbo.Notifications
				SET IsRead = 1, ReadAt = SYSUTCDATETIME()
				WHERE Id = ?
				  AND ShaleClientId = ?
				  AND UserId = ?
				  AND ISNULL(IsRead,0)=0
				""";
		try (Connection con = db.requireConnection();
		     PreparedStatement ps = con.prepareStatement(sql)) {
			for (Long id : notificationIds) {
				if (id == null || id <= 0) {
					continue;
				}
				ps.setLong(1, id);
				ps.setInt(2, shaleClientId);
				ps.setInt(3, userId);
				ps.addBatch();
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to mark notifications read", e);
		}
	}

	public void markNotificationDismissed(int shaleClientId, int userId, long notificationId) {
		if (notificationId <= 0) {
			return;
		}
		markNotificationsDismissed(shaleClientId, userId, List.of(notificationId));
	}

	public void markNotificationsDismissed(int shaleClientId, int userId, List<Long> notificationIds) {
		if(shaleClientId<=0||userId<=0||notificationIds==null||notificationIds.isEmpty())return;
		try(Connection con=db.requireConnection()){con.setAutoCommit(false);try{for(Long id:notificationIds)if(id!=null&&id>0)dismissOne(con,shaleClientId,userId,id);con.commit();}catch(Exception e){try{con.rollback();}catch(SQLException ignored){}throw e;}}catch(SQLException e){throw new RuntimeException("Failed to mark notifications dismissed",e);}
	}

	private static void dismissOne(Connection con,int tenant,int user,long id)throws SQLException{
		String eventKey=null;Long requestId=null;String action=null;boolean dismissed;
		try(PreparedStatement ps=con.prepareStatement("SELECT EventKey,EntityId,ActionType,IsDismissed FROM dbo.Notifications WITH (UPDLOCK,ROWLOCK) WHERE Id=? AND ShaleClientId=? AND UserId=?")){ps.setLong(1,id);ps.setInt(2,tenant);ps.setInt(3,user);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return;eventKey=rs.getString(1);requestId=(Long)rs.getObject(2);action=rs.getString(3);dismissed=rs.getBoolean(4);}}
		if(dismissed)return;
		LocalDateTime occurrence=parseFollowUpOccurrence(eventKey,requestId,user,action);
		try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.Notifications SET IsDismissed=1,DismissedAt=SYSUTCDATETIME() WHERE Id=? AND ShaleClientId=? AND UserId=? AND ISNULL(IsDismissed,0)=0")){ps.setLong(1,id);ps.setInt(2,tenant);ps.setInt(3,user);if(ps.executeUpdate()!=1)return;}
		if(occurrence==null)return;
		long caseId;Integer interval;LocalDateTime current;String status;
		String q="SELECT mr.CaseId,mr.FollowUpIntervalDays,mr.NextFollowUpAt,LOWER(LTRIM(RTRIM(COALESCE(rs.SystemKey,mr.Status)))) FROM dbo.MaterialRequests mr WITH (UPDLOCK,ROWLOCK) OUTER APPLY (SELECT TOP (1) r.SystemKey FROM dbo.RequestStatuses r WHERE (r.ShaleClientId=mr.ShaleClientId OR r.ShaleClientId IS NULL) AND r.IsDeleted=0 AND r.SystemKey IS NOT NULL AND (LOWER(LTRIM(RTRIM(r.SystemKey)))=LOWER(LTRIM(RTRIM(mr.Status))) OR LOWER(LTRIM(RTRIM(r.Name)))=LOWER(LTRIM(RTRIM(mr.Status)))) ORDER BY CASE WHEN r.ShaleClientId=mr.ShaleClientId THEN 0 ELSE 1 END,r.Id) rs WHERE mr.Id=? AND mr.ShaleClientId=? AND mr.IsDeleted=0";
		try(PreparedStatement ps=con.prepareStatement(q)){ps.setLong(1,requestId);ps.setInt(2,tenant);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return;caseId=rs.getLong(1);interval=(Integer)rs.getObject(2);Timestamp t=rs.getTimestamp(3);current=t==null?null:t.toLocalDateTime();status=rs.getString(4);}}
		if(interval==null||current==null||!current.equals(occurrence)||"closed".equals(status)||"cancelled".equals(status))return;
		try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.MaterialRequests SET NextFollowUpAt=DATEADD(day,FollowUpIntervalDays,SYSUTCDATETIME()),UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND ShaleClientId=? AND FollowUpIntervalDays IS NOT NULL AND NextFollowUpAt=?")){ps.setLong(1,requestId);ps.setInt(2,tenant);ps.setTimestamp(3,Timestamp.valueOf(occurrence));if(ps.executeUpdate()!=1)throw new SQLException("Recurring follow-up schedule changed during dismissal.");}
		try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.Cases SET UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND ShaleClientId=?")){ps.setLong(1,caseId);ps.setInt(2,tenant);ps.executeUpdate();}
	}
	private static LocalDateTime parseFollowUpOccurrence(String key,Long requestId,int user,String action){
		if(requestId==null||!"FOLLOW_UP_DUE".equalsIgnoreCase(action)||key==null)return null;String prefix="material-request:"+requestId+":follow-up:",suffix=":"+user;if(!key.startsWith(prefix)||!key.endsWith(suffix))return null;try{return LocalDateTime.parse(key.substring(prefix.length(),key.length()-suffix.length()));}catch(Exception ignored){return null;}
	}

	private static Instant toInstant(Timestamp timestamp) {
		return timestamp == null ? Instant.now() : timestamp.toInstant();
	}

	private static Long findByEventKey(Connection con, int shaleClientId, int userId, String eventKey) throws SQLException {
		String sql = "SELECT TOP (1) Id FROM dbo.Notifications WHERE ShaleClientId=? AND UserId=? AND EventKey=?";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId);
			ps.setInt(2, userId);
			ps.setString(3, eventKey);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getLong(1);
				}
				return null;
			}
		}
	}

	private static Long createIfAbsent(
			Connection con,
			int shaleClientId,
			int userId,
			String title,
			String message,
			long entityId,
			int createdByUserId,
			String category,
			String entityType,
			String actionType,
			String severity,
			String eventKey) throws SQLException {
		if (shaleClientId <= 0 || userId <= 0 || entityId <= 0 || eventKey == null || eventKey.isBlank()) {
			return null;
		}
		Long existing = findByEventKey(con, shaleClientId, userId, eventKey);
		if (existing != null) {
			return existing;
		}
		String insertSql = """
				INSERT INTO dbo.Notifications
					(ShaleClientId, UserId, Category, Severity, Title, Message, EntityType, EntityId, ActionType,
					 IsRead, IsDismissed, CreatedAt, CreatedByUserId, EventKey)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, SYSUTCDATETIME(), ?, ?);
				""";
		try (PreparedStatement ps = con.prepareStatement(insertSql)) {
			ps.setInt(1, shaleClientId);
			ps.setInt(2, userId);
			ps.setString(3, category == null || category.isBlank() ? "TASK" : category);
			ps.setString(4, severity == null || severity.isBlank() ? "INFO" : severity);
			ps.setString(5, title);
			ps.setString(6, message);
			ps.setString(7, entityType == null || entityType.isBlank() ? "Task" : entityType);
			ps.setLong(8, entityId);
			ps.setString(9, actionType);
			ps.setInt(10, createdByUserId);
			ps.setString(11, eventKey);
			ps.executeUpdate();
		}
		return findByEventKey(con, shaleClientId, userId, eventKey);
	}

	private static String safeUserDisplayName(String displayName) {
		String trimmed = displayName == null ? "" : displayName.trim();
		if (!trimmed.isBlank()) {
			return trimmed;
		}
		return null;
	}

	public record NotificationRow(
			long id,
			String category,
			String severity,
			String title,
			String message,
			String entityType,
			Long entityId,
			String actionType,
			String actorDisplayName,
			String entityTitle,
			Long caseId,
			String caseName,
			String caseResponsibleAttorney,
			String caseResponsibleAttorneyColor,
			Boolean caseNonEngagementLetterSent,
			String casePrimaryStatusName,
			String casePrimaryStatusColor,
			String casePracticeAreaColor,
			boolean isRead,
			Instant createdAt,
			String eventKey) {
	}

	public record NotificationCursorRow(long id,String category,String title,String message,Instant createdAt,boolean read,String entityType) {
		public NotificationCursorRow(long id,String category,String title,String message,Instant createdAt) {
			this(id, category, title, message, createdAt, false, null);
		}
	}
	public record NotificationPageRow(List<NotificationCursorRow> items,boolean hasMore) {}
	public record NotificationActivationRow(long notificationId,String entityType,long entityId,Long parentCaseId,String actionType) {}
}
