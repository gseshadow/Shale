package com.shale.core.util;

public final class Preconditions {
	private Preconditions() {
	}

	public static <T> T checkNotNull(T ref, String message) {
		if (ref == null)
			throw new IllegalArgumentException(message);
		return ref;
	}

	public static String checkNotBlank(String s, String message) {
		if (s == null || s.isBlank())
			throw new IllegalArgumentException(message);
		return s;
	}
}
