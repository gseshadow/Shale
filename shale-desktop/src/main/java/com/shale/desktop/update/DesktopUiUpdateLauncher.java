package com.shale.desktop.update;

import java.io.IOException;

import com.shale.core.platform.AppPaths;
import com.shale.desktop.DesktopConfig;
import com.shale.updater.UpdateManifest;
import com.shale.updater.UpdateService;
import com.shale.ui.services.UiUpdateLauncher;

public final class DesktopUiUpdateLauncher implements UiUpdateLauncher {

	private static final String MANIFEST_URL = "https://shalestorage.z13.web.core.windows.net/shale-stable.json";

	private final UpdateService updateService = new UpdateService();

	@Override
	public UiUpdateLauncher.UpdateCheckResult checkForUpdate() {
		if (!AppPaths.isWindows()) {
			// Keep macOS launchable until the updater/install replacement flow exists there.
			return new UiUpdateLauncher.UpdateCheckResult(false, false);
		}

		try {
			UpdateManifest manifest = updateService.fetchManifest(MANIFEST_URL);
			boolean updateAvailable = updateService.isUpdateAvailable(DesktopConfig.appVersion(), manifest);
			boolean mandatory = updateAvailable && manifest != null && manifest.isMandatory();
			return new UiUpdateLauncher.UpdateCheckResult(updateAvailable, mandatory);
		} catch (IOException | InterruptedException ex) {
			throw new RuntimeException("Failed to check for updates", ex);
		}
	}

	@Override
	public void launchUpdater() {
		if (!AppPaths.isWindows()) {
			throw new RuntimeException("In-app updates are not available on macOS yet. Continue using the installed app normally.");
		}
		DesktopUpdateLauncher.launchUpdater(DesktopConfig.appVersion());
	}
}
