package com.shale.updater;

import java.nio.file.Path;
import java.io.IOException;
import java.util.UUID;

import com.shale.core.update.UpdateAttemptState;
import com.shale.core.update.UpdateAttemptStore;
import com.shale.core.update.UpdateFailureCode;
import com.shale.core.update.UpdateExecutionLock;
import com.shale.core.update.UpdateInvocationMode;
import com.shale.core.platform.AppPaths;

import com.shale.updater.platform.PlatformSupport;

public class Main {

	public static void main(String[] args) {
		System.out.println("Shale Updater starting...");

		String currentVersion = "0.0.0";
		String installDirArg = null;
		String attemptIdArg = null;
		String attemptDirArg = null;
		String executionLockArg = null;
		UpdateInvocationMode invocationMode = UpdateInvocationMode.MANUAL;
		boolean lockHandoff = false;

		for (int i = 0; i < args.length; i++) {
			if ("--currentVersion".equals(args[i]) && i + 1 < args.length) {
				currentVersion = args[i + 1];
			}
			if ("--installDir".equals(args[i]) && i + 1 < args.length) {
				installDirArg = args[i + 1];
			}
			if ("--attemptId".equals(args[i]) && i + 1 < args.length) attemptIdArg = args[i + 1];
			if ("--attemptDir".equals(args[i]) && i + 1 < args.length) attemptDirArg = args[i + 1];
			if ("--executionLock".equals(args[i]) && i + 1 < args.length) executionLockArg = args[i + 1];
			if ("--invocationMode".equals(args[i]) && i + 1 < args.length) invocationMode = UpdateInvocationMode.fromArgument(args[i + 1]);
			if ("--lockHandoff".equals(args[i]) && i + 1 < args.length) lockHandoff = Boolean.parseBoolean(args[i + 1]);
		}

		if (installDirArg == null || installDirArg.isBlank()) {
			System.out.println("Missing required argument: --installDir");
			return;
		}

		String manifestUrl = "https://shalestorage.z13.web.core.windows.net/shale-stable.json";
		AttemptReporter reporter = AttemptReporter.create(attemptIdArg, attemptDirArg);

		try (UpdateExecutionLock executionLock = acquireExecutionLock(executionLockArg, lockHandoff)) {
			if (executionLock == null) { System.out.println("UPDATE_ALREADY_RUNNING"); return; }
			reporter.state(UpdateAttemptState.UPDATER_LAUNCHED, null, null);
			PlatformSupport platformSupport = PlatformSupport.create();
			System.out.println("Detected platform: " + platformSupport.platform());

			UpdateService service = new UpdateService();
			UpdateManifest manifest;
			try { manifest = service.fetchManifest(manifestUrl); }
			catch (Exception ex) { reporter.failed(UpdateFailureCode.MANIFEST_UNAVAILABLE); throw ex; }
			if (manifest == null || manifest.getVersion() == null || manifest.getVersion().isBlank()) {
				reporter.failed(UpdateFailureCode.MANIFEST_UNAVAILABLE);
				throw new IOException("Update manifest did not contain a version");
			}
			String zipUrl = manifest.getZipUrl(platformSupport.platform());
			String installerUrl = manifest.getInstallerUrl(platformSupport.platform());
			String sha256 = manifest.getSha256(platformSupport.platform());

			System.out.println("Current version: " + currentVersion);
			System.out.println("Latest version:  " + manifest.getVersion());

			if (service.isUpdateAvailable(currentVersion, manifest)) {
				reporter.state(UpdateAttemptState.UPDATER_LAUNCHED, manifest.getVersion(), null);

				System.out.println("Update available.");
				System.out.println("Zip URL: " + zipUrl);
				System.out.println("Installer URL: " + installerUrl);
				System.out.println("Sha256: " + sha256);
				System.out.println("Published At: " + manifest.getPublishedAt());

				Path stagingDir = null;

				if (zipUrl != null && !zipUrl.isBlank()) {
					System.out.println("Downloading update zip...");

					DownloadService downloader = new DownloadService();
					Path downloaded;
					try { downloaded = downloader.downloadToTemp(
							zipUrl,
							platformSupport.updateArchiveFileName(manifest.getVersion()),
							sha256
					); } catch (DownloadService.PackageValidationException ex) {
						reporter.failed(UpdateFailureCode.PACKAGE_VALIDATION_FAILED); throw ex;
					} catch (Exception ex) { reporter.failed(UpdateFailureCode.PACKAGE_DOWNLOAD_FAILED); throw ex; }

					System.out.println("Downloaded to: " + downloaded);

					ExtractService extractor = new ExtractService();
					try { stagingDir = extractor.extractToStaging(downloaded, manifest.getVersion()); }
					catch (Exception ex) { reporter.failed(UpdateFailureCode.PACKAGE_EXTRACTION_FAILED); throw ex; }

					System.out.println("Extracted to: " + stagingDir);
				}

				if (stagingDir == null) {
					System.out.println("No staging directory created. Aborting update.");
					reporter.failed(UpdateFailureCode.PACKAGE_UNAVAILABLE);
					return;
				}

				Path installDir = Path.of(installDirArg);
				Path stagedInstallDir = platformSupport.resolveStagedInstallDir(stagingDir);
				System.out.println("Resolved staged install dir: " + stagedInstallDir);

				try {
					if (!platformSupport.stopRunningApp(installDir, invocationMode)) {
						System.out.println("Unattended update deferred: cooperative application exit was not established.");
						return;
					}
					armRelaunchHelperOrContinue(platformSupport, installDir, manifest.getVersion());
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
				} catch (Exception ex) { reporter.failed(UpdateFailureCode.INSTALL_APPLY_FAILED); throw ex; }

				System.out.println("Install succeeded at: " + installDir);
				reporter.state(UpdateAttemptState.INSTALL_APPLIED, manifest.getVersion(), null);
				restartOrLogManualReopen(platformSupport, installDir, manifest.getVersion());

			} else {
				System.out.println("Already up to date.");
			}
		} catch (Exception ex) {
			System.out.println("Update check failed: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	private static UpdateExecutionLock acquireExecutionLock(String value, boolean handoff) throws IOException {
		Path path = value == null || value.isBlank()
				? UpdateExecutionLock.path(AppPaths.appSupportDir("Shale")) : Path.of(value);
		long deadline = System.nanoTime() + (handoff ? 5_000_000_000L : 0L);
		do {
			var acquired = UpdateExecutionLock.tryAcquire(path);
			if (acquired.isPresent()) return acquired.get();
			if (!handoff) return null;
			try { Thread.sleep(25); }
			catch (InterruptedException ex) { Thread.currentThread().interrupt(); return null; }
		} while (System.nanoTime() < deadline);
		return null;
	}

	private record AttemptReporter(UpdateAttemptStore store, UUID id) {
		static AttemptReporter create(String id, String directory) {
			try { return id == null || directory == null ? new AttemptReporter(null, null)
					: new AttemptReporter(new UpdateAttemptStore(Path.of(directory)), UUID.fromString(id)); }
			catch (RuntimeException ex) { return new AttemptReporter(null, null); }
		}
		void state(UpdateAttemptState state, String target, String actual) {
			if (store == null) return;
			try { store.transition(id, state, null, target, actual); }
			catch (IOException ex) { System.out.println("Update attempt outcome could not be recorded: " + ex.getClass().getSimpleName()); }
		}
		void failed(UpdateFailureCode code) {
			if (store == null) return;
			try { store.transition(id, UpdateAttemptState.FAILED, code, null, null); }
			catch (IOException ex) { System.out.println("Update attempt failure could not be recorded: " + ex.getClass().getSimpleName()); }
		}
	}

	static void armRelaunchHelperOrContinue(PlatformSupport platformSupport, Path installDir, String expectedVersion) {
		try {
			platformSupport.armRelaunchHelper(installDir, expectedVersion);
		} catch (Exception ex) {
			System.out.println("Failed to arm relaunch helper; continuing install: " + ex.getMessage());
			ex.printStackTrace(System.out);
		}
	}

	static boolean restartOrLogManualReopen(PlatformSupport platformSupport, Path installDir, String expectedVersion) {
		String displayInstallDir = installDir.toString().replace('\\', '/');

		try {
			platformSupport.restartApp(installDir, expectedVersion);
			System.out.println("Relaunch succeeded for: " + displayInstallDir);
			return true;
		} catch (Exception ex) {
			System.out.println("Install succeeded, but relaunch failed: " + ex.getMessage());
			ex.printStackTrace(System.out);
			System.out.println("Shale was updated successfully. Please reopen the app manually from: " + displayInstallDir);
			return true;
		}
	}
}
