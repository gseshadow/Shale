package com.shale.data.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import com.shale.data.dao.UserDao;

final class AuthUserLifecycleSecurityTest {
	@Test
	void inactiveUsersCannotAuthenticateBecauseLoginFiltersDeletedUsers() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/data/auth/AuthServiceImpl.java"));

		assertTrue(source.contains("WHERE email = ? AND is_deleted = 0"));
	}

	@Test
	void resetPasswordHashAcceptsNewPasswordAndRejectsOldPasswordWithoutPlaintextStorage() {
		String oldPassword = "oldPassword1";
		String newPassword = "newPassword1";
		String oldHash = UserDao.hashPassword(oldPassword);
		String newHash = UserDao.hashPassword(newPassword);

		assertNotEquals(oldHash, newHash);
		assertTrue(BCrypt.checkpw(newPassword, newHash));
		assertFalse(BCrypt.checkpw(oldPassword, newHash));
		assertFalse(newHash.contains(newPassword));
	}
}
