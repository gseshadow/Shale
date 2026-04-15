package com.shale.ui.notification;

import com.shale.data.dao.NotificationDao;
import com.shale.data.dao.NotificationDao.NotificationRow;
import com.shale.ui.privacy.PhiFieldRegistry;
import com.shale.ui.state.AppState;

import java.util.List;
import java.util.Objects;

public final class DurableNotificationService {
	private final NotificationDao notificationDao;
	private final AppState appState;
	private final NotificationPreferencesService notificationPreferencesService;

	public DurableNotificationService(NotificationDao notificationDao, AppState appState, NotificationPreferencesService notificationPreferencesService) {
		this.notificationDao = Objects.requireNonNull(notificationDao, "notificationDao");
		this.appState = Objects.requireNonNull(appState, "appState");
		this.notificationPreferencesService = Objects.requireNonNull(notificationPreferencesService, "notificationPreferencesService");
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
				.map(this::toAppNotification)
				.filter(Objects::nonNull)
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
		Integer shaleClientId = appState.getShaleClientId();
		Integer userId = appState.getUserId();
		if (shaleClientId == null || shaleClientId <= 0 || userId == null || userId <= 0) {
			return;
		}
		List<Long> durableIds = notifications.stream()
				.map(AppNotification::getDurableNotificationId)
				.filter(Objects::nonNull)
				.distinct()
				.toList();
		notificationDao.markNotificationsRead(shaleClientId, userId, durableIds);
	}

	public void dismiss(List<AppNotification> notifications) {
		if (notifications == null || notifications.isEmpty()) {
			return;
		}
		Integer shaleClientId = appState.getShaleClientId();
		Integer userId = appState.getUserId();
		if (shaleClientId == null || shaleClientId <= 0 || userId == null || userId <= 0) {
			return;
		}
		List<Long> durableIds = notifications.stream()
				.map(AppNotification::getDurableNotificationId)
				.filter(Objects::nonNull)
				.distinct()
				.toList();
		notificationDao.markNotificationsDismissed(shaleClientId, userId, durableIds);
	}

	private AppNotification toAppNotification(NotificationRow row) {
		NotificationCategory category = parseCategory(row.category());
		NotificationSeverity severity = parseSeverity(row.severity());
		if (!isEnabled(category, row.actionType())) {
			return null;
		}
		String id = "db-" + row.id();
		String title = category == NotificationCategory.TASK
				? safeTaskNotificationTitle(row.actionType())
				: Objects.toString(row.title(), "Notification");
		String message = category == NotificationCategory.TASK
				? safeTaskNotificationMessage(row.actionType())
				: Objects.toString(row.message(), "");
		return new AppNotification(
				id,
				category,
				severity,
				title,
				message,
				row.createdAt(),
				!row.isRead(),
				shouldShowAsBanner(category, row.actionType(), severity),
				NotificationTargetScope.USER_SCOPED,
				row.id(),
				row.eventKey(),
				row.entityType(),
				row.entityId(),
				entityTitle);
	}

	private static String safeTaskNotificationTitle(String actionType) {
		String normalizedAction = actionType == null ? "" : actionType.trim().toUpperCase();
		if ("NOTE_ADDED".equals(normalizedAction)) {
			return "Task note added";
		}
		if ("ASSIGNED".equals(normalizedAction)) {
			return "Task assigned to you";
		}
		return "Task updated";
	}

	private static String safeTaskNotificationMessage(String actionType) {
		String normalizedAction = actionType == null ? "" : actionType.trim().toUpperCase();
		if ("NOTE_ADDED".equals(normalizedAction)) {
			return "A task assigned to you has a new note.";
		}
		if ("ASSIGNED".equals(normalizedAction)) {
			return "A task was assigned to you.";
		}
		if ("DUE_OVERDUE".equals(normalizedAction) || "DUE_TODAY".equals(normalizedAction) || "DUE_TOMORROW".equals(normalizedAction)) {
			return "A task assigned to you has a due date update.";
		}
		return "A task assigned to you was updated.";
	}

	private static String safeTaskNotificationTitle(String actionType) {
		String normalizedAction = actionType == null ? "" : actionType.trim().toUpperCase();
		if ("NOTE_ADDED".equals(normalizedAction)) {
			return "Task note added";
		}
		if ("ASSIGNED".equals(normalizedAction)) {
			return "Task assigned to you";
		}
		return "Task updated";
	}

	private static String safeTaskNotificationMessage(String actionType) {
		String normalizedAction = actionType == null ? "" : actionType.trim().toUpperCase();
		if ("NOTE_ADDED".equals(normalizedAction)) {
			return "A task assigned to you has a new note.";
		}
		if ("ASSIGNED".equals(normalizedAction)) {
			return "A task was assigned to you.";
		}
		if ("DUE_OVERDUE".equals(normalizedAction) || "DUE_TODAY".equals(normalizedAction) || "DUE_TOMORROW".equals(normalizedAction)) {
			return "A task assigned to you has a due date update.";
		}
		return "A task assigned to you was updated.";
	}

	private boolean isEnabled(NotificationCategory category, String actionType) {
		if (category != NotificationCategory.TASK) {
			return true;
		}
		String normalizedAction = actionType == null ? "" : actionType.trim().toUpperCase();
		return switch (normalizedAction) {
			case "DUE_OVERDUE" -> notificationPreferencesService.isEnabled(NotificationPreferenceKey.TASK_DUE_OVERDUE);
			case "DUE_TODAY" -> notificationPreferencesService.isEnabled(NotificationPreferenceKey.TASK_DUE_TODAY);
			case "DUE_TOMORROW" -> notificationPreferencesService.isEnabled(NotificationPreferenceKey.TASK_DUE_TOMORROW);
			default -> notificationPreferencesService.isEnabled(NotificationPreferenceKey.TASK_ASSIGNED_TO_ME);
		};
	}

	private boolean shouldShowAsBanner(NotificationCategory category, String actionType, NotificationSeverity severity) {
		if (category != NotificationCategory.TASK) {
			return false;
		}
		String normalizedAction = actionType == null ? "" : actionType.trim().toUpperCase();
		if ("DUE_OVERDUE".equals(normalizedAction) || "DUE_TODAY".equals(normalizedAction)) {
			return "DUE_OVERDUE".equals(normalizedAction)
					? notificationPreferencesService.isEnabled(NotificationPreferenceKey.TASK_DUE_OVERDUE_BANNER)
					: notificationPreferencesService.isEnabled(NotificationPreferenceKey.TASK_DUE_TODAY_BANNER);
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
