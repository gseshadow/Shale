package com.shale.data.service.adapter;

import java.util.List;
import java.util.Objects;

import com.shale.core.service.NotificationServicePort;
import com.shale.data.dao.NotificationDao;

/**
 * Thin NotificationServicePort adapter over existing NotificationDao operations.
 */
public final class NotificationServiceAdapter implements NotificationServicePort {

	private final NotificationDao notificationDao;

	public NotificationServiceAdapter(NotificationDao notificationDao) {
		this.notificationDao = Objects.requireNonNull(notificationDao, "notificationDao");
	}

	@Override
	public List<NotificationSummary> listUnreadNotifications(int shaleClientId, int userId) {
		return notificationDao.listUnreadNotificationsForUser(shaleClientId, userId).stream()
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
		notificationDao.markNotificationRead(shaleClientId, userId, notificationId);
	}

	@Override
	public void dismiss(int shaleClientId, int userId, long notificationId) {
		notificationDao.markNotificationDismissed(shaleClientId, userId, notificationId);
	}

	@Override
	public long createNotification(CreateNotificationCommand command) {
		throw new UnsupportedOperationException(
				"TODO: NotificationServiceAdapter.createNotification requires entity/action-specific port commands matching NotificationDao create methods.");
	}
}
