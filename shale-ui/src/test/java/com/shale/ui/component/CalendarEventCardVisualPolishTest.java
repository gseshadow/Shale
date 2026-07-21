package com.shale.ui.component;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CalendarEventCardVisualPolishTest {
    @Test
    void sharedAndUserOwnedCalendarEventsUseDistinctScopedCues() throws Exception {
        String factory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/CalendarEventCardFactory.java"));
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertTrue(factory.contains("calendar-event-card-shared"));
        assertTrue(factory.contains("calendar-event-card-user-owned"));
        assertTrue(factory.contains("calendar-event-shared-marker"));
        assertTrue(factory.contains("calendar-event-owner-marker"));
        assertTrue(factory.contains("item.assignedToUserId() == null ? \"◈\" : \"●\""));
        assertTrue(factory.contains("ColorUtil.toCssBackgroundColorOrNull(item.assignedUserColor())"));
        assertTrue(factory.contains("Tooltip.install(card"));
        assertTrue(css.contains(".calendar-event-card-shared"));
        assertTrue(css.contains(".calendar-event-card-user-owned"));
        assertTrue(css.contains(".calendar-event-owner-marker"));
        assertTrue(css.contains(".calendar-event-shared-marker"));
    }

    @Test
    void eventTypeAccentAndUserOwnershipTintRemainSeparate() throws Exception {
        String factory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/CalendarEventCardFactory.java"));

        assertTrue(factory.contains("buildAccentBar(item.colorHex())"));
        assertTrue(factory.contains("buildOwnershipMarker(item)"));
        assertTrue(factory.contains("normalizeStoredColor(item.assignedUserColor())"));
        assertFalse(factory.contains("buildAccentBar(item.assignedUserColor())"));
    }
}
