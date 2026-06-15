package com.shale.data.service.adapter;

import java.util.Objects;

import com.shale.core.model.User;
import com.shale.core.result.Result;
import com.shale.core.service.AuthServicePort;
import com.shale.data.auth.AuthService;
import com.shale.data.errors.AuthException;

/**
 * Thin AuthServicePort adapter over the existing shale-data authentication service.
 */
public final class AuthServiceAdapter implements AuthServicePort {

	private final AuthService authService;

	public AuthServiceAdapter(AuthService authService) {
		this.authService = Objects.requireNonNull(authService, "authService");
	}

	@Override
	public Result<User> authenticate(String email, String password) {
		try {
			return Result.ok(authService.login(email, password));
		} catch (AuthException e) {
			return Result.fail(e.getMessage());
		}
	}
}
