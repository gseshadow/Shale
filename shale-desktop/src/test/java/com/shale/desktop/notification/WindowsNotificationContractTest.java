package com.shale.desktop.notification;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.ui.notification.AppNotification;
import com.shale.ui.notification.DesktopNotificationPresenter;
import org.junit.jupiter.api.Test;

class WindowsNotificationContractTest {
	@Test void identityIsStable() { assertEquals("com.shale.desktop.Shale", WindowsNotificationIdentity.APP_USER_MODEL_ID); }
	@Test void presenterBoundaryDoesNotAcceptDurableNotification() {
		for (var method : DesktopNotificationPresenter.class.getMethods())
			for (Class<?> parameter : method.getParameterTypes()) assertNotEquals(AppNotification.class, parameter);
	}
}
