package com.shale.ui.util;

import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ControlStylesRuntimeContractTest {
    @Test
    void applyingAClassificationReplacesOnlyConflictingSemanticClasses() {
        Button button = new Button("Save");
        button.getStyleClass().add("feature-action");

        ControlStyles.apply(button, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
        ControlStyles.apply(button, ControlStyles.Purpose.DANGER, ControlStyles.Size.SMALL);

        assertAll(
                () -> assertTrue(button.getStyleClass().contains(ControlStyles.BUTTON_BASE)),
                () -> assertTrue(button.getStyleClass().contains("shale-control-danger")),
                () -> assertTrue(button.getStyleClass().contains("shale-control-small")),
                () -> assertTrue(button.getStyleClass().contains("feature-action")),
                () -> assertFalse(button.getStyleClass().contains("shale-control-primary")),
                () -> assertFalse(button.getStyleClass().contains("shale-control-standard")));
    }

    @Test
    void disabledAndDefaultStateRetainSemanticIdentity() {
        Button button = ActionButtonFactory.semantic("Create", null,
                ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
        button.setDefaultButton(true);
        button.setDisable(true);

        assertTrue(button.isDefaultButton());
        assertTrue(button.isDisabled());
        assertTrue(button.getStyleClass().containsAll(java.util.List.of(
                "shale-control-button", "shale-control-primary", "shale-control-standard")));
    }
}
