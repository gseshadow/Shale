package com.shale.ui.component;

import javafx.scene.layout.VBox;

public final class NotificationCard extends VBox {

	public NotificationCard() {
		getStyleClass().add("notification-row");
	}

	public void setUnread(boolean unread) {
		getStyleClass().remove("notification-row-unread");
		if (unread) {
			getStyleClass().add("notification-row-unread");
		}
	}
}
