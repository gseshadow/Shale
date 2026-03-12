package com.shale.updater.platform;

import java.nio.file.Path;

public final class MacPlatformSupport implements PlatformSupport {

	@Override
	public Platform platform() {
		return Platform.MAC;
	}

	@Override
	public void stopRunningApp() {
		throw new UnsupportedOperationException("macOS updater support not implemented yet");
	}

	@Override
	public void restartApp(Path installDir) {
		throw new UnsupportedOperationException("macOS updater support not implemented yet");
	}

	@Override
	public String appExecutableName() {
		return "Shale.app"; // TODO confirm final macOS layout
	}
}