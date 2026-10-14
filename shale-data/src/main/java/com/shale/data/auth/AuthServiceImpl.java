package com.shale.data.auth;

import java.sql.Connection;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.shale.core.model.User;
import com.shale.data.config.DataSources;
import com.shale.data.errors.AuthException;

/**
 * JDBC implementation of AuthService. Looks up the user by email and verifies the bcrypt
 * hash in Java. The bounded database module is the only pre-tenant identity seam.
 */
public final class AuthServiceImpl implements AuthService {

	private final DataSource appDs;
	private final PasswordVerifier passwordVerifier;

	public AuthServiceImpl(DataSources dataSources, PasswordVerifier passwordVerifier) {
		this(dataSources.auth(), passwordVerifier);
	}

	public AuthServiceImpl(DataSource authSource, PasswordVerifier passwordVerifier) {
		this.appDs = java.util.Objects.requireNonNull(authSource);
		this.passwordVerifier = java.util.Objects.requireNonNull(passwordVerifier);
	}

	@Override
	public User login(String email, String password) throws AuthException {
		if (email == null || email.isBlank() || password == null) {
			throw new AuthException("Email and password are required.");
		}

		if (email.length() > 255) throw new AuthException("Invalid credentials.");
		final String sql = "{call dbo.LookupAuthenticationCredentialV1(?)}";
		int attempts = 0;
		while (true) {
			attempts++;
			try (Connection c = appDs.getConnection();
					CallableStatement ps = c.prepareCall(sql)) {

				ps.setString(1, email);
				ps.setQueryTimeout(5);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						throw new AuthException("Invalid credentials.");
					}

					Integer id = getNullableInt(rs, "Id");
					Integer tenant = getNullableInt(rs, "ShaleClientId");
					boolean deleted = rs.getBoolean("is_deleted");
					boolean unknownDeleted = rs.wasNull();
					if (id == null || id <= 0 || tenant == null || tenant <= 0
							|| deleted || unknownDeleted || rs.getBoolean("IsRemoved")) {
						throw new AuthException("Invalid credentials.");
					}

					final String storedHash = rs.getString("pw_hash");

					// Build the shared core User
					User user = User.builder()
							.id(id)
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
							.shaleClientId(tenant)
							.build();
					if (rs.next() || storedHash == null || !passwordVerifier.verify(password, storedHash)) {
						throw new AuthException("Invalid credentials.");
					}
					return user;
				}
			} catch (SQLException e) {
				if (attempts < 3 && isTransient(e)) {
					sleepQuiet(250L * attempts); // 250ms, 500ms
					continue;
				}
				// Driver messages/causes can contain SQL, identity or credential values.
				throw new AuthException("Authentication is temporarily unavailable.");
			}
		}

	}

	private static boolean isTransient(SQLException e) {
		// SQLState starting with "08" is connection exception in JDBC.
		String state = e.getSQLState();
		if (state != null && state.startsWith("08"))
			return true;
		// Hikari is already throwing SQLTransientConnectionException sometimes
		return (e instanceof java.sql.SQLTransientException);
	}

	private static void sleepQuiet(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	private static Integer getNullableInt(ResultSet rs, String col) throws SQLException {
		int v = rs.getInt(col);
		return rs.wasNull() ? null : v;
	}
}
