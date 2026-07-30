package com.shale.ui.util;

import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class ControlStylesTest {
    @Test void appliesEveryPurposeAndReplacesConflicts() {
        JavaFxTestSupport.runAndWait(() -> {
            Button button = new Button();
            for (ControlStyles.Purpose purpose : ControlStyles.Purpose.values()) {
                ControlStyles.apply(button, purpose);
                assertEquals(1, purposeCount(button));
            }
        });
    }

    @Test void appliesAndChangesSizesWithoutConflict() {
        JavaFxTestSupport.runAndWait(() -> {
            Button button = ControlStyles.apply(new Button(), ControlStyles.Purpose.PRIMARY, ControlStyles.Size.SMALL);
            assertTrue(button.getStyleClass().contains("shale-control-small"));
            ControlStyles.standard(button);
            assertFalse(button.getStyleClass().contains("shale-control-small"));
            assertTrue(button.getStyleClass().contains("shale-control-standard"));
        });
    }

    @Test void isIdempotentAndPreservesFeatureClasses() {
        JavaFxTestSupport.runAndWait(() -> {
            Button button = new Button();
            button.getStyleClass().add("feature-specific");
            ControlStyles.apply(button, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
            ControlStyles.apply(button, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
            assertTrue(button.getStyleClass().contains("feature-specific"));
            assertEquals(button.getStyleClass().size(), Set.copyOf(button.getStyleClass()).size());
        });
    }

    @Test void togglesInvalidPseudoClass() {
        JavaFxTestSupport.runAndWait(() -> {
            TextField field = ControlStyles.formControl(new TextField());
            PseudoClass invalid = PseudoClass.getPseudoClass("invalid");
            ControlStyles.setInvalid(field, true);
            assertTrue(field.getPseudoClassStates().contains(invalid));
            ControlStyles.setInvalid(field, false);
            assertFalse(field.getPseudoClassStates().contains(invalid));
        });
    }

    @Test void rejectsNullArgumentsConsistently() {
        assertThrows(NullPointerException.class, () -> ControlStyles.apply(null, ControlStyles.Purpose.PRIMARY));
        JavaFxTestSupport.runAndWait(() -> {
            assertThrows(NullPointerException.class, () -> ControlStyles.apply(new Button(), null));
            assertThrows(NullPointerException.class, () -> ControlStyles.formControl(null));
            assertThrows(NullPointerException.class, () -> ControlStyles.setInvalid(null, true));
        });
    }

    private static long purposeCount(Button button) {
        return button.getStyleClass().stream().filter(s -> s.startsWith("shale-control-")
                && !Set.of("shale-control-button", "shale-control-small", "shale-control-standard", "shale-control-icon-only").contains(s)).count();
    }
}
