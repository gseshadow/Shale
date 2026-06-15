package com.shale.ui.notification;

import com.shale.data.dao.NotificationDao;
import com.shale.data.dao.NotificationDao.NotificationRow;
import com.shale.ui.privacy.PhiFieldRegistry;
import com.shale.ui.state.AppState;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DurableNotificationService {
	private static final Logger log = LoggerFactory.getLogger(DurableNotificationService.class);

	private final NotificationDao notificationDao;
	private final AppState appState;
	private final NotificationPreferencesService notificationPreferencesService;
	private final ExecutorService persistenceExecutor;

	public DurableNotificationService(NotificationDao notificationDao, AppState appState, NotificationPreferencesService notificationPreferencesService) {
		this.notificationDao = Objects.requireNonNull(notificationDao, "notificationDao");
		this.appState = Objects.requireNonNull(appState, "appState");
		this.notificationPreferencesService = Objects.requireNonNull(notificationPreferencesService, "notificationPreferencesService");
		this.persistenceExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "notification-persistence-worker");
			t.setDaemon(true);
			return t;
		});
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
		long startNanos = System.nanoTime();
		List<NotificationRow> rows = notificationDao.listUnreadNotificationsForUser(shaleClientId, userId);
		long queryElapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
		List<AppNotification> mapped = rows.stream()
				.map(this::toAppNotification)
				.filter(Objects::nonNull)
				.toList();
		long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
		log.info("PERF notifications.durable.load tenantId={} userId={} rows={} mapped={} queryElapsedMs={} totalElapsedMs={} thread={}",
				shaleClientId, userId, rows.size(), mapped.size(), queryElapsedMs, elapsedMs, Thread.currentThread().getName());
		return mapped;
	}

	public void pushLoaded(NotificationCenterService notificationCenterService, List<AppNotification> notifications) {
		Objects.requireNonNull(notificationCenterService, "notificationCenterService");
		if (notifications == null || notifications.isEmpty()) {
			return;
		}
		notificationCenterService.pushNotifications(notifications);
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
		if (durableIds.isEmpty()) {
			return;
		}
		persistenceExecutor.submit(() -> {
			long startNanos = System.nanoTime();
			try {
				notificationDao.markNotificationsRead(shaleClientId, userId, durableIds);
				long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
				log.info("PERF notifications.persist.markRead tenantId={} userId={} count={} elapsedMs={} thread={}",
						shaleClientId, userId, durableIds.size(), elapsedMs, Thread.currentThread().getName());
			} catch (RuntimeException ex) {
				log.error("Notification mark-read persistence failed tenantId={} userId={} count={}", shaleClientId, userId, durableIds.size(), ex);
			}
		});
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
		if (durableIds.isEmpty()) {
			return;
		}
		persistenceExecutor.submit(() -> {
			long startNanos = System.nanoTime();
			try {
				notificationDao.markNotificationsDismissed(shaleClientId, userId, durableIds);
				long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
				log.info("PERF notifications.persist.dismiss tenantId={} userId={} count={} elapsedMs={} thread={}",
						shaleClientId, userId, durableIds.size(), elapsedMs, Thread.currentThread().getName());
			} catch (RuntimeException ex) {
				log.error("Notification dismiss persistence failed tenantId={} userId={} count={}", shaleClientId, userId, durableIds.size(), ex);
			}
		});
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
		String entityTitle = row.entityTitle();
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
				entityTitle,
				row.actionType(),
				row.actorDisplayName(),
				row.caseId(),
				row.caseName(),
				row.caseResponsibleAttorney(),
				row.caseResponsibleAttorneyColor(),
				row.caseNonEngagementLetterSent());
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
