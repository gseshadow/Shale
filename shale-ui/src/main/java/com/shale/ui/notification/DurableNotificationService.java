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
		pushLoaded(notificationCenterService, listUnread(shaleClientId, userId));
	}

	public List<AppNotification> listUnread(int shaleClientId, int userId) {
		if (shaleClientId <= 0 || userId <= 0) {
			return List.of();
		}
		List<NotificationRow> rows = notificationDao.listUnreadNotificationsForUser(shaleClientId, userId);
		return rows.stream()
				.map(DurableNotificationService::toAppNotification)
				.toList();
	}

	public void pushLoaded(NotificationCenterService notificationCenterService, List<AppNotification> notifications) {
		Objects.requireNonNull(notificationCenterService, "notificationCenterService");
		if (notifications == null || notifications.isEmpty()) {
			return;
		}
		for (AppNotification notification : notifications) {
			notificationCenterService.pushNotification(notification);
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
				shouldShowAsBanner(category, row.actionType(), severity),
				NotificationTargetScope.USER_SCOPED,
				row.id(),
				row.eventKey());
	}

	private static boolean shouldShowAsBanner(NotificationCategory category, String actionType, NotificationSeverity severity) {
		if (category != NotificationCategory.TASK) {
			return false;
		}
		String normalizedAction = actionType == null ? "" : actionType.trim().toUpperCase();
		if ("DUE_OVERDUE".equals(normalizedAction)) {
			return true;
		}
		return severity == NotificationSeverity.CRITICAL;
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
