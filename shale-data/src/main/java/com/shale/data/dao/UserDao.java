package com.shale.data.dao;

import com.shale.data.runtime.RuntimeSessionService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class UserDao {
	private final RuntimeSessionService runtime;

	public UserDao(RuntimeSessionService runtime) {
		this.runtime = runtime;
	}

	/** Returns the count of visible (tenant-filtered) users. */
	public int countActiveUsers() throws Exception {
		String sql = "SELECT COUNT(*) FROM dbo.Users WHERE is_deleted = 0";
		try (Connection c = runtime.getConnection();
				PreparedStatement ps = c.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			rs.next();
			return rs.getInt(1);
		}
	}
}
