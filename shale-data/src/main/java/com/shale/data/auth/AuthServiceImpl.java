package com.shale.data.auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.shale.core.model.User;
import com.shale.data.config.DataSources;
import com.shale.data.errors.AuthException;
 	
/**
 * JDBC implementation of AuthService. Looks up the user by email and verifies the bcrypt
 * hash. Adjust the column names/table name if your schema differs.
 */
public final class AuthServiceImpl implements AuthService {

	private final DataSource appDs;
	private final PasswordVerifier passwordVerifier;

	public AuthServiceImpl(DataSources dataSources, PasswordVerifier passwordVerifier) {
		this.appDs = dataSources.auth(); // "shale-app" datasource (Users table readable)
		this.passwordVerifier = passwordVerifier;
	}

	@Override
	public User login(String email, String password) throws AuthException {
		if (email == null || email.isBlank() || password == null) {
			throw new AuthException("Email and password are required.");
		}

		final String sql = "SELECT TOP 1 " +
				"  Id, " +
				"  name_first, name_last, email, " +
				"  password_hash AS pw_hash, " + // <- only this, no COALESCE
				"  color, is_attorney, is_admin, is_deleted, " +
				"  default_organization, organization_id, initials, " +
				"  ShaleClientId " +
				"FROM dbo.Users " +
				"WHERE email = ? AND is_deleted = 0";

		try (Connection c = appDs.getConnection();
				PreparedStatement ps = c.prepareStatement(sql)) {

			ps.setString(1, email);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new AuthException("Invalid credentials.");
				}

				final String storedHash = rs.getString("pw_hash");
				if (storedHash == null || !passwordVerifier.verify(password, storedHash)) {
					throw new AuthException("Invalid credentials.");
				}

				// Build the shared core User
				return User.builder()
						.id(rs.getInt("Id"))
						.nameFirst(rs.getString("name_first"))
						.nameLast(rs.getString("name_last"))
						.email(rs.getString("email"))
						.color(rs.getString("color"))
						.attorney(rs.getBoolean("is_attorney"))
						.admin(rs.getBoolean("is_admin"))
						.deleted(rs.getBoolean("is_deleted"))
						.defaultOrganization(getNullableInt(rs, "default_organization"))
						.organizationId(getNullableInt(rs, "organization_id"))
						.initials(rs.getString("initials"))
						.shaleClientId(rs.getInt("ShaleClientId"))
						.build();
			}
		} catch (SQLException e) {
			e.printStackTrace(); // dev only
			throw new AuthException("DB error: " + e.getMessage(), e);
		}

	}

	private static Integer getNullableInt(ResultSet rs, String col) throws SQLException {
		int v = rs.getInt(col);
		return rs.wasNull() ? null : v;
	}
}
