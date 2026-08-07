package com.shale.data.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class EntityActionAuditDao {
	public void append(Connection con, EntityActionAuditEvent event) throws SQLException {
		Objects.requireNonNull(con, "con");
		Objects.requireNonNull(event, "event");
		String sql = """
			INSERT INTO dbo.EntityActionAuditLog (
			  ShaleClientId, ActorUserId, EntityType, EntityId, Action, OccurredAt,
			  ParentEntityType, ParentEntityId, CorrelationId, Source, Metadata
			)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, event.shaleClientId());
			ps.setInt(2, event.actorUserId());
			ps.setString(3, event.entityType().name());
			ps.setLong(4, event.entityId());
			ps.setString(5, event.action().name());
			ps.setTimestamp(6, Timestamp.from(event.occurredAtUtc()));
			if (event.parentEntityType() == null) ps.setNull(7, java.sql.Types.NVARCHAR); else ps.setString(7, event.parentEntityType().name());
			if (event.parentEntityId() == null) ps.setNull(8, java.sql.Types.BIGINT); else ps.setLong(8, event.parentEntityId());
			ps.setString(9, event.correlationId());
			ps.setString(10, event.source());
			ps.setString(11, metadataJson(event.metadata()));
			ps.executeUpdate();
		}
	}

	public List<EntityActionAuditViewerRow> listViewerRows(com.shale.core.runtime.DbSessionProvider db, int requestedTenantId, Integer actorUserId, LocalDate startDate, LocalDate endDateInclusive, int limit) {
		Objects.requireNonNull(db, "db");
		if (requestedTenantId <= 0) throw new IllegalArgumentException("requestedTenantId must be > 0");
		int boundedLimit = Math.max(1, Math.min(limit, 500));
		try (Connection con = db.requireConnection()) {
			int sessionTenantId = requireCurrentShaleClientId(con);
			if (sessionTenantId != requestedTenantId) throw new IllegalStateException("Requested tenant does not match session context.");
			StringBuilder sql = new StringBuilder("""
				SELECT TOP (?)
				  e.Id, e.ShaleClientId, e.ActorUserId,
				  COALESCE(NULLIF(LTRIM(RTRIM(CONCAT(u.name_first, ' ', u.name_last))), ''), CONCAT('User #', e.ActorUserId)) AS ActorDisplayName,
				  e.EntityType, e.EntityId, e.Action, e.OccurredAt, e.ParentEntityType, e.ParentEntityId,
				  CONVERT(varchar(36), e.CorrelationId) AS CorrelationId, e.Source, e.Metadata
				FROM dbo.EntityActionAuditLog e
				LEFT JOIN dbo.Users u ON u.id = e.ActorUserId AND u.ShaleClientId = e.ShaleClientId
				WHERE e.ShaleClientId = ?
				""");
			List<Object> bindValues = new java.util.ArrayList<>();
			bindValues.add(boundedLimit);
			bindValues.add(requestedTenantId);
			if (actorUserId != null && actorUserId > 0) { sql.append(" AND e.ActorUserId = ?"); bindValues.add(actorUserId); }
			if (startDate != null) { sql.append(" AND e.OccurredAt >= ?"); bindValues.add(Timestamp.valueOf(startDate.atStartOfDay())); }
			if (endDateInclusive != null) { sql.append(" AND e.OccurredAt < ?"); bindValues.add(Timestamp.valueOf(endDateInclusive.plusDays(1).atStartOfDay())); }
			sql.append(" ORDER BY e.OccurredAt DESC, e.Id DESC");
			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				int i = 1; for (Object value : bindValues) ps.setObject(i++, value);
				List<EntityActionAuditViewerRow> rows = new java.util.ArrayList<>();
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						Timestamp occurred = rs.getTimestamp("OccurredAt");
						rows.add(new EntityActionAuditViewerRow(rs.getLong("Id"), rs.getInt("ShaleClientId"), rs.getInt("ActorUserId"), rs.getString("ActorDisplayName"), rs.getString("EntityType"), rs.getLong("EntityId"), rs.getString("Action"), occurred == null ? null : occurred.toInstant(), rs.getString("ParentEntityType"), asLong(rs, "ParentEntityId"), rs.getString("CorrelationId"), rs.getString("Source"), parseSafeMetadata(rs.getString("Metadata"))));
					}
				}
				return rows;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list entity action audit entries", e);
		}
	}

	private static int requireCurrentShaleClientId(Connection con) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT CAST(SESSION_CONTEXT(N'ShaleClientId') AS INT)" ); ResultSet rs = ps.executeQuery()) {
			if (!rs.next()) throw new IllegalStateException("ShaleClientId session context is missing.");
			int id = rs.getInt(1);
			if (rs.wasNull()) throw new IllegalStateException("ShaleClientId session context is missing.");
			return id;
		}
	}

	private static Long asLong(ResultSet rs, String column) throws SQLException { long value = rs.getLong(column); return rs.wasNull() ? null : value; }

	static Map<String, String> parseSafeMetadata(String json) {
		if (json == null || json.isBlank() || json.length() > 1000) return Map.of();
		Map<String, String> out = new LinkedHashMap<>();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\\"([A-Za-z0-9_]{1,64})\\\"\\s*:\\s*(?:\\\"([^\\\"{}\\[\\]]{0,128})\\\"|(true|false)|([0-9]{1,18}))").matcher(json);
		while (m.find()) {
			String key = m.group(1);
			if (!isAllowedMetadataKey(key)) continue;
			String value = m.group(2) != null ? m.group(2) : (m.group(3) != null ? m.group(3) : m.group(4));
			if (value != null && value.length() <= 128) out.put(key, value);
		}
		return Map.copyOf(out);
	}

	private static boolean isAllowedMetadataKey(String key) {
		String lower = key.toLowerCase(Locale.ROOT);
		for (String bad : java.util.Set.of("url","description","note","email","phone","name","credential","password","token","rowver","sql","exception","command","dto")) if (lower.contains(bad)) return false;
		return java.util.Set.of("CASE_ID","CASE_LINK_ID","CASE_LINK_SHARE_ID","EXTERNAL_LINK_ID","LINK_TYPE_ID","CONTACT_ID","PREVIOUS_PRIMARY_CASE_LINK_ID","NEW_PRIMARY_CASE_LINK_ID","REORDERED_LINK_COUNT","ACTIVE","FORM_CONFIGURATION_ID","FORM_KEY","SECTION_COUNT","CONFIGURED_FIELD_COUNT","INITIAL_CREATION").contains(key);
	}

	static String metadataJson(Map<EntityActionAuditEvent.MetadataKey, String> metadata) {
		if (metadata == null || metadata.isEmpty()) return null;
		StringBuilder b = new StringBuilder("{");
		boolean first = true;
		for (var e : metadata.entrySet()) {
			if (!first) b.append(',');
			first = false;
			b.append('"').append(e.getKey().name()).append("\":");
			b.append('"').append(e.getValue().replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
		}
		return b.append('}').toString();
	}
}
