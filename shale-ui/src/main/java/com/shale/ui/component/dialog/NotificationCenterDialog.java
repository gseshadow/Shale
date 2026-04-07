package com.shale.ui.component.dialog;

import com.shale.ui.notification.AppNotification;
import com.shale.ui.notification.NotificationCenterService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Objects;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class NotificationCenterDialog {
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
			.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
			.withZone(ZoneId.systemDefault());

	private NotificationCenterDialog() {
	}

	public static void show(Window owner, NotificationCenterService notificationService) {
		Objects.requireNonNull(notificationService, "notificationService");

		Stage stage = AppDialogs.createModalStage(owner, "Notifications");
		stage.setResizable(true);
		stage.setMinWidth(680);
		stage.setMinHeight(440);

		VBox root = new VBox(12);
		root.setPadding(new Insets(16));
		root.getStyleClass().add("app-dialog-root");

		Label heading = new Label("Notifications");
		heading.getStyleClass().add("app-dialog-title");

		Label subtitle = new Label("Newest first. Unread items are highlighted.");
		subtitle.getStyleClass().add("app-dialog-message");

		ListView<AppNotification> listView = new ListView<>();
		listView.setItems(notificationService.getNotificationsNewestFirst());
		listView.getStyleClass().add("notification-list");
		listView.setCellFactory(view -> new NotificationCell(notificationService));

		Button closeButton = new Button("Close");
		closeButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
		closeButton.setOnAction(event -> stage.close());
		closeButton.setDefaultButton(true);
		closeButton.setCancelButton(true);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox actions = new HBox(10, spacer, closeButton);

		VBox.setVgrow(listView, Priority.ALWAYS);
		root.getChildren().addAll(heading, subtitle, listView, actions);

		Scene scene = new Scene(root);
		scene.getStylesheets().add(Objects.requireNonNull(
				NotificationCenterDialog.class.getResource("/css/app.css")).toExternalForm());
		stage.setScene(scene);

		notificationService.markAllRead();
		stage.showAndWait();
	}

	private static final class NotificationCell extends ListCell<AppNotification> {
		private final NotificationCenterService notificationService;

		private NotificationCell(NotificationCenterService notificationService) {
			this.notificationService = notificationService;
		}

		@Override
		protected void updateItem(AppNotification item, boolean empty) {
			super.updateItem(item, empty);
			if (empty || item == null) {
				setText(null);
				setGraphic(null);
				return;
			}

			Label category = new Label(item.getCategory().name());
			category.getStyleClass().add("notification-row-category");

			Label timestamp = new Label(TIME_FORMATTER.format(item.getCreatedAt()));
			timestamp.getStyleClass().add("notification-row-time");

			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			HBox topRow = new HBox(8, category, spacer, timestamp);

			Label title = new Label(item.getTitle());
			title.getStyleClass().add("notification-row-title");

			Label message = new Label(item.getMessage());
			message.setWrapText(true);
			message.getStyleClass().add("notification-row-message");

			VBox wrapper = new VBox(6, topRow, title, message);
			wrapper.getStyleClass().add("notification-row");
			if (item.isUnread()) {
				wrapper.getStyleClass().add("notification-row-unread");
			}

			setGraphic(wrapper);
			setOnMouseClicked(event -> notificationService.markRead(item));
		}
	}
}
