package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

class CaseStatusSelectorTest {
    private record Status(int id, String name, String color) { }
    private static final Status FIRST = new Status(1, "Duplicate", "#ffffff");
    private static final Status SECOND = new Status(2, "Duplicate", "#123456");

    @BeforeAll static void startToolkit() { JavaFxTestSupport.ensureToolkitStarted(); }

    @Test void selectionUsesIdsAndReturnsExactTypedValue() {
        JavaFxTestSupport.runAndWait(() -> {
            var selector = selector(new Status(1, "Old name", "#000000"));
            assertTrue(card(selector, 0).getStyleClass().contains("shale-card-selected"));
            assertFalse(card(selector, 1).getStyleClass().contains("shale-card-selected"));
            AtomicReference<Status> activated = new AtomicReference<>();
            selector.setOnSelected(activated::set);
            ((Button) selector.getChildren().get(1)).fire();
            assertSame(SECOND, selector.getSelectedValue(), "duplicate names must remain distinct options");
            assertSame(SECOND, activated.get());
            assertFalse(card(selector, 0).getStyleClass().contains("shale-card-selected"));
            assertTrue(card(selector, 1).getStyleClass().contains("shale-card-selected"));
        });
    }

    @Test void nullSelectionRemainsRequiredAndDisabledButtonCannotActivate() {
        JavaFxTestSupport.runAndWait(() -> {
            var selector = selector(null);
            assertNull(selector.getSelectedValue(), "opening a picker must not silently select the first status");
            Button first = (Button) selector.getChildren().getFirst();
            assertTrue(first.isFocusTraversable(), "cards must remain keyboard reachable");
            selector.setDisable(true);
            first.fire();
            assertNull(selector.getSelectedValue());
        });
    }

    @Test void pickerCancelAndWindowDismissalReturnEmptyAndActivationReturnsRow() {
        JavaFxTestSupport.runAndWait(() -> {
            Platform.runLater(() -> ((Button) pane().lookupButton(ButtonType.CANCEL)).fire());
            assertTrue(open().isEmpty());
            Platform.runLater(() -> pane().getScene().getWindow().hide());
            assertTrue(open().isEmpty());
            Platform.runLater(() -> {
                var selector = (CaseStatusSelector<?>) pane().lookup(".case-status-selector");
                ((Button) selector.getChildren().get(1)).fire();
            });
            assertSame(SECOND, open().orElseThrow());
        });
    }

    @Test void coloredPillForegroundSurvivesSelectionAndInvalidColorUsesThemeFallback() {
        JavaFxTestSupport.runAndWait(() -> {
            var selector = selector(FIRST);
            StatusCard light = card(selector, 0);
            StatusCard dark = card(selector, 1);
            var lightName = (javafx.scene.control.Label) light.getChildren().getFirst();
            var darkName = (javafx.scene.control.Label) dark.getChildren().getFirst();
            assertTrue(light.getStyle().contains("#FFFFFFFF"), "configured light status color must remain authoritative");
            assertTrue(lightName.getStyle().contains("#172033"), "light status pills require a dark foreground");
            assertTrue(darkName.getStyle().contains("white"), "dark status pills require a light foreground");
            String before = darkName.getStyle();
            ((Button) selector.getChildren().get(1)).fire();
            assertEquals(before, darkName.getStyle(), "selected state must not replace the status foreground");
            var medium = new CaseStatusSelector<>(List.of(new Status(4, "Prelitigation", "#5B8DEF")), null,
                    Status::id, Status::name, Status::color);
            assertTrue(((javafx.scene.control.Label) card(medium, 0).getChildren().getFirst()).getStyle()
                    .contains("#172033"), "mid-brightness seeded status colors need the higher-contrast foreground");
            var fallback = new CaseStatusSelector<>(List.of(new Status(3, "Unset color", "invalid")), null,
                    Status::id, Status::name, Status::color);
            assertTrue(card(fallback, 0).getStyle().contains("-shale-color-card-surface"));
            assertTrue(((javafx.scene.control.Label) card(fallback, 0).getChildren().getFirst()).getStyle()
                    .contains("-shale-color-text-primary"));
        });
    }

    private static CaseStatusSelector<Status> selector(Status current) {
        return new CaseStatusSelector<>(List.of(FIRST, SECOND), current, Status::id, Status::name, Status::color);
    }
    private static StatusCard card(CaseStatusSelector<Status> selector, int index) {
        return (StatusCard) ((Button) selector.getChildren().get(index)).getGraphic();
    }
    private static java.util.Optional<Status> open() {
        return CaseStatusSelector.showPicker(null, List.of(FIRST, SECOND), FIRST, Status::id, Status::name, Status::color);
    }
    private static DialogPane pane() {
        return Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene)
                .filter(java.util.Objects::nonNull).map(scene -> scene.getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElseThrow();
    }
}
