package com.shale.updater.platform;

import java.nio.file.Path;

public interface PlatformSupport {

	Platform platform();

	void stopRunningApp() throws Exception;

	void restartApp(Path installDir) throws Exception;

	String appExecutableName();

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