package com.shale.ui.util;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;

/** Applies caller-decided availability to an action without making authorization decisions. */
public final class ControlAvailability {
    private ControlAvailability() { }

    public static void apply(ButtonBase control, boolean available, EventHandler<ActionEvent> handler) {
        apply(control, null, available, handler);
    }

    public static void apply(ButtonBase control, Node exclusiveContainer, boolean available,
            EventHandler<ActionEvent> handler) {
        if (control != null) {
            control.setVisible(available);
            control.setManaged(available);
            control.setFocusTraversable(available);
            control.setOnAction(available ? handler : null);
        }
        if (exclusiveContainer != null) {
            exclusiveContainer.setVisible(available);
            exclusiveContainer.setManaged(available);
        }
    }
}
