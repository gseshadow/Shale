package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

final class UserDaoCreateUserTest {
	@Test
	void normalizesEmailInternally() {
		assertEquals("admin@example.com", UserDao.normalizeEmail("  Admin@Example.COM  "));
	}

	@Test
	void hashesTemporaryPasswordWithBcrypt() {
		String hash = UserDao.hashTemporaryPassword("TempPass123!");

		assertNotEquals("TempPass123!", hash);
		assertTrue(hash.startsWith("$2"));
		assertTrue(BCrypt.checkpw("TempPass123!", hash));
	}

	@Test
	void validatesRequiredFieldsBeforeSave() {
		assertThrows(IllegalArgumentException.class, () -> UserDao.normalizeEmail(" "));
		assertThrows(IllegalArgumentException.class, () -> UserDao.hashTemporaryPassword(null));
	}
}
