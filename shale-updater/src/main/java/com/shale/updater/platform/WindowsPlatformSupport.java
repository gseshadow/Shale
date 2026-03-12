package com.shale.updater.platform;

import java.nio.file.Files;
import java.nio.file.Path;

public final class WindowsPlatformSupport implements PlatformSupport {

	@Override
	public Platform platform() {
		return Platform.WINDOWS;
	}

	@Override
	public void stopRunningApp() throws Exception {
		Process process = new ProcessBuilder(
				"taskkill", "/IM", "Shale.exe", "/F")
				.inheritIO()
				.start();

		int exit = process.waitFor();
		System.out.println("taskkill exit code: " + exit);
	}

	@Override
	public void restartApp(Path installDir) throws Exception {
		Path appExe = appExecutablePath(installDir);

		if (!Files.exists(appExe)) {
			System.out.println("Shale.exe not found after update at: " + appExe);
			return;
		}

		new ProcessBuilder(appExe.toString())
				.inheritIO()
				.start();

		System.out.println("Relaunched Shale from: " + appExe);
	}

	@Override
	public String appExecutableName() {
		return "Shale.exe";
	}
}