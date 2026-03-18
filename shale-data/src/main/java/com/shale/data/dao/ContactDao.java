package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

public final class ContactDao {

	private static final String CONTACTS_TABLE = "Contacts";

	private final DbSessionProvider db;

	public ContactDao(DbSessionProvider dbSessionProvider) {
		this.db = Objects.requireNonNull(dbSessionProvider, "dbSessionProvider");
	}

	public record ContactRow(
			int id,
			int shaleClientId,
			String name,
			String firstName,
			String lastName,
			String email,
			String phone,
			boolean deleted,
			Instant updatedAt
	) {
	}

	public ContactRow findById(int contactId) {
		if (contactId <= 0) {
			throw new IllegalArgumentException("contactId must be > 0");
		}

		String sql = """
				SELECT
				  c.Id,
				  c.ShaleClientId,
				  c.Name,
				  c.FirstName,
				  c.LastName,
				  c.Email,
				  c.Phone,
				  c.IsDeleted,
				  c.UpdatedAt
				FROM %s c
				WHERE c.Id = ?
				  AND c.ShaleClientId = ?
				  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL);
				""".formatted(CONTACTS_TABLE);

		try (Connection con = db.requireConnection();
				 PreparedStatement ps = con.prepareStatement(sql)) {
			int currentShaleClientId = requireCurrentShaleClientId(con);
			System.out.println("[ContactDao.findById] requestedContactId=" + contactId
					+ ", requestedShaleClientId=" + currentShaleClientId);

			int idx = 1;
			ps.setInt(idx++, contactId);
			ps.setInt(idx++, currentShaleClientId);

			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					System.out.println("[ContactDao.findById] rowReturned=false");
					logExistenceDiagnostics(con, contactId, currentShaleClientId);
					return null;
				}

				ContactRow row = new ContactRow(
						rs.getInt("Id"),
						rs.getInt("ShaleClientId"),
						rs.getString("Name"),
						rs.getString("FirstName"),
						rs.getString("LastName"),
						rs.getString("Email"),
						rs.getString("Phone"),
						toBoolean(rs.getObject("IsDeleted")),
						toInstant(rs.getTimestamp("UpdatedAt"))
				);

				System.out.println("[ContactDao.findById] rowReturned=true, rowShaleClientId=" + row.shaleClientId());
				return row;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load contact by id (id=" + contactId + ")", e);
		}
	}

	private void logExistenceDiagnostics(Connection con, int contactId, int currentShaleClientId) throws SQLException {
		String sql = """
				SELECT
				  CASE WHEN EXISTS (SELECT 1 FROM %s WHERE Id = ?) THEN 1 ELSE 0 END AS ExistsAny,
				  CASE WHEN EXISTS (SELECT 1 FROM %s WHERE Id = ? AND ShaleClientId = ?) THEN 1 ELSE 0 END AS ExistsWithTenant,
				  CASE WHEN EXISTS (
				    SELECT 1
				    FROM %s
				    WHERE Id = ?
				      AND ShaleClientId = ?
				      AND (IsDeleted = 0 OR IsDeleted IS NULL)
				  ) THEN 1 ELSE 0 END AS ExistsWithTenantAndActive;
				""".formatted(CONTACTS_TABLE, CONTACTS_TABLE, CONTACTS_TABLE);

		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int idx = 1;
			ps.setInt(idx++, contactId);
			ps.setInt(idx++, contactId);
			ps.setInt(idx++, currentShaleClientId);
			ps.setInt(idx++, contactId);
			ps.setInt(idx++, currentShaleClientId);

			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					System.out.println("[ContactDao.findById] diagnostics: existsAny=" + (rs.getInt("ExistsAny") == 1)
							+ ", existsWithTenant=" + (rs.getInt("ExistsWithTenant") == 1)
							+ ", existsWithTenantAndActive=" + (rs.getInt("ExistsWithTenantAndActive") == 1));
				}
			}
		}
	}

	private static int requireCurrentShaleClientId(Connection con) throws SQLException {
		String sql = "SELECT CAST(SESSION_CONTEXT(N'ShaleClientId') AS INT);";
		try (PreparedStatement ps = con.prepareStatement(sql);
				 ResultSet rs = ps.executeQuery()) {
			if (!rs.next()) {
				throw new IllegalStateException("ShaleClientId session context is missing.");
			}

			Integer shaleClientId = getNullableInt(rs, 1);
			if (shaleClientId == null || shaleClientId <= 0) {
				throw new IllegalStateException("ShaleClientId session context is missing.");
			}
			return shaleClientId;
		}
	}

	private static Integer getNullableInt(ResultSet rs, int colIndex) throws SQLException {
		int value = rs.getInt(colIndex);
		return rs.wasNull() ? null : value;
	}

	private static boolean toBoolean(Object value) {
		if (value == null) {
			return false;
		}
		if (value instanceof Boolean b) {
			return b;
		}
		if (value instanceof Number n) {
			return n.intValue() != 0;
		}
		return Boolean.parseBoolean(value.toString());
	}

	private static Instant toInstant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}
}
