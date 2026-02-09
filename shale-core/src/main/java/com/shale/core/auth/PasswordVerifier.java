package com.shale.core.auth;

/**
 * Strategy interface so shale-data can plug in BCrypt (or other) without creating a hard
 * dependency in core.
 */
public interface PasswordVerifier {
	/** Returns true iff the plain text matches the stored hash. */
	boolean verify(String plain, String hash);

	/** Name of the algorithm (e.g., "bcrypt"). */
	default String algorithm() {
		return "unknown";
	}
}
