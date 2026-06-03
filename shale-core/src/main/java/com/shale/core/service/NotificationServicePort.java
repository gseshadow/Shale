package com.shale.core.service;

import java.time.Instant;
import java.util.List;

/**
 * Shared durable-notification boundary for future desktop/server adapters.
 *
 * <p>This is deliberately independent of shale-ui notification presentation
 * classes and should be backed by NotificationDao in a later adapter step.</p>
 */
public interface NotificationServicePort {

	List<NotificationSummary> listUnreadNotifications(int shaleClientId, int userId);

	void markRead(int shaleClientId, int userId, long notificationId);

	void dismiss(int shaleClientId, int userId, long notificationId);

	/**
	 * TODO: refine notification creation around task/case/calendar event-specific
	 * commands rather than a generic placeholder payload.
	 */
	long createNotification(CreateNotificationCommand command);

	record NotificationSummary(
			long id,
			int shaleClientId,
			int userId,
			String category,
			String title,
			String body,
			Instant createdAt) {
	}

	record CreateNotificationCommand(
			int shaleClientId,
			int userId,
			int actorUserId,
			String category,
			String title,
			String body,
			String eventKey) {
	}
}
