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
			Integer defaultOrganizationId,
			Integer organizationId) {
	}

	public record UserProfileUpdateRequest(
			int userId,
			int shaleClientId,
			String firstName,
			String lastName,
			String email,
			String phone) {
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

			String phoneColumn = firstExistingColumn(con, "Users", "Phone", "phone", "PhoneCell", "phone_cell");
			String phoneSelect = (phoneColumn == null)
					? "CAST(NULL AS NVARCHAR(255)) AS Phone"
					: "u." + phoneColumn + " AS Phone";

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
					  u.default_organization AS DefaultOrganizationId,
					  u.organization_id AS OrganizationId
					FROM dbo.Users u
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

			String phoneColumn = firstExistingColumn(con, "Users", "Phone", "phone", "PhoneCell", "phone_cell");
			StringBuilder sql = new StringBuilder("""
					UPDATE dbo.Users
					SET name_first = ?,
					    name_last = ?,
					    email = ?
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
			RoleSchema schema = resolveRoleSchema(con);

			StringBuilder sql = new StringBuilder();
			sql.append("SELECT r.").append(schema.rolesIdColumn()).append(" AS RoleId, ")
					.append("r.").append(schema.rolesNameColumn()).append(" AS RoleName\n")
					.append("FROM dbo.").append(schema.userRolesTable()).append(" ur\n")
					.append("INNER JOIN dbo.Users u ON u.Id = ur.").append(schema.userRolesUserIdColumn()).append("\n")
					.append("INNER JOIN dbo.").append(schema.rolesTable()).append(" r ON r.")
					.append(schema.rolesIdColumn()).append(" = ur.").append(schema.userRolesRoleIdColumn()).append("\n")
					.append("WHERE ur.").append(schema.userRolesUserIdColumn()).append(" = ?\n")
					.append("  AND u.ShaleClientId = ?");
			appendUserVisibilityFilters(sql, con, "u");
			appendRoleVisibilityFilters(sql, con, schema, "r", shaleClientId);
			sql.append("\nORDER BY r.").append(schema.rolesNameColumn()).append(" ASC, r.")
					.append(schema.rolesIdColumn()).append(" ASC;");

			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				ps.setInt(1, userId);
				ps.setInt(2, shaleClientId);
				try (ResultSet rs = ps.executeQuery()) {
					List<UserRoleRow> out = new ArrayList<>();
					while (rs.next()) {
						out.add(new UserRoleRow(rs.getInt("RoleId"), rs.getString("RoleName")));
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

		try (Connection con = db.requireConnection()) {
			verifyTenantMatchesSession(con, shaleClientId);
			RoleSchema schema = resolveRoleSchema(con);

			StringBuilder sql = new StringBuilder();
			sql.append("SELECT r.").append(schema.rolesIdColumn()).append(" AS RoleId, ")
					.append("r.").append(schema.rolesNameColumn()).append(" AS RoleName\n")
					.append("FROM dbo.").append(schema.rolesTable()).append(" r\n")
					.append("WHERE NOT EXISTS (\n")
					.append("  SELECT 1 FROM dbo.").append(schema.userRolesTable()).append(" ur\n")
					.append("  WHERE ur.").append(schema.userRolesUserIdColumn()).append(" = ?\n")
					.append("    AND ur.").append(schema.userRolesRoleIdColumn()).append(" = r.")
					.append(schema.rolesIdColumn()).append("\n")
					.append(")");
			appendRoleVisibilityFilters(sql, con, schema, "r", shaleClientId);
			sql.append("\nORDER BY r.").append(schema.rolesNameColumn()).append(" ASC, r.")
					.append(schema.rolesIdColumn()).append(" ASC;");

			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				ps.setInt(1, userId);
				try (ResultSet rs = ps.executeQuery()) {
					List<UserRoleRow> out = new ArrayList<>();
					while (rs.next()) {
						out.add(new UserRoleRow(rs.getInt("RoleId"), rs.getString("RoleName")));
					}
					return out;
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to list assignable roles for user (id=" + userId + ")", e);
		}
	}

	public boolean addRoleToUser(int userId, int roleId, int shaleClientId) {
		if (userId <= 0 || roleId <= 0 || shaleClientId <= 0) {
			throw new IllegalArgumentException("userId, roleId, and shaleClientId must be > 0");
		}

		try (Connection con = db.requireConnection()) {
			verifyTenantMatchesSession(con, shaleClientId);
			RoleSchema schema = resolveRoleSchema(con);

			StringBuilder sql = new StringBuilder();
			sql.append("INSERT INTO dbo.").append(schema.userRolesTable()).append(" (")
					.append(schema.userRolesUserIdColumn()).append(", ")
					.append(schema.userRolesRoleIdColumn()).append(")\n")
					.append("SELECT ?, ?\n")
					.append("WHERE EXISTS (\n")
					.append("  SELECT 1 FROM dbo.Users u\n")
					.append("  WHERE u.Id = ?\n")
					.append("    AND u.ShaleClientId = ?");
			appendUserVisibilityFilters(sql, con, "u");
			sql.append("\n)\n")
					.append("AND EXISTS (\n")
					.append("  SELECT 1 FROM dbo.").append(schema.rolesTable()).append(" r\n")
					.append("  WHERE r.").append(schema.rolesIdColumn()).append(" = ?");
			appendRoleVisibilityFilters(sql, con, schema, "r", shaleClientId);
			sql.append("\n)\n")
					.append("AND NOT EXISTS (\n")
					.append("  SELECT 1 FROM dbo.").append(schema.userRolesTable()).append(" ur\n")
					.append("  WHERE ur.").append(schema.userRolesUserIdColumn()).append(" = ?\n")
					.append("    AND ur.").append(schema.userRolesRoleIdColumn()).append(" = ?\n")
					.append(");");

			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				int idx = 1;
				ps.setInt(idx++, userId);
				ps.setInt(idx++, roleId);
				ps.setInt(idx++, userId);
				ps.setInt(idx++, shaleClientId);
				ps.setInt(idx++, roleId);
				ps.setInt(idx++, userId);
				ps.setInt(idx++, roleId);
				return ps.executeUpdate() > 0;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to add role to user (userId=" + userId + ", roleId=" + roleId + ")", e);
		}
	}

	public boolean removeRoleFromUser(int userId, int roleId, int shaleClientId) {
		if (userId <= 0 || roleId <= 0 || shaleClientId <= 0) {
			throw new IllegalArgumentException("userId, roleId, and shaleClientId must be > 0");
		}

		try (Connection con = db.requireConnection()) {
			verifyTenantMatchesSession(con, shaleClientId);
			RoleSchema schema = resolveRoleSchema(con);

			StringBuilder sql = new StringBuilder();
			sql.append("DELETE ur\n")
					.append("FROM dbo.").append(schema.userRolesTable()).append(" ur\n")
					.append("INNER JOIN dbo.Users u ON u.Id = ur.").append(schema.userRolesUserIdColumn()).append("\n")
					.append("WHERE ur.").append(schema.userRolesUserIdColumn()).append(" = ?\n")
					.append("  AND ur.").append(schema.userRolesRoleIdColumn()).append(" = ?\n")
					.append("  AND u.ShaleClientId = ?");
			appendUserVisibilityFilters(sql, con, "u");
			sql.append(";");

			try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
				ps.setInt(1, userId);
				ps.setInt(2, roleId);
				ps.setInt(3, shaleClientId);
				return ps.executeUpdate() > 0;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to remove role from user (userId=" + userId + ", roleId=" + roleId + ")", e);
		}
	}

	private static void appendRoleVisibilityFilters(StringBuilder sql, Connection con, RoleSchema schema, String alias, int shaleClientId) throws SQLException {
		String prefix = (alias == null || alias.isBlank()) ? "" : alias + ".";
		if (schema.rolesTenantScoped()) {
			sql.append("\n  AND ").append(prefix).append("ShaleClientId = ").append(shaleClientId);
		}
		if (schema.rolesHasIsActive()) {
			sql.append("\n  AND (").append(prefix).append("IsActive = 1 OR ").append(prefix).append("IsActive IS NULL)");
		}
		if (schema.rolesHasIsDeleted()) {
			sql.append("\n  AND (").append(prefix).append("IsDeleted = 0 OR ").append(prefix).append("IsDeleted IS NULL)");
		}
		if (schema.rolesHasIsDeletedLower()) {
			sql.append("\n  AND (").append(prefix).append("is_deleted = 0 OR ").append(prefix).append("is_deleted IS NULL)");
		}
	}

	private static RoleSchema resolveRoleSchema(Connection con) throws SQLException {
		String userRolesTable = firstExistingTable(con, "UserRoles", "UsersRoles");
		String rolesTable = firstExistingTable(con, "Roles", "UserRoleTypes");
		if (userRolesTable == null || rolesTable == null) {
			throw new IllegalStateException("Role management tables are not available.");
		}

		String userRolesUserIdColumn = firstExistingColumn(con, userRolesTable, "UserId", "UsersId");
		String userRolesRoleIdColumn = firstExistingColumn(con, userRolesTable, "RoleId", "RolesId");
		String rolesIdColumn = firstExistingColumn(con, rolesTable, "Id", "RoleId");
		String rolesNameColumn = firstExistingColumn(con, rolesTable, "Name", "RoleName");
		if (userRolesUserIdColumn == null || userRolesRoleIdColumn == null || rolesIdColumn == null || rolesNameColumn == null) {
			throw new IllegalStateException("Role management schema is missing required columns.");
		}

		return new RoleSchema(
				userRolesTable,
				userRolesUserIdColumn,
				userRolesRoleIdColumn,
				rolesTable,
				rolesIdColumn,
				rolesNameColumn,
				tableHasColumn(con, rolesTable, "ShaleClientId"),
				tableHasColumn(con, rolesTable, "IsActive"),
				tableHasColumn(con, rolesTable, "IsDeleted"),
				tableHasColumn(con, rolesTable, "is_deleted"));
	}

	private record RoleSchema(
			String userRolesTable,
			String userRolesUserIdColumn,
			String userRolesRoleIdColumn,
			String rolesTable,
			String rolesIdColumn,
			String rolesNameColumn,
			boolean rolesTenantScoped,
			boolean rolesHasIsActive,
			boolean rolesHasIsDeleted,
			boolean rolesHasIsDeletedLower) {
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

	private static Integer getNullableInt(ResultSet rs, String col) throws SQLException {
		int value = rs.getInt(col);
		return rs.wasNull() ? null : value;
	}

	private static Integer getNullableInt(ResultSet rs, int colIndex) throws SQLException {
		int value = rs.getInt(colIndex);
		return rs.wasNull() ? null : value;
	}
}
