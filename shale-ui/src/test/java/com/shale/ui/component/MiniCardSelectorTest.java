package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

class MiniCardSelectorTest {
    @BeforeAll static void startToolkit() { JavaFxTestSupport.ensureToolkitStarted(); }

    @Test void nativeKeyboardActivationChangesTypedSelectionAndAccessibleState() {
        JavaFxTestSupport.runAndWait(() -> {
            var selector = new MiniCardSelector<>("Test", List.of(1, 2), 1,
                    value -> value, Object::toString, value -> new Label(value.toString()));
            Stage stage = new Stage(); stage.setScene(new Scene(selector)); stage.show();
            try {
                Button button = (Button) selector.getChildren().get(1);
                assertTrue(button.isFocusTraversable());
                button.requestFocus();
                button.fireEvent(key(KeyEvent.KEY_PRESSED));
                button.fireEvent(key(KeyEvent.KEY_RELEASED));
                assertEquals(2, selector.getSelectedValue(), "Space must activate a focused mini card");
                assertEquals("Selected Test", button.getAccessibleHelp());
                assertTrue(button.getGraphic().getStyleClass().contains("shale-card-selected"));
            } finally { stage.hide(); }
        });
    }

    private static KeyEvent key(javafx.event.EventType<KeyEvent> type) {
        return new KeyEvent(type, " ", " ", KeyCode.SPACE, false, false, false, false);
    }
}
