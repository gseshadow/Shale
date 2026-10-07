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

class PracticeAreaSelectorTest {
    private record Area(int id, String name, String color) { }
    private static final Area FIRST = new Area(1, "Duplicate", "#ffffff");
    private static final Area SECOND = new Area(2, "Duplicate", "#123456");

    @BeforeAll static void startToolkit() { JavaFxTestSupport.ensureToolkitStarted(); }

    @Test void selectionUsesIdsAndReturnsExactTypedValue() {
        JavaFxTestSupport.runAndWait(() -> {
            var selector = selector(new Area(1, "Old name", "#000000"));
            assertTrue(card(selector, 0).getStyleClass().contains("shale-card-selected"));
            assertFalse(card(selector, 1).getStyleClass().contains("shale-card-selected"));
            AtomicReference<Area> activated = new AtomicReference<>();
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
            assertNull(selector.getSelectedValue(), "opening a picker must not silently select the first area");
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
                var selector = (PracticeAreaSelector<?>) pane().lookup(".practice-area-selector");
                ((Button) selector.getChildren().get(1)).fire();
            });
            assertSame(SECOND, open().orElseThrow());
        });
    }

    private static PracticeAreaSelector<Area> selector(Area current) {
        return new PracticeAreaSelector<>(List.of(FIRST, SECOND), current, Area::id, Area::name, Area::color);
    }
    private static PracticeAreaCard card(PracticeAreaSelector<Area> selector, int index) {
        return (PracticeAreaCard) ((Button) selector.getChildren().get(index)).getGraphic();
    }
    private static java.util.Optional<Area> open() {
        return PracticeAreaSelector.showPicker(null, List.of(FIRST, SECOND), FIRST, Area::id, Area::name, Area::color);
    }
    private static DialogPane pane() {
        return Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene)
                .filter(java.util.Objects::nonNull).map(scene -> scene.getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElseThrow();
    }
}
