package com.shale.ui.component.factory;

import com.shale.ui.component.NotificationCard;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
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
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
	private final CaseCardFactory caseCardFactory;
	private final Set<String> expandedNotificationIds = new HashSet<>();

	public NotificationCardFactory(Consumer<AppNotification> onDismiss) {
		this(onDismiss, null);
	}

	public NotificationCardFactory(Consumer<AppNotification> onDismiss, Consumer<Integer> onOpenCase) {
		this.onDismiss = onDismiss;
		this.caseCardFactory = new CaseCardFactory(onOpenCase);
	}

	public NotificationCard create(NotificationCardModel model, Variant variant) {
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(variant, "variant");
		AppNotification item = model.notification();

		NotificationCard card = new NotificationCard();
		card.setUnread(item.isUnread());
		card.setExpanded(expandedNotificationIds.contains(item.getId()));

		Label category = new Label(resolveCategory(item));
		category.getStyleClass().add("notification-row-category");
		category.setTextOverrun(OverrunStyle.ELLIPSIS);

		Label title = new Label(item.getTitle());
		title.getStyleClass().add("notification-row-title");
		title.setWrapText(false);
		title.setTextOverrun(OverrunStyle.ELLIPSIS);
		title.setMaxWidth(Double.MAX_VALUE);

		Label message = new Label(item.getMessage());
		message.getStyleClass().add("notification-row-message");
		message.setWrapText(true);
		message.setMaxWidth(Double.MAX_VALUE);
		message.setMaxHeight(34);
		message.setTextOverrun(OverrunStyle.ELLIPSIS);

		VBox mainArea = new VBox(2, category, title, message);
		mainArea.getStyleClass().add("notification-row-main");
		mainArea.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(mainArea, Priority.ALWAYS);

		String entityContext = resolveEntityContext(item);
		if (entityContext != null) {
			Label contextLabel = new Label(entityContext);
			contextLabel.getStyleClass().add("notification-row-context");
			contextLabel.setWrapText(false);
			contextLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
			contextLabel.setMaxWidth(Double.MAX_VALUE);
			mainArea.getChildren().add(contextLabel);
		}

		Label timestamp = new Label(TIME_FORMATTER.format(item.getCreatedAt()));
		timestamp.getStyleClass().add("notification-row-time");
		timestamp.setTextOverrun(OverrunStyle.ELLIPSIS);

		Button dismissButton = createDismissButton(item);
		Button expandButton = createExpandButton(item, card);
		HBox controlRow = new HBox(4, timestamp, dismissButton, expandButton);
		controlRow.getStyleClass().add("notification-row-controls");
		controlRow.setAlignment(Pos.CENTER_RIGHT);

		VBox rightArea = new VBox(5, controlRow);
		rightArea.getStyleClass().add("notification-row-right");
		rightArea.setAlignment(Pos.TOP_RIGHT);
		Node caseCard = createCaseMiniCard(item);
		if (caseCard != null) {
			rightArea.getChildren().add(caseCard);
		}

		HBox collapsedRow = new HBox(10, mainArea, rightArea);
		collapsedRow.getStyleClass().add("notification-row-collapsed");
		collapsedRow.setAlignment(Pos.TOP_LEFT);
		card.getChildren().add(collapsedRow);

		VBox expandedContent = createExpandedContent(item);
		expandedContent.visibleProperty().bind(card.expandedProperty());
		expandedContent.managedProperty().bind(card.expandedProperty());
		card.getChildren().add(expandedContent);
		return card;
	}

	private VBox createExpandedContent(AppNotification item) {
		VBox expanded = new VBox(3);
		expanded.getStyleClass().add("notification-row-expanded");

		String actionType = normalize(item.getActionType());
		if (actionType != null) {
			expanded.getChildren().add(createExpandedLine("Action", actionType));
		}
		String eventKey = normalize(item.getEventKey());
		if (eventKey != null) {
			expanded.getChildren().add(createExpandedLine("Event", eventKey));
		}
		if (item.getDurableNotificationId() != null) {
			expanded.getChildren().add(createExpandedLine("Notification", "#" + item.getDurableNotificationId()));
		}
		if (expanded.getChildren().isEmpty()) {
			expanded.getChildren().add(createExpandedLine("Details", item.getSeverity().name()));
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
		button.setTooltip(new Tooltip(card.isExpanded() ? "Collapse notification" : "Expand notification"));
		card.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
			boolean expanded = Boolean.TRUE.equals(isExpanded);
			button.setText(expanded ? "▾" : "▸");
			button.setTooltip(new Tooltip(expanded ? "Collapse notification" : "Expand notification"));
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

	private Node createCaseMiniCard(AppNotification item) {
		Long caseId = item.getCaseId();
		if (caseId == null || caseId <= 0 || caseId > Integer.MAX_VALUE) {
			return null;
		}
		String caseName = resolveCaseContext(item);
		Node miniCard = caseCardFactory.create(
				new CaseCardModel(caseId, caseName == null ? "Case #" + caseId : caseName, null, null, null, null, false),
				CaseCardFactory.Variant.MINI);
		miniCard.getStyleClass().add("notification-row-case-mini-card");
		StackPane wrapper = new StackPane(miniCard);
		wrapper.getStyleClass().add("notification-row-case-mini");
		wrapper.setMaxWidth(180);
		wrapper.setOnMouseClicked(event -> event.consume());
		return wrapper;
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
