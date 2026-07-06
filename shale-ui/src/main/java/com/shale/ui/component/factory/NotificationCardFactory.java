package com.shale.ui.component.factory;

import com.shale.ui.component.NotificationCard;
import com.shale.ui.notification.AppNotification;
import com.shale.ui.notification.NotificationCategory;
import com.shale.ui.notification.NotificationGroup;
import com.shale.ui.notification.NotificationSeverity;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.List;
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
import javafx.scene.layout.VBox;

public final class NotificationCardFactory {
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
			.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
			.withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter EXPANDED_TIME_FORMATTER = DateTimeFormatter
			.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM)
			.withZone(ZoneId.systemDefault());

	public enum Variant {
		CENTER_ROW
	}

	public record NotificationCardModel(NotificationGroup group) {
		public NotificationCardModel {
			Objects.requireNonNull(group, "group");
		}

		public NotificationCardModel(AppNotification notification) {
			this(new NotificationGroup(NotificationGroup.groupKeyFor(notification), List.of(notification)));
		}
	}

	private final Consumer<AppNotification> onDismiss;
	private final CaseCardFactory caseCardFactory;
	private final Set<String> expandedGroupKeys = new HashSet<>();

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
		NotificationGroup group = model.group();
		AppNotification item = group.getLatestNotification();
		NotificationSeverity groupSeverity = group.getSeverity();

		NotificationCard card = new NotificationCard();
		card.setSeverity(groupSeverity);
		card.setUnread(group.isUnread());
		card.setExpanded(expandedGroupKeys.contains(group.getGroupKey()));

		Region unreadDot = new Region();
		unreadDot.getStyleClass().add("notification-card-unread-dot");
		if (!group.isUnread()) {
			unreadDot.getStyleClass().add("notification-card-unread-dot-read");
		}

		Label typeIcon = new Label(resolveIcon(item));
		typeIcon.getStyleClass().addAll("notification-card-icon", iconStyleClass(groupSeverity));
		VBox iconRail = new VBox(7, unreadDot, typeIcon);
		iconRail.getStyleClass().add("notification-card-icon-rail");
		iconRail.setAlignment(Pos.TOP_CENTER);

		Label category = new Label(resolveCategory(item));
		category.getStyleClass().add("notification-row-category");
		category.setTextOverrun(OverrunStyle.ELLIPSIS);
		Label severityLabel = createSeverityLabel(groupSeverity);

		Label title = new Label(item.getTitle());
		title.getStyleClass().add("notification-row-title");
		title.setWrapText(false);
		title.setTextOverrun(OverrunStyle.ELLIPSIS);
		title.setMinWidth(0);
		title.setMaxWidth(Double.MAX_VALUE);

		Label message = new Label(item.getMessage());
		message.getStyleClass().add("notification-row-message");
		message.setWrapText(true);
		message.setMinWidth(0);
		message.setMaxWidth(Double.MAX_VALUE);
		message.setMaxHeight(48);
		message.setTextOverrun(OverrunStyle.ELLIPSIS);

		VBox mainArea = new VBox(5, category, title, message);
		mainArea.getStyleClass().add("notification-row-main");
		mainArea.setMinWidth(0);
		mainArea.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(mainArea, Priority.ALWAYS);

		String entityContext = resolveEntityContext(item);
		if (entityContext != null) {
			Label contextLabel = new Label(entityContext);
			contextLabel.getStyleClass().add("notification-row-context");
			contextLabel.setWrapText(false);
			contextLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
			contextLabel.setMinWidth(0);
			contextLabel.setMaxWidth(Double.MAX_VALUE);
			mainArea.getChildren().add(contextLabel);
		}

		Label timestamp = new Label(TIME_FORMATTER.format(group.getLatestCreatedAt()));
		timestamp.getStyleClass().add("notification-row-time");
		timestamp.setTextOverrun(OverrunStyle.ELLIPSIS);
		timestamp.setMinWidth(0);
		timestamp.setMaxWidth(120);

		HBox topControls = new HBox(6);
		if (severityLabel != null) {
			topControls.getChildren().add(severityLabel);
		}
		topControls.getChildren().add(timestamp);
		if (group.getCount() > 1) {
			Label count = new Label(group.getCount() + " updates");
			count.getStyleClass().add("notification-row-category");
			topControls.getChildren().add(count);
		}
		Button dismissButton = createDismissButton(group);
		topControls.getChildren().add(dismissButton);
		topControls.getStyleClass().add("notification-row-controls");
		topControls.setAlignment(Pos.CENTER_RIGHT);

		Button expandButton = createExpandButton(group, card);
		Node caseCard = createCaseMiniCard(item);
		VBox rightArea = new VBox(8, topControls);
		rightArea.getStyleClass().add("notification-row-right");
		rightArea.setMinWidth(0);
		rightArea.setAlignment(Pos.TOP_RIGHT);
		if (caseCard != null) {
			rightArea.getChildren().add(caseCard);
		}
		rightArea.getChildren().add(expandButton);

		HBox collapsedRow = new HBox(14, iconRail, mainArea, rightArea);
		collapsedRow.getStyleClass().add("notification-row-collapsed");
		collapsedRow.setMinWidth(0);
		collapsedRow.setMaxWidth(Double.MAX_VALUE);
		collapsedRow.setAlignment(Pos.TOP_LEFT);
		card.getChildren().add(collapsedRow);

		VBox expandedContent = createExpandedContent(group, entityContext != null);
		expandedContent.visibleProperty().bind(card.expandedProperty());
		expandedContent.managedProperty().bind(card.expandedProperty());
		card.getChildren().add(expandedContent);
		return card;
	}

	private VBox createExpandedContent(NotificationGroup group, boolean entityContextVisible) {
		VBox expanded = new VBox(5);
		expanded.getStyleClass().add("notification-row-expanded");
		expanded.setMinWidth(0);
		expanded.setMaxWidth(Double.MAX_VALUE);

		if (group.getCount() == 1) {
			AppNotification item = group.getLatestNotification();
			String actionType = normalize(item.getActionType());
			if (actionType != null) {
				expanded.getChildren().add(createExpandedLine("Action", actionType));
			}
			expanded.getChildren().add(createExpandedLine("Created", EXPANDED_TIME_FORMATTER.format(item.getCreatedAt())));
			String actorDisplayName = normalize(item.getActorDisplayName());
			if (actorDisplayName != null) {
				expanded.getChildren().add(createExpandedLine("By", actorDisplayName));
			}
			if (!entityContextVisible) {
				String entityTitle = normalize(item.getEntityTitle());
				if (entityTitle != null) {
					expanded.getChildren().add(createExpandedLine("Entity", entityTitle));
				}
			}
			return expanded;
		}

		for (AppNotification child : group.getNotificationsNewestFirst()) {
			expanded.getChildren().add(createChildActivityRow(child));
		}
		return expanded;
	}

	private VBox createChildActivityRow(AppNotification item) {
		VBox row = new VBox(3);
		row.getStyleClass().add("notification-row-expanded-line");
		row.setMinWidth(0);
		row.setMaxWidth(Double.MAX_VALUE);

		String action = normalize(item.getActionType());
		String title = normalize(item.getTitle());
		String summary = action == null ? title : action + (title == null ? "" : " · " + title);
		row.getChildren().add(createExpandedLine(TIME_FORMATTER.format(item.getCreatedAt()), summary == null ? "Update" : summary));
		String message = normalize(item.getMessage());
		if (message != null) {
			row.getChildren().add(createExpandedLine("Message", message));
		}
		String actorDisplayName = normalize(item.getActorDisplayName());
		if (actorDisplayName != null) {
			row.getChildren().add(createExpandedLine("By", actorDisplayName));
		}
		return row;
	}

	private Label createExpandedLine(String label, String value) {
		Label line = new Label(label + ": " + value);
		line.getStyleClass().add("notification-row-expanded-line");
		line.setWrapText(true);
		line.setMinWidth(0);
		line.setMaxWidth(Double.MAX_VALUE);
		return line;
	}

	private Button createExpandButton(NotificationGroup group, NotificationCard card) {
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
				expandedGroupKeys.add(group.getGroupKey());
			} else {
				expandedGroupKeys.remove(group.getGroupKey());
			}
		});
		return button;
	}

	private static String iconStyleClass(NotificationSeverity severity) {
		return switch (severity == null ? NotificationSeverity.INFO : severity) {
		case CRITICAL -> "notification-icon-critical";
		case WARNING -> "notification-icon-warning";
		case INFO -> "notification-icon-info";
		};
	}

	private static Label createSeverityLabel(NotificationSeverity severity) {
		return switch (severity == null ? NotificationSeverity.INFO : severity) {
		case CRITICAL -> createSeverityLabel("Critical", "notification-row-severity-critical");
		case WARNING -> createSeverityLabel("Warning", "notification-row-severity-warning");
		case INFO -> null;
		};
	}

	private static Label createSeverityLabel(String text, String styleClass) {
		Label label = new Label(text);
		label.getStyleClass().addAll("notification-row-severity", styleClass);
		label.setTextOverrun(OverrunStyle.ELLIPSIS);
		return label;
	}

	private Button createDismissButton(NotificationGroup group) {
		Button button = new Button("×");
		button.getStyleClass().add("notification-row-dismiss");
		boolean sessionOnly = group.getNotificationsNewestFirst().stream()
				.allMatch(item -> item.getDurableNotificationId() == null);
		button.setTooltip(new Tooltip(sessionOnly ? "Dismiss for this session" : "Dismiss"));
		button.setOnAction(event -> {
			event.consume();
			if (onDismiss != null) {
				group.getNotificationsNewestFirst().forEach(onDismiss);
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
		Node caseCard = caseCardFactory.create(
				new CaseCardFactory.CaseCardModel(
						caseId,
						caseName == null ? "Case #" + caseId : caseName,
						null,
						null,
						item.getCaseResponsibleAttorney(),
						item.getCaseResponsibleAttorneyColor(),
						item.getCaseNonEngagementLetterSent()),
				CaseCardFactory.Variant.MINI);
		caseCard.getStyleClass().add("task-related-case-card");
		return caseCard;
	}

	private static String resolveCategory(AppNotification item) {
		NotificationCategory category = item.getCategory();
		return category == null ? "NOTIFICATION" : category.name();
	}

	private static String resolveIcon(AppNotification item) {
		String action = normalize(item.getActionType());
		String normalizedAction = action == null ? "" : action.toUpperCase(Locale.ROOT);
		NotificationCategory category = item.getCategory();
		if (category == NotificationCategory.CALENDAR) {
			return "📅";
		}
		if (normalizedAction.contains("ASSIGN")) {
			return "👤";
		}
		if (normalizedAction.contains("NOTE")) {
			return "💬";
		}
		if (normalizedAction.contains("DUE") || normalizedAction.contains("OVERDUE")) {
			return "⏰";
		}
		if (category == NotificationCategory.NETWORK || category == NotificationCategory.CONNECTIVITY) {
			return "⚠";
		}
		if (category == NotificationCategory.APP_UPDATE || category == NotificationCategory.SYSTEM) {
			return "ⓘ";
		}
		if (category == NotificationCategory.TASK) {
			return "✎";
		}
		return "•";
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
