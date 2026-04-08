package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NotificationDao {
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
					"ASSIGNED",
					"INFO",
					eventKey);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to create notification", e);
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
					actionType,
					severity,
					eventKey);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to create due-date notification", e);
		}
	}

	public List<NotificationRow> listUnreadNotificationsForUser(int shaleClientId, int userId) {
		if (shaleClientId <= 0 || userId <= 0) {
			return List.of();
		}
		String sql = """
				SELECT Id, Category, Severity, Title, Message, EntityType, EntityId, ActionType,
				       IsRead, CreatedAt, EventKey
				FROM dbo.Notifications
				WHERE ShaleClientId = ?
				  AND UserId = ?
				  AND ISNULL(IsDismissed, 0) = 0
				  AND ISNULL(IsRead, 0) = 0
				ORDER BY CreatedAt DESC, Id DESC;
				""";
		try (Connection con = db.requireConnection();
		     PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId);
			ps.setInt(2, userId);
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

	public void markNotificationRead(long notificationId) {
		if (notificationId <= 0) {
			return;
		}
		markNotificationsRead(List.of(notificationId));
	}

	public void markNotificationsRead(List<Long> notificationIds) {
		if (notificationIds == null || notificationIds.isEmpty()) {
			return;
		}
		String sql = "UPDATE dbo.Notifications SET IsRead = 1, ReadAt = SYSUTCDATETIME() WHERE Id = ? AND ISNULL(IsRead,0)=0";
		try (Connection con = db.requireConnection();
		     PreparedStatement ps = con.prepareStatement(sql)) {
			for (Long id : notificationIds) {
				if (id == null || id <= 0) {
					continue;
				}
				ps.setLong(1, id);
				ps.addBatch();
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to mark notifications read", e);
		}
	}

	public void markNotificationDismissed(long notificationId) {
		if (notificationId <= 0) {
			return;
		}
		markNotificationsDismissed(List.of(notificationId));
	}

	public void markNotificationsDismissed(List<Long> notificationIds) {
		if (notificationIds == null || notificationIds.isEmpty()) {
			return;
		}
		String sql = "UPDATE dbo.Notifications SET IsDismissed = 1, DismissedAt = SYSUTCDATETIME() WHERE Id = ? AND ISNULL(IsDismissed,0)=0";
		try (Connection con = db.requireConnection();
		     PreparedStatement ps = con.prepareStatement(sql)) {
			for (Long id : notificationIds) {
				if (id == null || id <= 0) {
					continue;
				}
				ps.setLong(1, id);
				ps.addBatch();
			}
			ps.executeBatch();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to mark notifications dismissed", e);
		}
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
			ps.setString(3, "TASK");
			ps.setString(4, severity == null || severity.isBlank() ? "INFO" : severity);
			ps.setString(5, title);
			ps.setString(6, message);
			ps.setString(7, "Task");
			ps.setLong(8, entityId);
			ps.setString(9, actionType);
			ps.setInt(10, createdByUserId);
			ps.setString(11, eventKey);
			ps.executeUpdate();
		}
		return findByEventKey(con, shaleClientId, userId, eventKey);
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
			boolean isRead,
			Instant createdAt,
			String eventKey) {
	}
}
