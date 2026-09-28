package com.shale.core.platform;

import java.nio.file.Path;
import java.util.Map;

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

	/**
	 * Resolves application data shared by every OS user, rather than the existing
	 * per-user support directory. Callers must handle an unavailable Windows
	 * ProgramData location explicitly.
	 */
	public static Path machineDataDir(String appName) {
		return machineDataDir(appName, platform(), System.getenv());
	}

	public static Path machineDataDir(String appName, AppPlatform platform, Map<String, String> environment) {
		if (appName == null || appName.isBlank()) {
			throw new IllegalArgumentException("appName must not be blank");
		}
		if (platform == null) {
			throw new NullPointerException("platform");
		}
		if (environment == null) {
			throw new NullPointerException("environment");
		}

		return switch (platform) {
			case WINDOWS -> {
				String programData = environment.get("ProgramData");
				if (programData == null || programData.isBlank()) {
					programData = environment.get("PROGRAMDATA");
				}
				if (programData == null || programData.isBlank()) {
					throw new IllegalStateException("Windows ProgramData is unavailable");
				}
				yield Path.of(programData.trim(), appName);
			}
			case MAC -> Path.of("/Library", "Application Support", appName);
			case OTHER -> throw new UnsupportedOperationException("Machine data storage is unsupported on this platform");
		};
	}
}
