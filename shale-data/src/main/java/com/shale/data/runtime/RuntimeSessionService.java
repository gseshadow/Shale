package com.shale.data.runtime;

import com.shale.core.model.User;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Provides runtime (tenant-aware) connections with RLS session context. Call
 * initialize(...) after login, then use getConnection().
 */
public final class RuntimeSessionService {

	private final DataSource runtimeDs;
	private volatile int shaleClientId = -1;
	private volatile int userId = -1;

	public RuntimeSessionService(DataSource runtimeDs) {
		this.runtimeDs = runtimeDs;
	}

	/** Initialize using explicit ids (called once after login). */
	public void initialize(int shaleClientId, int userId) {
		this.shaleClientId = shaleClientId;
		this.userId = userId;
	}

	/** Convenience overload */
	public void initialize(User user) {
		if (user == null)
			throw new IllegalArgumentException("user cannot be null");
		initialize(user.getShaleClientId(), user.getId());
	}

	/** Clear on logout */
	public void clear() {
		this.shaleClientId = -1;
		this.userId = -1;
	}

	public boolean isInitialized() {
		return shaleClientId > 0;
	}

	public Connection getConnection() throws SQLException {
		if (!isInitialized()) {
			throw new IllegalStateException("RuntimeSessionService not initialized.");
		}

		Connection c = runtimeDs.getConnection();

		try (PreparedStatement ps = c.prepareStatement(
				"EXEC sys.sp_set_session_context @key=N'ShaleClientId', @value=?")) {
			ps.setInt(1, shaleClientId);
			ps.execute();
		}

		try (PreparedStatement ps = c.prepareStatement(
				"EXEC sys.sp_set_session_context @key=N'PrincipalUserId', @value=?")) {
			ps.setInt(1, userId);
			ps.execute();
		}

		return c;
	}

}
