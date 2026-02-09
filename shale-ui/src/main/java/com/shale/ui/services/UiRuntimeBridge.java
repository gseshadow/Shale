package com.shale.ui.services;

public interface UiRuntimeBridge {
	/**
	 * Desktop must: - initialize runtime session (RLS / tenant context) - enable DB access
	 * for DbSessionProvider
	 */
	void onLoginSuccess(int userId, int shaleClientId, String email);

	void onLogout();
}
