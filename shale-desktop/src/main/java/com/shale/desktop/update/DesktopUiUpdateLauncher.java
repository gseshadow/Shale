package com.shale.desktop.update;

import java.io.IOException;

import com.shale.desktop.DesktopConfig;
import com.shale.updater.UpdateManifest;
import com.shale.updater.UpdateService;
import com.shale.ui.services.UiUpdateLauncher;

public final class DesktopUiUpdateLauncher implements UiUpdateLauncher {

	private static final String MANIFEST_URL = "https://shalestorage.z13.web.core.windows.net/shale-stable.json";

	private final UpdateService updateService = new UpdateService();

	@Override
	public UiUpdateLauncher.UpdateCheckResult checkForUpdate() {
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
		DesktopUpdateLauncher.launchUpdater(DesktopConfig.appVersion());
	}
}
