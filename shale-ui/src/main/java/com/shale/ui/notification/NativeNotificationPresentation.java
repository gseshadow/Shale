package com.shale.ui.notification;

import java.util.Objects;

/** Privacy-restricted data allowed to cross into a future native adapter. */
public record NativeNotificationPresentation(long notificationId, String heading, String message, String categoryCode) {
	public NativeNotificationPresentation {
		if (notificationId <= 0) throw new IllegalArgumentException("notificationId must be positive");
		heading = Objects.requireNonNull(heading, "heading");
		message = Objects.requireNonNull(message, "message");
		categoryCode = Objects.requireNonNull(categoryCode, "categoryCode");
	}
}
