package com.shale.desktop.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

public final class DesktopUpdateLauncher {

	private DesktopUpdateLauncher() {
	}

	public static void launchUpdater(String currentVersion) {
		Path logFile = null;

		try {
			Path installDir = DesktopInstallLocator.detectInstallDir();
			logFile = installDir.resolve("update-launcher.log");

			log(logFile, "==== Launch attempt " + LocalDateTime.now() + " ====");
			log(logFile, "Current version: " + currentVersion);
			log(logFile, "Detected install dir: " + installDir);

			Path updaterExe = installDir.resolve("app").resolve("updater").resolve("ShaleUpdater.exe");
			if (!Files.exists(updaterExe)) {
				Path alt = installDir.resolve("updater").resolve("ShaleUpdater.exe");
				if (Files.exists(alt)) {
					updaterExe = alt;
				}
			}

			log(logFile, "Updater path: " + updaterExe);
			log(logFile, "Updater exists: " + Files.exists(updaterExe));

			if (!Files.exists(updaterExe)) {
				throw new IllegalStateException("Updater not found: " + updaterExe);
			}

			Path updaterLog = installDir.resolve("updater-output.log");

			ProcessBuilder pb = new ProcessBuilder(
					updaterExe.toString(),
					"--currentVersion", currentVersion,
					"--installDir", installDir.toString());

			pb.redirectErrorStream(true);
			pb.redirectOutput(updaterLog.toFile());

			log(logFile, "Command: " + pb.command());
			Process process = pb.start();
			log(logFile, "Updater launched. PID available: " + process.pid());

		} catch (Exception ex) {
			if (logFile != null) {
				try {
					log(logFile, "Launch failed: " + ex);
				} catch (Exception ignored) {
				}
			}
			ex.printStackTrace();
			throw new RuntimeException("Failed to launch updater", ex);
		}
	}

	private static void log(Path logFile, String line) throws IOException {
		Files.writeString(
				logFile,
				line + System.lineSeparator(),
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
	}
}