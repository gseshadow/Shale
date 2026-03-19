package com.shale.core.platform;

public enum AppPlatform {
	WINDOWS,
	MAC,
	OTHER;

	public static AppPlatform detect() {
		String osName = System.getProperty("os.name", "").toLowerCase();

		if (osName.contains("win")) {
			return WINDOWS;
		}
		if (osName.contains("mac")) {
			return MAC;
		}
		return OTHER;
	}
}
