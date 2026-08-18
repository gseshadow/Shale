package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.*;

import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CaseDateAssociatedCaseCardTest {
    @Test void associatedCaseIsVisibleAccessibleAndRoutesMouseEnterAndSpaceByStableId() {
        JavaFxTestSupport.runAndWait(() -> {
            AtomicInteger routedId = new AtomicInteger();
            VBox section = CaseDateOccurrenceDialog.createCaseSection(model(492), routedId::set);
            Scene scene = new Scene(section);
            section.applyCss();
            section.layout();

            Label label = (Label) scene.lookup("#case-date-associated-case-label");
            var card = scene.lookup("#case-date-associated-case-card");
            assertAll(
                    () -> assertNotNull(label),
                    () -> assertEquals("Case", label.getText()),
                    () -> assertTrue(label.isVisible()),
                    () -> assertNotNull(card),
                    () -> assertSame(card, label.getLabelFor()),
                    () -> assertEquals("Open associated Case", card.getAccessibleText()),
                    () -> assertTrue(card.isFocusTraversable()));
            Event.fireEvent(card, primaryClick()); assertEquals(492, routedId.get());
            routedId.set(0); Event.fireEvent(card, key(KeyCode.ENTER)); assertEquals(492, routedId.get());
            routedId.set(0); Event.fireEvent(card, key(KeyCode.SPACE)); assertEquals(492, routedId.get());
        });
    }

    @Test void stableCaseIdIsRetainedAndMissingIdentityIsRejected() {
        assertEquals(492, model(492).id());
        assertThrows(IllegalArgumentException.class,
                () -> CaseDateOccurrenceDialog.createCaseSection(model(0), id -> {}));
    }

    private static CaseCardModel model(long id) {
        return new CaseCardModel(id, "Matter", null, null, null, "Attorney", "#123456",
                false, "Open", "#654321", "#abcdef");
    }
    private static MouseEvent primaryClick() { return new MouseEvent(MouseEvent.MOUSE_CLICKED,1,1,1,1,
            MouseButton.PRIMARY,1,false,false,false,false,true,false,false,true,false,true,null); }
    private static KeyEvent key(KeyCode code) { return new KeyEvent(KeyEvent.KEY_PRESSED,"","",code,false,false,false,false); }
}
