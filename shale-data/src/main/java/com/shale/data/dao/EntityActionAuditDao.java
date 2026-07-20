package com.shale.data.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
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
