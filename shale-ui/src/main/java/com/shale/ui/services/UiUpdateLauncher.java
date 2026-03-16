package com.shale.ui.services;

public interface UiUpdateLauncher {
	record UpdateCheckResult(boolean updateAvailable, boolean mandatory) {
	}

	UpdateCheckResult checkForUpdate();

	void launchUpdater();
}
