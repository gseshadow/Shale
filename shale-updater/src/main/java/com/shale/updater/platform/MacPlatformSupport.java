package com.shale.updater.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
		List<String> command = relaunchCommand(installDir);
		log("Attempting macOS relaunch strategy: open-command");
		log("macOS relaunch command: " + command);
		try {
			Process process = startRelaunchCommand(command);
			String output = readProcessOutput(process.getInputStream());
			int exit = process.waitFor();
			if (exit != 0) {
				throw new IOException("macOS relaunch command exited with code " + exit
						+ (output.isBlank() ? "" : ": " + output));
			}
			log("macOS relaunch command succeeded for: " + installDir);
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

	List<String> relaunchCommand(Path installDir) {
		return List.of("/usr/bin/open", "-n", installDir.toString());
	}

	Process startRelaunchCommand(List<String> command) throws IOException {
		return new ProcessBuilder(command)
				.redirectErrorStream(true)
				.start();
	}

	String readProcessOutput(InputStream inputStream) throws IOException {
		return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
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
