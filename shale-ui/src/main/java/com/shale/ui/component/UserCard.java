package com.shale.ui.component;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

public class UserCard extends HBox {

    private final Label nameLabel = new Label();
    private final StackPane avatarHolder = new StackPane();

    private Integer userId;
    private Consumer<Integer> onOpen;

    public UserCard() {
        buildUiMiniDefaults();
        wireEvents();
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public void setOnOpen(Consumer<Integer> onOpen) {
        this.onOpen = onOpen;
    }

    public void setName(String name) {
        nameLabel.setText(name == null || name.isBlank() ? "—" : name);
    }

    public void setBackgroundCssColor(String css) {
        String bg = (css == null || css.isBlank()) ? "rgba(0,0,0,0.06)" : css;
        setStyle(("""
                -fx-background-color: %s;
                -fx-background-radius: 14;
                -fx-border-radius: 14;
                -fx-border-color: rgba(0,0,0,0.08);
                """).formatted(bg));
    }

    // --- Variants ---

    public void applyMini() {
        getChildren().clear();

        setPadding(new Insets(4, 10, 4, 10));
        setSpacing(6);

        nameLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");

        getChildren().add(nameLabel);
    }

    public void applyCompact() {
        getChildren().clear();

        setPadding(new Insets(8, 10, 8, 10));
        setSpacing(8);

        Node avatar = buildAvatar(18);
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600;");

        getChildren().addAll(avatar, nameLabel);
    }

    public void applyFull() {
        getChildren().clear();

        setPadding(new Insets(10, 12, 10, 12));
        setSpacing(10);

        Node avatar = buildAvatar(26);
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        // Extend later: role, email, phone, etc
        VBox text = new VBox(2, nameLabel);
        getChildren().addAll(avatar, text);
    }

    private Node buildAvatar(double radius) {
        // Placeholder avatar (circle). Swap later for ImageView clipped to circle.
        Circle c = new Circle(radius);
        c.setStyle("-fx-fill: rgba(255,255,255,0.55); -fx-stroke: rgba(0,0,0,0.10);");
        avatarHolder.getChildren().setAll(c);
        return avatarHolder;
    }

    private void buildUiMiniDefaults() {
        setCursor(Cursor.HAND);
        applyMini();
    }

    private void wireEvents() {
        setOnMouseClicked(e -> {
            if (onOpen != null && userId != null) {
                onOpen.accept(userId);
            }
        });
    }

    public Node asNode() {
        return this;
    }
}
