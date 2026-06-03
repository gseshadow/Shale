package com.shale.data.service.adapter;

import java.util.List;
import java.util.Objects;

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
	public long createNotification(CreateNotificationCommand command) {
		throw new UnsupportedOperationException(
				"TODO: NotificationServiceAdapter.createNotification requires entity/action-specific port commands matching NotificationDao create methods.");
	}

	interface NotificationGateway {
		List<NotificationDao.NotificationRow> listUnreadNotificationsForUser(int shaleClientId, int userId);

		void markNotificationRead(int shaleClientId, int userId, long notificationId);

		void markNotificationDismissed(int shaleClientId, int userId, long notificationId);
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
	}
}
