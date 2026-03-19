package com.shale.ui.component;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class ContactCard extends HBox {

    public enum Variant {
        FULL, COMPACT, MINI
    }

    private final Label nameLabel = new Label();
    private final Label roleLabel = new Label();
    private final Label emailLabel = new Label();
    private final Label phoneLabel = new Label();

    private Integer contactId;
    private Consumer<Integer> onOpen;
    private String backgroundCss;
    private boolean hovered;

    public ContactCard() {
        buildUiMiniDefaults();
        wireEvents();
    }

    public void setContactId(Integer contactId) {
        this.contactId = contactId;
    }

    public void setOnOpen(Consumer<Integer> onOpen) {
        this.onOpen = onOpen;
    }

    public void setName(String name) {
        nameLabel.setText(name == null || name.isBlank() ? "—" : name);
    }

    public void setRole(String role) {
        String normalized = role == null ? "" : role.trim();
        roleLabel.setText(normalized);
        roleLabel.setVisible(!normalized.isBlank());
        roleLabel.setManaged(!normalized.isBlank());
    }

    public void setEmail(String email) {
        emailLabel.setText(normalizeOptional(email));
    }

    public void setPhone(String phone) {
        phoneLabel.setText(normalizeOptional(phone));
    }

    public void setBackgroundCssColor(String css) {
        backgroundCss = css;
        refreshSurfaceStyle();
    }

    public void applyMini() {
        getChildren().clear();

        setPrefWidth(Region.USE_COMPUTED_SIZE);
        setMaxWidth(Region.USE_COMPUTED_SIZE);
        setPadding(new Insets(4, 10, 4, 10));
        setSpacing(6);

        nameLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");

        getChildren().add(nameLabel);
    }

    public void applyCompact() {
        getChildren().clear();

        setAlignment(Pos.TOP_LEFT);
        setPrefWidth(280);
        setMaxWidth(280);
        setPadding(new Insets(10, 12, 10, 12));
        setSpacing(10);

        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #112542;");
        roleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.62);");
        emailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.72);");
        emailLabel.setWrapText(true);

        VBox text = new VBox(4, nameLabel);
        if (roleLabel.isManaged()) {
            text.getChildren().add(roleLabel);
        }
        text.getChildren().add(emailLabel);
        getChildren().add(text);
    }

    public void applyFull() {
        getChildren().clear();

        setAlignment(Pos.TOP_LEFT);
        setMinWidth(296);
        setPrefWidth(312);
        setMaxWidth(312);
        setPadding(new Insets(14, 16, 14, 16));
        setSpacing(16);

        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #112542;");
        roleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.62);");
        emailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.76);");
        phoneLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.82);");
        emailLabel.setWrapText(true);
        phoneLabel.setWrapText(true);

        VBox text = new VBox(6, nameLabel);
        if (roleLabel.isManaged()) {
            text.getChildren().add(roleLabel);
        }
        text.getChildren().add(emailLabel);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(text, spacer, phoneLabel);
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
            if (onOpen != null && contactId != null) {
                onOpen.accept(contactId);
            }
        });
    }

    public Node asNode() {
        return this;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private void refreshSurfaceStyle() {
        setStyle(CardSurfaceStyles.cardContainerStyle(backgroundCss, hovered));
    }
}
