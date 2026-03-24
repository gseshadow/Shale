package com.shale.updater.platform;

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
		restartApp(installDir, null);
	}

	@Override
	public void restartApp(Path installDir, String expectedVersion) throws Exception {
		Path targetApp = relaunchTargetPath();
		Path appBinary = targetApp.resolve("Contents").resolve("MacOS").resolve("Shale");

		log("Attempting direct macOS relaunch via app binary");
		log("macOS relaunch target app: " + targetApp);
		log("macOS relaunch target version: " + expectedVersion);
		log("macOS relaunch binary path: " + appBinary);
		log("macOS relaunch target app exists: " + Files.exists(targetApp));
		log("macOS relaunch target app is directory: " + Files.isDirectory(targetApp));
		log("macOS relaunch binary exists: " + Files.exists(appBinary));
		log("macOS relaunch binary executable: " + Files.isExecutable(appBinary));

		if (!Files.exists(appBinary)) {
			throw new IOException("macOS relaunch binary missing: " + appBinary);
		}
		if (!Files.isExecutable(appBinary)) {
			throw new IOException("macOS relaunch binary is not executable: " + appBinary);
		}

		// Give the filesystem a brief moment to settle after replacement.
		Thread.sleep(1000L);

		ProcessBuilder builder = new ProcessBuilder(appBinary.toString()).redirectErrorStream(true);
		if (targetApp.getParent() != null) {
			builder.directory(targetApp.getParent().toFile());
		}

		log("macOS relaunch command: " + builder.command());
		log("macOS relaunch working directory: " + builder.directory());

		Process process = builder.start();

		log("macOS relaunch process started");
		log("macOS relaunch process pid: " + process.pid());

		Thread.sleep(1000L);
		log("macOS relaunch process alive after 1s: " + process.isAlive());
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

	Path relaunchTargetPath() {
		return Path.of("/Applications").resolve(appExecutableName());
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