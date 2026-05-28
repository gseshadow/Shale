package com.shale.ui.component.dialog;

import com.shale.ui.component.NotificationCard;
import com.shale.ui.component.factory.NotificationCardFactory;
import com.shale.ui.notification.AppNotification;
import com.shale.ui.notification.NotificationCenterService;

import java.util.Objects;
import java.util.function.Consumer;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class NotificationCenterDialog {
	private static final double DEFAULT_WIDTH = 720;
	private static final double DEFAULT_HEIGHT = 520;
	private static final double MIN_WIDTH = 640;
	private static final double MIN_HEIGHT = 420;

	private NotificationCenterDialog() {
	}

	public static void show(
			Window owner,
			NotificationCenterService notificationService,
			Consumer<Long> onOpenTask,
			Consumer<AppNotification> onActivateNotification) {
		Objects.requireNonNull(notificationService, "notificationService");

		Stage stage = new Stage();
		if (owner != null) {
			stage.initOwner(owner);
		}
		stage.initModality(Modality.WINDOW_MODAL);
		stage.setTitle("Notifications");
		stage.setResizable(true);
		stage.setMinWidth(MIN_WIDTH);
		stage.setMinHeight(MIN_HEIGHT);

		Label heading = new Label("Notifications");
		heading.getStyleClass().add("app-dialog-title");

		Label subtitle = new Label("Newest first. Unread items are highlighted.");
		subtitle.getStyleClass().add("app-dialog-message");

		ListView<AppNotification> listView = new ListView<>();
		listView.setItems(notificationService.getNotificationsNewestFirst());
		listView.getStyleClass().add("notification-list");
		listView.setCellFactory(view -> new NotificationCell(notificationService, onOpenTask, onActivateNotification));
		notificationService.unreadCountProperty().addListener((obs, oldValue, newValue) -> listView.refresh());

		Button markAllReadButton = new Button("Mark all read");
		markAllReadButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
		markAllReadButton.disableProperty().bind(notificationService.unreadCountProperty().lessThanOrEqualTo(0));
		markAllReadButton.setOnAction(event -> notificationService.markAllRead());

		Button closeButton = new Button("Close");
		closeButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
		closeButton.setOnAction(event -> stage.close());
		closeButton.setDefaultButton(true);
		closeButton.setCancelButton(true);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox actions = new HBox(10, markAllReadButton, spacer, closeButton);

		VBox.setVgrow(listView, Priority.ALWAYS);
		VBox body = new VBox(8, heading, subtitle, listView, actions);
		body.getStyleClass().add("app-dialog-root");
		body.setFillWidth(true);
		body.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		body.setPadding(new Insets(12));

		Scene scene = new Scene(body, DEFAULT_WIDTH, DEFAULT_HEIGHT);
		scene.getStylesheets().add(Objects.requireNonNull(
				NotificationCenterDialog.class.getResource("/css/app.css")).toExternalForm());
		stage.setScene(scene);
		stage.showAndWait();
	}

	private static final class NotificationCell extends ListCell<AppNotification> {
		private final NotificationCenterService notificationService;
		private final Consumer<AppNotification> onActivateNotification;
		private final Consumer<Long> onOpenTask;
		private final NotificationCardFactory notificationCardFactory;
		private final ChangeListener<Boolean> unreadListener = (obs, oldValue, newValue) -> updateUnreadStyle();
		private AppNotification observedItem;

		private NotificationCell(
				NotificationCenterService notificationService,
				Consumer<Long> onOpenTask,
				Consumer<AppNotification> onActivateNotification) {
			this.notificationService = notificationService;
			this.onActivateNotification = onActivateNotification;
			this.onOpenTask = onOpenTask;
			this.notificationCardFactory = new NotificationCardFactory(this::dismissNotification);
			setOnMouseClicked(event -> {
				if (isFromInteractiveChild(event)) {
					return;
				}
				AppNotification selected = getItem();
				if (selected != null) {
					notificationService.markRead(selected);
					Long taskId = resolveTaskId(selected);
					if (taskId != null && onOpenTask != null) {
						onOpenTask.accept(taskId);
					} else if (this.onActivateNotification != null) {
						this.onActivateNotification.accept(selected);
					}
				}
			});
		}

		@Override
		protected void updateItem(AppNotification item, boolean empty) {
			super.updateItem(item, empty);
			if (observedItem != null) {
				observedItem.unreadProperty().removeListener(unreadListener);
				observedItem = null;
			}
			if (empty || item == null) {
				setText(null);
				setGraphic(null);
				return;
			}
			observedItem = item;
			observedItem.unreadProperty().addListener(unreadListener);
			setText(null);
			setGraphic(notificationCardFactory.create(
					new NotificationCardFactory.NotificationCardModel(item),
					NotificationCardFactory.Variant.CENTER_ROW));
			updateUnreadStyle();
		}

		private void updateUnreadStyle() {
			if (getGraphic() instanceof NotificationCard card) {
				AppNotification item = getItem();
				card.setUnread(item != null && item.isUnread());
			}
		}

		private void dismissNotification(AppNotification item) {
			if (item == null) {
				return;
			}
			try {
				notificationService.dismiss(item);
			} catch (RuntimeException ex) {
				System.err.println("[NotificationCenterDialog] dismiss failed for notification id=" + item.getId());
				ex.printStackTrace(System.err);
				throw ex;
			}
		}

		private static boolean isFromInteractiveChild(MouseEvent event) {
			if (event == null || !(event.getTarget() instanceof Node node)) {
				return false;
			}
			return hasStyleClassInAncestorChain(node, "notification-row-dismiss");
		}

		private static boolean hasStyleClassInAncestorChain(Node node, String styleClass) {
			Node current = node;
			while (current != null) {
				if (current.getStyleClass().contains(styleClass)) {
					return true;
				}
				current = current.getParent();
			}
			return false;
		}

		private static Long resolveTaskId(AppNotification item) {
			if (item == null || item.getEntityId() == null || item.getEntityId() <= 0) {
				return null;
			}
			String entityType = item.getEntityType();
			if (entityType == null || !"TASK".equalsIgnoreCase(entityType.trim())) {
				return null;
			}
			return item.getEntityId();
		}
	}
}
