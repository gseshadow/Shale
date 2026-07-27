package com.shale.ui.notification;

/** A native boundary which structurally cannot receive a durable notification DTO. */
@FunctionalInterface
public interface DesktopNotificationPresenter extends AutoCloseable {
	PresentationResult present(NativeNotificationPresentation presentation);
	/** Invalidates presentation work queued for the previous authenticated generation. */
	default void invalidate() { }
	@Override default void close() { }
}
