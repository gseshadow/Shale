package com.shale.ui.component;

import java.util.List;
import java.util.function.Consumer;

import com.shale.core.service.ContactServicePort.ClassificationPresentation;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** The single reusable Contact summary card used by directory, search, and embedded surfaces. */
public class ContactCard extends VBox {
    public enum Variant { FULL, COMPACT, MINI }

    private final Label nameLabel = new Label();
    private final Label roleLabel = new Label();
    private final Label emailLabel = new Label();
    private final Label phoneLabel = new Label();
    private List<ClassificationPresentation> classifications = List.of();
    private Integer contactId;
    private Consumer<Integer> onOpen;
    private boolean suppressPlaceholderLines;
    private boolean interactive = true;

    public ContactCard() {
        getStyleClass().addAll("contact-card", "shale-entity-card");
        nameLabel.setId("contact-card-name-label");
        roleLabel.setId("contact-card-role-label");
        emailLabel.setId("contact-card-email-label");
        phoneLabel.setId("contact-card-phone-label");
        setFocusTraversable(true);
        buildUiMiniDefaults();
        wireEvents();
    }

    public void setContactId(Integer contactId) { this.contactId = contactId; refreshAccessibility(); }
    public void setOnOpen(Consumer<Integer> onOpen) { this.onOpen = onOpen; }
    public void setName(String name) {
        String value = name == null || name.isBlank() ? "—" : name.strip();
        nameLabel.setText(value);
        nameLabel.setTooltip(new Tooltip(value));
        refreshAccessibility();
    }
    public void setRole(String role) { setOptional(roleLabel, role); refreshAccessibility(); }
    public void setEmail(String email) { setOptional(emailLabel, email); refreshAccessibility(); }
    public void setPhone(String phone) { setOptional(phoneLabel, phone); refreshAccessibility(); }
    public void setClassifications(List<ClassificationPresentation> values) {
        classifications = values == null ? List.of() : List.copyOf(values);
    }

    /** Retained compatibility API; Contact surfaces are deliberately theme-owned and neutral. */
    public void setBackgroundCssColor(String ignored) { setStyle(null); }
    public void setSuppressPlaceholderLines(boolean value) { suppressPlaceholderLines = value; }
    public void setInteractive(boolean value) {
        interactive = value;
        setFocusTraversable(value);
        setCursor(value ? Cursor.HAND : Cursor.DEFAULT);
        getStyleClass().remove("contact-card-display-only");
        if (!value) getStyleClass().add("contact-card-display-only");
    }
    public void setSelected(boolean selected) {
        getStyleClass().remove("shale-card-selected");
        if (selected) getStyleClass().add("shale-card-selected");
    }
    public void setInactive(boolean inactive) {
        getStyleClass().remove("contact-card-inactive");
        if (inactive) getStyleClass().add("contact-card-inactive");
    }

    public void applyMini() {
        prepareVariant("contact-card-mini", "shale-entity-card-inline", "shale-entity-card-embedded");
        nameLabel.getStyleClass().setAll("label", "contact-card-name", "contact-card-name-mini");
        setPrefWidth(Region.USE_COMPUTED_SIZE);
        setMaxWidth(Region.USE_COMPUTED_SIZE);
        getChildren().setAll(nameLabel);
    }

    public void applyCompactMini() {
        applyMini();
        getStyleClass().add("contact-card-compact-mini");
        setMaxWidth(96);
        nameLabel.getStyleClass().setAll("label", "contact-card-name", "contact-card-name-compact-mini");
        configureEllipsis();
    }

    public void applySecondaryMini() {
        applyMini();
        getStyleClass().add("contact-card-secondary-mini");
        setMaxWidth(124);
        nameLabel.getStyleClass().setAll("label", "contact-card-name", "contact-card-name-secondary-mini");
        configureEllipsis();
    }

    public void applyCompact() { applyDetailed("contact-card-compact", "shale-entity-card-compact"); }
    public void applyFull() { applyDetailed("contact-card-full", "shale-entity-card-full"); }

    private void applyDetailed(String variantClass, String densityClass) {
        prepareVariant(variantClass, densityClass);
        setAlignment(Pos.TOP_LEFT);
        nameLabel.getStyleClass().setAll("label", "contact-card-name", "shale-subsection-title");
        roleLabel.getStyleClass().setAll("label", "contact-card-role", "shale-metadata");
        emailLabel.getStyleClass().setAll("label", "contact-card-email", "shale-metadata-muted");
        phoneLabel.getStyleClass().setAll("label", "contact-card-phone", "shale-metadata");
        nameLabel.setWrapText(true);
        emailLabel.setWrapText(true);
        phoneLabel.setWrapText(false);
        phoneLabel.setMinWidth(Region.USE_PREF_SIZE);

        VBox identity = new VBox();
        identity.getStyleClass().add("contact-card-identity");
        identity.getChildren().add(nameLabel);
        addIfPresent(identity, roleLabel);
        addIfPresent(identity, emailLabel);
        addIfPresent(identity, phoneLabel);
        getChildren().setAll(identity,
                new ContactClassificationChipGroup(classifications, ContactClassificationChipGroup.Size.COMPACT));
    }

    private void addIfPresent(VBox parent, Label label) {
        if (!label.getText().isBlank() || !suppressPlaceholderLines) {
            if (!label.getText().isBlank()) parent.getChildren().add(label);
        }
    }

    private void prepareVariant(String... classes) {
        getChildren().clear();
        getStyleClass().removeAll("contact-card-full", "contact-card-compact", "contact-card-mini",
                "contact-card-compact-mini", "contact-card-secondary-mini", "shale-entity-card-full",
                "shale-entity-card-compact", "shale-entity-card-inline", "shale-entity-card-embedded");
        getStyleClass().addAll(classes);
        nameLabel.setWrapText(false);
        nameLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        nameLabel.setMinWidth(Region.USE_COMPUTED_SIZE);
        nameLabel.setMaxWidth(Region.USE_COMPUTED_SIZE);
    }

    private void configureEllipsis() {
        nameLabel.setWrapText(false);
        nameLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        nameLabel.setMinWidth(0);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
    }

    private void buildUiMiniDefaults() { setCursor(Cursor.HAND); applyMini(); }
    private void wireEvents() {
        setOnMouseClicked(event -> { if (activate()) event.consume(); });
        setOnKeyPressed(event -> {
            if ((event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) && activate()) event.consume();
        });
    }
    private boolean activate() {
        if (!interactive || onOpen == null || contactId == null) return false;
        onOpen.accept(contactId);
        return true;
    }
    private void refreshAccessibility() {
        StringBuilder text = new StringBuilder("Contact: ").append(nameLabel.getText());
        appendAccessible(text, roleLabel); appendAccessible(text, emailLabel); appendAccessible(text, phoneLabel);
        setAccessibleText(text.toString());
    }
    private static void appendAccessible(StringBuilder text, Label label) {
        if (!label.getText().isBlank()) text.append(", ").append(label.getText());
    }
    private static void setOptional(Label label, String value) {
        String normalized = value == null ? "" : value.strip();
        label.setText(normalized);
        label.setVisible(!normalized.isBlank());
        label.setManaged(!normalized.isBlank());
        label.setTooltip(normalized.isBlank() ? null : new Tooltip(normalized));
    }
    public Node asNode() { return this; }
}
