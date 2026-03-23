package com.shale.updater;

import java.nio.file.Path;

import com.shale.updater.platform.PlatformSupport;

public class Main {

	public static void main(String[] args) {
		System.out.println("Shale Updater starting...");

		String currentVersion = "0.0.0";
		String installDirArg = null;

		for (int i = 0; i < args.length; i++) {
			if ("--currentVersion".equals(args[i]) && i + 1 < args.length) {
				currentVersion = args[i + 1];
			}
			if ("--installDir".equals(args[i]) && i + 1 < args.length) {
				installDirArg = args[i + 1];
			}
		}

		if (installDirArg == null || installDirArg.isBlank()) {
			System.out.println("Missing required argument: --installDir");
			return;
		}

		String manifestUrl = "https://shalestorage.z13.web.core.windows.net/shale-stable.json";

		try {
			PlatformSupport platformSupport = PlatformSupport.create();
			System.out.println("Detected platform: " + platformSupport.platform());

			UpdateService service = new UpdateService();
			UpdateManifest manifest = service.fetchManifest(manifestUrl);
			String zipUrl = manifest.getZipUrl(platformSupport.platform());
			String installerUrl = manifest.getInstallerUrl(platformSupport.platform());
			String sha256 = manifest.getSha256(platformSupport.platform());

			System.out.println("Current version: " + currentVersion);
			System.out.println("Latest version:  " + manifest.getVersion());

			if (service.isUpdateAvailable(currentVersion, manifest)) {

				System.out.println("Update available.");
				System.out.println("Zip URL: " + zipUrl);
				System.out.println("Installer URL: " + installerUrl);
				System.out.println("Sha256: " + sha256);
				System.out.println("Published At: " + manifest.getPublishedAt());

				Path stagingDir = null;

				if (zipUrl != null && !zipUrl.isBlank()) {
					System.out.println("Downloading update zip...");

					DownloadService downloader = new DownloadService();
					Path downloaded = downloader.downloadToTemp(
							zipUrl,
							platformSupport.updateArchiveFileName(manifest.getVersion()),
							sha256
					);

					System.out.println("Downloaded to: " + downloaded);

					ExtractService extractor = new ExtractService();
					stagingDir = extractor.extractToStaging(downloaded, manifest.getVersion());

					System.out.println("Extracted to: " + stagingDir);
				}

				if (stagingDir == null) {
					System.out.println("No staging directory created. Aborting update.");
					return;
				}

				Path installDir = Path.of(installDirArg);
				Path stagedInstallDir = platformSupport.resolveStagedInstallDir(stagingDir);
				System.out.println("Resolved staged install dir: " + stagedInstallDir);

				platformSupport.stopRunningApp(installDir);

				InstallService installService = new InstallService();
				Path backupDir = installService.backupInstallDir(installDir);
				System.out.println("Backup created at: " + backupDir);

				if (platformSupport.replacesInstallDir()) {
					installService.replaceInstallDir(stagedInstallDir, installDir);
					System.out.println("Install dir replaced from staged update.");
				} else {
					installService.applyStagedUpdate(stagedInstallDir, installDir);
					System.out.println("Update copied into install dir.");
				}

				System.out.println("Install succeeded at: " + installDir);
				restartOrLogManualReopen(platformSupport, installDir);

			} else {
				System.out.println("Already up to date.");
			}
		} catch (Exception ex) {
			System.out.println("Update check failed: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	static void restartOrLogManualReopen(PlatformSupport platformSupport, Path installDir) {
		try {
			platformSupport.restartApp(installDir);
			System.out.println("Relaunch succeeded for: " + installDir);
		} catch (Exception ex) {
			System.out.println("Install succeeded, but relaunch failed: " + ex.getMessage());
			ex.printStackTrace(System.out);
			System.out.println("Shale was updated successfully. Please reopen the app manually from: " + installDir);
		}
	}
}
