package com.shale.ui.component;

import javafx.geometry.BoundingBox;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransientPopupSupportTest {
    private static final Rectangle2D SCREEN = new Rectangle2D(100, 50, 1000, 700);

    @Test void positionsBesideAnchorWhenThereIsRoom() {
        var position = TransientPopupSupport.position(new BoundingBox(200, 150, 200, 100), 300, 200, SCREEN, 10);
        assertEquals(410, position.x());
        assertEquals(150, position.y());
    }

    @Test void flipsAndClampsWithinTheOwningScreensVisualBounds() {
        var position = TransientPopupSupport.position(new BoundingBox(1000, 680, 80, 40), 300, 240, SCREEN, 10);
        assertTrue(position.x() >= SCREEN.getMinX() && position.x() + 300 <= SCREEN.getMaxX());
        assertTrue(position.y() >= SCREEN.getMinY() && position.y() + 240 <= SCREEN.getMaxY());
        assertTrue(position.x() < 1000, "A right-edge popup should flip to the anchor's left.");
    }
}
