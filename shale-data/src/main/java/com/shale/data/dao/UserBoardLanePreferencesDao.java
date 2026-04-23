package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class UserBoardLanePreferencesDao {
	private final DbSessionProvider db;

	public UserBoardLanePreferencesDao(DbSessionProvider db) {
		this.db = Objects.requireNonNull(db, "db");
	}

	public Set<String> listPinnedLaneKeys(int shaleClientId, int userId, String boardKey, String laneType) {
		if (shaleClientId <= 0 || userId <= 0 || isBlank(boardKey) || isBlank(laneType)) {
			return Set.of();
		}
		String sql = """
				SELECT LaneKey
				FROM dbo.UserBoardLanePreferences
				WHERE ShaleClientId = ?
				  AND UserId = ?
				  AND BoardKey = ?
				  AND LaneType = ?
				  AND IsPinned = 1
				ORDER BY LaneKey;
				""";
		try (Connection con = db.requireConnection();
		     PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId);
			ps.setInt(2, userId);
			ps.setString(3, boardKey);
			ps.setString(4, laneType);
			try (ResultSet rs = ps.executeQuery()) {
				Set<String> pinned = new LinkedHashSet<>();
				while (rs.next()) {
					String laneKey = rs.getString("LaneKey");
					if (!isBlank(laneKey)) {
						pinned.add(laneKey.trim());
					}
				}
				return pinned;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list board lane preferences", e);
		}
	}

	public Set<String> listCollapsedLaneKeys(int shaleClientId, int userId, String boardKey, String laneType) {
		if (shaleClientId <= 0 || userId <= 0 || isBlank(boardKey) || isBlank(laneType)) {
			return Set.of();
		}
		String sql = """
				SELECT LaneKey
				FROM dbo.UserBoardLanePreferences
				WHERE ShaleClientId = ?
				  AND UserId = ?
				  AND BoardKey = ?
				  AND LaneType = ?
				  AND IsCollapsed = 1
				ORDER BY LaneKey;
				""";
		try (Connection con = db.requireConnection();
		     PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId);
			ps.setInt(2, userId);
			ps.setString(3, boardKey);
			ps.setString(4, laneType);
			try (ResultSet rs = ps.executeQuery()) {
				Set<String> collapsed = new LinkedHashSet<>();
				while (rs.next()) {
					String laneKey = rs.getString("LaneKey");
					if (!isBlank(laneKey)) {
						collapsed.add(laneKey.trim());
					}
				}
				return collapsed;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list collapsed board lane preferences", e);
		}
	}

	public void upsertLanePreference(
			int shaleClientId,
			int userId,
			String boardKey,
			String laneType,
			String laneKey,
			boolean isPinned,
			Integer sortIndex,
			Boolean isCollapsed,
			Integer updatedByUserId) {
		if (shaleClientId <= 0 || userId <= 0 || isBlank(boardKey) || isBlank(laneType) || isBlank(laneKey)) {
			return;
		}
		String updateSql = """
				UPDATE dbo.UserBoardLanePreferences
				SET IsPinned = ?,
				    SortIndex = ?,
				    IsCollapsed = ?,
				    UpdatedAt = SYSUTCDATETIME(),
				    UpdatedByUserId = ?
				WHERE ShaleClientId = ?
				  AND UserId = ?
				  AND BoardKey = ?
				  AND LaneType = ?
				  AND LaneKey = ?;
				""";
		String insertSql = """
				INSERT INTO dbo.UserBoardLanePreferences
					(ShaleClientId, UserId, BoardKey, LaneType, LaneKey, IsPinned, SortIndex, IsCollapsed, UpdatedAt, UpdatedByUserId)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME(), ?);
				""";
		try (Connection con = db.requireConnection();
		     PreparedStatement update = con.prepareStatement(updateSql);
		     PreparedStatement insert = con.prepareStatement(insertSql)) {
			update.setBoolean(1, isPinned);
			setNullableInt(update, 2, sortIndex);
			setNullableBoolean(update, 3, isCollapsed);
			setNullableInt(update, 4, updatedByUserId);
			update.setInt(5, shaleClientId);
			update.setInt(6, userId);
			update.setString(7, boardKey);
			update.setString(8, laneType);
			update.setString(9, laneKey);
			int rows = update.executeUpdate();
			if (rows > 0) {
				return;
			}

			insert.setInt(1, shaleClientId);
			insert.setInt(2, userId);
			insert.setString(3, boardKey);
			insert.setString(4, laneType);
			insert.setString(5, laneKey);
			insert.setBoolean(6, isPinned);
			setNullableInt(insert, 7, sortIndex);
			setNullableBoolean(insert, 8, isCollapsed);
			setNullableInt(insert, 9, updatedByUserId);
			insert.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to upsert board lane preference", e);
		}
	}

	private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
		if (value == null || value <= 0) {
			ps.setNull(index, Types.INTEGER);
			return;
		}
		ps.setInt(index, value);
	}

	private static void setNullableBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
		if (value == null) {
			ps.setNull(index, Types.BIT);
			return;
		}
		ps.setBoolean(index, value);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
