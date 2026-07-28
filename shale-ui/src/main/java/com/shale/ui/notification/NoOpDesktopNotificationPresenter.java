package com.shale.ui.notification;

import java.util.Objects;

/** Production presenter until a separately reviewed platform adapter is introduced. */
public final class NoOpDesktopNotificationPresenter implements DesktopNotificationPresenter {
	@Override
	public PresentationResult present(NativeNotificationPresentation presentation) {
		Objects.requireNonNull(presentation, "presentation");
		return PresentationResult.UNSUPPORTED;
	}
}
