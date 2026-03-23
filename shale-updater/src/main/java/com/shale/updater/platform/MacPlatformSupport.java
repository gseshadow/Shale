package com.shale.updater.platform;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class MacPlatformSupport implements PlatformSupport {

	@Override
	public Platform platform() {
		return Platform.MAC;
	}

	@Override
	public void stopRunningApp(Path installDir) throws Exception {
		Path appBinary = installDir.resolve("Contents").resolve("MacOS").resolve("Shale");
		log("macOS fallback external stop attempted for: " + appBinary);
		runBestEffort(List.of("pkill", "-f", appBinary.toString()));
		runBestEffort(List.of("pkill", "-x", "Shale"));
		Thread.sleep(1000L);
	}

	@Override
	public void restartApp(Path installDir) throws Exception {
		log("Attempting macOS relaunch strategy: Desktop.open(" + installDir + ")");

		if (!isDesktopOpenSupported()) {
			IOException error = new IOException("Desktop OPEN action is not supported for macOS relaunch");
			log("macOS relaunch failed: " + error.getMessage());
			throw error;
		}

		try {
			openAppBundle(installDir);
			log("macOS relaunch request submitted for: " + installDir);
		} catch (Exception ex) {
			log("macOS relaunch failed: " + ex.getMessage());
			throw ex;
		}
	}

	@Override
	public String appExecutableName() {
		return "Shale.app";
	}

	@Override
	public Path resolveStagedInstallDir(Path stagingDir) throws Exception {
		Path directBundle = stagingDir.resolve(appExecutableName());
		if (Files.isDirectory(directBundle)) {
			return directBundle;
		}

		try (Stream<Path> children = Files.list(stagingDir)) {
			return children
					.filter(Files::isDirectory)
					.filter(path -> path.getFileName() != null)
					.filter(path -> path.getFileName().toString().endsWith(".app"))
					.findFirst()
					.orElseThrow(() -> new IOException("macOS update zip did not contain " + appExecutableName()));
		}
	}

	@Override
	public boolean replacesInstallDir() {
		return true;
	}

	boolean isDesktopOpenSupported() {
		return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
	}

	void openAppBundle(Path installDir) throws IOException {
		Desktop.getDesktop().open(installDir.toFile());
	}

	private void runBestEffort(List<String> command) {
		try {
			Process process = new ProcessBuilder(command)
					.inheritIO()
					.start();
			int exit = process.waitFor();
			System.out.println(String.join(" ", command) + " exit code: " + exit);
		} catch (Exception ex) {
			System.out.println("Ignoring macOS stop command failure for " + command + ": " + ex.getMessage());
		}
	}

	private void log(String message) {
		System.out.println(message);
	}
}
