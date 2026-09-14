package com.shale.ui.component;

import com.shale.ui.util.ControlStyles;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Domain-neutral read-only contact-method card shared by Contact and Organization profiles. */
public final class ContactMethodDisplayCard extends VBox {
    private final Label valueLabel;
    private final Button actionButton;

    public ContactMethodDisplayCard(String value, String kind, boolean primary, String actionText, Runnable action) {
        super(7);
        valueLabel = new Label(value == null || value.isBlank() ? "—" : value);
        valueLabel.setWrapText(true);
        valueLabel.setMinWidth(0);
        valueLabel.setMaxWidth(Double.MAX_VALUE);
        valueLabel.getStyleClass().add("contact-point-value");
        valueLabel.setTooltip(new Tooltip(valueLabel.getText()));

        HBox badgesAndAction = new HBox(6, badge(kind, false));
        if (primary) badgesAndAction.getChildren().add(badge("Primary", true));
        if (actionText != null && !actionText.isBlank() && action != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            actionButton = new Button(actionText);
            actionButton.setAccessibleText(actionText);
            ControlStyles.apply(actionButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
            actionButton.setOnAction(event -> { event.consume(); action.run(); });
            actionButton.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
            badgesAndAction.getChildren().addAll(spacer, actionButton);
        } else {
            actionButton = null;
        }
        getChildren().addAll(badgesAndAction, valueLabel);
        getStyleClass().add("contact-point-card");
    }

    public Label valueLabel() { return valueLabel; }
    public Button actionButton() { return actionButton; }

    private static Label badge(String text, boolean primary) {
        String label = text == null || text.isBlank() ? "Other" : text;
        Label badge = new Label(label);
        badge.getStyleClass().add("contact-point-badge");
        if (primary) badge.getStyleClass().add("contact-point-primary");
        return badge;
    }
}
