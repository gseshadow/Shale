package com.shale.ui.component.factory;

import com.shale.ui.component.NotificationCard;
import com.shale.ui.notification.AppNotification;
import com.shale.ui.notification.NotificationCategory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

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

	public NotificationCardFactory(Consumer<AppNotification> onDismiss) {
		this.onDismiss = onDismiss;
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

		Label title = new Label(item.getTitle());
		title.getStyleClass().add("notification-row-title");
		title.setWrapText(false);
		title.setTextOverrun(OverrunStyle.ELLIPSIS);
		title.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(title, Priority.ALWAYS);

		Label timestamp = new Label(TIME_FORMATTER.format(item.getCreatedAt()));
		timestamp.getStyleClass().add("notification-row-time");
		timestamp.setTextOverrun(OverrunStyle.ELLIPSIS);

		Button dismissButton = createDismissButton(item);
		HBox topRow = new HBox(6, category, title, timestamp, dismissButton);
		topRow.setAlignment(Pos.CENTER_LEFT);
		topRow.getStyleClass().add("notification-row-meta");

		Label message = new Label(item.getMessage());
		message.getStyleClass().add("notification-row-message");
		message.setWrapText(true);
		message.setMaxWidth(Double.MAX_VALUE);
		message.setMaxHeight(30);
		message.setTextOverrun(OverrunStyle.ELLIPSIS);

		card.getChildren().addAll(topRow, message);
		String context = resolveContext(item);
		if (context != null && !context.isBlank()) {
			Label contextLabel = new Label(context);
			contextLabel.getStyleClass().add("notification-row-context");
			contextLabel.setWrapText(false);
			contextLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
			contextLabel.setMaxWidth(Double.MAX_VALUE);
			card.getChildren().add(contextLabel);
		}
		return card;
	}

	private Button createDismissButton(AppNotification item) {
		Button button = new Button("×");
		button.getStyleClass().add("notification-row-dismiss");
		String tooltip = item.getDurableNotificationId() == null
				? "Dismiss for this session"
				: "Dismiss";
		button.setTooltip(new Tooltip(tooltip));
		button.setOnAction(event -> {
			event.consume();
			if (onDismiss != null) {
				onDismiss.accept(item);
			}
		});
		return button;
	}

	private static String resolveCategory(AppNotification item) {
		NotificationCategory category = item.getCategory();
		return category == null ? "NOTIFICATION" : category.name();
	}

	private static String resolveContext(AppNotification item) {
		if (item == null) {
			return null;
		}
		String entityTitle = normalize(item.getEntityTitle());
		String entityType = normalize(item.getEntityType());
		Long entityId = item.getEntityId();
		if (entityTitle != null) {
			return entityType == null ? entityTitle : entityType + ": " + entityTitle;
		}
		if (entityType != null && entityId != null && entityId > 0) {
			return entityType + " #" + entityId;
		}
		return null;
	}

	private static String normalize(String text) {
		if (text == null) {
			return null;
		}
		String normalized = text.trim();
		return normalized.isBlank() ? null : normalized;
	}
}
