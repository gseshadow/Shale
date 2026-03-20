package com.shale.updater.platform;

import java.nio.file.Path;

public final class UnsupportedPlatformSupport implements PlatformSupport {

	@Override
	public Platform platform() {
		return Platform.UNSUPPORTED;
	}

	@Override
	public void stopRunningApp(Path installDir) {
		throw new UnsupportedOperationException("Unsupported platform: " + System.getProperty("os.name"));
	}

	@Override
	public void restartApp(Path installDir) {
		throw new UnsupportedOperationException("Unsupported platform: " + System.getProperty("os.name"));
	}

	@Override
	public String appExecutableName() {
		return "Shale";
	}
}
