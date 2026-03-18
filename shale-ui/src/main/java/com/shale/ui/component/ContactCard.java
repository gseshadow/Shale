package com.shale.ui.component;

import java.util.function.Consumer;

import javafx.geometry.Insets;
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
    private final Label emailLabel = new Label();
    private final Label phoneLabel = new Label();

    private Integer contactId;
    private Consumer<Integer> onOpen;

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

    public void setEmail(String email) {
        emailLabel.setText(normalizeOptional(email));
    }

    public void setPhone(String phone) {
        phoneLabel.setText(normalizeOptional(phone));
    }

    public void setBackgroundCssColor(String css) {
        String bg = (css == null || css.isBlank()) ? "rgba(0,0,0,0.03)" : css;
        setStyle(("""
                -fx-background-color: %s;
                -fx-background-radius: 14;
                -fx-border-radius: 14;
                -fx-border-color: rgba(0,0,0,0.08);
                """).formatted(bg));
    }

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

        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600;");
        emailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(0,0,0,0.62);");

        VBox text = new VBox(2, nameLabel, emailLabel);
        getChildren().add(text);
    }

    public void applyFull() {
        getChildren().clear();

        setPadding(new Insets(10, 12, 10, 12));
        setSpacing(12);

        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");
        emailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(0,0,0,0.68);");
        phoneLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(0,0,0,0.68);");

        VBox text = new VBox(4, nameLabel, emailLabel);
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
}
