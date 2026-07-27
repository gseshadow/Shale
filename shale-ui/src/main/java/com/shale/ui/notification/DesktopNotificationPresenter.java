package com.shale.ui.notification;

/** A native boundary which structurally cannot receive a durable notification DTO. */
@FunctionalInterface
public interface DesktopNotificationPresenter {
	PresentationResult present(NativeNotificationPresentation presentation);
}
