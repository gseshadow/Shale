package com.shale.ui.component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.component.factory.TaskCardFactory.AssignedUserModel;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.DueProximityStyles;
import com.shale.ui.util.ColorUtil;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.AccessibleRole;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Window;

public final class TaskCard extends VBox {

	public enum Variant {
		FULL, MY_TASKS, USER_ASSIGNED_TASKS, COMPACT, COMPACT_FLUID, MINI
	}

	private static final DateTimeFormatter DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
	private static final DateTimeFormatter DUE_DATE_COMPACT_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
	private static final double COMPACT_CARD_WIDTH = 280;
	private static final double USER_ASSIGNED_VISUAL_BOTTOM_INSET = 10;
	private static final double TASK_DETAILS_TOOLTIP_MAX_WIDTH = 360;
	private static final Duration TASK_DETAILS_TOOLTIP_HIDE_DELAY = Duration.millis(120);
	private static final Duration TASK_DETAILS_POPUP_SHOW_DELAY = Duration.millis(400);
	private static final double TASK_DETAILS_POPUP_CURSOR_OFFSET = 10;

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
	private List<CaseCardFactory.PresentationDate> relatedCasePresentationDates = List.of();
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
	private boolean completed;
	private boolean overdue;
	private boolean dueSoon;
	private boolean fullExpanded;
	private String fullDescription = "";
	private Popup taskDetailsPopup;
	private Parent taskDetailsPopupContent;
	private final PauseTransition taskDetailsPopupHideDelay = new PauseTransition(TASK_DETAILS_TOOLTIP_HIDE_DELAY);
	private final PauseTransition taskDetailsPopupShowDelay = new PauseTransition(TASK_DETAILS_POPUP_SHOW_DELAY);
	private boolean taskDetailsPopupMouseOver;

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
		setAccessibleText("Task: " + titleLabel.getText());
		titleLabel.setTooltip(new Tooltip(titleLabel.getText()));
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
		this.completed = completed;
		completedLabel.setManaged(completed);
		completedLabel.setVisible(completed);
		completedLabel.setText(completed ? "Completed" : "");
		toggleCompleteButton.setText(completed ? "Mark Incomplete" : "Complete");
		setOpacity(completed ? 0.9 : 1.0);
		pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("completed"), completed);
		refreshDuePresentation();
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
			moreLabel.getStyleClass().add("task-card__metadata");
			moreLabel.setStyle("-fx-font-size: 10px;");
			cards.getChildren().add(moreLabel);
		}
		assigneeHost.getChildren().setAll(cards);
		teamSection.setManaged(true);
		teamSection.setVisible(true);
	}

	public void setRelatedCase(Long caseId, String caseName, String casePrimaryStatusName, String casePrimaryStatusColor,
			String casePracticeAreaColor, String responsibleAttorney, String responsibleAttorneyColor,
			Boolean nonEngagementLetterSent, List<CaseCardFactory.PresentationDate> presentationDates) {
		relatedCaseId = caseId;
		relatedCaseName = caseName == null ? "" : caseName.trim();
		relatedCasePrimaryStatusName = casePrimaryStatusName == null ? "" : casePrimaryStatusName.trim();
		relatedCasePrimaryStatusColor = casePrimaryStatusColor == null ? "" : casePrimaryStatusColor.trim();
		relatedCasePracticeAreaColor = casePracticeAreaColor == null ? "" : casePracticeAreaColor.trim();
		relatedCaseResponsibleAttorney = responsibleAttorney == null ? "" : responsibleAttorney.trim();
		relatedCaseResponsibleAttorneyColor = responsibleAttorneyColor == null ? "" : responsibleAttorneyColor.trim();
		relatedCaseNonEngagementLetterSent = nonEngagementLetterSent;
		relatedCasePresentationDates = List.copyOf(presentationDates == null ? List.of() : presentationDates);
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
		dueAccentCss = DueProximityStyles.accentColor(dueAt, completedAt);
		LocalDateTime now = LocalDateTime.now();
		overdue = completedAt == null && dueAt != null && dueAt.isBefore(now);
		dueSoon = completedAt == null && dueAt != null && !overdue && !dueAt.isAfter(now.plusDays(1));
		pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("overdue"), overdue);
		pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("due-soon"), dueSoon);
		refreshDuePresentation();
		refreshSurfaceStyle();
	}

	private void refreshDuePresentation() {
		if (dueAtValue == null) return;
		String date = (currentVariant == Variant.COMPACT ? DUE_DATE_COMPACT_FORMAT : DUE_DATE_FORMAT).format(dueAtValue);
		String cue = overdue ? "Overdue · Due " : dueSoon ? "Due soon · Due " : "Due ";
		dueLabel.setText(cue + date);
		dueLabel.setAccessibleText((completed ? "Completed. " : "") + dueLabel.getText());
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
		titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;");
		dueLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: 600;");
		createdByLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: 500;");
		titleLabel.setWrapText(false);
		titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
		titleLabel.setMinWidth(0);
		titleLabel.setMaxWidth(Double.MAX_VALUE);
		compactTitleBlock.setMinWidth(0);
		compactTitleBlock.setSpacing(1);
		dueLabel.setWrapText(false);
		compactTitleRow.setAlignment(Pos.CENTER_LEFT);
		configureRelatedSections();
		completedLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: 700;");
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

	/**
	 * Fluid list variant used by User View's vertically scrolling Assigned Tasks
	 * column. Unlike the My Shale board variant, every container in this path
	 * participates in managed, computed-height layout and can shrink to the
	 * ScrollPane viewport after its vertical bar consumes width.
	 */
	public void applyUserAssignedTasks() {
		applyMyTasks();
		currentVariant = Variant.USER_ASSIGNED_TASKS;

		setMinHeight(Region.USE_COMPUTED_SIZE);
		setPrefHeight(Region.USE_COMPUTED_SIZE);
		setMaxHeight(Region.USE_COMPUTED_SIZE);
		cardRow.setMinSize(0, Region.USE_COMPUTED_SIZE);
		cardRow.setPrefHeight(Region.USE_COMPUTED_SIZE);
		cardRow.setMaxSize(Double.MAX_VALUE, Region.USE_COMPUTED_SIZE);
		bodyPane.setMinSize(0, Region.USE_COMPUTED_SIZE);
		bodyPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
		bodyPane.setMaxSize(Double.MAX_VALUE, Region.USE_COMPUTED_SIZE);
		myTasksMetadataBlock.setMinHeight(Region.USE_COMPUTED_SIZE);
		myTasksMetadataBlock.setPrefHeight(Region.USE_COMPUTED_SIZE);
		myTasksMetadataBlock.setMaxHeight(Region.USE_COMPUTED_SIZE);
		myTasksMetadataBlock.getStyleClass().setAll("app-taskcard-user-assigned-metadata");
		// The nested CaseCard/ContactCard shadows are part of boundsInLocal, but JavaFX
		// intentionally excludes effects from managed layoutBounds. Extend this card's
		// painted surface over that visual edge without changing list spacing or fixing height.
		setPadding(new Insets(8, 10, 8 + USER_ASSIGNED_VISUAL_BOTTOM_INSET, 10));
		renderRelatedCaseCard();
	}

	public void applyFull() {
		currentVariant = Variant.FULL;
		fullHeaderText.getChildren().setAll(titleLabel, dueLabel);
		setDueAt(dueAtValue);
		configureRelatedSections();
		titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");
		titleLabel.setWrapText(false);
		titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
		titleLabel.setMinWidth(0);
		titleLabel.setMaxWidth(Double.MAX_VALUE);
		dueLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 600;");
		dueLabel.setWrapText(false);
		dueLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
		createdByLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 500;");
		descriptionLabel.setStyle("-fx-font-size: 12px;");
		descriptionLabel.setWrapText(true);
		completedLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700;");
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
		setFocusTraversable(true);
		setAccessibleRole(AccessibleRole.BUTTON);
		titleLabel.getStyleClass().add("task-card__title");
		dueLabel.getStyleClass().add("task-card__due");
		createdByLabel.getStyleClass().add("task-card__creator");
		descriptionLabel.getStyleClass().add("task-card__description");
		completedLabel.getStyleClass().add("task-card__completed-cue");
		caseSectionLabel.getStyleClass().add("task-card__metadata");
		teamSectionLabel.getStyleClass().add("task-card__metadata");
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
		ControlStyles.apply(toggleCompleteButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
		toggleCompleteButton.getStyleClass().add("app-taskcard-action-button");
		ControlStyles.apply(expandDetailsButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
		ControlStyles.iconOnly(expandDetailsButton);
		expandDetailsButton.setTooltip(new Tooltip("Expand task card details"));
		expandDetailsButton.setAccessibleText("Expand task card details");
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
			scheduleTaskDetailsPopupShow();
		});
		setOnMouseExited(e ->
		{
			hovered = false;
			setTranslateY(0);
			refreshSurfaceStyle();
			cancelTaskDetailsPopupShow();
			scheduleTaskDetailsPopupHide();
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
		setOnKeyPressed(e -> {
			if ((e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) && onOpen != null && taskId != null) {
				e.consume();
				onOpen.accept(taskId);
			}
		});
		taskDetailsPopupShowDelay.setOnFinished(e -> {
			if (hovered) {
				showTaskDetailsPopup();
			}
		});
		taskDetailsPopupHideDelay.setOnFinished(e -> {
			if (!hovered && !taskDetailsPopupMouseOver) {
				hideTaskDetailsPopup();
			}
		});
		sceneProperty().addListener((obs, oldScene, newScene) -> {
			if (newScene == null) {
				disposeTaskDetailsPopup();
			} else {
				Window window = newScene.getWindow();
				if (window != null) {
					window.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, e -> disposeTaskDetailsPopup());
				}
			}
		});
		setAssignees(List.of());
		setRelatedCase(null, null, null, null, null, null, null, null, List.of());
	}

	private void setFullExpanded(boolean expanded) {
		fullExpanded = expanded;
		if (currentVariant == Variant.FULL || currentVariant == Variant.MY_TASKS
				|| currentVariant == Variant.USER_ASSIGNED_TASKS) {
			fullExpandedContent.setManaged(expanded);
			fullExpandedContent.setVisible(expanded);
			expandDetailsButton.setText(expanded ? "−" : "+");
			String accessibleAction = expanded ? "Collapse task card details" : "Expand task card details";
			expandDetailsButton.setAccessibleText(accessibleAction);
			expandDetailsButton.getTooltip().setText(accessibleAction);
		}
	}

	private void refreshTaskDetailsTooltip() {
		hideTaskDetailsPopup();
		taskDetailsPopup = buildTaskDetailsPopup(titleLabel.getText(), fullDescription);
		taskDetailsPopupContent = (Parent) taskDetailsPopup.getContent().getFirst();
		taskDetailsPopupContent.setOnMouseEntered(e -> {
			taskDetailsPopupHideDelay.stop();
			taskDetailsPopupMouseOver = true;
		});
		taskDetailsPopupContent.setOnMouseExited(e -> {
			taskDetailsPopupMouseOver = false;
			scheduleTaskDetailsPopupHide();
		});
	}

	Popup getTaskDetailsPopupForTesting() {
		return taskDetailsPopup;
	}

	private void scheduleTaskDetailsPopupShow() {
		if (taskDetailsPopup == null || taskDetailsPopup.isShowing()) {
			return;
		}
		taskDetailsPopupHideDelay.stop();
		taskDetailsPopupShowDelay.playFromStart();
	}

	private void cancelTaskDetailsPopupShow() {
		taskDetailsPopupShowDelay.stop();
	}

	private void showTaskDetailsPopup() {
		if (taskDetailsPopup == null || getScene() == null || getScene().getWindow() == null) {
			return;
		}
		taskDetailsPopupHideDelay.stop();
		if (taskDetailsPopup.isShowing()) {
			return;
		}
		javafx.geometry.Bounds cardBounds = localToScreen(getBoundsInLocal());
		if (cardBounds == null) return;
		double requestedX = cardBounds.getMaxX() + TASK_DETAILS_POPUP_CURSOR_OFFSET;
		double requestedY = cardBounds.getMinY();
		TransientPopupSupport.register(taskDetailsPopupContent);
		taskDetailsPopup.show(this, requestedX, requestedY);
		taskDetailsPopup.getScene().getRoot().applyCss();
		taskDetailsPopup.getScene().getRoot().autosize();
		taskDetailsPopup.getScene().getRoot().layout();
		correctTaskDetailsPopupForScreenEdges(cardBounds);
	}

	private void correctTaskDetailsPopupForScreenEdges(javafx.geometry.Bounds anchor) {
		Window popupWindow = taskDetailsPopup.getScene().getWindow();
		Rectangle2D bounds = Screen.getScreensForRectangle(anchor.getMinX(), anchor.getMinY(), anchor.getWidth(), anchor.getHeight()).stream()
				.findFirst()
				.orElse(Screen.getPrimary())
				.getVisualBounds();
		TransientPopupSupport.PopupPosition position = TransientPopupSupport.position(anchor,
				popupWindow.getWidth(), popupWindow.getHeight(), bounds, TASK_DETAILS_POPUP_CURSOR_OFFSET);
		popupWindow.setX(position.x());
		popupWindow.setY(position.y());
	}

	private void scheduleTaskDetailsPopupHide() {
		taskDetailsPopupHideDelay.playFromStart();
	}

	private void hideTaskDetailsPopup() {
		taskDetailsPopupShowDelay.stop();
		taskDetailsPopupHideDelay.stop();
		if (taskDetailsPopup != null) {
			taskDetailsPopup.hide();
			TransientPopupSupport.unregister(taskDetailsPopupContent);
		}
	}

	private void disposeTaskDetailsPopup() {
		hideTaskDetailsPopup();
		if (taskDetailsPopup != null) taskDetailsPopup.getContent().clear();
		taskDetailsPopup = null;
		taskDetailsPopupContent = null;
		fullDescription = "";
		taskDetailsPopupMouseOver = false;
	}

	static Popup buildTaskDetailsPopup(String title, String description) {
		String normalizedTitle = title == null || title.isBlank() ? "Untitled task" : title.trim();
		String normalizedDescription = normalizeTaskDetailsText(description);
		Label heading = new Label(normalizedTitle);
		heading.getStyleClass().add("task-hover-title");
		heading.setWrapText(true);
		VBox content = new VBox(heading);
		content.getStyleClass().add("task-hover-popup");
		content.setAccessibleRole(AccessibleRole.TEXT);
		content.setAccessibleText(buildTaskDetailsTooltipText(title, description));
		if (!normalizedDescription.isBlank()) {
			Label details = new Label(normalizedDescription);
			details.getStyleClass().add("task-hover-description");
			details.setWrapText(true);
			javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(details);
			scroll.getStyleClass().add("task-hover-description-scroll");
			scroll.setFitToWidth(true);
			scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
			scroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
			scroll.setMaxHeight(260);
			content.getChildren().add(scroll);
		}

		Popup popup = new Popup();
		popup.setAutoFix(true);
		popup.setAutoHide(false);
		popup.getContent().setAll(content);
		popup.setOnHidden(event -> TransientPopupSupport.unregister(content));
		return popup;
	}

	static String buildTaskDetailsTooltipText(String title, String description) {
		String normalizedTitle = title == null || title.isBlank() ? "Untitled task" : title.trim();
		String displayedDescription = normalizeTaskDetailsText(description);
		return displayedDescription.isBlank() ? normalizedTitle : normalizedTitle + "\n\n" + displayedDescription;
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
				? "-fx-font-size: 9px; -fx-font-weight: 700;"
				: "-fx-font-size: 10px; -fx-font-weight: 700;";
		caseSectionLabel.setStyle(sectionLabelStyle);
		teamSectionLabel.setStyle(sectionLabelStyle);
		relatedCaseHost.setAlignment(Pos.CENTER_LEFT);
		relatedCaseHost.setMinWidth(0);
		relatedCaseHost.setMaxWidth(isFluidRelatedCaseVariant() ? Double.MAX_VALUE : Region.USE_PREF_SIZE);
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
				new CaseCardModel(relatedCaseId, relatedCaseName, relatedCaseResponsibleAttorney,
						relatedCaseResponsibleAttorneyColor, relatedCaseNonEngagementLetterSent,
					relatedCasePrimaryStatusName, relatedCasePrimaryStatusColor, relatedCasePracticeAreaColor,
					relatedCasePresentationDates),
				CaseCardFactory.Variant.EMBEDDED);
		if (caseCard instanceof Region region && isFluidRelatedCaseVariant()) {
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

	private boolean isFluidRelatedCaseVariant() {
		return currentVariant == Variant.MY_TASKS || currentVariant == Variant.USER_ASSIGNED_TASKS;
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
