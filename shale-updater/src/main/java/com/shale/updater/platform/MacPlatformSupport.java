package com.shale.updater.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class MacPlatformSupport implements PlatformSupport {

	@Override
	public Platform platform() {
		return Platform.MAC;
	}

	@Override
	public void stopRunningApp(Path installDir) throws Exception {
		Path appBinary = installDir.resolve("Contents").resolve("MacOS").resolve("Shale");
		runBestEffort(List.of("pkill", "-f", appBinary.toString()));
		runBestEffort(List.of("pkill", "-x", "Shale"));
		Thread.sleep(1000L);
	}

	@Override
	public void restartApp(Path installDir) throws Exception {
		new ProcessBuilder("open", installDir.toString())
				.inheritIO()
				.start();

		System.out.println("Relaunched Shale from: " + installDir);
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
}
