package com.shale.desktop.notification;

import com.shale.ui.notification.DesktopNotificationPresenter;
import com.shale.ui.notification.NoOpDesktopNotificationPresenter;
import java.util.function.Function;

public final class DesktopNotificationPresenterFactory {
	private DesktopNotificationPresenterFactory() { }
	public static DesktopNotificationPresenter create() {
		return create(WindowsNotificationRuntime.detect(), JniWindowsNotificationBridge::new);
	}
	static DesktopNotificationPresenter create(WindowsNotificationRuntime runtime,
			Function<java.nio.file.Path, WindowsNotificationBridge> bridgeFactory) {
		if (!runtime.eligible()) return new NoOpDesktopNotificationPresenter();
		try { return new WindowsDesktopNotificationPresenter(bridgeFactory.apply(runtime.library())); }
		catch (Throwable unavailable) { return new NoOpDesktopNotificationPresenter(); }
	}
}
