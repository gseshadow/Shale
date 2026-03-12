package com.shale.updater.platform;

public enum Platform {
	WINDOWS,
	MAC,
	UNSUPPORTED;

	public static Platform detect() {
		String os = System.getProperty("os.name", "").toLowerCase();

		if (os.contains("win")) {
			return WINDOWS;
		}
		if (os.contains("mac")) {
			return MAC;
		}
		return UNSUPPORTED;
	}
}