package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import com.shale.ui.util.ContactExternalActions;

final class ContactMethodDisplayCardTest {
    @Test void usesContactBadgesOrdinaryValueAndSemanticActionButton() {
        JavaFxTestSupport.runAndWait(() -> {
            AtomicInteger calls = new AtomicInteger();
            ContactMethodDisplayCard card = new ContactMethodDisplayCard("Very long value that remains ordinary wrapping display text", "Work", true, "Call", calls::incrementAndGet);
            Label value = card.valueLabel(); Button action = card.actionButton();
            assertTrue(card.getStyleClass().contains("contact-point-card"));
            assertTrue(value.getStyleClass().contains("contact-point-value"));
            assertFalse(value.getStyleClass().contains("external-action-link"));
            assertTrue(value.isWrapText()); assertNotNull(value.getTooltip());
            assertNotNull(action); assertEquals("Call", action.getText());
            assertTrue(action.getStyleClass().contains("shale-control-secondary"));
            assertTrue(action.getStyleClass().contains("shale-control-small"));
            assertEquals("Work", ((Label)((javafx.scene.layout.HBox)card.getChildren().get(0)).getChildren().get(0)).getText());
            assertEquals("Primary", ((Label)((javafx.scene.layout.HBox)card.getChildren().get(0)).getChildren().get(1)).getText());
            action.fire(); assertEquals(1, calls.get());
        });
    }

    @Test void faxHasNoButtonOrEmptyActionSpacer() {
        JavaFxTestSupport.runAndWait(() -> {
            ContactMethodDisplayCard fax = new ContactMethodDisplayCard("555-0100", "Fax", false, null, null);
            assertNull(fax.actionButton());
            assertEquals(1, ((javafx.scene.layout.HBox)fax.getChildren().get(0)).getChildren().size());
        });
    }

    @Test void fullOrganizationCardUsesReadOnlySummariesWithoutExternalActions() {
        JavaFxTestSupport.runAndWait(() -> {
            AtomicInteger launches=new AtomicInteger(),navigations=new AtomicInteger();
            OrganizationCard card=new OrganizationCard();card.setOrganizationId(7);card.setOnOpen(id->navigations.incrementAndGet());
            card.setExternalActions(new ContactExternalActions(uri->launches.incrementAndGet()));
            card.setName("Example");card.setStructuredPhone("(555) 010-1000","+15550101000",null);
            card.setWebsite("javascript:alert(1)");card.applyFull();
            assertEquals(2,card.lookupAll(".organization-card-summary-box").size());
            assertTrue(card.lookupAll(".organization-card-summary-box .button").isEmpty());
            assertEquals(0,launches.get());assertEquals(0,navigations.get());
        });
    }
}
