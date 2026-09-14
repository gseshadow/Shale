package com.shale.ui.component;

import java.util.function.Consumer;
import java.util.List;
import com.shale.core.service.ContactServicePort.ClassificationPresentation;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.input.KeyCode;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class ContactCard extends VBox {

    public enum Variant {
        FULL, COMPACT, MINI
    }

    private final Label nameLabel = new Label();
    private final Label roleLabel = new Label();
    private final Label emailLabel = new Label();
    private final Label phoneLabel = new Label();
    private List<ClassificationPresentation> classifications=List.of();

    private Integer contactId;
    private Consumer<Integer> onOpen;
    private String backgroundCss;
    private boolean hovered;
    private boolean suppressPlaceholderLines;
    private boolean interactive = true;

    public ContactCard() {
        nameLabel.setId("contact-card-name-label");
        phoneLabel.setId("contact-card-phone-label");
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
    public void setClassifications(List<ClassificationPresentation> values){classifications=List.copyOf(values);}

    public void setBackgroundCssColor(String css) {
        backgroundCss = css;
        refreshSurfaceStyle();
    }

    public void setSuppressPlaceholderLines(boolean suppressPlaceholderLines) {
        this.suppressPlaceholderLines = suppressPlaceholderLines;
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        setCursor(interactive ? Cursor.HAND : Cursor.DEFAULT);
    }

    public void applyMini() {
        getChildren().clear();
        resetNameLabelVariantStyles();
        nameLabel.getStyleClass().addAll("contact-card-name", "contact-card-name-mini");

        setPrefWidth(Region.USE_COMPUTED_SIZE);
        setMaxWidth(Region.USE_COMPUTED_SIZE);
        setPadding(new Insets(4, 10, 4, 10));
        setSpacing(6);

        nameLabel.setStyle(null);

        getChildren().add(nameLabel);
    }


    public void applyCompactMini() {
        applyMini();
        setPadding(new Insets(2, 6, 2, 6));
        setSpacing(4);
        setMaxWidth(96);
        resetNameLabelVariantStyles();
        nameLabel.getStyleClass().addAll("contact-card-name", "contact-card-name-compact-mini");
        nameLabel.setStyle(null);
        nameLabel.setWrapText(false);
        nameLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        nameLabel.setMinWidth(0);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
    }

    public void applySecondaryMini() {
        applyMini();
        setPadding(new Insets(3, 8, 3, 8));
        setSpacing(5);
        setMaxWidth(124);
        resetNameLabelVariantStyles();
        nameLabel.getStyleClass().addAll("contact-card-name", "contact-card-name-secondary-mini");
        nameLabel.setStyle(null);
        nameLabel.setWrapText(false);
        nameLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        nameLabel.setMinWidth(0);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
    }

    public void applyCompact() {
        getChildren().clear();

        setAlignment(Pos.TOP_LEFT);
        setPrefWidth(280);
        setMaxWidth(280);
        setPadding(new Insets(10, 12, 10, 12));
        setSpacing(7);

        resetNameLabelVariantStyles();
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #112542;");
        roleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.62);");
        emailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.72);");
        phoneLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.74);");
        emailLabel.setWrapText(true);
        phoneLabel.setWrapText(false);
        phoneLabel.setMinWidth(Region.USE_PREF_SIZE);

        VBox text = new VBox(4, nameLabel);
        if (roleLabel.isManaged()) {
            text.getChildren().add(roleLabel);
        }
        if (!(suppressPlaceholderLines && "—".equals(emailLabel.getText()))) {
            text.getChildren().add(emailLabel);
        }
        if (!(suppressPlaceholderLines && "—".equals(phoneLabel.getText()))) text.getChildren().add(phoneLabel);
        getChildren().addAll(text,
                new ContactClassificationChipGroup(classifications,ContactClassificationChipGroup.Size.COMPACT));
    }

    public void applyFull() {
        getChildren().clear();

        setAlignment(Pos.TOP_LEFT);
        setMinWidth(296);
        setPrefWidth(312);
        setMaxWidth(312);
        setPadding(new Insets(14, 16, 14, 16));
        setSpacing(16);

        resetNameLabelVariantStyles();
        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #112542;");
        roleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.62);");
        emailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.76);");
        phoneLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.82);");
        emailLabel.setWrapText(true);
        phoneLabel.setWrapText(false);
        phoneLabel.setMinWidth(Region.USE_PREF_SIZE);

        VBox text = new VBox(6, nameLabel);
        if (roleLabel.isManaged()) {
            text.getChildren().add(roleLabel);
        }
        text.getChildren().add(emailLabel);
        text.getChildren().add(phoneLabel);
        getChildren().addAll(text,
                new ContactClassificationChipGroup(classifications,ContactClassificationChipGroup.Size.COMPACT));
    }

    private void resetNameLabelVariantStyles() {
        nameLabel.getStyleClass().removeAll("contact-card-name", "contact-card-name-mini", "contact-card-name-compact-mini", "contact-card-name-secondary-mini");
        nameLabel.setWrapText(false);
        nameLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        nameLabel.setMinWidth(Region.USE_COMPUTED_SIZE);
        nameLabel.setMaxWidth(Region.USE_COMPUTED_SIZE);
    }

    private void buildUiMiniDefaults() {
        setCursor(Cursor.HAND);
        setBackgroundCssColor(null);
        applyMini();
    }

    private void wireEvents() {
        setOnMouseEntered(e -> {
            if (!interactive) return;
            hovered = true;
            setTranslateY(-1.5);
            refreshSurfaceStyle();
        });
        setOnMouseExited(e -> {
            if (!interactive) return;
            hovered = false;
            setTranslateY(0);
            refreshSurfaceStyle();
        });
        setOnMouseClicked(e -> {
            if (interactive && onOpen != null && contactId != null) {
                onOpen.accept(contactId);
            }
        });
        setOnKeyPressed(e -> {
            if (interactive && onOpen != null && contactId != null && (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE)) {
                onOpen.accept(contactId);
                e.consume();
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
