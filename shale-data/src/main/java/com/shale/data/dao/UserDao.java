package com.shale.data.dao;

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
	private static final int ROLE_ADMIN = 1;
	private static final int ROLE_ATTORNEY = 7;

	public record DirectoryUserRow(
			int id,
			String displayName,
			String email,
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
			boolean deleted,
			String defaultOrganizationName,
			Integer defaultOrganizationId,
			Integer organizationId) {
	}

	public record UserProfileUpdateRequest(
			int userId,
			int shaleClientId,
			String firstName,
			String lastName,
			String email,
			String phone,
			String initials,
			String color,
			Integer defaultOrganizationId) {
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
		String baseSql = """
				SELECT
				  u.Id,
				  LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )) AS DisplayName,
				  COALESCE(u.email, '') AS Email,
				  u.Color,
				  u.Initials
				FROM dbo.Users u
				WHERE u.ShaleClientId = ?
				  AND NULLIF(LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )), '') IS NOT NULL
				""";

		String orderSql = """
				ORDER BY DisplayName ASC, u.Id ASC;
				""";

		try (Connection con = db.requireConnection()) {
			verifyTenantMatchesSession(con, shaleClientId);

			StringBuilder sql = new StringBuilder(baseSql);
			appendUserVisibilityFilters(sql, con, "u");
			sql.append(orderSql);

			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				ps.setInt(1, shaleClientId);
				try (ResultSet rs = ps.executeQuery()) {
					List<DirectoryUserRow> out = new ArrayList<>();
					while (rs.next()) {
						out.add(new DirectoryUserRow(
								rs.getInt("Id"),
								rs.getString("DisplayName"),
								rs.getString("Email"),
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
					  COALESCE(u.is_admin, 0) AS IsAdmin,
					  COALESCE(u.is_attorney, 0) AS IsAttorney,
					  COALESCE(u.is_deleted, 0) AS IsDeleted,
					  defaultOrg.Name AS DefaultOrganizationName,
					  u.default_organization AS DefaultOrganizationId,
					  u.organization_id AS OrganizationId
					FROM dbo.Users u
					LEFT JOIN dbo.Organizations defaultOrg
					  ON defaultOrg.Id = u.default_organization
					 AND defaultOrg.ShaleClientId = u.ShaleClientId
					 AND (defaultOrg.IsDeleted = 0 OR defaultOrg.IsDeleted IS NULL)
					WHERE u.Id = ?
					  AND u.ShaleClientId = ?
					""");
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
							rs.getBoolean("IsDeleted"),
							rs.getString("DefaultOrganizationName"),
							getNullableInt(rs, "DefaultOrganizationId"),
							getNullableInt(rs, "OrganizationId"));
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
					    Color = ?,
					    default_organization = ?
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
				setNullableInteger(ps, idx++, request.defaultOrganizationId());
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
					  COALESCE(u.is_admin, 0) AS IsAdmin,
					  COALESCE(u.is_attorney, 0) AS IsAttorney
					FROM dbo.Users u
					WHERE u.Id = ?
					  AND u.ShaleClientId = ?
					""";
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
						out.add(new UserRoleRow(ROLE_ADMIN, "Admin"));
					}
					if (rs.getBoolean("IsAttorney")) {
						out.add(new UserRoleRow(ROLE_ATTORNEY, "Attorney"));
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
		if (!assignedIds.contains(ROLE_ADMIN)) {
			available.add(new UserRoleRow(ROLE_ADMIN, "Admin"));
		}
		if (!assignedIds.contains(ROLE_ATTORNEY)) {
			available.add(new UserRoleRow(ROLE_ATTORNEY, "Attorney"));
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
		return switch (roleId) {
		case ROLE_ADMIN -> "is_admin";
		case ROLE_ATTORNEY -> "is_attorney";
		default -> throw new IllegalArgumentException("Unsupported role id: " + roleId);
		};
	}

	private static String existingPhoneColumn(Connection con) throws SQLException {
		for (String column : List.of("Phone", "PhoneCell", "phone_cell", "PhoneNumber", "phone", "phone_number")) {
			if (tableHasColumn(con, "Users", column)) {
				return column;
			}
		}
		return null;
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
