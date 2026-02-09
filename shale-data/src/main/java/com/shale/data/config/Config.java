package com.shale.data.config;

import java.util.Optional;

public final class Config {
	public String appJdbcUrl() {
		return must("SHALE_APP_JDBC_URL");
	}

	public String appUser() {
		return must("SHALE_APP_USER");
	}

	public String appPass() {
		return must("SHALE_APP_PASS");
	}

	public String rtJdbcUrl() {
		return must("SHALE_RT_JDBC_URL");
	}

	public String rtUser() {
		return must("SHALE_RT_USER");
	}

	public String rtPass() {
		return must("SHALE_RT_PASS");
	}

	public int maxPoolSize() {
		return intVal("DB_MAX_POOL_SIZE", 10);
	}

	public long connTimeoutMs() {
		return longVal("DB_CONNECTION_TIMEOUT_MS", 10000L);
	}

	private static String must(String key) {
		return Optional.ofNullable(get(key))
				.filter(s -> !s.isBlank())
				.orElseThrow(() -> new IllegalStateException("Missing required config: " + key));
	}

	private static int intVal(String key, int def) {
		String v = get(key);
		return (v == null || v.isBlank()) ? def : Integer.parseInt(v.trim());
	}

	private static long longVal(String key, long def) {
		String v = get(key);
		return (v == null || v.isBlank()) ? def : Long.parseLong(v.trim());
	}

	private static String get(String key) {
		String v = System.getenv(key);
		if (v == null)
			v = System.getProperty(key);
		return v;
	}
}
