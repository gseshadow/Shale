package com.shale.desktop.update;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shale.core.platform.AppPaths;
import com.shale.core.update.UpdateAttempt;
import com.shale.core.update.UpdateAttemptState;
import com.shale.core.update.UpdateAttemptStore;
import com.shale.core.update.UpdateFailureCode;
import com.shale.core.update.UpdateExecutionLock;
import com.shale.updater.UpdateManifest;
import com.shale.updater.UpdateService;
import com.shale.updater.platform.Platform;
import com.shale.ui.services.AppVersionProvider;
import com.shale.ui.services.UiUpdateLauncher;

public final class DesktopUiUpdateLauncher implements UiUpdateLauncher {

	private static final Logger log = LoggerFactory.getLogger(DesktopUiUpdateLauncher.class);

	@FunctionalInterface
	interface UpdaterLauncher {
		void launch(String currentVersion, UUID attemptId, java.nio.file.Path attemptDirectory);
	}

	@FunctionalInterface
	interface AppShutdownHandler {
		void shutdown();
	}

	private static final String MANIFEST_URL = "https://shalestorage.z13.web.core.windows.net/shale-stable.json";

	private final UpdateService updateService;
	private final String manifestUrl;
	private final UpdaterLauncher updaterLauncher;
	private final AppShutdownHandler appShutdownHandler;

	public DesktopUiUpdateLauncher() {
		this(new UpdateService(), MANIFEST_URL, DesktopUpdateLauncher::launchUpdater, javafx.application.Platform::exit);
	}

	DesktopUiUpdateLauncher(UpdateService updateService, String manifestUrl) {
		this(updateService, manifestUrl, DesktopUpdateLauncher::launchUpdater, javafx.application.Platform::exit);
	}

	DesktopUiUpdateLauncher(UpdateService updateService, String manifestUrl, UpdaterLauncher updaterLauncher) {
		this(updateService, manifestUrl, updaterLauncher, javafx.application.Platform::exit);
	}

	DesktopUiUpdateLauncher(
			UpdateService updateService,
			String manifestUrl,
			UpdaterLauncher updaterLauncher,
			AppShutdownHandler appShutdownHandler) {
		this.updateService = Objects.requireNonNull(updateService);
		this.manifestUrl = Objects.requireNonNull(manifestUrl);
		this.updaterLauncher = Objects.requireNonNull(updaterLauncher);
		this.appShutdownHandler = Objects.requireNonNull(appShutdownHandler);
	}

	@Override
	public UiUpdateLauncher.UpdateCheckResult checkForUpdate() {
		// Detection must stay cross-platform: macOS should still fetch/parse/compare here.
		// Platform-specific restrictions belong in launchUpdater()/installer execution, not detection.
		Platform platform = Platform.detect();
		String currentVersion = AppVersionProvider.currentVersion();
		log.debug("Updater detection entry: platform={}", platform);
		log.debug("Updater current version: {}", currentVersion);
		log.info("Updater manifest endpoint configured");

		try {
			UpdateManifest manifest = updateService.fetchManifest(manifestUrl);
			String remoteVersion = manifest == null ? null : manifest.getVersion();
			String zipUrl = manifest == null ? null : manifest.getZipUrl(platform);
			String installerUrl = manifest == null ? null : manifest.getInstallerUrl(platform);
			String sha256 = manifest == null ? null : manifest.getSha256(platform);
			int comparison = updateService.compareVersions(currentVersion, manifest);
			boolean versionUpdateAvailable = comparison > 0;
			boolean macAssetAvailable = platform != Platform.MAC || !isBlank(zipUrl);
			boolean updateAvailable = versionUpdateAvailable && macAssetAvailable;
			boolean mandatory = updateAvailable && manifest != null && manifest.isMandatory();

			log.debug("Updater manifest fetch result: {}", manifest == null ? "manifest=<null>" : "manifest=ok");
			log.info("Updater parsed remote version: {}", printable(remoteVersion));
			log.debug("Updater comparison result: remoteIsNewer={} compare={}", versionUpdateAvailable, comparison);
			log.debug("Updater parsed {} asset: zipUrlConfigured={} installerUrlConfigured={} sha256Configured={}",
					platform, !isBlank(zipUrl), !isBlank(installerUrl), !isBlank(sha256));
			if (platform == Platform.MAC) {
				log.debug("Updater macOS asset selection result: macZipConfigured={} available={}", !isBlank(zipUrl), macAssetAvailable);
			}
			log.info("Updater decision: updateAvailable={} mandatory={}", updateAvailable, mandatory);

			return new UiUpdateLauncher.UpdateCheckResult(updateAvailable, mandatory);
		} catch (IOException | InterruptedException | RuntimeException ex) {
			log.warn("Update check failed", ex);
			throw new RuntimeException("Failed to check for updates", ex);
		}
	}

	@Override
	public void launchUpdater() {
		String currentVersion = AppVersionProvider.currentVersion();
		java.nio.file.Path executionLockPath = executionLockPath();
		final UpdateExecutionLock handoffLock;
		try {
			handoffLock = UpdateExecutionLock.tryAcquire(executionLockPath)
					.orElseThrow(() -> new IllegalStateException("A Shale update is already in progress."));
		} catch (IOException ex) {
			throw new IllegalStateException("Update coordination is unavailable; try again later.", ex);
		}
		try (handoffLock) {
		UUID attemptId = UUID.randomUUID();
		UpdateAttemptStore attempts = new UpdateAttemptStore(attemptDirectory());
		try {
			attempts.create(UpdateAttempt.start(attemptId, currentVersion, null, Instant.now()));
		} catch (IOException ex) {
			log.warn("Could not persist local update attempt; updater handoff will continue", ex);
		}
		log.debug("Updater launch entry");
		log.debug("Updater selected platform: {}", AppPaths.platform());
		log.debug("Updater current version for launch: {}", currentVersion);

		try {
			updaterLauncher.launch(currentVersion, attemptId, attempts.directory());
			try { attempts.transition(attemptId, UpdateAttemptState.UPDATER_LAUNCHED, null, null, null); }
			catch (IOException ex) { log.warn("Could not record updater launch outcome", ex); }
			log.info("Updater launch handoff reported success");
			if (AppPaths.isMac()) {
				log.info("macOS updater handoff succeeded; app self-shutdown initiated");
				appShutdownHandler.shutdown();
			}
		} catch (RuntimeException ex) {
			try { attempts.transition(attemptId, UpdateAttemptState.FAILED, UpdateFailureCode.UPDATER_LAUNCH_FAILED, null, null); }
			catch (IOException recordingFailure) { log.warn("Could not record updater launch failure", recordingFailure); }
			log.error("Updater launch failure", ex);
			throw ex;
		}
		} catch (IOException ex) {
			throw new IllegalStateException("Update coordination could not be released safely.", ex);
		}
	}

	public static java.nio.file.Path executionLockPath() {
		String override = System.getProperty("SHALE_UPDATE_EXECUTION_LOCK");
		return override == null || override.isBlank()
				? UpdateExecutionLock.path(AppPaths.appSupportDir("Shale")) : java.nio.file.Path.of(override);
	}

	public static java.nio.file.Path attemptDirectory() {
		String override = System.getProperty("SHALE_UPDATE_ATTEMPT_DIR");
		return override == null || override.isBlank()
				? AppPaths.appSupportDir("Shale").resolve("update-attempts") : java.nio.file.Path.of(override);
	}

	private static String printable(String value) {
		if (value == null) {
			return "<null>";
		}
		if (value.isBlank()) {
			return "<blank>";
		}
		return value;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
