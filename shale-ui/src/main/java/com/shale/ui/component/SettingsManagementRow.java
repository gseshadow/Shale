package com.shale.ui.component;

import com.shale.ui.util.ControlStyles;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Shared presentation primitive for a single Settings directory entry. */
public final class SettingsManagementRow extends HBox {
    private final Label titleLabel = new Label();
    private final Label descriptionLabel = new Label();
    private final Button actionButton = new Button();
    private EventHandler<ActionEvent> actionHandler;

    public SettingsManagementRow() {
        getStyleClass().add("settings-management-row");
        setAlignment(Pos.CENTER_LEFT);

        titleLabel.getStyleClass().add("settings-management-title");
        descriptionLabel.getStyleClass().add("settings-management-description");
        descriptionLabel.setWrapText(true);
        VBox copy = new VBox(titleLabel, descriptionLabel);
        copy.getStyleClass().add("settings-management-copy");
        HBox.setHgrow(copy, Priority.ALWAYS);

        ControlStyles.apply(actionButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
        actionButton.setFocusTraversable(true);
        getChildren().addAll(copy, actionButton);
        setAccessibleRole(AccessibleRole.PARENT);
        setActionText("Manage");
    }

    public String getTitle() { return titleLabel.getText(); }
    public void setTitle(String title) {
        titleLabel.setText(title == null ? "" : title);
        updateAccessibleText();
    }

    public String getDescription() { return descriptionLabel.getText(); }
    public void setDescription(String description) {
        descriptionLabel.setText(description == null ? "" : description);
        updateAccessibleText();
    }

    public String getActionText() { return actionButton.getText(); }
    public void setActionText(String actionText) {
        actionButton.setText(actionText == null ? "Manage" : actionText);
        updateAccessibleText();
    }

    /** Configures the complete presentation and action contract for this directory entry. */
    public void configure(String title, String description, String actionText,
            EventHandler<ActionEvent> handler) {
        setTitle(title);
        setDescription(description);
        setActionText(actionText);
        setOnAction(handler);
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        actionHandler = handler;
        actionButton.setOnAction(handler);
    }
    public Button getActionButton() { return actionButton; }

    public void setAvailable(boolean available) {
        setVisible(available);
        setManaged(available);
        actionButton.setVisible(available);
        actionButton.setManaged(available);
        actionButton.setFocusTraversable(available);
        actionButton.setOnAction(available ? actionHandler : null);
    }

    private void updateAccessibleText() {
        String action = getActionText().isBlank() ? "Open" : getActionText();
        actionButton.setAccessibleText(action + " " + getTitle());
        setAccessibleText(getTitle() + ". " + getDescription());
    }
}
