package com.shale.ui.component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.component.factory.TaskCardFactory.AssignedUserModel;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class TaskCard extends VBox {

	public enum Variant {
		FULL, COMPACT, COMPACT_FLUID, MINI
	}

	private static final DateTimeFormatter DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

	private final Label titleLabel = new Label();
	private final Label dueLabel = new Label();
	private final Label descriptionLabel = new Label();
	private final Label completedLabel = new Label();
	private final StackPane relatedCaseHost = new StackPane();
	private final StackPane assigneeHost = new StackPane();
	private final Label caseSectionLabel = new Label("Case:");
	private final VBox caseSection = new VBox(3, caseSectionLabel, relatedCaseHost);
	private final Label teamSectionLabel = new Label("Team:");
	private final VBox teamSection = new VBox(3, teamSectionLabel, assigneeHost);
	private final Button toggleCompleteButton = new Button();
	private final Region actionsSpacer = new Region();
	private final HBox actionsRow = new HBox(8, actionsSpacer, toggleCompleteButton);
	private final UserCardFactory userCardFactory = new UserCardFactory(id -> {
	});
	private final CaseCardFactory caseCardFactory = new CaseCardFactory(id -> {
	});

	private Long taskId;
	private Consumer<Long> onOpen;
	private Consumer<Long> onToggleComplete;
	private Consumer<Integer> onOpenAssigneeUser;
	private Consumer<Integer> onOpenRelatedCase;
	private String backgroundCss;
	private String borderCss;
	private boolean hovered;

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
	}

	public void setDueAt(LocalDateTime dueAt) {
		if (dueAt == null) {
			dueLabel.setText("");
			dueLabel.setManaged(false);
			dueLabel.setVisible(false);
			return;
		}

		dueLabel.setText("Due " + DUE_DATE_FORMAT.format(dueAt));
		dueLabel.setManaged(true);
		dueLabel.setVisible(true);
	}

	public void setDescriptionPreview(String description) {
		String text = description == null ? "" : description.trim();
		if (text.length() > 140) {
			text = text.substring(0, 137) + "...";
		}
		descriptionLabel.setText(text);
		boolean hasText = !text.isBlank();
		descriptionLabel.setManaged(hasText);
		descriptionLabel.setVisible(hasText);
	}

	public void setCompleted(boolean completed) {
		completedLabel.setManaged(completed);
		completedLabel.setVisible(completed);
		completedLabel.setText(completed ? "Completed" : "");
		toggleCompleteButton.setText(completed ? "Mark Incomplete" : "Complete");
		setOpacity(completed ? 0.78 : 1.0);
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

	public void setRelatedCase(Long caseId, String caseName, String responsibleAttorney, String responsibleAttorneyColor,
			Boolean nonEngagementLetterSent) {
		String normalizedName = caseName == null ? "" : caseName.trim();
		if (caseId == null || caseId <= 0 || normalizedName.isBlank()) {
			relatedCaseHost.getChildren().clear();
			caseSection.setManaged(false);
			caseSection.setVisible(false);
			return;
		}
		var caseCard = caseCardFactory.create(
				new CaseCardModel(caseId, normalizedName, null, null, responsibleAttorney, responsibleAttorneyColor, nonEngagementLetterSent),
				CaseCardFactory.Variant.MINI);
		caseCard.setOnMouseClicked(e -> {
			e.consume();
			if (onOpenRelatedCase != null) {
				onOpenRelatedCase.accept(caseId.intValue());
			}
		});
		relatedCaseHost.getChildren().setAll(caseCard);
		caseSection.setManaged(true);
		caseSection.setVisible(true);
	}

	public void setBackgroundCssColor(String css) {
		this.backgroundCss = css;
		refreshSurfaceStyle();
	}

	public void setBorderByDueState(LocalDateTime dueAt, LocalDateTime completedAt) {
		if (completedAt != null) {
			borderCss = "#16a34a";
			refreshSurfaceStyle();
			return;
		}
		if (dueAt == null) {
			borderCss = null;
			refreshSurfaceStyle();
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		if (dueAt.isBefore(now)) {
			borderCss = "#7f1d1d";
		} else if (!dueAt.isAfter(now.plusDays(1))) {
			borderCss = "#dc2626";
		} else if (!dueAt.isAfter(now.plusWeeks(1))) {
			borderCss = "#f97316";
		} else if (!dueAt.isAfter(now.plusWeeks(2))) {
			borderCss = "#eab308";
		} else {
			borderCss = null;
		}
		refreshSurfaceStyle();
	}

	public void applyMini() {
		getChildren().setAll(titleLabel, completedLabel);
		setSpacing(2);
		setPadding(new Insets(4, 10, 4, 10));
		setMaxWidth(Region.USE_COMPUTED_SIZE);
		setPrefWidth(Region.USE_COMPUTED_SIZE);
		titleLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");
		completedLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: rgba(22,101,52,0.95);");
		refreshSurfaceStyle();
	}

	public void applyCompact() {
		getChildren().setAll(titleLabel, dueLabel, descriptionLabel, caseSection, teamSection, completedLabel, actionsRow);
		setSpacing(6);
		setPadding(new Insets(10, 12, 10, 12));
		setAlignment(Pos.TOP_LEFT);
		setMinWidth(320);
		setPrefWidth(320);
		setMaxWidth(320);
		titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #112542;");
		dueLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.72);");
		descriptionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.78);");
		descriptionLabel.setWrapText(true);
		configureRelatedSections();
		completedLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: rgba(22,101,52,0.95);");
		actionsRow.setAlignment(Pos.CENTER_RIGHT);
		refreshSurfaceStyle();
	}

	public void applyCompactFluid() {
		applyCompact();
		setMinWidth(Region.USE_COMPUTED_SIZE);
		setPrefWidth(Region.USE_COMPUTED_SIZE);
		setMaxWidth(Double.MAX_VALUE);
	}

	public void applyFull() {
		getChildren().setAll(titleLabel, dueLabel, descriptionLabel, caseSection, teamSection, completedLabel, actionsRow);
		setSpacing(8);
		setPadding(new Insets(14, 16, 14, 16));
		setAlignment(Pos.TOP_LEFT);
		setMinWidth(420);
		setPrefWidth(420);
		setMaxWidth(420);
		titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #112542;");
		dueLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.72);");
		descriptionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.78);");
		descriptionLabel.setWrapText(true);
		configureRelatedSections();
		completedLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: rgba(22,101,52,0.95);");
		actionsRow.setAlignment(Pos.CENTER_RIGHT);
		refreshSurfaceStyle();
	}

	private void wireEvents() {
		HBox.setHgrow(actionsSpacer, javafx.scene.layout.Priority.ALWAYS);
		toggleCompleteButton.getStyleClass().add("button-secondary");
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
		setRelatedCase(null, null, null, null, null);
	}

	private void configureRelatedSections() {
		caseSectionLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: rgba(17,37,66,0.62);");
		teamSectionLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: rgba(17,37,66,0.62);");
		relatedCaseHost.setAlignment(Pos.CENTER_LEFT);
		relatedCaseHost.setMaxWidth(Region.USE_PREF_SIZE);
		assigneeHost.setAlignment(Pos.CENTER_LEFT);
		assigneeHost.setMaxWidth(Region.USE_PREF_SIZE);
	}

	private void refreshSurfaceStyle() {
		setStyle(CardSurfaceStyles.cardContainerStyle(backgroundCss, borderCss, hovered));
	}
}
