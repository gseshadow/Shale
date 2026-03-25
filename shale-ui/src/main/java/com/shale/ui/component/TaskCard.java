package com.shale.ui.component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
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

    private Long taskId;
    private Consumer<Long> onOpen;
    private String backgroundCss;
    private boolean hovered;
    private boolean completed;

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
        this.completed = completed;
        completedLabel.setManaged(completed);
        completedLabel.setVisible(completed);
        completedLabel.setText(completed ? "Completed" : "");
        setOpacity(completed ? 0.78 : 1.0);
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
        getChildren().setAll(titleLabel, dueLabel, descriptionLabel, completedLabel);
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
        completedLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: rgba(22,101,52,0.95);");
        refreshSurfaceStyle();
    }

    public void applyFull() {
        getChildren().setAll(titleLabel, dueLabel, descriptionLabel, completedLabel);
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
        completedLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: rgba(22,101,52,0.95);");
        refreshSurfaceStyle();
    }

    private void wireEvents() {
        setOnMouseEntered(e -> {
            hovered = true;
            setTranslateY(-1.5);
            refreshSurfaceStyle();
        });
        setOnMouseExited(e -> {
            hovered = false;
            setTranslateY(0);
            refreshSurfaceStyle();
        });
        setOnMouseClicked(e -> {
            if (onOpen != null && taskId != null) {
                onOpen.accept(taskId);
            }
        });
    }

    private void refreshSurfaceStyle() {
        setStyle(CardSurfaceStyles.cardContainerStyle(backgroundCss, hovered));
    }
}
