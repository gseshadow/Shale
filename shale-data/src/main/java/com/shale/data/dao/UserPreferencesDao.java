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
import java.util.Map;
import java.util.Objects;

public final class UserPreferencesDao {
	private final DbSessionProvider db;

	public UserPreferencesDao(DbSessionProvider db) {
		this.db = Objects.requireNonNull(db, "db");
	}

	public List<UserPreferenceRow> listPreferencesForUser(int shaleClientId, int userId) {
		if (shaleClientId <= 0 || userId <= 0) {
			return List.of();
		}
		String sql = """
				SELECT Id, PreferenceKey, PreferenceValue, ValueType, UpdatedAt, UpdatedByUserId
				FROM dbo.UserPreferences
				WHERE ShaleClientId = ?
				  AND UserId = ?
				ORDER BY PreferenceKey;
				""";
		try (Connection con = db.requireConnection();
		     PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId);
			ps.setInt(2, userId);
			try (ResultSet rs = ps.executeQuery()) {
				List<UserPreferenceRow> rows = new ArrayList<>();
				while (rs.next()) {
					rows.add(new UserPreferenceRow(
							rs.getLong("Id"),
							rs.getString("PreferenceKey"),
							rs.getString("PreferenceValue"),
							rs.getString("ValueType"),
							toInstant(rs.getTimestamp("UpdatedAt")),
							rs.getObject("UpdatedByUserId") == null ? null : rs.getInt("UpdatedByUserId")));
				}
				return rows;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list user preferences", e);
		}
	}

	public void upsertPreference(
			int shaleClientId,
			int userId,
			String preferenceKey,
			String preferenceValue,
			String valueType,
			Integer updatedByUserId) {
		if (preferenceKey == null || preferenceKey.isBlank()) {
			return;
		}
		upsertPreferences(
				shaleClientId,
				userId,
				Map.of(preferenceKey, new PreferenceValue(preferenceValue, valueType)),
				updatedByUserId);
	}

	public void upsertPreferences(
			int shaleClientId,
			int userId,
			Map<String, PreferenceValue> valuesByKey,
			Integer updatedByUserId) {
		if (shaleClientId <= 0 || userId <= 0 || valuesByKey == null || valuesByKey.isEmpty()) {
			return;
		}
		String updateSql = """
				UPDATE dbo.UserPreferences
				SET PreferenceValue = ?,
				    ValueType = ?,
				    UpdatedAt = SYSUTCDATETIME(),
				    UpdatedByUserId = ?
				WHERE ShaleClientId = ?
				  AND UserId = ?
				  AND PreferenceKey = ?;
				""";
		String insertSql = """
				INSERT INTO dbo.UserPreferences
					(ShaleClientId, UserId, PreferenceKey, PreferenceValue, ValueType, UpdatedAt, UpdatedByUserId)
				VALUES (?, ?, ?, ?, ?, SYSUTCDATETIME(), ?);
				""";
		try (Connection con = db.requireConnection();
		     PreparedStatement update = con.prepareStatement(updateSql);
		     PreparedStatement insert = con.prepareStatement(insertSql)) {
			for (Map.Entry<String, PreferenceValue> entry : valuesByKey.entrySet()) {
				String key = entry.getKey();
				PreferenceValue value = entry.getValue();
				if (key == null || key.isBlank() || value == null) {
					continue;
				}

				update.setString(1, value.value());
				update.setString(2, value.valueType());
				setNullableInt(update, 3, updatedByUserId);
				update.setInt(4, shaleClientId);
				update.setInt(5, userId);
				update.setString(6, key);
				int rows = update.executeUpdate();
				if (rows > 0) {
					continue;
				}

				insert.setInt(1, shaleClientId);
				insert.setInt(2, userId);
				insert.setString(3, key);
				insert.setString(4, value.value());
				insert.setString(5, value.valueType());
				setNullableInt(insert, 6, updatedByUserId);
				insert.executeUpdate();
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to upsert user preferences", e);
		}
	}

	private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
		if (value == null || value <= 0) {
			ps.setNull(index, java.sql.Types.INTEGER);
			return;
		}
		ps.setInt(index, value);
	}

	private static Instant toInstant(Timestamp timestamp) {
		return timestamp == null ? Instant.now() : timestamp.toInstant();
	}

	public record PreferenceValue(String value, String valueType) {
	}

	public record UserPreferenceRow(
			long id,
			String preferenceKey,
			String preferenceValue,
			String valueType,
			Instant updatedAt,
			Integer updatedByUserId) {
	}
}
