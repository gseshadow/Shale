package com.shale.data.auth;

import org.mindrot.jbcrypt.BCrypt;

public final class BCryptPasswordVerifier implements PasswordVerifier {
	@Override
	public boolean verify(String plaintext, String hashed) {
		if (plaintext == null || hashed == null)
			return false;
		try {
			return BCrypt.checkpw(plaintext, hashed);
		} catch (RuntimeException ex) {
			// Malformed hash, etc.
			return false;
		}
	}
}
