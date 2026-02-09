package com.shale.data.auth;

public interface PasswordVerifier {
	boolean verify(String plaintext, String hashed);
}
