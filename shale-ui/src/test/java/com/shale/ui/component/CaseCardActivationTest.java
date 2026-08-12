package com.shale.ui.component;

import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CaseCardActivationTest {
    @Test
    void primaryMouseAndKeyboardActivateExactlyOnce() {
        JavaFxTestSupport.runAndWait(() -> {
            CaseCard card = new CaseCard(42);
            AtomicInteger opened = new AtomicInteger();
            card.setOnOpen(id -> {
                assertEquals(42, id);
                opened.incrementAndGet();
            });

            MouseEvent click = new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1,
                    MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, true, false, false, null);
            Event.fireEvent(card, click);
            assertEquals(1, opened.get());

            fireKey(card, KeyCode.ENTER);
            fireKey(card, KeyCode.SPACE);
            assertEquals(3, opened.get());
            assertTrue(card.isFocusTraversable());
        });
    }

    @Test
    void secondaryMouseClickDoesNotNavigate() {
        JavaFxTestSupport.runAndWait(() -> {
            CaseCard card = new CaseCard(7);
            AtomicInteger opened = new AtomicInteger();
            card.setOnOpen(id -> opened.incrementAndGet());
            Event.fireEvent(card, new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1,
                    MouseButton.SECONDARY, 1, false, false, false, false,
                    false, false, true, false, false, false, null));
            assertEquals(0, opened.get());
        });
    }

    private static void fireKey(CaseCard card, KeyCode code) {
        KeyEvent event = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false);
        Event.fireEvent(card, event);
    }
}
