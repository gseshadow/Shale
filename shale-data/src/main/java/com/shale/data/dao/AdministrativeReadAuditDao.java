package com.shale.data.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/** Insert-only persistence boundary for successful sensitive administrative reads. */
public final class AdministrativeReadAuditDao {
	public void append(Connection connection, AdministrativeReadAuditEvent event) throws SQLException {
		Objects.requireNonNull(connection, "connection");
		Objects.requireNonNull(event, "event");
		String sql = """
			INSERT INTO dbo.AdministrativeReadAuditLog
			 (ShaleClientId,ActorUserId,ReadType,ResultCount,Metadata)
			VALUES (?,?,?,?,?)
			""";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, event.shaleClientId());
			statement.setInt(2, event.actorUserId());
			statement.setString(3, event.readType().name());
			statement.setInt(4, event.resultCount());
			statement.setString(5, event.metadata());
			statement.executeUpdate();
		}
	}
}
