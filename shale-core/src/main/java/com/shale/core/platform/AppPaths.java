package com.shale.core.platform;

import java.nio.file.Path;

public final class AppPaths {

	private AppPaths() {
	}

	public static AppPlatform platform() {
		return AppPlatform.detect();
	}

	public static boolean isWindows() {
		return platform() == AppPlatform.WINDOWS;
	}

	public static boolean isMac() {
		return platform() == AppPlatform.MAC;
	}

	public static Path appSupportDir(String appName) {
		if (isWindows()) {
			String localAppData = System.getenv("LOCALAPPDATA");
			if (localAppData != null && !localAppData.isBlank()) {
				return Path.of(localAppData, appName);
			}
			return Path.of(System.getProperty("user.home"), "AppData", "Local", appName);
		}

		if (isMac()) {
			return Path.of(System.getProperty("user.home"), "Library", "Application Support", appName);
		}

		return Path.of(System.getProperty("user.home"), "." + appName.toLowerCase());
	}

	public static Path appLogFile(String appName, String fileName) {
		return appSupportDir(appName).resolve(fileName);
	}
}
