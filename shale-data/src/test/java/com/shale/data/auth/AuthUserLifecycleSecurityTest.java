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
	void loginUsesReviewedBoundedModuleWithLifecycleChecks() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/data/auth/AuthServiceImpl.java"));

		assertTrue(source.contains("{call dbo.LookupAuthenticationCredentialV1(?)}"));
        String module = Files.readString(Path.of("..", "docs", "sql", "2026-10-14_pretenant_authentication_modules.sql"));
        assertTrue(module.contains("is_deleted=0 AND COALESCE(IsRemoved,0)=0"), "password login must reject disabled and removed identities");
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
