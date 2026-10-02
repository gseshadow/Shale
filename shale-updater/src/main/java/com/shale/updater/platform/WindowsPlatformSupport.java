package com.shale.updater.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import com.shale.core.update.UpdateInvocationMode;

public final class WindowsPlatformSupport implements PlatformSupport {

	@Override
	public Platform platform() {
		return Platform.WINDOWS;
	}

	@Override
	public void stopRunningApp(Path installDir) throws Exception {
		Process process = new ProcessBuilder(
				"taskkill", "/IM", "Shale.exe", "/F")
				.inheritIO()
				.start();

		int exit = process.waitFor();
		System.out.println("taskkill exit code: " + exit);
	}

	@Override
	public boolean stopRunningApp(Path installDir, UpdateInvocationMode mode) throws Exception {
		if (mode == UpdateInvocationMode.MANUAL) {
			stopRunningApp(installDir);
			return true;
		}
		Process process = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq Shale.exe")
				.redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int exit = process.waitFor();
		return exit == 0 && !output.toLowerCase(java.util.Locale.ROOT).contains("shale.exe");
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
