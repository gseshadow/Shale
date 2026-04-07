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
	private final boolean showAsBanner;
	private final BooleanProperty unread;

	public AppNotification(
			String id,
			NotificationCategory category,
			NotificationSeverity severity,
			String title,
			String message,
			Instant createdAt,
			boolean unread,
			boolean showAsBanner) {
		this.id = Objects.requireNonNull(id, "id");
		this.category = Objects.requireNonNull(category, "category");
		this.severity = Objects.requireNonNull(severity, "severity");
		this.title = Objects.requireNonNull(title, "title");
		this.message = Objects.requireNonNull(message, "message");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.unread = new SimpleBooleanProperty(unread);
		this.showAsBanner = showAsBanner;
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
