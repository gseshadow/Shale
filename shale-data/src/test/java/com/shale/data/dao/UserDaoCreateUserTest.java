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
				new UserDao.UserCreateRequest("", "User", "new@example.com", "password1", null, null, false, false, null, null)));
		assertThrows(IllegalArgumentException.class, () -> UserDao.validateCreateRequest(
				new UserDao.UserCreateRequest("New", "", "new@example.com", "password1", null, null, false, false, null, null)));
		assertThrows(IllegalArgumentException.class, () -> UserDao.validateCreateRequest(
				new UserDao.UserCreateRequest("New", "User", "", "password1", null, null, false, false, null, null)));
		assertThrows(IllegalArgumentException.class, () -> UserDao.validateCreateRequest(
				new UserDao.UserCreateRequest("New", "User", "new@example.com", "short", null, null, false, false, null, null)));
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

}
