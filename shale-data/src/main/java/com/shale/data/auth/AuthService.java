package com.shale.data.auth;

import com.shale.core.model.User;
import com.shale.data.errors.AuthException;

/**
 * Provides authentication operations for Shale.
 */
public interface AuthService {
	/**
	 * @return the core User on success
	 * @throws AuthException on invalid credentials or system errors
	 */
	User login(String email, String password) throws AuthException;
}
