package com.shale.ui.component;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class StatusCard extends HBox {

    private final Label nameLabel = new Label();
    private String backgroundCss = "rgba(0,0,0,0.06)";
    private String textCss = "#172033";
    private String fontStyle = "-fx-font-size: 12px; -fx-font-weight: 600;";

    private Integer statusId;
    private Consumer<Integer> onOpen;

    public StatusCard() {
        buildUiMiniDefaults();
        wireEvents();
    }

    public void setStatusId(Integer statusId) {
        this.statusId = statusId;
    }

    public void setOnOpen(Consumer<Integer> onOpen) {
        this.onOpen = onOpen;
    }

    public void setName(String name) {
        nameLabel.setText(name == null || name.isBlank() ? "—" : name);
    }

    public void setBackgroundCssColor(String css) {
        backgroundCss = (css == null || css.isBlank()) ? "rgba(0,0,0,0.06)" : css;
        refreshStyle();
    }

    public void setTextCssColor(String css) {
        textCss = (css == null || css.isBlank()) ? "#172033" : css;
        refreshStyle();
    }

    public void applyMini() {
        getChildren().clear();
        setPadding(new Insets(3, 10, 3, 10));
        setSpacing(6);
        fontStyle = "-fx-font-size: 12px; -fx-font-weight: 800;";
        getChildren().addAll(nameLabel);
        refreshStyle();
    }

    public void applyCompact() {
        getChildren().clear();
        setPadding(new Insets(3, 10, 3, 10));
        setSpacing(8);
        fontStyle = "-fx-font-size: 12px; -fx-font-weight: 800;";
        getChildren().addAll(nameLabel);
        refreshStyle();
    }

    public void applyFull() {
        getChildren().clear();
        setPadding(new Insets(6, 16, 6, 16));
        setSpacing(10);
        fontStyle = "-fx-font-size: 13px; -fx-font-weight: 800;";
        getChildren().addAll(nameLabel);
        refreshStyle();
    }

    private void buildUiMiniDefaults() {
        setCursor(Cursor.HAND);
        getStyleClass().add("shale-status-pill");
        applyMini();
    }

    private void wireEvents() {
        setOnMouseClicked(e -> {
            if (onOpen != null && statusId != null) {
                onOpen.accept(statusId);
            }
        });
    }

    private void refreshStyle() {
        setStyle("-fx-background-color: " + backgroundCss + "; -fx-text-fill: " + textCss + ";");
        nameLabel.setStyle(fontStyle + " -fx-text-fill: " + textCss + ";");
    }

    public Node asNode() {
        return this;
    }
}
