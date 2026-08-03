package com.shale.ui.component;

import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UserCardTableMiniTest {
    @Test void tableMiniRendersIdentityColorMetadataFallbackAndInactiveState() {
        Assumptions.assumeTrue(System.getenv("DISPLAY") != null || System.getProperty("os.name", "").toLowerCase().contains("win"), "graphical runtime unavailable");
        JavaFxTestSupport.runAndWait(() -> {
            UserCardFactory factory = new UserCardFactory(null);
            UserCard inactive = factory.createTableMini(new UserCardModel(41, "Alex Morgan", "#336699", "AM"), "User ID #41", true);
            assertTrue(inactive.isMouseTransparent());
            assertTrue(inactive.getStyleClass().containsAll(java.util.List.of("shale-entity-card", "shale-embedded-card-surface", "shale-entity-card-inline", "user-card-mini", "user-card-table-mini")));
            assertTrue(inactive.getStyle().isEmpty(), "table MINI must use the shared CSS card shell rather than inline styling");
            assertTrue(inactive.getPseudoClassStates().contains(PseudoClass.getPseudoClass("inactive")));
            assertEquals("Alex Morgan", ((Label) inactive.lookup(".user-card-table-name")).getText());
            assertEquals("User ID #41", ((Label) inactive.lookup(".user-card-table-metadata")).getText());
            assertEquals("AM", ((Label) inactive.lookup(".user-card-avatar-initials")).getText());
            assertEquals(Color.web("#336699"), ((Circle) inactive.lookup(".user-card-avatar-circle")).getFill());

            UserCard fallback = factory.createTableMini(new UserCardModel(42, "", null, null), "User ID #42", false);
            assertFalse(fallback.getPseudoClassStates().contains(PseudoClass.getPseudoClass("inactive")));
            assertEquals("—", ((Label) fallback.lookup(".user-card-table-name")).getText());
            assertEquals("?", ((Label) fallback.lookup(".user-card-avatar-initials")).getText());
        });
    }
}
