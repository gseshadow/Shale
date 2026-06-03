package com.shale.core.service;

import com.shale.core.model.User;
import com.shale.core.result.Result;

/**
 * Shared authentication boundary for desktop and future server adapters.
 *
 * <p>Implementations are intentionally not moved in Step 2; the first adapter
 * should wrap the existing shale-data AuthService/AuthServiceImpl behavior.</p>
 */
public interface AuthServicePort {

	/**
	 * Authenticate credentials and return the tenant-aware Shale user identity.
	 *
	 * TODO: replace the raw password string with a request DTO or credential type
	 * when shale-server's login/session design is finalized.
	 */
	Result<User> authenticate(String email, String password);
}
