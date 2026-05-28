package com.shale.ui.component;

import javafx.scene.layout.VBox;

public final class NotificationCard extends VBox {

	public NotificationCard() {
		getStyleClass().add("notification-row");
		setFillWidth(true);
		setMaxWidth(Double.MAX_VALUE);
	}

	public void setUnread(boolean unread) {
		getStyleClass().remove("notification-row-unread");
		if (unread) {
			getStyleClass().add("notification-row-unread");
		}
	}
}
