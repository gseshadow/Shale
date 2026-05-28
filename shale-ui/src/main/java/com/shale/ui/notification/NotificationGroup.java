package com.shale.ui.notification;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class NotificationGroup {
	private final String groupKey;
	private final List<AppNotification> notificationsNewestFirst;

	public NotificationGroup(String groupKey, List<AppNotification> notifications) {
		this.groupKey = Objects.requireNonNull(groupKey, "groupKey");
		Objects.requireNonNull(notifications, "notifications");
		if (notifications.isEmpty()) {
			throw new IllegalArgumentException("Notification group must contain at least one notification");
		}
		this.notificationsNewestFirst = notifications.stream()
				.filter(Objects::nonNull)
				.sorted(Comparator.comparing(AppNotification::getCreatedAt).reversed())
				.toList();
		if (this.notificationsNewestFirst.isEmpty()) {
			throw new IllegalArgumentException("Notification group must contain at least one notification");
		}
	}

	public String getGroupKey() {
		return groupKey;
	}

	public List<AppNotification> getNotificationsNewestFirst() {
		return notificationsNewestFirst;
	}

	public AppNotification getLatestNotification() {
		return notificationsNewestFirst.get(0);
	}

	public Instant getLatestCreatedAt() {
		return getLatestNotification().getCreatedAt();
	}

	public boolean isUnread() {
		return notificationsNewestFirst.stream().anyMatch(AppNotification::isUnread);
	}

	public int getCount() {
		return notificationsNewestFirst.size();
	}

	public boolean isStandalone() {
		return getCount() == 1 && groupKey.startsWith("notification:");
	}

	public Long getTaskId() {
		AppNotification latest = getLatestNotification();
		if (latest.getEntityId() == null || latest.getEntityId() <= 0) {
			return null;
		}
		String entityType = latest.getEntityType();
		if (entityType == null || !"TASK".equalsIgnoreCase(entityType.trim())) {
			return null;
		}
		return latest.getEntityId();
	}

	public static String groupKeyFor(AppNotification notification) {
		Objects.requireNonNull(notification, "notification");
		String entityType = normalize(notification.getEntityType());
		Long entityId = notification.getEntityId();
		if (entityType != null && entityId != null && entityId > 0) {
			return "entity:" + entityType.toUpperCase(Locale.ROOT) + ":" + entityId;
		}
		return "notification:" + notification.getId();
	}

	private static String normalize(String text) {
		if (text == null) {
			return null;
		}
		String normalized = text.trim();
		return normalized.isBlank() ? null : normalized;
	}
}
