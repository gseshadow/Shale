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

    @Test void organizationCardActionDoesNotNavigateAndUnsafeWebsiteHasNoButton() {
        JavaFxTestSupport.runAndWait(() -> {
            AtomicInteger launches=new AtomicInteger(),navigations=new AtomicInteger();
            OrganizationCard card=new OrganizationCard();card.setOrganizationId(7);card.setOnOpen(id->navigations.incrementAndGet());
            card.setExternalActions(new ContactExternalActions(uri->launches.incrementAndGet()));
            card.setName("Example");card.setStructuredPhone("(555) 010-1000","+15550101000",null);
            card.setWebsite("javascript:alert(1)");card.applyFull();
            var methodCards=card.lookupAll(".contact-point-card");assertEquals(2,methodCards.size());
            Button call=(Button)card.lookup(".contact-point-card .shale-control-button");assertNotNull(call);
            call.fire();assertEquals(1,launches.get());assertEquals(0,navigations.get());
            long websiteButtons=methodCards.stream().map(n->(ContactMethodDisplayCard)n).filter(c->c.valueLabel().getText().contains("javascript:")).filter(c->c.actionButton()!=null).count();
            assertEquals(0,websiteButtons);
        });
    }
}
