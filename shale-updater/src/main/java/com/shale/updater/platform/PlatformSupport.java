package com.shale.updater.platform;

import java.nio.file.Path;

public interface PlatformSupport {

	Platform platform();

	void stopRunningApp(Path installDir) throws Exception;

	void restartApp(Path installDir) throws Exception;

	default void restartApp(Path installDir, String expectedVersion) throws Exception {
		restartApp(installDir);
	}

	String appExecutableName();

	default Path resolveStagedInstallDir(Path stagingDir) throws Exception {
		return stagingDir;
	}

	default String updateArchiveFileName(String version) {
		return "ShaleUpdate-" + platform().name().toLowerCase() + "-" + version + ".zip";
	}

	default boolean replacesInstallDir() {
		return false;
	}

	default Path appExecutablePath(Path installDir) {
		return installDir.resolve(appExecutableName());
	}

	static PlatformSupport create() {
		Platform platform = Platform.detect();

		return switch (platform) {
			case WINDOWS -> new WindowsPlatformSupport();
			case MAC -> new MacPlatformSupport();
			default -> new UnsupportedPlatformSupport();
		};
	}
}
