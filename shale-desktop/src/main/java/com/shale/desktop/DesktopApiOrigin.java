package com.shale.desktop;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Resolves and validates the single HTTP origin used by desktop server-session features. */
public final class DesktopApiOrigin {
	public static final String OVERRIDE_KEY = "SHALE_SERVER_API_BASE_URL";
	public static final String PACKAGED_KEY = "SHALE_PACKAGED_SERVER_API_BASE_URL";

	private DesktopApiOrigin() { }

	public static Optional<URI> resolve(Properties system, Map<String, String> environment,
			Properties packaged, String appEnvironment, boolean packagedInstallation) {
		String explicit = nonBlank(system.getProperty(OVERRIDE_KEY));
		if (explicit == null) {
			explicit = nonBlank(environment.get(OVERRIDE_KEY));
		}
		boolean development = isDevelopment(appEnvironment) && !packagedInstallation;
		if (explicit != null) {
			return Optional.of(validate(explicit, development, "explicit desktop API override"));
		}
		if (development) {
			return Optional.empty();
		}
		String configured = nonBlank(packaged.getProperty(PACKAGED_KEY));
		if (configured == null) {
			return Optional.empty();
		}
		return Optional.of(validate(configured, false, "packaged desktop API configuration"));
	}

	static URI validate(String value, boolean development, String source) {
		final URI parsed;
		try {
			parsed = new URI(value.trim());
		} catch (URISyntaxException ex) {
			throw invalid(source);
		}
		String scheme = parsed.getScheme();
		String host = parsed.getHost();
		boolean loopback = host != null && (host.equalsIgnoreCase("localhost")
				|| host.equals("127.0.0.1") || host.equals("::1"));
		boolean secure = "https".equalsIgnoreCase(scheme);
		boolean explicitLoopbackDevelopment = development && "http".equalsIgnoreCase(scheme) && loopback;
		String path = parsed.getPath();
		if ((!secure && !explicitLoopbackDevelopment) || host == null || parsed.getUserInfo() != null
				|| parsed.getQuery() != null || parsed.getFragment() != null
				|| (path != null && !path.isBlank() && path.chars().anyMatch(character -> character != '/'))) {
			throw invalid(source);
		}
		try {
			return new URI(scheme.toLowerCase(Locale.ROOT), null, host.toLowerCase(Locale.ROOT),
					parsed.getPort(), null, null, null);
		} catch (URISyntaxException impossible) {
			throw invalid(source);
		}
	}

	private static boolean isDevelopment(String environment) {
		String value = environment == null ? "" : environment.trim().toLowerCase(Locale.ROOT);
		return value.isEmpty() || value.equals("dev") || value.equals("development") || value.equals("local") || value.equals("test");
	}

	private static String nonBlank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static IllegalStateException invalid(String source) {
		return new IllegalStateException("Invalid " + source + ": use an HTTPS origin without credentials, path, query, or fragment; HTTP is allowed only for an explicit loopback development override.");
	}
}
