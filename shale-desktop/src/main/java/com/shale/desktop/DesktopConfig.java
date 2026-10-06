package com.shale.desktop;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.net.URI;

import com.shale.data.auth.AuthService;
import com.shale.data.auth.AuthServiceImpl;
import com.shale.data.auth.BCryptPasswordVerifier;
import com.shale.data.config.Config;
import com.shale.data.config.DataSources;
import com.shale.data.runtime.RuntimeSessionService;

public final class DesktopConfig {
	public final String appEnv;
	public final String negotiateEndpointUrl;
	public final java.util.Optional<URI> serverApiOrigin;

	public final DataSources dataSources;
	public final AuthService authService;
	public final RuntimeSessionService runtimeService;

	private static final Properties PROPS = loadProperties();

	private DesktopConfig(
			String appEnv,
			String negotiateEndpointUrl,
			java.util.Optional<URI> serverApiOrigin,
			DataSources dataSources,
			AuthService authService,
			RuntimeSessionService runtimeService) {
		System.out.println("DesktopConfig()"); // TODO remove
		this.appEnv = appEnv;
		this.negotiateEndpointUrl = negotiateEndpointUrl;
		this.serverApiOrigin = serverApiOrigin;
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
		pushToSystemProperty("APP_VERSION", env);
		
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

		boolean packagedInstallation = !System.getProperty("jpackage.app-path", "").isBlank();
		var serverApiOrigin = DesktopApiOrigin.resolve(System.getProperties(), env, PROPS, appEnv, packagedInstallation);

		return new DesktopConfig(appEnv, negotiateUrl, serverApiOrigin, dsrc, auth, runtime);
	}

	private static Properties loadProperties() {
		Properties props = new Properties();

		loadResource(props, "desktop-production.properties");
		loadResource(props, "application.properties");
		return props;
	}

	private static void loadResource(Properties props, String resource) {
		try (InputStream in = DesktopConfig.class.getClassLoader().getResourceAsStream(resource)) {
			if (in != null) {
				props.load(in);
			}
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to load desktop configuration resource: " + resource, ex);
		}
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
	
	public static String appVersion() {
		return System.getProperty("APP_VERSION", "0.0.0");
	}
}
