package com.shale.data.service.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.service.NotificationServicePort;
import com.shale.data.dao.NotificationDao;

/**
 * Thin NotificationServicePort adapter over existing NotificationDao operations.
 */
public final class NotificationServiceAdapter implements NotificationServicePort {

	private final NotificationGateway notificationGateway;

	public NotificationServiceAdapter(NotificationDao notificationDao) {
		this(new DaoNotificationGateway(notificationDao));
	}

	NotificationServiceAdapter(NotificationGateway notificationGateway) {
		this.notificationGateway = Objects.requireNonNull(notificationGateway, "notificationGateway");
	}

	@Override
	public List<NotificationSummary> listUnreadNotifications(int shaleClientId, int userId) {
		return notificationGateway.listUnreadNotificationsForUser(shaleClientId, userId).stream()
				.map(row -> new NotificationSummary(
						row.id(),
						shaleClientId,
						userId,
						row.category(),
						row.title(),
						row.message(),
						row.createdAt()))
				.toList();
	}

	@Override
	public void markRead(int shaleClientId, int userId, long notificationId) {
		notificationGateway.markNotificationRead(shaleClientId, userId, notificationId);
	}

	@Override
	public void dismiss(int shaleClientId, int userId, long notificationId) {
		notificationGateway.markNotificationDismissed(shaleClientId, userId, notificationId);
	}

	@Override
	public Optional<Long> createTaskAssignedNotification(TaskNotificationCommand command) {
		Objects.requireNonNull(command, "command");
		return Optional.ofNullable(notificationGateway.createTaskAssignedNotification(
				command.shaleClientId(), command.userId(), command.title(), command.body(),
				command.taskId(), command.actorUserId(), command.eventKey()));
	}

	@Override
	public Optional<Long> createTaskNoteAddedNotification(TaskNotificationCommand command) {
		Objects.requireNonNull(command, "command");
		return Optional.ofNullable(notificationGateway.createTaskNoteAddedNotification(
				command.shaleClientId(), command.userId(), command.title(), command.body(),
				command.taskId(), command.actorUserId(), command.eventKey()));
	}

	@Override
	public Optional<Long> createTaskDueDateNotification(TaskDueDateNotificationCommand command) {
		Objects.requireNonNull(command, "command");
		return Optional.ofNullable(notificationGateway.createTaskDueDateNotification(
				command.shaleClientId(), command.userId(), command.title(), command.body(),
				command.taskId(), command.actorUserId(), command.actionType(), command.severity(), command.eventKey()));
	}

	@Override
	public Optional<Long> createTaskActionNotification(TaskActionNotificationCommand command) {
		Objects.requireNonNull(command, "command");
		return Optional.ofNullable(notificationGateway.createTaskActionNotification(
				command.shaleClientId(), command.userId(), command.title(), command.body(),
				command.taskId(), command.actorUserId(), command.actionType(), command.eventKey()));
	}

	@Override
	public Optional<Long> createCalendarEventAssignedNotification(CalendarEventNotificationCommand command) {
		Objects.requireNonNull(command, "command");
		return Optional.ofNullable(notificationGateway.createCalendarEventAssignedNotification(
				command.shaleClientId(), command.userId(), command.title(), command.body(),
				command.calendarEventId(), command.actorUserId(), command.actionType(), command.eventKey()));
	}

	interface NotificationGateway {
		List<NotificationDao.NotificationRow> listUnreadNotificationsForUser(int shaleClientId, int userId);

		void markNotificationRead(int shaleClientId, int userId, long notificationId);

		void markNotificationDismissed(int shaleClientId, int userId, long notificationId);

		Long createTaskAssignedNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String eventKey);

		Long createTaskNoteAddedNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String eventKey);

		Long createTaskDueDateNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String actionType, String severity, String eventKey);

		Long createTaskActionNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String actionType, String eventKey);

		Long createCalendarEventAssignedNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String actionType, String eventKey);
	}

	private record DaoNotificationGateway(NotificationDao notificationDao) implements NotificationGateway {
		private DaoNotificationGateway {
			Objects.requireNonNull(notificationDao, "notificationDao");
		}

		@Override
		public List<NotificationDao.NotificationRow> listUnreadNotificationsForUser(int shaleClientId, int userId) {
			return notificationDao.listUnreadNotificationsForUser(shaleClientId, userId);
		}

		@Override
		public void markNotificationRead(int shaleClientId, int userId, long notificationId) {
			notificationDao.markNotificationRead(shaleClientId, userId, notificationId);
		}

		@Override
		public void markNotificationDismissed(int shaleClientId, int userId, long notificationId) {
			notificationDao.markNotificationDismissed(shaleClientId, userId, notificationId);
		}

		@Override
		public Long createTaskAssignedNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String eventKey) {
			return notificationDao.createTaskAssignedNotification(shaleClientId, userId, title, message, entityId, createdByUserId, eventKey);
		}

		@Override
		public Long createTaskNoteAddedNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String eventKey) {
			return notificationDao.createTaskNoteAddedNotification(shaleClientId, userId, title, message, entityId, createdByUserId, eventKey);
		}

		@Override
		public Long createTaskDueDateNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String actionType, String severity, String eventKey) {
			return notificationDao.createTaskDueDateNotification(shaleClientId, userId, title, message, entityId, createdByUserId, actionType, severity, eventKey);
		}

		@Override
		public Long createTaskActionNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String actionType, String eventKey) {
			return notificationDao.createTaskActionNotification(shaleClientId, userId, title, message, entityId, createdByUserId, actionType, eventKey);
		}

		@Override
		public Long createCalendarEventAssignedNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String actionType, String eventKey) {
			return notificationDao.createCalendarEventAssignedNotification(shaleClientId, userId, title, message, entityId, createdByUserId, actionType, eventKey);
		}
	}
}
