package com.shale.desktop.ui;

import com.shale.ui.services.UiAuthService;
import com.shale.data.auth.AuthService;
import com.shale.core.model.User;

/**
 * Desktop adapter that bridges shale-ui's UiAuthService interface to the real data-layer
 * AuthService.
 */
public final class DesktopUiAuthService implements UiAuthService {

	private final AuthService authService;

	public DesktopUiAuthService(AuthService authService) {
		this.authService = authService;
	}

	@Override
	public Result login(String email, String password) throws Exception {
		// Call real authentication in shale-data
		User user = authService.login(email, password);

		// Convert core User -> UI layer Result record
		return new Result(
				user.getId(), // userId
				user.getShaleClientId(), // tenant identifier for RLS
				user.getEmail() // email
		);
	}
}
