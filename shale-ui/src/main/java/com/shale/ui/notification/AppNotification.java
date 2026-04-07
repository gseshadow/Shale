package com.shale.ui.notification;

import java.time.Instant;
import java.util.Objects;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public final class AppNotification {
	private final String id;
	private final NotificationCategory category;
	private final NotificationSeverity severity;
	private final String title;
	private final String message;
	private final Instant createdAt;
	private final NotificationTargetScope targetScope;
	private final boolean showAsBanner;
	private final Long durableNotificationId;
	private final String eventKey;
	private final BooleanProperty unread;

	public AppNotification(
			String id,
			NotificationCategory category,
			NotificationSeverity severity,
			String title,
			String message,
			Instant createdAt,
			boolean unread,
			boolean showAsBanner,
			NotificationTargetScope targetScope) {
		this(id, category, severity, title, message, createdAt, unread, showAsBanner, targetScope, null, null);
	}

	public AppNotification(
			String id,
			NotificationCategory category,
			NotificationSeverity severity,
			String title,
			String message,
			Instant createdAt,
			boolean unread,
			boolean showAsBanner,
			NotificationTargetScope targetScope,
			Long durableNotificationId,
			String eventKey) {
		this.id = Objects.requireNonNull(id, "id");
		this.category = Objects.requireNonNull(category, "category");
		this.severity = Objects.requireNonNull(severity, "severity");
		this.title = Objects.requireNonNull(title, "title");
		this.message = Objects.requireNonNull(message, "message");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.unread = new SimpleBooleanProperty(unread);
		this.showAsBanner = showAsBanner;
		this.targetScope = Objects.requireNonNull(targetScope, "targetScope");
		this.durableNotificationId = durableNotificationId;
		this.eventKey = eventKey;
	}

	public String getId() {
		return id;
	}

	public NotificationCategory getCategory() {
		return category;
	}

	public NotificationSeverity getSeverity() {
		return severity;
	}

	public String getTitle() {
		return title;
	}

	public String getMessage() {
		return message;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}


	public NotificationTargetScope getTargetScope() {
		return targetScope;
	}

	public Long getDurableNotificationId() {
		return durableNotificationId;
	}

	public String getEventKey() {
		return eventKey;
	}
	public boolean isShowAsBanner() {
		return showAsBanner;
	}

	public boolean isUnread() {
		return unread.get();
	}

	public void setUnread(boolean unread) {
		this.unread.set(unread);
	}

	public BooleanProperty unreadProperty() {
		return unread;
	}
}
