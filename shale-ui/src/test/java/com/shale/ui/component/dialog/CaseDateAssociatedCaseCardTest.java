package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.*;

import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

class CaseDateAssociatedCaseCardTest {
    @Test void associatedCaseIsAVisibleAccessibleNonNavigatingMiniCard() {
        JavaFxTestSupport.runAndWait(() -> {
            VBox section = CaseDateOccurrenceDialog.createCaseSection(model(492));
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
                    () -> assertEquals("Associated Case", card.getAccessibleText()),
                    () -> assertTrue(card.isMouseTransparent()),
                    () -> assertFalse(card.isFocusTraversable()));
        });
    }

    @Test void stableCaseIdIsRetainedAndMissingIdentityIsRejected() {
        assertEquals(492, model(492).id());
        assertThrows(IllegalArgumentException.class,
                () -> CaseDateOccurrenceDialog.createCaseSection(model(0)));
    }

    private static CaseCardModel model(long id) {
        return new CaseCardModel(id, "Matter", null, null, null, "Attorney", "#123456",
                false, "Open", "#654321", "#abcdef");
    }
}
