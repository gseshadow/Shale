package com.shale.data.dao;

import com.shale.core.semantics.RoleSemantics;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.data.runtime.RuntimeSessionService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.Set;
import java.util.EnumMap;
import java.util.Objects;

import org.mindrot.jbcrypt.BCrypt;

public final class UserDao {
	private final EntityActionAuditDao entityActionAuditDao = new EntityActionAuditDao();
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
			boolean deleted,
			byte[] rowVer) {
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

	public record UserCreateRequest(
			String firstName,
			String lastName,
			String email,
			String temporaryPassword,
			String color,
			String initials,
			boolean attorney,
			boolean admin) {
	}

	public record UserManagementRow(
			int id, String firstName, String lastName, String name, String email, String phone, String color, String initials,
			boolean attorney, boolean admin, boolean deleted, byte[] rowVer) {
	}

	public record ExistingEmailRow(int id, boolean deleted) {
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
					  COALESCE(u.is_deleted, 0) AS IsDeleted,
					  u.RowVer
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
							rs.getBoolean("IsDeleted"),
							rs.getBytes("RowVer"));
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

	public UserDetailRow createUser(UserCreateRequest request) {
		Objects.requireNonNull(request, "request");
		validateCreateRequest(request);

		try (Connection con = db.requireConnection()) {
			int shaleClientId = requireCurrentShaleClientId(con);
			requireCurrentAdmin(con, shaleClientId);
			String email = normalizeEmail(request.email());
			ExistingEmailRow existingEmail = findExistingEmail(con, shaleClientId, email);
			if (existingEmail != null) {
				throw new IllegalArgumentException(duplicateEmailMessage(existingEmail.deleted()));
			}
			String passwordHash = hashPassword(request.temporaryPassword());
			String phoneColumn = existingPhoneColumn(con);

			StringBuilder sql = new StringBuilder("""
					INSERT INTO dbo.Users (
					  name_first, name_last, email, password_hash, password_alg,
					  Color, Initials, is_attorney, is_admin, is_deleted, ShaleClientId
					""");
			if (phoneColumn != null) {
				sql.append(", ").append(phoneColumn);
			}
			sql.append("""
					)
					VALUES (?, ?, ?, ?, 'bcrypt', ?, ?, ?, ?, 0, ?
					""");
			if (phoneColumn != null) {
				sql.append(", NULL");
			}
			sql.append(")");

			try (PreparedStatement ps = con.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS)) {
				int idx = 1;
				setNullableString(ps, idx++, request.firstName());
				setNullableString(ps, idx++, request.lastName());
				ps.setString(idx++, email);
				ps.setString(idx++, passwordHash);
				setNullableString(ps, idx++, request.color());
				setNullableString(ps, idx++, request.initials());
				ps.setBoolean(idx++, request.attorney());
				ps.setBoolean(idx++, request.admin());
				ps.setInt(idx++, shaleClientId);
				ps.executeUpdate();
				try (ResultSet keys = ps.getGeneratedKeys()) {
					if (keys.next()) {
						return findById(keys.getInt(1), shaleClientId);
					}
				}
			}
			throw new IllegalStateException("User was created but no generated id was returned.");
		} catch (SQLException e) {
			throw new RuntimeException("Failed to create tenant user", e);
		}
	}

	static void validateCreateRequest(UserCreateRequest request) {
		if (isBlank(request.firstName())) throw new IllegalArgumentException("First name is required.");
		if (isBlank(request.lastName())) throw new IllegalArgumentException("Last name is required.");
		if (isBlank(request.email())) throw new IllegalArgumentException("Email is required.");
		if (!normalizeEmail(request.email()).contains("@")) throw new IllegalArgumentException("A valid email is required.");
		validatePassword(request.temporaryPassword());
	}

	public static String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

	public static String hashPassword(String plaintext) {
		return BCrypt.hashpw(plaintext, BCrypt.gensalt());
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	public List<UserManagementRow> listUsersForManagement(boolean includeInactive) {
		try (Connection con = db.requireConnection()) {
			int tenant = requireCurrentShaleClientId(con); requireCurrentAdmin(con, tenant);
			String phone = existingPhoneColumn(con);
			String sql = "SELECT Id,COALESCE(name_first,'') FirstName,COALESCE(name_last,'') LastName," +
				"LTRIM(RTRIM(COALESCE(name_first,'')+CASE WHEN COALESCE(name_first,'')='' OR COALESCE(name_last,'')='' THEN '' ELSE ' ' END+COALESCE(name_last,''))) DisplayName," +
				"COALESCE(email,'') Email," + phoneSelectExpression(phone, null) + ",COALESCE(Color,'') Color,COALESCE(Initials,'') Initials," +
				"COALESCE(is_attorney,0) IsAttorney,COALESCE(is_admin,0) IsAdmin,COALESCE(is_deleted,0) IsDeleted,RowVer FROM dbo.Users WHERE ShaleClientId=?" +
				(includeInactive ? "" : " AND COALESCE(is_deleted,0)=0") + " ORDER BY IsDeleted,DisplayName,Id";
			try (PreparedStatement ps=con.prepareStatement(sql)) { ps.setInt(1,tenant); try(ResultSet rs=ps.executeQuery()) {
				List<UserManagementRow> out=new ArrayList<>(); while(rs.next()) out.add(managementRow(rs)); return out;
			}}
		} catch(SQLException e){ throw new RuntimeException("Failed to list users for management",e); }
	}

	public record UserUpdateRequest(int userId, byte[] expectedRowVer, String firstName, String lastName,
			String email, String phone, String initials, String color, Set<Integer> roleIds) {
		public UserUpdateRequest { expectedRowVer=expectedRowVer==null?null:expectedRowVer.clone(); roleIds=roleIds==null?Set.of():Set.copyOf(roleIds); }
		@Override public byte[] expectedRowVer(){ return expectedRowVer==null?null:expectedRowVer.clone(); }
	}
	public record UserUpdateResult(UserManagementRow user, boolean changed) {}

	/** Atomic administrative profile/role update. Lifecycle and credentials are deliberately excluded. */
	public UserUpdateResult updateManagedUser(UserUpdateRequest request) {
		Objects.requireNonNull(request,"request");
		String first=trimRequired(request.firstName(),"First name"), last=trimRequired(request.lastName(),"Last name");
		String email=normalizeEmail(request.email()); if(!email.contains("@")) throw new IllegalArgumentException("A valid email is required.");
		if(request.expectedRowVer()==null||request.expectedRowVer().length==0) throw new IllegalArgumentException("User version is required.");
		if(!Set.of(RoleSemantics.ROLE_ADMIN,RoleSemantics.ROLE_ATTORNEY).containsAll(request.roleIds())) throw new IllegalArgumentException("An unsupported user role was supplied.");
		try(Connection con=db.requireConnection()) { int tenant=requireCurrentShaleClientId(con), actor=requireCurrentAdmin(con,tenant); con.setAutoCommit(false);
			try {
				UserManagementRow old=findManagementUser(con,tenant,request.userId()); if(old==null) throw new IllegalArgumentException("User was not found for this tenant.");
				if(!Arrays.equals(old.rowVer(),request.expectedRowVer())) throw new IllegalStateException("This user was changed by someone else. Reload and try again.");
				ExistingEmailRow duplicate=findExistingEmail(con,tenant,email); if(duplicate!=null&&duplicate.id()!=request.userId()) throw new IllegalArgumentException(duplicateEmailMessage(duplicate.deleted()));
				boolean admin=request.roleIds().contains(RoleSemantics.ROLE_ADMIN), attorney=request.roleIds().contains(RoleSemantics.ROLE_ATTORNEY);
				if(old.admin()&&!admin&&(!old.deleted()&&countActiveAdmins(con,tenant)<=1)) throw new IllegalArgumentException("Cannot remove the last active admin in this tenant.");
				String phoneColumn=existingPhoneColumn(con); String phoneValue=blankToNull(request.phone()), initials=blankToNull(request.initials()), color=blankToNull(request.color());
				boolean changed=!old.firstName().equals(first)||!old.lastName().equals(last)||!normalizeEmail(old.email()).equals(email)||!Objects.equals(blankToNull(old.phone()),phoneValue)||!Objects.equals(blankToNull(old.initials()),initials)||!Objects.equals(blankToNull(old.color()),color)||old.admin()!=admin||old.attorney()!=attorney;
				if(!changed){ con.rollback(); return new UserUpdateResult(old,false); }
				String sql="UPDATE dbo.Users SET name_first=?,name_last=?,email=?,Initials=?,Color=?,is_admin=?,is_attorney=?,UpdatedAt=SYSUTCDATETIME()"+(phoneColumn==null?"":","+phoneColumn+"=?")+" WHERE Id=? AND ShaleClientId=? AND RowVer=?";
				try(PreparedStatement ps=con.prepareStatement(sql)){int i=1;ps.setString(i++,first);ps.setString(i++,last);ps.setString(i++,email);setNullableString(ps,i++,initials);setNullableString(ps,i++,color);ps.setBoolean(i++,admin);ps.setBoolean(i++,attorney);if(phoneColumn!=null)setNullableString(ps,i++,phoneValue);ps.setInt(i++,request.userId());ps.setInt(i++,tenant);ps.setBytes(i++,request.expectedRowVer());if(ps.executeUpdate()!=1)throw new IllegalStateException("This user was changed by someone else. Reload and try again.");}
				var md=new EnumMap<EntityActionAuditEvent.MetadataKey,Object>(EntityActionAuditEvent.MetadataKey.class);md.put(EntityActionAuditEvent.MetadataKey.TARGET_USER_ID,request.userId());md.put(EntityActionAuditEvent.MetadataKey.ADMIN_ROLE,admin);md.put(EntityActionAuditEvent.MetadataKey.ATTORNEY_ROLE,attorney);
				entityActionAuditDao.append(con,EntityActionAuditEvent.now(tenant,actor,EntityActionAuditEvent.EntityType.USER,request.userId(),EntityActionAuditEvent.Action.UPDATED,null,null,md)); con.commit();
				return new UserUpdateResult(findManagementUser(con,tenant,request.userId()),true);
			}catch(Exception ex){try{con.rollback();}catch(SQLException ignored){} if(ex instanceof RuntimeException re)throw re; throw new IllegalStateException("Failed to update user.",ex);}
		}catch(SQLException e){throw new RuntimeException("Failed to update user.",e);}
	}
	private static String trimRequired(String v,String label){String n=v==null?"":v.trim();if(n.isEmpty())throw new IllegalArgumentException(label+" is required.");return n;}
	private static String blankToNull(String v){if(v==null)return null;String n=v.trim();return n.isEmpty()?null:n;}

	public ExistingEmailRow findExistingEmailForCurrentTenant(String email) {
		String normalizedEmail = normalizeEmail(email);
		if (normalizedEmail.isBlank()) {
			return null;
		}
		try (Connection con = db.requireConnection()) {
			int shaleClientId = requireCurrentShaleClientId(con);
			return findExistingEmail(con, shaleClientId, normalizedEmail);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to validate user email", e);
		}
	}

	public boolean deactivateUser(int userId) {
		if (userId <= 0) throw new IllegalArgumentException("userId must be > 0");
		try (Connection con = db.requireConnection()) {
			int shaleClientId = requireCurrentShaleClientId(con);
			int principalUserId = requireCurrentAdmin(con, shaleClientId);
			if (userId == principalUserId) throw new IllegalArgumentException("You cannot deactivate yourself.");
			UserManagementRow target = findManagementUser(con, shaleClientId, userId);
			if (target == null) throw new IllegalArgumentException("User was not found for this tenant.");
			if (target.admin() && countActiveAdmins(con, shaleClientId) <= 1) {
				throw new IllegalArgumentException("Cannot deactivate the last active admin in this tenant.");
			}
			try (PreparedStatement ps = con.prepareStatement("UPDATE dbo.Users SET is_deleted = 1 WHERE Id = ? AND ShaleClientId = ?")) {
				ps.setInt(1, userId);
				ps.setInt(2, shaleClientId);
				return ps.executeUpdate() > 0;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to deactivate user", e);
		}
	}

	public boolean reactivateUser(int userId) {
		if (userId <= 0) throw new IllegalArgumentException("userId must be > 0");
		try (Connection con = db.requireConnection()) {
			int shaleClientId = requireCurrentShaleClientId(con);
			requireCurrentAdmin(con, shaleClientId);
			try (PreparedStatement ps = con.prepareStatement("UPDATE dbo.Users SET is_deleted = 0 WHERE Id = ? AND ShaleClientId = ?")) {
				ps.setInt(1, userId);
				ps.setInt(2, shaleClientId);
				return ps.executeUpdate() > 0;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to reactivate user", e);
		}
	}

	public boolean resetPassword(int userId, String newPassword) {
		if (userId <= 0) throw new IllegalArgumentException("userId must be > 0");
		validatePassword(newPassword);
		String passwordHash = hashPassword(newPassword);
		try (Connection con = db.requireConnection()) {
			int shaleClientId = requireCurrentShaleClientId(con);
			requireCurrentAdmin(con, shaleClientId);
			try (PreparedStatement ps = con.prepareStatement("UPDATE dbo.Users SET password_hash = ?, password_alg = 'bcrypt' WHERE Id = ? AND ShaleClientId = ?")) {
				ps.setString(1, passwordHash);
				ps.setInt(2, userId);
				ps.setInt(3, shaleClientId);
				return ps.executeUpdate() > 0;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to reset user password", e);
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

	private static ExistingEmailRow findExistingEmail(Connection con, int shaleClientId, String normalizedEmail) throws SQLException {
		String sql = """
				SELECT TOP 1 Id, COALESCE(is_deleted, 0) AS IsDeleted
				FROM dbo.Users
				WHERE ShaleClientId = ?
				  AND COALESCE(email_norm, LOWER(LTRIM(RTRIM(email)))) = ?
				ORDER BY COALESCE(is_deleted, 0) ASC, Id ASC
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shaleClientId);
			ps.setString(2, normalizedEmail);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) return null;
				return new ExistingEmailRow(rs.getInt("Id"), rs.getBoolean("IsDeleted"));
			}
		}
	}

	public static String duplicateEmailMessage(boolean inactive) {
		return inactive
				? "A user with this email already exists but is inactive. Reactivate the existing account instead."
				: "A user with this email already exists.";
	}

	static void validatePassword(String password) {
		if (password == null || password.length() < 8) {
			throw new IllegalArgumentException("Password must be at least 8 characters.");
		}
	}

	private static UserManagementRow findManagementUser(Connection con,int tenant,int userId)throws SQLException{
		String phone=existingPhoneColumn(con);String sql="SELECT Id,COALESCE(name_first,'') FirstName,COALESCE(name_last,'') LastName,LTRIM(RTRIM(COALESCE(name_first,'')+' '+COALESCE(name_last,''))) DisplayName,COALESCE(email,'') Email,"+phoneSelectExpression(phone,null)+",COALESCE(Color,'') Color,COALESCE(Initials,'') Initials,COALESCE(is_attorney,0) IsAttorney,COALESCE(is_admin,0) IsAdmin,COALESCE(is_deleted,0) IsDeleted,RowVer FROM dbo.Users WHERE Id=? AND ShaleClientId=?";
		try(PreparedStatement ps=con.prepareStatement(sql)){ps.setInt(1,userId);ps.setInt(2,tenant);try(ResultSet rs=ps.executeQuery()){return rs.next()?managementRow(rs):null;}}
	}
	private static UserManagementRow managementRow(ResultSet rs)throws SQLException{return new UserManagementRow(rs.getInt("Id"),rs.getString("FirstName"),rs.getString("LastName"),rs.getString("DisplayName"),rs.getString("Email"),rs.getString("Phone"),rs.getString("Color"),rs.getString("Initials"),rs.getBoolean("IsAttorney"),rs.getBoolean("IsAdmin"),rs.getBoolean("IsDeleted"),rs.getBytes("RowVer"));}

	private static int countActiveAdmins(Connection con, int shaleClientId) throws SQLException {
		try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM dbo.Users WHERE ShaleClientId = ? AND COALESCE(is_deleted, 0) = 0 AND COALESCE(is_admin, 0) = 1")) {
			ps.setInt(1, shaleClientId);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getInt(1);
			}
		}
	}


	private static int requireCurrentAdmin(Connection con, int shaleClientId) throws SQLException {
		Integer principalUserId = requireCurrentPrincipalUserId(con);
		String sql = "SELECT COALESCE(is_admin, 0) FROM dbo.Users WHERE Id = ? AND ShaleClientId = ? AND COALESCE(is_deleted, 0) = 0";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, principalUserId);
			ps.setInt(2, shaleClientId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next() || !rs.getBoolean(1)) {
					throw new SecurityException("Only admin users can create users.");
				}
			}
		}
		return principalUserId;
	}

	private static Integer requireCurrentPrincipalUserId(Connection con) throws SQLException {
		String sql = "SELECT CAST(SESSION_CONTEXT(N'PrincipalUserId') AS INT);";
		try (PreparedStatement ps = con.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			if (!rs.next()) {
				throw new IllegalStateException("PrincipalUserId session context is missing.");
			}
			Integer principalUserId = getNullableInt(rs, 1);
			if (principalUserId == null || principalUserId <= 0) {
				throw new IllegalStateException("PrincipalUserId session context is missing.");
			}
			return principalUserId;
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
