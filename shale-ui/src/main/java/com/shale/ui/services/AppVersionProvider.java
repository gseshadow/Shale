package com.shale.ui.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

public final class AppVersionProvider {
	private static final String VERSION_RESOURCE = "/version.properties";
	private static final String VERSION_KEY = "app.version";
	private static final String DEFAULT_VERSION = "unknown";

	private AppVersionProvider() {
	}

	public static String currentVersion() {
		String configuredVersion = System.getProperty("APP_VERSION");
		if (configuredVersion != null && !configuredVersion.isBlank()) {
			return configuredVersion.trim();
		}

		Package appPackage = AppVersionProvider.class.getPackage();
		if (appPackage != null) {
			String implementationVersion = appPackage.getImplementationVersion();
			if (implementationVersion != null && !implementationVersion.isBlank()) {
				return implementationVersion.trim();
			}
		}

		Properties props = new Properties();
		try (InputStream in = AppVersionProvider.class.getResourceAsStream(VERSION_RESOURCE)) {
			if (in != null) {
				props.load(in);
				String resourceVersion = props.getProperty(VERSION_KEY);
				if (resourceVersion != null && !resourceVersion.isBlank()) {
					return resourceVersion.trim();
				}
			}
		} catch (IOException ex) {
			log("Failed to read " + VERSION_RESOURCE + ": " + stackTrace(ex));
		}

		return DEFAULT_VERSION;
	}

	private static void log(String message) {
		System.out.println("[Updater] " + message);
	}

	private static String stackTrace(Throwable error) {
		StringWriter buffer = new StringWriter();
		error.printStackTrace(new PrintWriter(buffer));
		return buffer.toString();
	}
}
