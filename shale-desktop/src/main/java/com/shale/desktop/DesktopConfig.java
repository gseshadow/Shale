package com.shale.desktop;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import com.shale.data.auth.AuthService;
import com.shale.data.auth.AuthServiceImpl;
import com.shale.data.auth.BCryptPasswordVerifier;
import com.shale.data.config.Config;
import com.shale.data.config.DataSources;
import com.shale.data.runtime.RuntimeSessionService;

public final class DesktopConfig {
	public final String appEnv;
	public final String negotiateEndpointUrl;

	public final DataSources dataSources;
	public final AuthService authService;
	public final RuntimeSessionService runtimeService;

	private static final Properties PROPS = loadProperties();

	private DesktopConfig(
			String appEnv,
			String negotiateEndpointUrl,
			DataSources dataSources,
			AuthService authService,
			RuntimeSessionService runtimeService) {
		System.out.println("DesktopConfig()"); // TODO remove
		this.appEnv = appEnv;
		this.negotiateEndpointUrl = negotiateEndpointUrl;
		this.dataSources = dataSources;
		this.authService = authService;
		this.runtimeService = runtimeService;
	}

	public static DesktopConfig load() {
		System.out.println("DesktopConfig.load()"); // TODO remove

		Map<String, String> env = System.getenv();

		String appEnv = get("APP_ENV", env, "dev");
		String negotiateUrl = require("NEGOTIATE_ENDPOINT_URL", env);

		// Bridge properties/env into system properties for shale-data Config
		pushToSystemProperty("APP_ENV", env);
		pushToSystemProperty("NEGOTIATE_ENDPOINT_URL", env);
		pushToSystemProperty("LIVE_NEGOTIATE_ENDPOINT_URL", env);
		pushToSystemProperty("LIVE_PUBLISH_ENDPOINT_URL", env);

		pushToSystemProperty("SHALE_APP_JDBC_URL", env);
		pushToSystemProperty("SHALE_APP_USER", env);
		pushToSystemProperty("SHALE_APP_PASS", env);

		pushToSystemProperty("SHALE_RT_JDBC_URL", env);
		pushToSystemProperty("SHALE_RT_USER", env);
		pushToSystemProperty("SHALE_RT_PASS", env);

		pushToSystemProperty("DB_MAX_POOL_SIZE", env);
		pushToSystemProperty("DB_CONNECTION_TIMEOUT_MS", env);

		pushToSystemProperty("WEBPUBSUB_HUB", env);

		Config cfg = new Config();
		DataSources dsrc = new DataSources(cfg);

		AuthService auth = new AuthServiceImpl(dsrc, new BCryptPasswordVerifier());
		RuntimeSessionService runtime = new RuntimeSessionService(dsrc.runtime());

		return new DesktopConfig(appEnv, negotiateUrl, dsrc, auth, runtime);
	}

	private static Properties loadProperties() {
		Properties props = new Properties();

		try (InputStream in = DesktopConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
			if (in != null) {
				props.load(in);
				System.out.println("Loaded application.properties"); // TODO remove
			} else {
				System.out.println("application.properties not found on classpath"); // TODO remove
			}
		} catch (Exception ex) {
			System.out.println("Failed to load application.properties: " + ex.getMessage()); // TODO remove
		}

		return props;
	}

	private static void pushToSystemProperty(String key, Map<String, String> env) {
		String value = PROPS.getProperty(key);

		if (value == null || value.isBlank()) {
			value = env.get(key);
		}

		if (value != null && !value.isBlank()) {
			System.setProperty(key, value.trim());
		}
	}

	private static String get(String key, Map<String, String> env, String defaultValue) {
		String value = PROPS.getProperty(key);

		if (value == null || value.isBlank()) {
			value = env.get(key);
		}

		if (value == null || value.isBlank()) {
			return defaultValue;
		}

		return value.trim();
	}

	private static String require(String key, Map<String, String> env) {
		System.out.println("DesktopConfig.require(" + key + ")"); // TODO remove

		String value = PROPS.getProperty(key);

		if (value == null || value.isBlank()) {
			value = env.get(key);
		}

		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing required config value: " + key);
		}

		return value.trim();
	}

	public AuthService getAuthService() {
		return authService;
	}
}