package com.shale.ui.component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

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
		FULL, COMPACT, MINI
	}

	private static final DateTimeFormatter DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

	private final Label titleLabel = new Label();
	private final Label dueLabel = new Label();
	private final Label descriptionLabel = new Label();
	private final Label completedLabel = new Label();
	private final StackPane assigneeHost = new StackPane();
	private final Button toggleCompleteButton = new Button();
	private final Region actionsSpacer = new Region();
	private final HBox actionsRow = new HBox(8, actionsSpacer, toggleCompleteButton);
	private final UserCardFactory userCardFactory = new UserCardFactory(id -> {
	});

	private Long taskId;
	private Consumer<Long> onOpen;
	private Consumer<Long> onToggleComplete;
	private String backgroundCss;
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

	public void setAssignee(Integer userId, String displayName, String colorCss) {
		String normalized = displayName == null ? "" : displayName.trim();
		if (userId == null || userId <= 0 || normalized.isBlank()) {
			assigneeHost.getChildren().clear();
			assigneeHost.setManaged(false);
			assigneeHost.setVisible(false);
			return;
		}

		var assigneeCard = userCardFactory.create(
				new UserCardModel(userId, normalized, colorCss, null),
				UserCardFactory.Variant.MINI);
		assigneeCard.setMouseTransparent(true);
		assigneeHost.getChildren().setAll(assigneeCard);
		assigneeHost.setManaged(true);
		assigneeHost.setVisible(true);
	}

	public void setBackgroundCssColor(String css) {
		this.backgroundCss = css;
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
		getChildren().setAll(titleLabel, dueLabel, descriptionLabel, assigneeHost, completedLabel, actionsRow);
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
		assigneeHost.setAlignment(Pos.CENTER_LEFT);
		completedLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: rgba(22,101,52,0.95);");
		actionsRow.setAlignment(Pos.CENTER_RIGHT);
		refreshSurfaceStyle();
	}

	public void applyFull() {
		getChildren().setAll(titleLabel, dueLabel, descriptionLabel, assigneeHost, completedLabel, actionsRow);
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
		assigneeHost.setAlignment(Pos.CENTER_LEFT);
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
		setAssignee(null, null, null);
	}

	private void refreshSurfaceStyle() {
		setStyle(CardSurfaceStyles.cardContainerStyle(backgroundCss, hovered));
	}
}
