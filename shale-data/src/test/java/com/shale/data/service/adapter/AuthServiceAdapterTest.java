package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.shale.core.model.User;
import com.shale.core.result.Result;
import com.shale.data.auth.AuthService;
import com.shale.data.errors.AuthException;

class AuthServiceAdapterTest {

	@Test
	void authenticateDelegatesSuccessfulLogin() {
		User user = User.builder()
				.id(7)
				.email("user@example.com")
				.shaleClientId(42)
				.build();
		AuthServiceAdapter adapter = new AuthServiceAdapter((email, password) -> user);

		Result<User> result = adapter.authenticate("user@example.com", "secret");

		assertTrue(result.isOk());
		assertSame(user, result.value().orElseThrow());
	}

	@Test
	void authenticateMapsAuthExceptionToFailedResult() {
		AuthService failingAuthService = (email, password) -> {
			throw new AuthException("Invalid credentials.");
		};
		AuthServiceAdapter adapter = new AuthServiceAdapter(failingAuthService);

		Result<User> result = adapter.authenticate("user@example.com", "bad");

		assertFalse(result.isOk());
		assertEquals("Invalid credentials.", result.error().orElseThrow());
	}
}
