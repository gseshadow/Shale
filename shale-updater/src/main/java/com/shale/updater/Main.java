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

			System.out.println("Current version: " + currentVersion);
			System.out.println("Latest version:  " + manifest.getVersion());

			if (service.isUpdateAvailable(currentVersion, manifest)) {

				System.out.println("Update available.");
				System.out.println("Zip URL: " + manifest.getZipUrl());
				System.out.println("Installer URL: " + manifest.getInstallerUrl());
				System.out.println("Sha256: " + manifest.getSha256());
				System.out.println("Published At: " + manifest.getPublishedAt());

				Path stagingDir = null;

				if (manifest.getZipUrl() != null && !manifest.getZipUrl().isBlank()) {
					System.out.println("Downloading update zip...");

					DownloadService downloader = new DownloadService();
					Path downloaded = downloader.downloadToTemp(
							manifest.getZipUrl(),
							"ShaleUpdate.zip",
							manifest.getSha256()
					);
//					Path downloaded = downloader.downloadToTemp(
//							manifest.getZipUrl(),
//							"ShaleApp-" + manifest.getVersion() + ".zip");

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

				platformSupport.stopRunningApp();

				InstallService installService = new InstallService();
				Path backupDir = installService.backupInstallDir(installDir);
				System.out.println("Backup created at: " + backupDir);

				installService.applyStagedUpdate(stagingDir, installDir);
				System.out.println("Update copied into install dir.");

				platformSupport.restartApp(installDir);

			} else {
				System.out.println("Already up to date.");
			}
		} catch (Exception ex) {
			System.out.println("Update check failed: " + ex.getMessage());
			ex.printStackTrace();
		}
	}
}