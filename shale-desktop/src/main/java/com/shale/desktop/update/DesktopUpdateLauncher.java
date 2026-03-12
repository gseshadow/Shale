package com.shale.desktop.update;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DesktopUpdateLauncher {

	private DesktopUpdateLauncher() {
	}

	public static void launchUpdater(String currentVersion) {
		try {
			Path installDir = DesktopInstallLocator.detectInstallDir();
			Path updaterExe = installDir.resolve("app").resolve("updater").resolve("ShaleUpdater.exe");

			System.out.println("Detected install dir: " + installDir);
			System.out.println("Updater path: " + updaterExe);

			if (!Files.exists(updaterExe)) {
				throw new IllegalStateException("Updater not found: " + updaterExe);
			}

			new ProcessBuilder(
					updaterExe.toString(),
					"--currentVersion", currentVersion,
					"--installDir", installDir.toString())
					.inheritIO()
					.start();

			System.out.println("Updater launched.");
		} catch (Exception ex) {
			throw new RuntimeException("Failed to launch updater", ex);
		}
	}
}