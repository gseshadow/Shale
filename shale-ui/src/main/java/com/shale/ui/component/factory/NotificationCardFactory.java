package com.shale.ui.component.factory;

import com.shale.ui.component.NotificationCard;
import com.shale.ui.notification.AppNotification;
import com.shale.ui.notification.NotificationCategory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public final class NotificationCardFactory {
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
			.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
			.withZone(ZoneId.systemDefault());

	public enum Variant {
		CENTER_ROW
	}

	public record NotificationCardModel(AppNotification notification) {
		public NotificationCardModel {
			Objects.requireNonNull(notification, "notification");
		}
	}

	private final Consumer<AppNotification> onDismiss;
	private final Consumer<AppNotification> onMarkRead;
	private final Consumer<Long> onOpenTask;
	private final TaskCardFactory taskCardFactory;

	public NotificationCardFactory(
			Consumer<AppNotification> onDismiss,
			Consumer<AppNotification> onMarkRead,
			Consumer<Long> onOpenTask) {
		this.onDismiss = onDismiss;
		this.onMarkRead = onMarkRead;
		this.onOpenTask = onOpenTask;
		this.taskCardFactory = new TaskCardFactory(
				ignored -> {},
				ignored -> {},
				ignored -> {},
				ignored -> {});
	}

	public NotificationCard create(NotificationCardModel model, Variant variant) {
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(variant, "variant");
		AppNotification item = model.notification();

		NotificationCard card = new NotificationCard();
		card.setUnread(item.isUnread());

		Label category = new Label(resolveCategory(item));
		category.getStyleClass().add("notification-row-category");
		category.setTextOverrun(OverrunStyle.ELLIPSIS);

		Label timestamp = new Label(TIME_FORMATTER.format(item.getCreatedAt()));
		timestamp.getStyleClass().add("notification-row-time");
		timestamp.setTextOverrun(OverrunStyle.ELLIPSIS);

		Button dismissButton = createDismissButton(item);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox metaRow = new HBox(6, category, spacer, timestamp, dismissButton);
		metaRow.setAlignment(Pos.CENTER_LEFT);
		metaRow.getStyleClass().add("notification-row-meta");

		Label title = new Label(item.getTitle());
		title.getStyleClass().add("notification-row-title");
		title.setWrapText(false);
		title.setTextOverrun(OverrunStyle.ELLIPSIS);
		title.setMaxWidth(Double.MAX_VALUE);

		Label message = new Label(item.getMessage());
		message.getStyleClass().add("notification-row-message");
		message.setWrapText(true);
		message.setMaxWidth(Double.MAX_VALUE);
		message.setTextOverrun(OverrunStyle.ELLIPSIS);

		card.getChildren().addAll(metaRow, title, message);
		Region taskPreview = createTaskPreview(item);
		if (taskPreview != null) {
			card.getChildren().add(taskPreview);
		}
		return card;
	}

	private Button createDismissButton(AppNotification item) {
		Button button = new Button(item.getDurableNotificationId() == null ? "Dismiss (session)" : "Dismiss");
		button.getStyleClass().add("notification-row-dismiss");
		if (item.getDurableNotificationId() == null) {
			button.setTooltip(new Tooltip("This notification will be hidden for the current session only."));
		}
		button.setOnAction(event -> {
			event.consume();
			if (onDismiss != null) {
				onDismiss.accept(item);
			}
		});
		return button;
	}

	private Region createTaskPreview(AppNotification item) {
		Long taskId = resolveTaskId(item);
		if (taskId == null || taskId <= 0) {
			return null;
		}
		TaskCardFactory.TaskCardModel model = new TaskCardFactory.TaskCardModel(
				taskId,
				null,
				null,
				null,
				null,
				null,
				resolveTaskPreviewTitle(item, taskId),
				null,
				null,
				null,
				null,
				null,
				List.of());
		Region previewCard = taskCardFactory.create(model, TaskCardFactory.Variant.MINI);
		previewCard.getStyleClass().add("notification-task-preview");
		previewCard.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
			event.consume();
			if (onMarkRead != null) {
				onMarkRead.accept(item);
			}
			if (onOpenTask != null) {
				onOpenTask.accept(taskId);
			}
		});
		previewCard.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
		return previewCard;
	}

	private static String resolveCategory(AppNotification item) {
		NotificationCategory category = item.getCategory();
		return category == null ? "NOTIFICATION" : category.name();
	}

	private static String resolveTaskPreviewTitle(AppNotification item, long taskId) {
		if (item != null && item.getEntityTitle() != null && !item.getEntityTitle().isBlank()) {
			return item.getEntityTitle().trim();
		}
		return "Task #" + taskId;
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
