package com.shale.ui.component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;


import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.component.factory.TaskCardFactory.AssignedUserModel;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.util.ColorUtil;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.layout.VBox;

public final class TaskCard extends VBox {

	public enum Variant {
		FULL, MY_TASKS, COMPACT, COMPACT_FLUID, MINI
	}

	private static final DateTimeFormatter DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
	private static final DateTimeFormatter DUE_DATE_COMPACT_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
	private static final double COMPACT_CARD_WIDTH = 280;
	private static final double TASK_DETAILS_TOOLTIP_MAX_WIDTH = 360;
	private static final double TASK_DETAILS_TOOLTIP_MAX_DESCRIPTION_HEIGHT = 220;
	private static final double TASK_DETAILS_TOOLTIP_LINE_HEIGHT = 17;

	private final Label titleLabel = new Label();
	private final Label dueLabel = new Label();
	private final Label createdByLabel = new Label();
	private final Label descriptionLabel = new Label();
	private final Label completedLabel = new Label();
	private final Label statusPill = new Label();
	private final Region dueAccentBar = new Region();
	private final HBox cardRow = new HBox(0);
	private final VBox bodyPane = new VBox(6);
	private final StackPane relatedCaseHost = new StackPane();
	private final StackPane assigneeHost = new StackPane();
	private final VBox compactTitleBlock = new VBox(2, titleLabel, createdByLabel, dueLabel);
	private final Region compactHeaderSpacer = new Region();
	private final HBox compactTitleRow = new HBox(8, compactTitleBlock, compactHeaderSpacer, statusPill);
	private final Label caseSectionLabel = new Label("Case:");
	private final VBox caseSection = new VBox(3, caseSectionLabel, relatedCaseHost);
	private final Label teamSectionLabel = new Label("Team:");
	private final VBox teamSection = new VBox(3, teamSectionLabel, assigneeHost);
	private final Region compactMetadataSpacer = new Region();
	private final HBox compactMetadataRow = new HBox(8, caseSection, compactMetadataSpacer, teamSection);
	private final Button toggleCompleteButton = new Button();
	private final Region actionsSpacer = new Region();
	private final HBox actionsRow = new HBox(8, actionsSpacer, toggleCompleteButton);
	private final Button expandDetailsButton = new Button("+");
	private final VBox fullHeaderText = new VBox(2, titleLabel, dueLabel);
	private final StackPane myTasksTitleRow = new StackPane(titleLabel);
	private final Region myTasksMetadataSpacer = new Region();
	private final HBox myTasksMetadataRow = new HBox(8, dueLabel, myTasksMetadataSpacer, statusPill, expandDetailsButton);
	private final VBox myTasksMetadataBlock = new VBox(4, myTasksMetadataRow, relatedCaseHost);
	private final Region fullHeaderSpacer = new Region();
	private final HBox fullHeaderRow = new HBox(8, fullHeaderText, fullHeaderSpacer, statusPill, expandDetailsButton);
	private final VBox fullExpandedContent = new VBox(6, createdByLabel, teamSection, descriptionLabel, completedLabel, actionsRow);
	private final UserCardFactory userCardFactory = new UserCardFactory(id -> {
	});
	private final CaseCardFactory caseCardFactory = new CaseCardFactory(id -> {
	});

	private Long taskId;
	private Long relatedCaseId;
	private String relatedCaseName = "";
	private String relatedCasePrimaryStatusName = "";
	private String relatedCasePrimaryStatusColor = "";
	private String relatedCasePracticeAreaColor = "";
	private String relatedCaseResponsibleAttorney = "";
	private String relatedCaseResponsibleAttorneyColor = "";
	private Boolean relatedCaseNonEngagementLetterSent;
	private LocalDateTime dueAtValue;
	private Variant currentVariant = Variant.MINI;
	private Consumer<Long> onOpen;
	private Consumer<Long> onToggleComplete;
	private Consumer<Integer> onOpenAssigneeUser;
	private Consumer<Integer> onOpenRelatedCase;
	private String backgroundCss;
	private String dueAccentCss;
	private String statusColorCss = "#F1F5F9";
	private boolean hovered;
	private boolean fullExpanded;
	private String fullDescription = "";
	private Tooltip taskDetailsTooltip;

	public TaskCard() {
		setCursor(Cursor.HAND);
		wireEvents();
		applyMini();
	}

	public void setTaskId(Long taskId) {
		this.taskId = taskId;
	}

	public void setOnOpen(Consumer<Long> onOpen) {
		this.onOpen = onOpen;
	}

	public void setOnToggleComplete(Consumer<Long> onToggleComplete) {
		this.onToggleComplete = onToggleComplete;
	}

	public void setOnOpenAssigneeUser(Consumer<Integer> onOpenAssigneeUser) {
		this.onOpenAssigneeUser = onOpenAssigneeUser;
	}

	public void setOnOpenRelatedCase(Consumer<Integer> onOpenRelatedCase) {
		this.onOpenRelatedCase = onOpenRelatedCase;
	}

	public void setTitle(String title) {
		titleLabel.setText((title == null || title.isBlank()) ? "Untitled task" : title.trim());
		refreshTaskDetailsTooltip();
	}

	public void setDueAt(LocalDateTime dueAt) {
		dueAtValue = dueAt;
		if (dueAt == null) {
			dueLabel.setText("");
			dueLabel.setManaged(false);
			dueLabel.setVisible(false);
			return;
		}

		String formattedDueAt = switch (currentVariant) {
			case COMPACT -> DUE_DATE_COMPACT_FORMAT.format(dueAt);
			default -> DUE_DATE_FORMAT.format(dueAt);
		};
		dueLabel.setText("Due " + formattedDueAt);
		dueLabel.setManaged(true);
		dueLabel.setVisible(true);
	}

	public void setDescriptionPreview(String description) {
		String fullText = normalizeTaskDetailsText(description);
		fullDescription = fullText;
		String text = fullText;
		if (text.length() > 140) {
			text = text.substring(0, 137) + "...";
		}
		descriptionLabel.setText(text);
		boolean hasText = !text.isBlank();
		descriptionLabel.setManaged(hasText);
		descriptionLabel.setVisible(hasText);
		refreshTaskDetailsTooltip();
	}

	public void setCreatedByDisplayName(String createdByDisplayName) {
		String normalized = createdByDisplayName == null ? "" : createdByDisplayName.trim();
		createdByLabel.setText("Created by: " + (normalized.isBlank() ? "Unknown" : normalized));
		createdByLabel.setManaged(true);
		createdByLabel.setVisible(true);
	}

	public void setCompleted(boolean completed) {
		completedLabel.setManaged(completed);
		completedLabel.setVisible(completed);
		completedLabel.setText(completed ? "Completed" : "");
		toggleCompleteButton.setText(completed ? "Mark Incomplete" : "Complete");
		setOpacity(completed ? 0.9 : 1.0);
	}

	public void setAssignees(List<AssignedUserModel> users) {
		List<AssignedUserModel> safeUsers = users == null ? List.of() : users;
		if (safeUsers.isEmpty()) {
			assigneeHost.getChildren().clear();
			teamSection.setManaged(false);
			teamSection.setVisible(false);
			return;
		}
		VBox cards = new VBox(4);
		int maxVisible = 3;
		for (int i = 0; i < safeUsers.size() && i < maxVisible; i++) {
			AssignedUserModel user = safeUsers.get(i);
			if (user == null || user.userId() <= 0 || user.displayName() == null || user.displayName().isBlank()) {
				continue;
			}
			var assigneeCard = userCardFactory.create(
					new UserCardModel(user.userId(), user.displayName().trim(), user.colorCss(), null),
					UserCardFactory.Variant.MINI);
			int selectedUserId = user.userId();
			assigneeCard.setOnMouseClicked(e -> {
				e.consume();
				if (onOpenAssigneeUser != null) {
					onOpenAssigneeUser.accept(selectedUserId);
				}
			});
			cards.getChildren().add(assigneeCard);
		}
		if (safeUsers.size() > maxVisible) {
			Label moreLabel = new Label("+" + (safeUsers.size() - maxVisible) + " more");
			moreLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(17,37,66,0.62);");
			cards.getChildren().add(moreLabel);
		}
		assigneeHost.getChildren().setAll(cards);
		teamSection.setManaged(true);
		teamSection.setVisible(true);
	}

	public void setRelatedCase(Long caseId, String caseName, String casePrimaryStatusName, String casePrimaryStatusColor,
			String casePracticeAreaColor, String responsibleAttorney, String responsibleAttorneyColor,
			Boolean nonEngagementLetterSent) {
		relatedCaseId = caseId;
		relatedCaseName = caseName == null ? "" : caseName.trim();
		relatedCasePrimaryStatusName = casePrimaryStatusName == null ? "" : casePrimaryStatusName.trim();
		relatedCasePrimaryStatusColor = casePrimaryStatusColor == null ? "" : casePrimaryStatusColor.trim();
		relatedCasePracticeAreaColor = casePracticeAreaColor == null ? "" : casePracticeAreaColor.trim();
		relatedCaseResponsibleAttorney = responsibleAttorney == null ? "" : responsibleAttorney.trim();
		relatedCaseResponsibleAttorneyColor = responsibleAttorneyColor == null ? "" : responsibleAttorneyColor.trim();
		relatedCaseNonEngagementLetterSent = nonEngagementLetterSent;
		renderRelatedCaseCard();
	}

	public void setPriorityBackgroundColor(String storedColor) {
		this.backgroundCss = priorityGradientCss(storedColor);
		refreshSurfaceStyle();
	}

	public void setTaskStatus(String statusName, String statusColor) {
		statusColorCss = CaseCard.normalizeColor(statusColor, "#F1F5F9");
		statusPill.setText(statusName == null || statusName.isBlank() ? "—" : statusName.trim());
		statusPill.setStyle(statusPillStyle());
	}

	public void setBorderByDueState(LocalDateTime dueAt, LocalDateTime completedAt) {
		if (completedAt != null) {
			dueAccentCss = "#16a34a";
			refreshSurfaceStyle();
			return;
		}
		if (dueAt == null) {
			dueAccentCss = null;
			refreshSurfaceStyle();
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		if (dueAt.isBefore(now)) {
			dueAccentCss = "#7f1d1d";
		} else if (!dueAt.isAfter(now.plusDays(1))) {
			dueAccentCss = "#dc2626";
		} else if (!dueAt.isAfter(now.plusWeeks(1))) {
			dueAccentCss = "#f97316";
		} else if (!dueAt.isAfter(now.plusWeeks(2))) {
			dueAccentCss = "#eab308";
		} else {
			dueAccentCss = null;
		}
		refreshSurfaceStyle();
	}

	public void applyMini() {
		currentVariant = Variant.MINI;
		bodyPane.getChildren().setAll(titleLabel);
		getChildren().setAll(cardRow);
		setSpacing(2);
		setPadding(new Insets(4, 10, 4, 10));
		setMaxWidth(Region.USE_COMPUTED_SIZE);
		setPrefWidth(Region.USE_COMPUTED_SIZE);
		titleLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");
		refreshSurfaceStyle();
	}

	public void applyCompact() {
		currentVariant = Variant.COMPACT;
		setDueAt(dueAtValue);
		compactTitleBlock.getChildren().setAll(titleLabel, createdByLabel, dueLabel);
		compactTitleRow.getChildren().setAll(compactTitleBlock, compactHeaderSpacer, statusPill);
		bodyPane.getChildren().setAll(compactTitleRow, compactMetadataRow, completedLabel);
		getChildren().setAll(cardRow);
		setSpacing(3);
		setPadding(new Insets(6, 8, 6, 8));
		setAlignment(Pos.TOP_LEFT);
		setMinWidth(COMPACT_CARD_WIDTH);
		setPrefWidth(COMPACT_CARD_WIDTH);
		setMaxWidth(COMPACT_CARD_WIDTH);
		titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #112542;");
		dueLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.72);");
		createdByLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: 500; -fx-text-fill: rgba(17,37,66,0.62);");
		titleLabel.setWrapText(false);
		titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
		titleLabel.setMinWidth(0);
		titleLabel.setMaxWidth(Double.MAX_VALUE);
		compactTitleBlock.setMinWidth(0);
		compactTitleBlock.setSpacing(1);
		dueLabel.setWrapText(false);
		compactTitleRow.setAlignment(Pos.CENTER_LEFT);
		configureRelatedSections();
		completedLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: rgba(22,101,52,0.95);");
		compactMetadataRow.setAlignment(Pos.TOP_LEFT);
		compactMetadataRow.getStyleClass().setAll("app-taskcard-compact-meta-row");
		caseSection.getStyleClass().setAll("app-taskcard-compact-meta-section");
		teamSection.getStyleClass().setAll("app-taskcard-compact-meta-section");
		compactTitleRow.getStyleClass().setAll("app-taskcard-compact-title-row");
		compactTitleBlock.getStyleClass().setAll("app-taskcard-compact-title-block");
		caseSection.setMinWidth(0);
		teamSection.setMinWidth(0);
		refreshSurfaceStyle();
	}

	public void applyCompactFluid() {
		applyCompact();
		currentVariant = Variant.COMPACT_FLUID;
		setDueAt(dueAtValue);
		setMinWidth(Region.USE_COMPUTED_SIZE);
		setPrefWidth(Region.USE_COMPUTED_SIZE);
		setMaxWidth(Double.MAX_VALUE);
	}

	public void applyMyTasks() {
		applyFull();
		currentVariant = Variant.MY_TASKS;
		myTasksTitleRow.getChildren().setAll(titleLabel);
		myTasksMetadataRow.getChildren().setAll(dueLabel, myTasksMetadataSpacer, statusPill, expandDetailsButton);
		myTasksMetadataBlock.getChildren().setAll(myTasksMetadataRow, relatedCaseHost);
		bodyPane.getChildren().setAll(myTasksTitleRow, myTasksMetadataBlock, fullExpandedContent);
		myTasksTitleRow.setAlignment(Pos.CENTER_LEFT);
		myTasksTitleRow.setMinWidth(0);
		myTasksTitleRow.setMaxWidth(Double.MAX_VALUE);
		myTasksMetadataRow.setAlignment(Pos.CENTER_LEFT);
		myTasksMetadataRow.setMinWidth(0);
		myTasksMetadataRow.setMaxWidth(Double.MAX_VALUE);
		myTasksMetadataBlock.setAlignment(Pos.TOP_LEFT);
		myTasksMetadataBlock.setFillWidth(true);
		myTasksMetadataBlock.setMinWidth(0);
		myTasksMetadataBlock.setMaxWidth(Double.MAX_VALUE);
		relatedCaseHost.setMinWidth(0);
		relatedCaseHost.setMaxWidth(Double.MAX_VALUE);
		renderRelatedCaseCard();
	}

	public void applyFull() {
		currentVariant = Variant.FULL;
		fullHeaderText.getChildren().setAll(titleLabel, dueLabel);
		setDueAt(dueAtValue);
		configureRelatedSections();
		titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #112542;");
		titleLabel.setWrapText(false);
		titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
		titleLabel.setMinWidth(0);
		titleLabel.setMaxWidth(Double.MAX_VALUE);
		dueLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.72);");
		dueLabel.setWrapText(false);
		dueLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
		createdByLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 500; -fx-text-fill: rgba(17,37,66,0.62);");
		descriptionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.78);");
		descriptionLabel.setWrapText(true);
		completedLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: rgba(22,101,52,0.95);");
		setSpacing(6);
		setPadding(new Insets(8, 10, 8, 10));
		setAlignment(Pos.TOP_LEFT);
		setMinWidth(0);
		setPrefWidth(Region.USE_COMPUTED_SIZE);
		setMaxWidth(Double.MAX_VALUE);
		actionsRow.setAlignment(Pos.CENTER_RIGHT);

		bodyPane.getChildren().setAll(fullHeaderRow, fullExpandedContent);
		getChildren().setAll(cardRow);
		setFullExpanded(false);
	}

	private void wireEvents() {
		HBox.setHgrow(compactTitleBlock, javafx.scene.layout.Priority.ALWAYS);
		HBox.setHgrow(compactHeaderSpacer, javafx.scene.layout.Priority.ALWAYS);
		HBox.setHgrow(compactMetadataSpacer, javafx.scene.layout.Priority.ALWAYS);
		HBox.setHgrow(actionsSpacer, javafx.scene.layout.Priority.ALWAYS);
		HBox.setHgrow(fullHeaderText, javafx.scene.layout.Priority.ALWAYS);
		HBox.setHgrow(fullHeaderSpacer, javafx.scene.layout.Priority.ALWAYS);
		HBox.setHgrow(myTasksMetadataSpacer, javafx.scene.layout.Priority.ALWAYS);
		HBox.setHgrow(bodyPane, javafx.scene.layout.Priority.ALWAYS);
		HBox.setHgrow(dueAccentBar, javafx.scene.layout.Priority.NEVER);
		getStyleClass().addAll("task-card", "shale-entity-card", "shale-entity-card-clickable");
		dueAccentBar.getStyleClass().add("task-card__due-accent-bar");
		bodyPane.getStyleClass().add("task-card__body");
		statusPill.getStyleClass().addAll("task-card__status-pill", "shale-status-pill", "shale-status-pill-compact");
		statusPill.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
		statusPill.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
		cardRow.getChildren().setAll(dueAccentBar, bodyPane);
		dueAccentBar.setMinWidth(7);
		dueAccentBar.setPrefWidth(7);
		dueAccentBar.setMaxWidth(7);
		HBox.setMargin(dueAccentBar, new Insets(8, 0, 8, 8));
		bodyPane.setPadding(new Insets(8, 10, 8, 10));
		toggleCompleteButton.getStyleClass().addAll(
				"app-toolbar-button",
				"app-toolbar-button-success",
				"app-taskcard-action-button");
		expandDetailsButton.getStyleClass().addAll("app-toolbar-button", "app-toolbar-button-neutral");
		expandDetailsButton.setFocusTraversable(false);
		expandDetailsButton.setMinSize(20, 20);
		expandDetailsButton.setPrefSize(20, 20);
		expandDetailsButton.setMaxSize(20, 20);
		expandDetailsButton.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-padding: 0 0 0 0;");
		expandDetailsButton.setOnAction(e -> {
			e.consume();
			if (currentVariant != Variant.FULL && currentVariant != Variant.MY_TASKS) {
				return;
			}
			setFullExpanded(!fullExpanded);
		});
		toggleCompleteButton.setOnAction(e ->
		{
			e.consume();
			if (onToggleComplete != null && taskId != null) {
				onToggleComplete.accept(taskId);
			}
		});
		setOnMouseEntered(e ->
		{
			hovered = true;
			setTranslateY(-1.5);
			refreshSurfaceStyle();
		});
		setOnMouseExited(e ->
		{
			hovered = false;
			setTranslateY(0);
			refreshSurfaceStyle();
		});
		setOnMouseClicked(e ->
		{
			if (e.isConsumed()) {
				return;
			}
			if (onOpen != null && taskId != null) {
				onOpen.accept(taskId);
			}
		});
		setAssignees(List.of());
		setRelatedCase(null, null, null, null, null, null, null, null);
	}

	private void setFullExpanded(boolean expanded) {
		fullExpanded = expanded;
		if (currentVariant == Variant.FULL || currentVariant == Variant.MY_TASKS) {
			fullExpandedContent.setManaged(expanded);
			fullExpandedContent.setVisible(expanded);
			expandDetailsButton.setText(expanded ? "−" : "+");
		}
	}

	private void refreshTaskDetailsTooltip() {
		if (taskDetailsTooltip != null) {
			Tooltip.uninstall(this, taskDetailsTooltip);
		}
		taskDetailsTooltip = buildTaskDetailsTooltip(titleLabel.getText(), fullDescription);
		Tooltip.install(this, taskDetailsTooltip);
	}

	static Tooltip buildTaskDetailsTooltip(String title, String description) {
		Label titleNode = new Label((title == null || title.isBlank()) ? "Untitled task" : title.trim());
		titleNode.setWrapText(true);
		titleNode.setMinHeight(Region.USE_PREF_SIZE);
		titleNode.setPrefHeight(Region.USE_COMPUTED_SIZE);
		titleNode.setMaxHeight(Region.USE_PREF_SIZE);
		titleNode.setMaxWidth(TASK_DETAILS_TOOLTIP_MAX_WIDTH);
		titleNode.setStyle("-fx-font-size: 13px; -fx-font-weight: 800;");

		String normalizedDescription = normalizeTaskDetailsText(description);
		VBox content = new VBox(4, titleNode);
		content.setFillWidth(true);
		content.setMinHeight(Region.USE_PREF_SIZE);
		content.setPrefHeight(Region.USE_COMPUTED_SIZE);
		content.setMaxHeight(Region.USE_PREF_SIZE);
		content.setMaxWidth(TASK_DETAILS_TOOLTIP_MAX_WIDTH);
		VBox.setVgrow(titleNode, javafx.scene.layout.Priority.NEVER);
		if (!normalizedDescription.isBlank()) {
			Label descriptionNode = new Label(normalizedDescription);
			descriptionNode.setWrapText(true);
			descriptionNode.setMinHeight(Region.USE_PREF_SIZE);
			descriptionNode.setPrefHeight(Region.USE_COMPUTED_SIZE);
			descriptionNode.setMaxHeight(Region.USE_PREF_SIZE);
			descriptionNode.setMaxWidth(TASK_DETAILS_TOOLTIP_MAX_WIDTH);
			descriptionNode.setStyle("-fx-font-size: 12px; -fx-line-spacing: 1px;");
			if (estimatedTooltipDescriptionHeight(normalizedDescription) > TASK_DETAILS_TOOLTIP_MAX_DESCRIPTION_HEIGHT) {
				ScrollPane scroller = new ScrollPane(descriptionNode);
				scroller.setFitToWidth(true);
				scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
				scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
				scroller.setMinHeight(Region.USE_PREF_SIZE);
				scroller.setPrefViewportHeight(TASK_DETAILS_TOOLTIP_MAX_DESCRIPTION_HEIGHT);
				scroller.setMaxHeight(TASK_DETAILS_TOOLTIP_MAX_DESCRIPTION_HEIGHT);
				scroller.setMaxWidth(TASK_DETAILS_TOOLTIP_MAX_WIDTH);
				VBox.setVgrow(scroller, javafx.scene.layout.Priority.NEVER);
				content.getChildren().add(scroller);
			} else {
				VBox.setVgrow(descriptionNode, javafx.scene.layout.Priority.NEVER);
				content.getChildren().add(descriptionNode);
			}
		}

		Tooltip tooltip = new Tooltip();
		tooltip.setGraphic(content);
		tooltip.setText(null);
		tooltip.setMinHeight(Region.USE_PREF_SIZE);
		tooltip.setPrefHeight(Region.USE_COMPUTED_SIZE);
		tooltip.setMaxHeight(Region.USE_PREF_SIZE);
		tooltip.setMaxWidth(TASK_DETAILS_TOOLTIP_MAX_WIDTH);
		return tooltip;
	}

	static double estimatedTooltipDescriptionHeight(String text) {
		String normalized = normalizeTaskDetailsText(text);
		if (normalized.isBlank()) {
			return 0;
		}
		int logicalLines = normalized.split("\n", -1).length;
		int wrapLines = Math.max(0, normalized.length() / 54);
		return Math.max(logicalLines, wrapLines + 1) * TASK_DETAILS_TOOLTIP_LINE_HEIGHT;
	}

	static String normalizeTaskDetailsText(String text) {
		if (text == null) {
			return "";
		}
		return text
				.replace("\r\n", "\n")
				.replace('\r', '\n')
				.replaceAll("[\\t ]+\\n", "\n")
				.replaceAll("\\n[\\t ]+", "\n")
				.replaceAll("\\n{3,}", "\n\n")
				.trim();
	}

	private void configureRelatedSections() {
		caseSection.getChildren().setAll(caseSectionLabel, relatedCaseHost);
		teamSection.getChildren().setAll(teamSectionLabel, assigneeHost);
		String sectionLabelStyle = currentVariant == Variant.COMPACT || currentVariant == Variant.COMPACT_FLUID
				? "-fx-font-size: 9px; -fx-font-weight: 700; -fx-text-fill: rgba(17,37,66,0.62);"
				: "-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: rgba(17,37,66,0.62);";
		caseSectionLabel.setStyle(sectionLabelStyle);
		teamSectionLabel.setStyle(sectionLabelStyle);
		relatedCaseHost.setAlignment(Pos.CENTER_LEFT);
		relatedCaseHost.setMinWidth(0);
		relatedCaseHost.setMaxWidth(currentVariant == Variant.MY_TASKS ? Double.MAX_VALUE : Region.USE_PREF_SIZE);
		assigneeHost.setAlignment(Pos.CENTER_LEFT);
		assigneeHost.setMaxWidth(Region.USE_PREF_SIZE);
	}

	private void renderRelatedCaseCard() {
		boolean hasCase = relatedCaseId != null && relatedCaseId > 0 && !relatedCaseName.isBlank();
		if (!hasCase) {
			relatedCaseHost.getChildren().clear();
			relatedCaseHost.setManaged(false);
			relatedCaseHost.setVisible(false);
			caseSection.setManaged(false);
			caseSection.setVisible(false);
			return;
		}
		var caseCard = caseCardFactory.create(
				new CaseCardModel(relatedCaseId, relatedCaseName, null, null, relatedCaseResponsibleAttorney,
						relatedCaseResponsibleAttorneyColor, relatedCaseNonEngagementLetterSent,
						relatedCasePrimaryStatusName, relatedCasePrimaryStatusColor, relatedCasePracticeAreaColor),
				CaseCardFactory.Variant.EMBEDDED);
		if (caseCard instanceof Region region && currentVariant == Variant.MY_TASKS) {
			region.setMinWidth(0);
			region.setPrefWidth(Region.USE_COMPUTED_SIZE);
			region.setMaxWidth(Double.MAX_VALUE);
			relatedCaseHost.setMinWidth(0);
			relatedCaseHost.setMaxWidth(Double.MAX_VALUE);
		}
		caseCard.setOnMouseClicked(e -> {
			e.consume();
			if (onOpenRelatedCase != null) {
				onOpenRelatedCase.accept(relatedCaseId.intValue());
			}
		});
		relatedCaseHost.getChildren().setAll(caseCard);
		relatedCaseHost.setManaged(true);
		relatedCaseHost.setVisible(true);
		caseSection.setManaged(true);
		caseSection.setVisible(true);
	}

	private void refreshSurfaceStyle() {
		setStyle(CardSurfaceStyles.cardContainerStyle(backgroundCss, hovered));
		bodyPane.setStyle("-fx-background-color: transparent;");
		dueAccentBar.setStyle("-fx-background-color: " + (dueAccentCss == null || dueAccentCss.isBlank() ? "#CBD5E1" : dueAccentCss) + "; -fx-background-radius: 999;");
		statusPill.setStyle(statusPillStyle());
	}

	private String statusPillStyle() {
		return StatusPillStyles.pillStyle("-fx-font-size: 10px; -fx-font-weight: 800;", statusColorCss);
	}

	private String priorityGradientCss(String storedColor) {
		String css = ColorUtil.toCssBackgroundColorOrNull(storedColor);
		return css == null ? null : EntityCardGradientStyles.caseStrengthGradient(css, false);
	}

}
