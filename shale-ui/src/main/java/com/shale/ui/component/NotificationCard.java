package com.shale.ui.component;

import com.shale.ui.notification.NotificationSeverity;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.VBox;

public final class NotificationCard extends VBox {
	private final BooleanProperty expanded = new SimpleBooleanProperty(false);

	public NotificationCard() {
		getStyleClass().add("notification-row");
		setFillWidth(true);
		setMinWidth(0);
		setMaxWidth(Double.MAX_VALUE);
	}

	public BooleanProperty expandedProperty() {
		return expanded;
	}

	public boolean isExpanded() {
		return expanded.get();
	}

	public void setExpanded(boolean expanded) {
		this.expanded.set(expanded);
	}

	public void setUnread(boolean unread) {
		getStyleClass().remove("notification-row-unread");
		if (unread) {
			getStyleClass().add("notification-row-unread");
		}
	}

	public void setSeverity(NotificationSeverity severity) {
		getStyleClass().removeAll("notification-card-info", "notification-card-warning", "notification-card-critical");
		getStyleClass().add(switch (severity == null ? NotificationSeverity.INFO : severity) {
		case CRITICAL -> "notification-card-critical";
		case WARNING -> "notification-card-warning";
		case INFO -> "notification-card-info";
		});
	}
}
