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
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Window;

public final class TaskCard extends VBox {

	public enum Variant {
		FULL, MY_TASKS, COMPACT, COMPACT_FLUID, MINI
	}

	private static final DateTimeFormatter DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
	private static final DateTimeFormatter DUE_DATE_COMPACT_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
	private static final double COMPACT_CARD_WIDTH = 280;
	private static final double TASK_DETAILS_TOOLTIP_MAX_WIDTH = 360;
	private static final int TASK_DETAILS_TOOLTIP_MAX_DESCRIPTION_LINES = 8;
	private static final double TASK_DETAILS_TOOLTIP_DESCRIPTION_FONT_SIZE = 12;
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
	private Popup taskDetailsPopup;
	private Label taskDetailsPopupContent;
	private final PauseTransition taskDetailsPopupHideDelay = new PauseTransition(TASK_DETAILS_TOOLTIP_HIDE_DELAY);
	private final PauseTransition taskDetailsPopupShowDelay = new PauseTransition(TASK_DETAILS_POPUP_SHOW_DELAY);
	private boolean taskDetailsPopupMouseOver;
	private double latestTaskDetailsPopupScreenX;
	private double latestTaskDetailsPopupScreenY;

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
		dueAccentCss = DueProximityStyles.accentColor(dueAt, completedAt);
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
			captureTaskDetailsPopupPointer(e.getScreenX(), e.getScreenY());
			scheduleTaskDetailsPopupShow();
		});
		setOnMouseMoved(e -> captureTaskDetailsPopupPointer(e.getScreenX(), e.getScreenY()));
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
				hideTaskDetailsPopup();
			} else {
				Window window = newScene.getWindow();
				if (window != null) {
					window.setOnHidden(e -> hideTaskDetailsPopup());
				}
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
		hideTaskDetailsPopup();
		taskDetailsPopup = buildTaskDetailsPopup(titleLabel.getText(), fullDescription);
		taskDetailsPopupContent = (Label) taskDetailsPopup.getContent().getFirst();
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

	private void captureTaskDetailsPopupPointer(double screenX, double screenY) {
		latestTaskDetailsPopupScreenX = screenX;
		latestTaskDetailsPopupScreenY = screenY;
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
		double requestedX = latestTaskDetailsPopupScreenX + TASK_DETAILS_POPUP_CURSOR_OFFSET;
		double requestedY = latestTaskDetailsPopupScreenY + TASK_DETAILS_POPUP_CURSOR_OFFSET;
		taskDetailsPopup.show(this, requestedX, requestedY);
		taskDetailsPopup.getScene().getRoot().applyCss();
		taskDetailsPopup.getScene().getRoot().autosize();
		taskDetailsPopup.getScene().getRoot().layout();
		correctTaskDetailsPopupForScreenEdges(requestedX, requestedY);
	}

	private void correctTaskDetailsPopupForScreenEdges(double requestedX, double requestedY) {
		Window popupWindow = taskDetailsPopup.getScene().getWindow();
		Rectangle2D bounds = Screen.getScreensForRectangle(requestedX, requestedY, 1, 1).stream()
				.findFirst()
				.orElse(Screen.getPrimary())
				.getVisualBounds();
		double correctedX = Math.min(requestedX, bounds.getMaxX() - popupWindow.getWidth() - TASK_DETAILS_POPUP_CURSOR_OFFSET);
		double correctedY = Math.min(requestedY, bounds.getMaxY() - popupWindow.getHeight() - TASK_DETAILS_POPUP_CURSOR_OFFSET);
		popupWindow.setX(Math.max(bounds.getMinX() + TASK_DETAILS_POPUP_CURSOR_OFFSET, correctedX));
		popupWindow.setY(Math.max(bounds.getMinY() + TASK_DETAILS_POPUP_CURSOR_OFFSET, correctedY));
	}

	private void scheduleTaskDetailsPopupHide() {
		taskDetailsPopupHideDelay.playFromStart();
	}

	private void hideTaskDetailsPopup() {
		taskDetailsPopupShowDelay.stop();
		taskDetailsPopupHideDelay.stop();
		if (taskDetailsPopup != null) {
			taskDetailsPopup.hide();
		}
	}

	static Popup buildTaskDetailsPopup(String title, String description) {
		Label content = new Label(buildTaskDetailsTooltipText(title, description));
		content.getStyleClass().add("tooltip");
		content.setWrapText(true);
		content.setPrefWidth(tooltipWidthForText(content.getText()));
		content.setMaxWidth(TASK_DETAILS_TOOLTIP_MAX_WIDTH);
		content.setStyle("-fx-font-size: 12px; -fx-line-spacing: 1px;");

		Popup popup = new Popup();
		popup.setAutoFix(true);
		popup.setAutoHide(false);
		popup.getContent().setAll(content);
		return popup;
	}

	static String buildTaskDetailsTooltipText(String title, String description) {
		String normalizedTitle = title == null || title.isBlank() ? "Untitled task" : title.trim();
		String displayedDescription = descriptionForTooltip(description);
		return displayedDescription.isBlank() ? normalizedTitle : normalizedTitle + "\n\n" + displayedDescription;
	}

	static double tooltipWidthForText(String text) {
		String normalized = text == null ? "" : text;
		double widestLine = 0;
		for (String line : normalized.split("\n", -1)) {
			Text measuringText = new Text(line);
			measuringText.setFont(Font.font(TASK_DETAILS_TOOLTIP_DESCRIPTION_FONT_SIZE));
			widestLine = Math.max(widestLine, measuringText.getLayoutBounds().getWidth());
		}
		return Math.min(TASK_DETAILS_TOOLTIP_MAX_WIDTH, Math.max(160, widestLine + 34));
	}

	static String descriptionForTooltip(String text) {
		String normalized = normalizeTaskDetailsText(text);
		if (normalized.isBlank() || wrappedDescriptionLineCount(normalized) <= TASK_DETAILS_TOOLTIP_MAX_DESCRIPTION_LINES) {
			return normalized;
		}
		int low = 0;
		int high = normalized.length();
		String best = "...";
		while (low <= high) {
			int mid = (low + high) >>> 1;
			String candidate = appendInlineEllipsis(normalized.substring(0, mid));
			if (wrappedDescriptionLineCount(candidate) <= TASK_DETAILS_TOOLTIP_MAX_DESCRIPTION_LINES) {
				best = candidate;
				low = mid + 1;
			} else {
				high = mid - 1;
			}
		}
		return best;
	}

	static int wrappedDescriptionLineCount(String text) {
		String normalized = normalizeTaskDetailsText(text);
		if (normalized.isBlank()) {
			return 0;
		}
		Text measuringText = new Text(normalized);
		measuringText.setFont(Font.font(TASK_DETAILS_TOOLTIP_DESCRIPTION_FONT_SIZE));
		measuringText.setWrappingWidth(TASK_DETAILS_TOOLTIP_MAX_WIDTH);
		double lineHeight = Font.font(TASK_DETAILS_TOOLTIP_DESCRIPTION_FONT_SIZE).getSize() + 5;
		return Math.max(1, (int) Math.ceil(measuringText.getLayoutBounds().getHeight() / lineHeight));
	}

	static double estimatedTooltipDescriptionHeight(String text) {
		return wrappedDescriptionLineCount(text) * (TASK_DETAILS_TOOLTIP_DESCRIPTION_FONT_SIZE + 5);
	}

	private static String appendInlineEllipsis(String text) {
		String trimmed = text.stripTrailing();
		while (!trimmed.isBlank() && (trimmed.endsWith(".") || trimmed.endsWith(",") || trimmed.endsWith(";") || trimmed.endsWith(":"))) {
			trimmed = trimmed.substring(0, trimmed.length() - 1).stripTrailing();
		}
		return trimmed.isBlank() ? "..." : trimmed + "...";
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
