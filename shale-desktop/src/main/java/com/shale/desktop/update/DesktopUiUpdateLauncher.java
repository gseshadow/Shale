package com.shale.desktop.update;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

import com.shale.core.platform.AppPaths;
import com.shale.updater.UpdateManifest;
import com.shale.updater.UpdateService;
import com.shale.updater.platform.Platform;
import com.shale.ui.services.AppVersionProvider;
import com.shale.ui.services.UiUpdateLauncher;

public final class DesktopUiUpdateLauncher implements UiUpdateLauncher {

	@FunctionalInterface
	interface UpdaterLauncher {
		void launch(String currentVersion);
	}

	private static final String MANIFEST_URL = "https://shalestorage.z13.web.core.windows.net/shale-stable.json";

	private final UpdateService updateService;
	private final String manifestUrl;
	private final UpdaterLauncher updaterLauncher;

	public DesktopUiUpdateLauncher() {
		this(new UpdateService(), MANIFEST_URL, DesktopUpdateLauncher::launchUpdater);
	}

	DesktopUiUpdateLauncher(UpdateService updateService, String manifestUrl) {
		this(updateService, manifestUrl, DesktopUpdateLauncher::launchUpdater);
	}

	DesktopUiUpdateLauncher(UpdateService updateService, String manifestUrl, UpdaterLauncher updaterLauncher) {
		this.updateService = Objects.requireNonNull(updateService);
		this.manifestUrl = Objects.requireNonNull(manifestUrl);
		this.updaterLauncher = Objects.requireNonNull(updaterLauncher);
	}

	@Override
	public UiUpdateLauncher.UpdateCheckResult checkForUpdate() {
		// Detection must stay cross-platform: macOS should still fetch/parse/compare here.
		// Platform-specific restrictions belong in launchUpdater()/installer execution, not detection.
		Platform platform = Platform.detect();
		String currentVersion = AppVersionProvider.currentVersion();
		log("Detection entry: platform=" + platform);
		log("Current version: " + currentVersion);
		log("Manifest URL: " + manifestUrl);

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

			log("Manifest fetch result: " + (manifest == null ? "manifest=<null>" : "manifest=ok"));
			log("Parsed remote version: " + printable(remoteVersion));
			log("Comparison result: remoteIsNewer=" + versionUpdateAvailable + " (compare=" + comparison + ")");
			log("Parsed " + platform + " asset: zipUrl=" + printable(zipUrl)
					+ ", installerUrl=" + printable(installerUrl)
					+ ", sha256=" + printable(sha256));
			if (platform == Platform.MAC) {
				log("macOS asset selection result: macZipUrl=" + printable(zipUrl) + ", available=" + macAssetAvailable);
			}
			log("Final updateAvailable decision: updateAvailable=" + updateAvailable + ", mandatory=" + mandatory);

			return new UiUpdateLauncher.UpdateCheckResult(updateAvailable, mandatory);
		} catch (IOException | InterruptedException | RuntimeException ex) {
			log("Update check exception: " + stackTrace(ex));
			throw new RuntimeException("Failed to check for updates", ex);
		}
	}

	@Override
	public void launchUpdater() {
		String currentVersion = AppVersionProvider.currentVersion();
		log("launchUpdater entry");
		log("Selected platform: " + AppPaths.platform());
		log("Current version for updater launch: " + currentVersion);

		try {
			updaterLauncher.launch(currentVersion);
			log("Updater launch handoff reported success");
		} catch (RuntimeException ex) {
			log("Updater launch failure: " + stackTrace(ex));
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

	private static void log(String message) {
		System.out.println("[Updater] " + message);
	}

	private static String stackTrace(Throwable error) {
		StringWriter buffer = new StringWriter();
		error.printStackTrace(new PrintWriter(buffer));
		return buffer.toString();
	}
}
