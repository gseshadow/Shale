package com.shale.ui.component.dialog;

import com.shale.ui.notification.AppNotification;
import com.shale.ui.notification.NotificationCenterService;
import com.shale.ui.notification.NotificationCategory;
import com.shale.ui.component.factory.TaskCardFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.Node;
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

	public static void show(Window owner, NotificationCenterService notificationService, Consumer<Long> onOpenTask) {
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
		listView.setCellFactory(view -> new NotificationCell(notificationService, onOpenTask));
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
		root.getChildren().addAll(heading, subtitle, listView, actions);

		Scene scene = new Scene(root);
		scene.getStylesheets().add(Objects.requireNonNull(
				NotificationCenterDialog.class.getResource("/css/app.css")).toExternalForm());
		stage.setScene(scene);
		stage.showAndWait();
	}

	private static final class NotificationCell extends ListCell<AppNotification> {
		private final NotificationCenterService notificationService;
		private final Consumer<Long> onOpenTask;
		private final TaskCardFactory taskCardFactory;
		private final ChangeListener<Boolean> unreadListener = (obs, oldValue, newValue) -> updateUnreadStyle();
		private AppNotification observedItem;

		private NotificationCell(NotificationCenterService notificationService, Consumer<Long> onOpenTask) {
			this.notificationService = notificationService;
			this.onOpenTask = onOpenTask;
			this.taskCardFactory = new TaskCardFactory(
					ignored -> {},
					ignored -> {},
					ignored -> {},
					ignored -> {});
			setOnMouseClicked(event -> {
				if (isFromInteractiveChild(event)) {
					return;
				}
				AppNotification selected = getItem();
				if (selected != null) {
					notificationService.markRead(selected);
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

			Label category = new Label(item.getCategory().name());
			category.getStyleClass().add("notification-row-category");

			Label timestamp = new Label(TIME_FORMATTER.format(item.getCreatedAt()));
			timestamp.getStyleClass().add("notification-row-time");

			Button dismissButton = createDismissButton(item);
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			HBox topRow = new HBox(8, category, spacer, timestamp, dismissButton);

			Label title = new Label(item.getTitle());
			title.getStyleClass().add("notification-row-title");

			Label message = new Label(item.getMessage());
			message.setWrapText(true);
			message.getStyleClass().add("notification-row-message");

			VBox wrapper = new VBox(6, topRow, title, message);
			Region taskPreview = createTaskPreview(item);
			if (taskPreview != null) {
				wrapper.getChildren().add(taskPreview);
			}
			wrapper.getStyleClass().add("notification-row");

			setGraphic(wrapper);
			updateUnreadStyle();
		}

		private void updateUnreadStyle() {
			if (!(getGraphic() instanceof VBox wrapper)) {
				return;
			}
			wrapper.getStyleClass().remove("notification-row-unread");
			AppNotification item = getItem();
			if (item != null && item.isUnread()) {
				wrapper.getStyleClass().add("notification-row-unread");
			}
		}

		private Region createTaskPreview(AppNotification item) {
			Long taskId = resolveTaskId(item);
			if (taskId == null || taskId <= 0) {
				return null;
			}
			String previewTitle = resolveTaskPreviewTitle(item, taskId);

			TaskCardFactory.TaskCardModel model = new TaskCardFactory.TaskCardModel(
					taskId,
					null,
					null,
					null,
					null,
					previewTitle,
					null,
					null,
					null,
					null,
					null,
					null,
					null);
			Region previewCard = taskCardFactory.create(model, TaskCardFactory.Variant.MINI);
			previewCard.getStyleClass().add("notification-task-preview");
			previewCard.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> onTaskPreviewPressed(item, taskId, event));
			previewCard.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
			return previewCard;
		}

		private static String resolveTaskPreviewTitle(AppNotification item, long taskId) {
			if (item != null && item.getEntityTitle() != null && !item.getEntityTitle().isBlank()) {
				return item.getEntityTitle().trim();
			}
			String messageTitle = extractTaskTitleFromMessage(item == null ? null : item.getMessage());
			if (messageTitle != null) {
				return messageTitle;
			}
			return "Task #" + taskId;
		}

		private static String extractTaskTitleFromMessage(String message) {
			if (message == null) {
				return null;
			}
			String trimmed = message.trim();
			if (!trimmed.startsWith("Task:")) {
				return null;
			}
			String withoutPrefix = trimmed.substring("Task:".length()).trim();
			if (withoutPrefix.isBlank()) {
				return null;
			}
			int separator = withoutPrefix.indexOf(" • ");
			String candidate = separator >= 0 ? withoutPrefix.substring(0, separator).trim() : withoutPrefix;
			if (candidate.isBlank() || candidate.matches("(?i)^Task\\s*#\\d+$")) {
				return null;
			}
			return candidate;
		}

		private void onTaskPreviewPressed(AppNotification item, Long taskId, MouseEvent event) {
			event.consume();
			if (item != null) {
				notificationService.markRead(item);
			}
			if (onOpenTask != null && taskId != null && taskId > 0) {
				onOpenTask.accept(taskId);
			}
		}

		private Button createDismissButton(AppNotification item) {
			Button button = new Button("Dismiss");
			button.getStyleClass().add("notification-row-dismiss");
			button.addEventFilter(MouseEvent.MOUSE_PRESSED, MouseEvent::consume);
			if (item == null || item.getDurableNotificationId() == null) {
				button.setText("Dismiss (session)");
				button.setTooltip(new Tooltip("This notification will be hidden for the current session only."));
			}
			button.setOnAction(event -> {
				event.consume();
				if (item != null) {
					notificationService.dismiss(item);
				}
			});
			return button;
		}


		private static boolean isFromInteractiveChild(MouseEvent event) {
			if (event == null || !(event.getTarget() instanceof Node node)) {
				return false;
			}
			return hasStyleClassInAncestorChain(node, "notification-row-dismiss")
					|| hasStyleClassInAncestorChain(node, "notification-task-preview");
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
			if (item == null || item.getCategory() == null || item.getCategory() != NotificationCategory.TASK) {
				return null;
			}
			Long entityId = item.getEntityId();
			if (entityId == null || entityId <= 0) {
				return null;
			}
			String entityType = item.getEntityType();
			if (entityType != null && !entityType.isBlank() && !"TASK".equalsIgnoreCase(entityType.trim())) {
				return null;
			}
			return entityId;
		}
	}
}
