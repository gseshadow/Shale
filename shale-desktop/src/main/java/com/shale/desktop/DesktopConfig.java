package com.shale.desktop;

import java.util.Map;

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

	private DesktopConfig(
			String appEnv,
			String negotiateEndpointUrl,
			DataSources dataSources,
			AuthService authService,
			RuntimeSessionService runtimeService) {
		System.out.println("DesktopConfig()");// TODO
		this.appEnv = appEnv;
		this.negotiateEndpointUrl = negotiateEndpointUrl;
		this.dataSources = dataSources;
		this.authService = authService;
		this.runtimeService = runtimeService;
	}

	public static DesktopConfig load() {
		System.out.println("DesktopConfig.load()");// TODO
		Map<String, String> env = System.getenv();

		String appEnv = env.getOrDefault("APP_ENV", "dev");
		String negotiateUrl = require(env, "NEGOTIATE_ENDPOINT_URL");

		// Let shale-data Config pull all DB settings from env/system properties
		Config cfg = new Config();
		DataSources dsrc = new DataSources(cfg);

		// Services
		AuthService auth = new AuthServiceImpl(dsrc, new BCryptPasswordVerifier());
		RuntimeSessionService runtime = new RuntimeSessionService(dsrc.runtime());

		return new DesktopConfig(appEnv, negotiateUrl, dsrc, auth, runtime);
	}

	private static String require(Map<String, String> env, String key) {
		System.out.println("DesktopConfig.require()");// TODO
		String v = env.get(key);
		if (v == null || v.isBlank()) {
			throw new IllegalStateException("Missing required env var: " + key);
		}
		return v;
	}

	public AuthService getAuthService() {
		return authService;
	}

}
