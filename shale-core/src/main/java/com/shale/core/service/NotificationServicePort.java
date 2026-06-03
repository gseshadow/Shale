package com.shale.core.service;

import java.time.Instant;
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
