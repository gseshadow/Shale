package com.shale.data.dao;

import com.shale.core.semantics.RoleSemantics;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.data.runtime.RuntimeSessionService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class UserDao {
	public record DirectoryUserRow(
			int id,
			String firstName,
			String lastName,
			String displayName,
			String email,
			String phone,
			String color,
			String initials) {
	}

	public record UserDetailRow(
			int id,
			int shaleClientId,
			String firstName,
			String lastName,
			String displayName,
			String email,
			String phone,
			String color,
			String initials,
			boolean admin,
			boolean attorney,
			boolean deleted) {
	}

	public record UserProfileUpdateRequest(
			int userId,
			int shaleClientId,
			String firstName,
			String lastName,
			String email,
			String phone,
			String initials,
			String color) {
	}

	public record UserRoleRow(int roleId, String roleName) {
	}

	private final DbSessionProvider db;

	public UserDao(DbSessionProvider db) {
		this.db = Objects.requireNonNull(db, "db");
	}

	public UserDao(RuntimeSessionService runtime) {
		this(() -> {
			try {
				return runtime.getConnection();
			} catch (SQLException e) {
				throw new RuntimeException("Failed to open runtime user connection", e);
			}
		});
	}

	public List<DirectoryUserRow> searchUsers(int shaleClientId, String query) {
		if (shaleClientId <= 0) {
			throw new IllegalArgumentException("shaleClientId must be > 0");
		}
		String normalizedQuery = normalizeSearchQuery(query);
		if (normalizedQuery.isBlank()) {
			return List.of();
		}

		try (Connection con = db.requireConnection()) {
			verifyTenantMatchesSession(con, shaleClientId);
			String phoneColumn = existingPhoneColumn(con);
			String phoneSelect = phoneSelectExpression(phoneColumn, "u");
			String phoneDigits = normalizePhoneDigits(query);
			String phoneDigitsExpr = phoneDigitsExpression(phoneColumn, "u");

			StringBuilder sql = new StringBuilder("""
					SELECT
					  u.Id,
					  COALESCE(u.name_first, '') AS FirstName,
					  COALESCE(u.name_last, '') AS LastName,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS DisplayName,
					  COALESCE(u.email, '') AS Email,
					""");
			sql.append("\n  ").append(phoneSelect).append(",\n");
			sql.append("""
					  u.Color,
					  u.Initials
					FROM dbo.Users u
					WHERE u.ShaleClientId = ?
					  AND NULLIF(LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )), '') IS NOT NULL
					  AND (
					    LOWER(COALESCE(u.name_first, '')) LIKE ?
					    OR LOWER(COALESCE(u.name_last, '')) LIKE ?
					    OR LOWER(LTRIM(RTRIM(
					      COALESCE(u.name_first, '') +
					      CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					      COALESCE(u.name_last, '')
					    ))) LIKE ?
					    OR LOWER(COALESCE(u.email, '')) LIKE ?
					    OR (? <> '' AND 
					""");
			sql.append(phoneDigitsExpr);
			sql.append("""
					 LIKE ?)
					  )
					""");
			appendUserVisibilityFilters(sql, con, "u");
			sql.append("""
					ORDER BY DisplayName ASC, u.Id ASC;
					""");

			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				String likeValue = containsPattern(normalizedQuery);
				String phoneLikeValue = containsPattern(phoneDigits);
				ps.setInt(1, shaleClientId);
				ps.setString(2, likeValue);
				ps.setString(3, likeValue);
				ps.setString(4, likeValue);
				ps.setString(5, likeValue);
				ps.setString(6, phoneDigits);
				ps.setString(7, phoneLikeValue);
				try (ResultSet rs = ps.executeQuery()) {
					List<DirectoryUserRow> out = new ArrayList<>();
					while (rs.next()) {
						out.add(new DirectoryUserRow(
							rs.getInt("Id"),
							rs.getString("FirstName"),
							rs.getString("LastName"),
							rs.getString("DisplayName"),
							rs.getString("Email"),
							rs.getString("Phone"),
							rs.getString("Color"),
							rs.getString("Initials")));
					}
					return out;
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to search tenant users (clientId=" + shaleClientId + ")", e);
		}
	}

	/** Returns the count of visible (tenant-filtered) users. */
	public int countActiveUsers() throws Exception {
		String sql = "SELECT COUNT(*) FROM dbo.Users WHERE is_deleted = 0";
		try (Connection c = db.requireConnection();
				PreparedStatement ps = c.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			rs.next();
			return rs.getInt(1);
		}
	}

	public List<DirectoryUserRow> listUsersForTenant(int shaleClientId) {
		try (Connection con = db.requireConnection()) {
			verifyTenantMatchesSession(con, shaleClientId);
			String phoneColumn = existingPhoneColumn(con);
			String phoneSelect = phoneSelectExpression(phoneColumn, "u");

			StringBuilder sql = new StringBuilder("""
					SELECT
					  u.Id,
					  COALESCE(u.name_first, '') AS FirstName,
					  COALESCE(u.name_last, '') AS LastName,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS DisplayName,
					  COALESCE(u.email, '') AS Email,
					""");
			sql.append("\n  ").append(phoneSelect).append(",\n");
			sql.append("""
					  u.Color,
					  u.Initials
					FROM dbo.Users u
					WHERE u.ShaleClientId = ?
					  AND NULLIF(LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )), '') IS NOT NULL
					""");
			appendUserVisibilityFilters(sql, con, "u");
			sql.append("""
					ORDER BY DisplayName ASC, u.Id ASC;
					""");

			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				ps.setInt(1, shaleClientId);
				try (ResultSet rs = ps.executeQuery()) {
					List<DirectoryUserRow> out = new ArrayList<>();
					while (rs.next()) {
						out.add(new DirectoryUserRow(
								rs.getInt("Id"),
								rs.getString("FirstName"),
								rs.getString("LastName"),
								rs.getString("DisplayName"),
								rs.getString("Email"),
								rs.getString("Phone"),
								rs.getString("Color"),
								rs.getString("Initials")));
					}
					return out;
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list tenant users (clientId=" + shaleClientId + ")", e);
		}
	}

	public UserDetailRow findById(int userId, int shaleClientId) {
		if (userId <= 0) {
			throw new IllegalArgumentException("userId must be > 0");
		}
		if (shaleClientId <= 0) {
			throw new IllegalArgumentException("shaleClientId must be > 0");
		}

		try (Connection con = db.requireConnection()) {
			verifyTenantMatchesSession(con, shaleClientId);

			String phoneColumn = existingPhoneColumn(con);
			String phoneSelect = phoneSelectExpression(phoneColumn, "u");

			StringBuilder sql = new StringBuilder("""
					SELECT
					  u.Id,
					  u.ShaleClientId,
					  COALESCE(u.name_first, '') AS FirstName,
					  COALESCE(u.name_last, '') AS LastName,
					  LTRIM(RTRIM(
					    COALESCE(u.name_first, '') +
					    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
					    COALESCE(u.name_last, '')
					  )) AS DisplayName,
					  COALESCE(u.email, '') AS Email,
					""");
			sql.append("\n  ").append(phoneSelect).append(",\n");
			sql.append("""
					  COALESCE(u.Color, '') AS Color,
					  COALESCE(u.Initials, '') AS Initials,
					  COALESCE(u.%s, 0) AS IsAdmin,
					  COALESCE(u.%s, 0) AS IsAttorney,
					  COALESCE(u.is_deleted, 0) AS IsDeleted
					FROM dbo.Users u
					WHERE u.Id = ?
					  AND u.ShaleClientId = ?
					""".formatted(RoleSemantics.FLAG_IS_ADMIN, RoleSemantics.FLAG_IS_ATTORNEY));
			appendUserVisibilityFilters(sql, con, "u");
			sql.append(";");

			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				ps.setInt(1, userId);
				ps.setInt(2, shaleClientId);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						return null;
					}
					return new UserDetailRow(
							rs.getInt("Id"),
							rs.getInt("ShaleClientId"),
							rs.getString("FirstName"),
							rs.getString("LastName"),
							rs.getString("DisplayName"),
							rs.getString("Email"),
							rs.getString("Phone"),
							rs.getString("Color"),
							rs.getString("Initials"),
							rs.getBoolean("IsAdmin"),
							rs.getBoolean("IsAttorney"),
							rs.getBoolean("IsDeleted"));
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load user by id (id=" + userId + ")", e);
		}
	}

	public boolean updateBasicProfile(UserProfileUpdateRequest request) {
		Objects.requireNonNull(request, "request");
		if (request.userId() <= 0) {
			throw new IllegalArgumentException("userId must be > 0");
		}
		if (request.shaleClientId() <= 0) {
			throw new IllegalArgumentException("shaleClientId must be > 0");
		}

		try (Connection con = db.requireConnection()) {
			verifyTenantMatchesSession(con, request.shaleClientId());

			String phoneColumn = existingPhoneColumn(con);
			StringBuilder sql = new StringBuilder("""
					UPDATE dbo.Users
					SET name_first = ?,
					    name_last = ?,
					    email = ?,
					    Initials = ?,
					    Color = ?
					""");
			if (phoneColumn != null) {
				sql.append(",\n    ").append(phoneColumn).append(" = ?");
			}
			sql.append("\nWHERE Id = ?\n  AND ShaleClientId = ?");
			appendUserVisibilityFilters(sql, con, null);
			sql.append(";");

			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				int idx = 1;
				setNullableString(ps, idx++, request.firstName());
				setNullableString(ps, idx++, request.lastName());
				setNullableString(ps, idx++, request.email());
				setNullableString(ps, idx++, request.initials());
				setNullableString(ps, idx++, request.color());
				if (phoneColumn != null) {
					setNullableString(ps, idx++, request.phone());
				}
				ps.setInt(idx++, request.userId());
				ps.setInt(idx++, request.shaleClientId());
				return ps.executeUpdate() > 0;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update user basic profile (id=" + request.userId() + ")", e);
		}
	}

	public List<UserRoleRow> listAssignedRoles(int userId, int shaleClientId) {
		if (userId <= 0 || shaleClientId <= 0) {
			throw new IllegalArgumentException("userId and shaleClientId must be > 0");
		}

		try (Connection con = db.requireConnection()) {
			verifyTenantMatchesSession(con, shaleClientId);
			String sql = """
					SELECT
					  COALESCE(u.%s, 0) AS IsAdmin,
					  COALESCE(u.%s, 0) AS IsAttorney
					FROM dbo.Users u
					WHERE u.Id = ?
					  AND u.ShaleClientId = ?
					""".formatted(RoleSemantics.FLAG_IS_ADMIN, RoleSemantics.FLAG_IS_ATTORNEY);
			StringBuilder sqlBuilder = new StringBuilder(sql);
			appendUserVisibilityFilters(sqlBuilder, con, "u");
			try (PreparedStatement ps = con.prepareStatement(sqlBuilder.toString())) {
				ps.setInt(1, userId);
				ps.setInt(2, shaleClientId);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						return List.of();
					}
					List<UserRoleRow> out = new ArrayList<>();
					if (rs.getBoolean("IsAdmin")) {
						out.add(new UserRoleRow(RoleSemantics.ROLE_ADMIN, RoleSemantics.roleLabel(RoleSemantics.ROLE_ADMIN)));
					}
					if (rs.getBoolean("IsAttorney")) {
						out.add(new UserRoleRow(RoleSemantics.ROLE_ATTORNEY, RoleSemantics.roleLabel(RoleSemantics.ROLE_ATTORNEY)));
					}
					return out;
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list assigned roles for user (id=" + userId + ")", e);
		}
	}

	public List<UserRoleRow> listAssignableRoles(int userId, int shaleClientId) {
		if (userId <= 0 || shaleClientId <= 0) {
			throw new IllegalArgumentException("userId and shaleClientId must be > 0");
		}

		List<UserRoleRow> assigned = listAssignedRoles(userId, shaleClientId);
		java.util.Set<Integer> assignedIds = new java.util.HashSet<>();
		for (UserRoleRow row : assigned) {
			assignedIds.add(row.roleId());
		}
		List<UserRoleRow> available = new ArrayList<>();
		if (!assignedIds.contains(RoleSemantics.ROLE_ADMIN)) {
			available.add(new UserRoleRow(RoleSemantics.ROLE_ADMIN, RoleSemantics.roleLabel(RoleSemantics.ROLE_ADMIN)));
		}
		if (!assignedIds.contains(RoleSemantics.ROLE_ATTORNEY)) {
			available.add(new UserRoleRow(RoleSemantics.ROLE_ATTORNEY, RoleSemantics.roleLabel(RoleSemantics.ROLE_ATTORNEY)));
		}
		return available;
	}

	public boolean addRoleToUser(int userId, int roleId, int shaleClientId) {
		return updateUserRoleFlag(userId, roleId, shaleClientId, true);
	}

	public boolean removeRoleFromUser(int userId, int roleId, int shaleClientId) {
		return updateUserRoleFlag(userId, roleId, shaleClientId, false);
	}

	private boolean updateUserRoleFlag(int userId, int roleId, int shaleClientId, boolean enabled) {
		if (userId <= 0 || roleId <= 0 || shaleClientId <= 0) {
			throw new IllegalArgumentException("userId, roleId, and shaleClientId must be > 0");
		}

		String column = roleFlagColumn(roleId);
		try (Connection con = db.requireConnection()) {
			verifyTenantMatchesSession(con, shaleClientId);
			if (!tableHasColumn(con, "Users", column)) {
				throw new IllegalStateException("User role column is not available: " + column);
			}

			StringBuilder sql = new StringBuilder("UPDATE dbo.Users SET ")
					.append(column).append(" = ?\n")
					.append("WHERE Id = ?\n")
					.append("  AND ShaleClientId = ?");
			appendUserVisibilityFilters(sql, con, null);
			sql.append(";");

			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				ps.setBoolean(1, enabled);
				ps.setInt(2, userId);
				ps.setInt(3, shaleClientId);
				return ps.executeUpdate() > 0;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update user role flag (userId=" + userId + ", roleId=" + roleId + ")", e);
		}
	}

	private static String roleFlagColumn(int roleId) {
		return RoleSemantics.roleFlagColumn(roleId);
	}

	private static String existingPhoneColumn(Connection con) throws SQLException {
		for (String column : List.of("Phone", "PhoneCell", "phone_cell", "PhoneNumber", "phone", "phone_number")) {
			if (tableHasColumn(con, "Users", column)) {
				return column;
			}
		}
		return null;
	}


	private static String normalizeSearchQuery(String query) {
		if (query == null) {
			return "";
		}
		return query.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private static String containsPattern(String normalizedQuery) {
		return "%" + normalizedQuery + "%";
	}

	private static String normalizePhoneDigits(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder digits = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isDigit(c)) {
				digits.append(c);
			}
		}
		return digits.toString();
	}

	private static String phoneDigitsExpression(String phoneColumn, String alias) {
		if (phoneColumn == null || phoneColumn.isBlank()) {
			return "CAST(NULL AS NVARCHAR(255))";
		}
		String prefix = (alias == null || alias.isBlank()) ? "" : alias + ".";
		String value = "COALESCE(" + prefix + phoneColumn + ", '')";
		return "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(" + value
				+ ", ' ', ''), '-', ''), '(', ''), ')', ''), '.', ''), '+', ''), '/', '')";
	}

	private static String phoneSelectExpression(String phoneColumn, String alias) {
		String prefix = (alias == null || alias.isBlank()) ? "" : alias + ".";
		if (phoneColumn == null || phoneColumn.isBlank()) {
			return "CAST(NULL AS NVARCHAR(255)) AS Phone";
		}
		return "NULLIF(LTRIM(RTRIM(" + prefix + phoneColumn + ")), '') AS Phone";
	}

	private static void appendUserVisibilityFilters(StringBuilder sql, Connection con, String alias) throws SQLException {
		String prefix = (alias == null || alias.isBlank()) ? "" : alias + ".";
		boolean hasIsActive = tableHasColumn(con, "Users", "IsActive");
		boolean hasIsDeleted = tableHasColumn(con, "Users", "IsDeleted");
		boolean hasIsDeletedLower = tableHasColumn(con, "Users", "is_deleted");

		if (hasIsActive) {
			sql.append("\n  AND (").append(prefix).append("IsActive = 1 OR ").append(prefix).append("IsActive IS NULL)");
		}
		if (hasIsDeleted) {
			sql.append("\n  AND (").append(prefix).append("IsDeleted = 0 OR ").append(prefix).append("IsDeleted IS NULL)");
		}
		if (hasIsDeletedLower) {
			sql.append("\n  AND (").append(prefix).append("is_deleted = 0 OR ").append(prefix).append("is_deleted IS NULL)");
		}
	}

	private static void verifyTenantMatchesSession(Connection con, int requestedShaleClientId) throws SQLException {
		int currentShaleClientId = requireCurrentShaleClientId(con);
		if (requestedShaleClientId != currentShaleClientId) {
			throw new IllegalArgumentException("shaleClientId does not match current session");
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

	private static String firstExistingTable(Connection con, String... tableNames) throws SQLException {
		for (String tableName : tableNames) {
			if (tableExists(con, tableName)) {
				return tableName;
			}
		}
		return null;
	}

	private static boolean tableExists(Connection con, String tableName) throws SQLException {
		String sql = """
				SELECT 1
				FROM INFORMATION_SCHEMA.TABLES
				WHERE TABLE_SCHEMA = 'dbo'
				  AND TABLE_NAME = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, tableName);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static String firstExistingColumn(Connection con, String tableName, String... columnNames) throws SQLException {
		for (String columnName : columnNames) {
			if (tableHasColumn(con, tableName, columnName)) {
				return columnName;
			}
		}
		return null;
	}

	private static boolean tableHasColumn(Connection con, String tableName, String columnName) throws SQLException {
		String sql = """
				SELECT 1
				FROM INFORMATION_SCHEMA.COLUMNS
				WHERE TABLE_SCHEMA = 'dbo'
				  AND TABLE_NAME = ?
				  AND COLUMN_NAME = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, tableName);
			ps.setString(2, columnName);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
		if (value == null || value.isBlank()) {
			ps.setNull(index, java.sql.Types.NVARCHAR);
			return;
		}
		ps.setString(index, value.trim());
	}

	private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
		if (value == null) {
			ps.setNull(index, java.sql.Types.INTEGER);
			return;
		}
		ps.setInt(index, value);
	}

	private static Integer getNullableInt(ResultSet rs, String col) throws SQLException {
		int value = rs.getInt(col);
		return rs.wasNull() ? null : value;
	}

	private static Integer getNullableInt(ResultSet rs, int colIndex) throws SQLException {
		int value = rs.getInt(colIndex);
		return rs.wasNull() ? null : value;
	}
}
