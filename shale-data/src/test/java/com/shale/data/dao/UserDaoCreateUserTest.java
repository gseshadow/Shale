package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

final class UserDaoCreateUserTest {
	@Test
	void createRequestDoesNotExposeTenantOrSystemFields() {
		var components = UserDao.UserCreateRequest.class.getRecordComponents();
		for (var component : components) {
			String name = component.getName().toLowerCase();
			assertFalse(name.contains("shaleclientid"));
			assertFalse(name.equals("id"));
			assertFalse(name.equals("created_at"));
			assertFalse(name.equals("legacyuserid"));
		}
	}

	@Test
	void requiredFieldsAreValidated() {
		assertThrows(IllegalArgumentException.class, () -> UserDao.validateCreateRequest(
				new UserDao.UserCreateRequest("", "User", "new@example.com", "password1", null, null, false, false)));
		assertThrows(IllegalArgumentException.class, () -> UserDao.validateCreateRequest(
				new UserDao.UserCreateRequest("New", "", "new@example.com", "password1", null, null, false, false)));
		assertThrows(IllegalArgumentException.class, () -> UserDao.validateCreateRequest(
				new UserDao.UserCreateRequest("New", "User", "", "password1", null, null, false, false)));
		assertThrows(IllegalArgumentException.class, () -> UserDao.validateCreateRequest(
				new UserDao.UserCreateRequest("New", "User", "new@example.com", "short", null, null, false, false)));
	}

	@Test
	void emailIsNormalized() {
		assertEquals("admin@example.com", UserDao.normalizeEmail("  Admin@Example.COM  "));
	}

	@Test
	void passwordIsBcryptHashedNotPlaintext() {
		String hash = UserDao.hashPassword("temporaryPassword1");

		assertTrue(hash.startsWith("$2"));
		assertFalse(hash.contains("temporaryPassword1"));
		assertTrue(BCrypt.checkpw("temporaryPassword1", hash));
	}

	@Test
	void createUserDerivesTenantFromSessionAndRequiresAdminPrincipal() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/UserDao.java"));

		assertTrue(source.contains("int shaleClientId = requireCurrentShaleClientId(con);"));
		assertTrue(source.contains("requireCurrentAdmin(con, shaleClientId);"));
		assertTrue(source.contains("ps.setInt(idx++, shaleClientId);"),
				"Inserted ShaleClientId must come from current session context, not the create request/UI.");
		assertTrue(source.contains("SESSION_CONTEXT(N'PrincipalUserId')"));
		assertTrue(source.contains("Only admin users can create users."));
	}


	@Test
	void duplicateEmailMessagesDistinguishActiveAndInactiveUsers() {
		assertEquals("A user with this email already exists.", UserDao.duplicateEmailMessage(false));
		assertEquals("A user with this email already exists but is inactive. Reactivate the existing account instead.",
				UserDao.duplicateEmailMessage(true));
	}

	@Test
	void userManagementActionsAreTenantScopedAndUseSoftDeleteAndBcrypt() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/UserDao.java"));

		assertTrue(source.contains("findExistingEmail(con, shaleClientId, email)"),
				"Create must re-check duplicate normalized email on the server before insert.");
		assertTrue(source.contains("WHERE ShaleClientId = ?"),
				"Management queries/actions must be tenant-scoped.");
		assertTrue(source.contains("UPDATE dbo.Users SET is_deleted = 1 WHERE Id = ? AND ShaleClientId = ?"),
				"Deactivate should soft-delete only within the current tenant.");
		assertTrue(source.contains("UPDATE dbo.Users SET is_deleted = 0 WHERE Id = ? AND ShaleClientId = ?"),
				"Reactivate should preserve the user id and tenant scope.");
		assertTrue(source.contains("if (userId == principalUserId)"),
				"Self-deactivation must be blocked.");
		assertTrue(source.contains("countActiveAdmins(con, shaleClientId) <= 1"),
				"Last active tenant admin must be protected.");
		assertTrue(source.contains("password_hash = ?, password_alg = 'bcrypt'"),
				"Password reset must store a bcrypt hash, not plaintext.");
		assertTrue(!source.contains("System.out.println(newPassword)") && !source.contains("println(passwordHash)"),
				"Password reset must not log plaintext passwords or hashes.");
	}


	@Test
	void normalizedEmailUniquenessBackstopIsDocumented() throws Exception {
		String sql = Files.readString(Path.of("../docs/sql/2026-06-16_users_email_norm_unique.sql"));

		assertTrue(sql.contains("CREATE UNIQUE INDEX UX_Users_ShaleClientId_EmailNorm"));
		assertTrue(sql.contains("ON dbo.Users (ShaleClientId, email_norm)"));
	}

}
