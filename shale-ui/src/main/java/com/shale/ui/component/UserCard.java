package com.shale.ui.component;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.css.PseudoClass;
import javafx.scene.paint.Color;
import com.shale.ui.util.ColorUtil;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

public class UserCard extends HBox {

    private final Label nameLabel = new Label();
    private final StackPane avatarHolder = new StackPane();
    private final Label initialsLabel = new Label();
    private final Label secondaryLabel = new Label();
    private static final PseudoClass INACTIVE = PseudoClass.getPseudoClass("inactive");
    private String initials;

    private Integer userId;
    private Consumer<Integer> onOpen;
    private String backgroundCss;
    private boolean hovered;

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
        backgroundCss = css;
        refreshSurfaceStyle();
    }


    public void setInitials(String initials) {
        this.initials = normalizeInitials(initials);
    }

    public void setSecondaryMetadata(String metadata) {
        secondaryLabel.setText(metadata == null ? "" : metadata.trim());
    }

    public void setInactive(boolean inactive) {
        pseudoClassStateChanged(INACTIVE, inactive);
        setAccessibleText((inactive ? "Inactive user: " : "User: ") + nameLabel.getText() + (secondaryLabel.getText().isBlank() ? "" : ", " + secondaryLabel.getText()));
    }

    private static String normalizeInitials(String value) {
        if (value == null) return "";
        String normalized = value.trim().replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
        return normalized.length() > 3 ? normalized.substring(0, 3) : normalized;
    }

    private static String initialsFromName(String value) {
        if (value == null || value.isBlank() || "—".equals(value.trim())) return "?";
        String[] parts = value.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(java.util.Locale.ROOT);
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
        setBackgroundCssColor(null);
        applyMini();
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
            if (onOpen != null && userId != null) {
                onOpen.accept(userId);
            }
        });
    }

    public Node asNode() {
        return this;
    }

    private void refreshSurfaceStyle() {
        setStyle(CardSurfaceStyles.cardContainerStyle(backgroundCss, hovered));
    }
}
