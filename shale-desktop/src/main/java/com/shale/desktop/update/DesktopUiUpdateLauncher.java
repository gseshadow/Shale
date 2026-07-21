package com.shale.desktop.update;

import java.io.IOException;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shale.core.platform.AppPaths;
import com.shale.updater.UpdateManifest;
import com.shale.updater.UpdateService;
import com.shale.updater.platform.Platform;
import com.shale.ui.services.AppVersionProvider;
import com.shale.ui.services.UiUpdateLauncher;

public final class DesktopUiUpdateLauncher implements UiUpdateLauncher {

	private static final Logger log = LoggerFactory.getLogger(DesktopUiUpdateLauncher.class);

	@FunctionalInterface
	interface UpdaterLauncher {
		void launch(String currentVersion);
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
		log.debug("Updater launch entry");
		log.debug("Updater selected platform: {}", AppPaths.platform());
		log.debug("Updater current version for launch: {}", currentVersion);

		try {
			updaterLauncher.launch(currentVersion);
			log.info("Updater launch handoff reported success");
			if (AppPaths.isMac()) {
				log.info("macOS updater handoff succeeded; app self-shutdown initiated");
				appShutdownHandler.shutdown();
			}
		} catch (RuntimeException ex) {
			log.error("Updater launch failure", ex);
			throw ex;
		}
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
