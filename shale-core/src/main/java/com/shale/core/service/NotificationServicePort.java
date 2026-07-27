package com.shale.core.service;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Shared durable-notification boundary for future desktop/server adapters.
 *
 * <p>This is deliberately independent of shale-ui notification presentation
 * classes and should be backed by NotificationDao in a later adapter step.</p>
 */
public interface NotificationServicePort {

	List<NotificationSummary> listUnreadNotifications(int shaleClientId, int userId);

	NotificationPage listNotifications(int shaleClientId, int userId, NotificationCursor cursor, int limit);

	int countUnreadNotifications(int shaleClientId, int userId);

	Optional<NotificationActivationTarget> findActivationTarget(int shaleClientId, int userId, long notificationId);

	void markRead(int shaleClientId, int userId, long notificationId);

	void dismiss(int shaleClientId, int userId, long notificationId);

	Optional<Long> createTaskAssignedNotification(TaskNotificationCommand command);

	Optional<Long> createTaskNoteAddedNotification(TaskNotificationCommand command);

	Optional<Long> createTaskDueDateNotification(TaskDueDateNotificationCommand command);

	Optional<Long> createTaskActionNotification(TaskActionNotificationCommand command);

	Optional<Long> createCalendarEventAssignedNotification(CalendarEventNotificationCommand command);

	record NotificationSummary(
			long id,
			int shaleClientId,
			int userId,
			String category,
			String title,
			String body,
			Instant createdAt) {
	}

	record NotificationCursor(String value) {
		public NotificationCursor {
			value = value == null ? "" : value.trim();
			if (!value.isEmpty()) {
				try {
					byte[] decoded = Base64.getUrlDecoder().decode(value);
					if (decoded.length != Long.BYTES || ByteBuffer.wrap(decoded).getLong() < 0) {
						throw new IllegalArgumentException("Invalid notification cursor.");
					}
				} catch (IllegalArgumentException ex) {
					throw new IllegalArgumentException("Invalid notification cursor.");
				}
			}
		}

		public static NotificationCursor start() {
			return new NotificationCursor("");
		}

		public static NotificationCursor after(long notificationId) {
			if (notificationId < 0) throw new IllegalArgumentException("notificationId must not be negative");
			return new NotificationCursor(Base64.getUrlEncoder().withoutPadding()
					.encodeToString(ByteBuffer.allocate(Long.BYTES).putLong(notificationId).array()));
		}

		public long afterNotificationId() {
			return value.isEmpty() ? 0 : ByteBuffer.wrap(Base64.getUrlDecoder().decode(value)).getLong();
		}
	}

	record NotificationPage(List<NotificationSummary> items, NotificationCursor nextCursor, boolean hasMore) {
		public NotificationPage {
			items = List.copyOf(items);
		}
	}

	record NotificationActivationTarget(
			long notificationId,
			String entityType,
			long entityId,
			Long parentCaseId,
			String actionType) {
	}

	record TaskNotificationCommand(
			int shaleClientId,
			int userId,
			String title,
			String body,
			long taskId,
			int actorUserId,
			String eventKey) {
	}

	record TaskDueDateNotificationCommand(
			int shaleClientId,
			int userId,
			String title,
			String body,
			long taskId,
			int actorUserId,
			String actionType,
			String severity,
			String eventKey) {
	}

	record TaskActionNotificationCommand(
			int shaleClientId,
			int userId,
			String title,
			String body,
			long taskId,
			int actorUserId,
			String actionType,
			String eventKey) {
	}

	record CalendarEventNotificationCommand(
			int shaleClientId,
			int userId,
			String title,
			String body,
			long calendarEventId,
			int actorUserId,
			String actionType,
			String eventKey) {
	}
}
