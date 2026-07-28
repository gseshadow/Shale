package com.shale.ui.notification;

import com.shale.core.service.NotificationServicePort.NotificationSummary;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Projects the already-resolved in-app display text across the native boundary. */
public final class NotificationPrivacyProjector {
	private static final Logger log = LoggerFactory.getLogger(NotificationPrivacyProjector.class);
	private static final String FALLBACK_HEADING = "Shale";
	private static final String FALLBACK_MESSAGE = "You have a new notification in Shale.";

	public NativeNotificationPresentation project(NotificationSummary source, AppNotification display) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(display, "display");
		String category = "MATERIALREQUEST".equalsIgnoreCase(source.entityType())
				? "MATERIAL_REQUEST" : allowlistedCategory(source.category());
		if ("TASK".equals(category) && recognizedTaskAction(source.actionType())
				&& usable(display.getTitle()) && usable(display.getMessage())) {
			return new NativeNotificationPresentation(source.id(), display.getTitle().trim(), display.getMessage().trim(), category);
		}
		String reason = "TASK".equals(category)
				? (recognizedTaskAction(source.actionType()) ? "missing_display_content" : "unrecognized_type")
				: "category_not_display_allowlisted";
		log.debug("Native notification fallback notificationId={} type={} reason={}", source.id(), category, reason);
		return new NativeNotificationPresentation(source.id(), FALLBACK_HEADING, fallbackMessage(category), category);
	}

	private static boolean usable(String value) { return value != null && !value.isBlank(); }

	private static boolean recognizedTaskAction(String value) {
		String action = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		return switch (action) {
			case "ASSIGNED", "NOTE_ADDED", "DUE_OVERDUE", "DUE_TODAY", "DUE_TOMORROW",
					"TASK_CREATED", "TASK_COMPLETED", "TASK_REOPENED", "TASK_TITLE_CHANGED",
					"TASK_DESCRIPTION_CHANGED", "TASK_DUE_DATE_CHANGED", "TASK_PRIORITY_CHANGED",
					"TASK_STATUS_CHANGED", "TASK_ASSIGNMENT_ADDED", "TASK_ASSIGNMENT_REMOVED", "TASK_DELETED" -> true;
			default -> false;
		};
	}

	private static String fallbackMessage(String category) {
		return switch (category) {
			case "MATERIAL_REQUEST" -> "A material request requires attention.";
			case "CASE" -> "A case requires your attention in Shale.";
			case "CALENDAR" -> "You have an upcoming item in Shale.";
			default -> FALLBACK_MESSAGE;
		};
	}

	static String allowlistedCategory(String value) {
		String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
		return switch (normalized) {
			case "TASK", "MATERIAL_REQUEST", "CASE", "CALENDAR" -> normalized;
			default -> "OTHER";
		};
	}
}
