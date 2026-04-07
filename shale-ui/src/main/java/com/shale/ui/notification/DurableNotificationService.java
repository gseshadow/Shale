package com.shale.ui.notification;

import com.shale.data.dao.NotificationDao;
import com.shale.data.dao.NotificationDao.NotificationRow;
import com.shale.ui.state.AppState;

import java.util.List;
import java.util.Objects;

public final class DurableNotificationService {
	private final NotificationDao notificationDao;
	private final AppState appState;

	public DurableNotificationService(NotificationDao notificationDao, AppState appState) {
		this.notificationDao = Objects.requireNonNull(notificationDao, "notificationDao");
		this.appState = Objects.requireNonNull(appState, "appState");
	}

	public void loadUnreadInto(NotificationCenterService notificationCenterService) {
		Objects.requireNonNull(notificationCenterService, "notificationCenterService");
		Integer shaleClientId = appState.getShaleClientId();
		Integer userId = appState.getUserId();
		if (shaleClientId == null || shaleClientId <= 0 || userId == null || userId <= 0) {
			return;
		}
		List<NotificationRow> rows = notificationDao.listUnreadNotificationsForUser(shaleClientId, userId);
		for (NotificationRow row : rows) {
			notificationCenterService.pushNotification(toAppNotification(row));
		}
	}

	public void markRead(List<AppNotification> notifications) {
		if (notifications == null || notifications.isEmpty()) {
			return;
		}
		List<Long> durableIds = notifications.stream()
				.map(AppNotification::getDurableNotificationId)
				.filter(Objects::nonNull)
				.toList();
		notificationDao.markNotificationsRead(durableIds);
	}

	private static AppNotification toAppNotification(NotificationRow row) {
		NotificationCategory category = parseCategory(row.category());
		NotificationSeverity severity = parseSeverity(row.severity());
		String id = "db-" + row.id();
		return new AppNotification(
				id,
				category,
				severity,
				Objects.toString(row.title(), "Notification"),
				Objects.toString(row.message(), ""),
				row.createdAt(),
				!row.isRead(),
				category == NotificationCategory.TASK,
				NotificationTargetScope.USER_SCOPED,
				row.id(),
				row.eventKey());
	}

	private static NotificationCategory parseCategory(String value) {
		if (value == null || value.isBlank()) {
			return NotificationCategory.SYSTEM;
		}
		try {
			return NotificationCategory.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException ignored) {
			return NotificationCategory.SYSTEM;
		}
	}

	private static NotificationSeverity parseSeverity(String value) {
		if (value == null || value.isBlank()) {
			return NotificationSeverity.INFO;
		}
		try {
			return NotificationSeverity.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException ignored) {
			return NotificationSeverity.INFO;
		}
	}
}
