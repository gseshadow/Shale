package com.shale.ui.notification;

import com.shale.core.service.NotificationServicePort.NotificationSummary;
import java.util.Locale;
import java.util.Objects;

/** Converts internal records to generic text without copying durable visible content. */
public final class NotificationPrivacyProjector {
	public NativeNotificationPresentation project(NotificationSummary source) {
		Objects.requireNonNull(source, "source");
		String category = "MATERIALREQUEST".equalsIgnoreCase(source.entityType())
				? "MATERIAL_REQUEST" : allowlistedCategory(source.category());
		String message = switch (category) {
			case "TASK" -> "You have a new task in Shale.";
			case "MATERIAL_REQUEST" -> "A material request requires attention.";
			case "CASE" -> "A case requires your attention in Shale.";
			case "CALENDAR" -> "You have an upcoming item in Shale.";
			default -> "You have a new notification in Shale.";
		};
		return new NativeNotificationPresentation(source.id(), "Shale", message, category);
	}

	static String allowlistedCategory(String value) {
		String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
		return switch (normalized) {
			case "TASK", "MATERIAL_REQUEST", "CASE", "CALENDAR" -> normalized;
			default -> "OTHER";
		};
	}
}
