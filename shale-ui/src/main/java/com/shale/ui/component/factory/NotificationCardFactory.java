package com.shale.ui.component.factory;

import com.shale.ui.component.NotificationCard;
import com.shale.ui.notification.AppNotification;
import com.shale.ui.notification.NotificationCategory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

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
	private final Set<String> expandedNotificationIds = new HashSet<>();

	public NotificationCardFactory(Consumer<AppNotification> onDismiss) {
		this.onDismiss = onDismiss;
	}

	public NotificationCard create(NotificationCardModel model, Variant variant) {
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(variant, "variant");
		AppNotification item = model.notification();

		NotificationCard card = new NotificationCard();
		card.setUnread(item.isUnread());
		card.setExpanded(expandedNotificationIds.contains(item.getId()));

		Button expandButton = createExpandButton(item, card);

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
		HBox topRow = new HBox(5, expandButton, category, title, timestamp, dismissButton);
		topRow.setAlignment(Pos.CENTER_LEFT);
		topRow.getStyleClass().add("notification-row-meta");

		Label message = new Label(item.getMessage());
		message.getStyleClass().add("notification-row-message");
		message.setWrapText(true);
		message.setMaxWidth(Double.MAX_VALUE);
		message.setMaxHeight(26);
		message.setTextOverrun(OverrunStyle.ELLIPSIS);

		card.getChildren().addAll(topRow, message);
		HBox contextRow = createCollapsedContextRow(item);
		if (!contextRow.getChildren().isEmpty()) {
			card.getChildren().add(contextRow);
		}

		VBox expandedContent = createExpandedContent(item);
		expandedContent.visibleProperty().bind(card.expandedProperty());
		expandedContent.managedProperty().bind(card.expandedProperty());
		card.getChildren().add(expandedContent);
		return card;
	}

	private HBox createCollapsedContextRow(AppNotification item) {
		HBox contextRow = new HBox(6);
		contextRow.setAlignment(Pos.CENTER_LEFT);
		contextRow.getStyleClass().add("notification-row-context-line");

		String entityContext = resolveEntityContext(item);
		Label caseChip = createCaseChip(item);
		if (entityContext != null) {
			Label contextLabel = new Label(entityContext);
			contextLabel.getStyleClass().add("notification-row-context");
			contextLabel.setWrapText(false);
			contextLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
			contextLabel.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(contextLabel, Priority.ALWAYS);
			contextRow.getChildren().add(contextLabel);
		} else if (caseChip != null) {
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			contextRow.getChildren().add(spacer);
		}
		if (caseChip != null) {
			contextRow.getChildren().add(caseChip);
		}
		return contextRow;
	}

	private VBox createExpandedContent(AppNotification item) {
		VBox expanded = new VBox(3);
		expanded.getStyleClass().add("notification-row-expanded");

		Label fullMessage = new Label(item.getMessage());
		fullMessage.getStyleClass().add("notification-row-expanded-message");
		fullMessage.setWrapText(true);
		fullMessage.setMaxWidth(Double.MAX_VALUE);
		expanded.getChildren().add(fullMessage);

		String entityContext = resolveEntityContext(item);
		if (entityContext != null) {
			expanded.getChildren().add(createExpandedLine("Related", entityContext));
		}
		String caseContext = resolveCaseContext(item);
		if (caseContext != null) {
			expanded.getChildren().add(createExpandedLine("Case", caseContext));
		}
		String actionType = normalize(item.getActionType());
		if (actionType != null) {
			expanded.getChildren().add(createExpandedLine("Action", actionType));
		}
		return expanded;
	}

	private Label createExpandedLine(String label, String value) {
		Label line = new Label(label + ": " + value);
		line.getStyleClass().add("notification-row-expanded-line");
		line.setWrapText(true);
		line.setMaxWidth(Double.MAX_VALUE);
		return line;
	}

	private Button createExpandButton(AppNotification item, NotificationCard card) {
		Button button = new Button(card.isExpanded() ? "▾" : "▸");
		button.getStyleClass().add("notification-row-expand");
		button.setTooltip(new Tooltip("Expand notification"));
		card.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
			button.setText(Boolean.TRUE.equals(isExpanded) ? "▾" : "▸");
			button.setTooltip(new Tooltip(Boolean.TRUE.equals(isExpanded) ? "Collapse notification" : "Expand notification"));
		});
		button.setOnAction(event -> {
			event.consume();
			boolean expanded = !card.isExpanded();
			card.setExpanded(expanded);
			if (expanded) {
				expandedNotificationIds.add(item.getId());
			} else {
				expandedNotificationIds.remove(item.getId());
			}
		});
		return button;
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

	private static Label createCaseChip(AppNotification item) {
		String caseContext = resolveCaseContext(item);
		if (caseContext == null) {
			return null;
		}
		Label chip = new Label(caseContext);
		chip.getStyleClass().add("notification-row-case-chip");
		chip.setWrapText(false);
		chip.setTextOverrun(OverrunStyle.ELLIPSIS);
		chip.setMaxWidth(180);
		return chip;
	}

	private static String resolveCategory(AppNotification item) {
		NotificationCategory category = item.getCategory();
		return category == null ? "NOTIFICATION" : category.name();
	}

	private static String resolveEntityContext(AppNotification item) {
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

	private static String resolveCaseContext(AppNotification item) {
		if (item == null) {
			return null;
		}
		String caseName = normalize(item.getCaseName());
		Long caseId = item.getCaseId();
		if (caseName != null) {
			return caseName;
		}
		if (caseId != null && caseId > 0) {
			return "Case #" + caseId;
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
