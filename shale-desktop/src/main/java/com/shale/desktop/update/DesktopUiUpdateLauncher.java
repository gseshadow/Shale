package com.shale.desktop.update;

import com.shale.desktop.DesktopConfig;
import com.shale.ui.services.UiUpdateLauncher;

public final class DesktopUiUpdateLauncher implements UiUpdateLauncher {

	@Override
	public void launchUpdater() {
		DesktopUpdateLauncher.launchUpdater(DesktopConfig.appVersion());
	}
}