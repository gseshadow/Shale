package com.shale.ui.services;

/**
 * UI-facing auth adapter. Implement this in shale-desktop (or wire to shale-data) and
 * inject into SceneManager.
 */
public interface UiAuthService {
	record Result(int userId, int shaleClientId, String email) {
	}

	Result login(String email, String password) throws Exception;
}
