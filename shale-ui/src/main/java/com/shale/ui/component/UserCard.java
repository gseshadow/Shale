package com.shale.ui.component;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.AccessibleRole;
import javafx.css.PseudoClass;
import javafx.scene.paint.Color;
import com.shale.ui.util.ColorUtil;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

public class UserCard extends HBox {

    private final Label nameLabel = new Label();
    private final StackPane avatarHolder = new StackPane();
    private final Circle avatarCircle = new Circle();
    private final Label initialsLabel = new Label();
    private final Label secondaryLabel = new Label();
    private static final PseudoClass INACTIVE = PseudoClass.getPseudoClass("inactive");
    private String initials;

    private Integer userId;
    private Consumer<Integer> onOpen;
    private String backgroundCss;
    private String teamAccentStyle;
    private boolean hovered;
    private boolean inactive;

    public UserCard() {
        nameLabel.setId("user-card-name-label");
        nameLabel.getStyleClass().add("user-card-name");
        avatarCircle.getStyleClass().add("user-card-avatar-circle");
        initialsLabel.setId("user-card-avatar-initials");
        initialsLabel.getStyleClass().add("user-card-avatar-initials");
        initialsLabel.setMouseTransparent(true);
        initialsLabel.setFocusTraversable(false);
        initialsLabel.setAccessibleRole(AccessibleRole.NODE);
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.BUTTON);
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
        String displayName = name == null || name.isBlank() ? "—" : name;
        nameLabel.setText(displayName);
        updateAccessibleText();
        if (nameLabel.getTooltip() != null)
            nameLabel.getTooltip().setText(displayName);
    }

    /**
     * Opts a mini card into width supplied by a compact host such as a table cell.
     * Other shared-card call sites retain their intrinsic sizing.
     */
    public void useAvailableWidth() {
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        nameLabel.setMinWidth(0);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        nameLabel.setTooltip(new Tooltip(nameLabel.getText()));
        HBox.setHgrow(nameLabel, Priority.ALWAYS);
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
        updateAccessibleText();
    }

    public void setInactive(boolean inactive) {
        this.inactive = inactive;
        pseudoClassStateChanged(INACTIVE, inactive);
        updateAccessibleText();
    }

    /** Applies the Team directory's opt-in treatment without changing other UserCard callers. */
    public void applyTeamAccent(String storedColor) {
        String color = ColorUtil.toCssBackgroundColorOrNull(storedColor);
        getStyleClass().addAll("user-card", "shale-entity-card", "shale-entity-card-clickable",
                "user-card-team-accented");
        initialsLabel.setText(initialsFromName(nameLabel.getText()));
        avatarCircle.setStyle("");
        avatarHolder.getChildren().setAll(avatarCircle, initialsLabel);
        teamAccentStyle = color == null ? null : """
                -shale-user-accent-strong: %s;
                -shale-user-accent-sustained: %s;
                -shale-user-accent-medium: %s;
                -shale-user-accent-light: %s;
                -shale-user-accent-clear: %s;
                -shale-user-name-foreground: %s;
                -shale-user-avatar-background: %s;
                -shale-user-avatar-foreground: %s;
                """.formatted(
                ColorUtil.toCssRgba(color, 0.72),
                ColorUtil.toCssRgba(color, 0.68),
                ColorUtil.toCssRgba(color, 0.40),
                ColorUtil.toCssRgba(color, 0.16),
                ColorUtil.toCssRgba(color, 0.00),
                ColorUtil.readableTextColor(color),
                color,
                ColorUtil.readableTextColor(color));
        refreshSurfaceStyle();
    }

    private void updateAccessibleText() {
        setAccessibleText((inactive ? "Inactive user: " : "User: ") + nameLabel.getText()
                + (secondaryLabel.getText().isBlank() ? "" : ", " + secondaryLabel.getText()));
    }

    private static String normalizeInitials(String value) {
        if (value == null) return "";
        String normalized = value.trim().replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
        return normalized.length() > 3 ? normalized.substring(0, 3) : normalized;
    }

    private static String initialsFromName(String value) {
        if (value == null) return "?";
        String normalizedName = value.replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "");
        if (normalizedName.isEmpty() || "—".equals(normalizedName)) return "?";
        String[] parts = normalizedName.split("[\\s\\p{Z}]+");
        String first = firstCodePoint(parts[0]);
        String last = parts.length > 1 ? firstCodePoint(parts[parts.length - 1]) : "";
        String uppercase = (first + last).toUpperCase(java.util.Locale.ROOT);
        int end = uppercase.offsetByCodePoints(0, Math.min(2, uppercase.codePointCount(0, uppercase.length())));
        return uppercase.substring(0, end);
    }

    private static String firstCodePoint(String value) {
        int end = value.offsetByCodePoints(0, 1);
        return value.substring(0, end);
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
        avatarCircle.setRadius(radius);
        avatarCircle.setStyle("-fx-fill: rgba(255,255,255,0.55); -fx-stroke: rgba(0,0,0,0.10);");
        avatarHolder.getChildren().setAll(avatarCircle);
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
        setStyle(getStyleClass().contains("user-card-team-accented") ? teamAccentStyle
                : CardSurfaceStyles.cardContainerStyle(backgroundCss, hovered));
    }
}
