package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TaskCardHoverDescriptionPreviewTest {
    @Test
    void blankDescriptionsRemainHidden() {
        assertEquals("", TaskCard.normalizeTaskDetailsText(null));
        assertEquals("", TaskCard.normalizeTaskDetailsText("   \n\t  "));
    }

    @Test
    void newlineHeavyDescriptionsPreserveUsefulLineBreaksForTooltip() {
        String normalized = TaskCard.normalizeTaskDetailsText("first line\n\n\nsecond line\nthird line");

        assertEquals("first line\n\nsecond line\nthird line", normalized);
    }

    @Test
    void longDescriptionsAreNotTruncatedBeforeTooltipDisplay() {
        String singleLine = "A".repeat(900);

        String normalized = TaskCard.normalizeTaskDetailsText(singleLine);

        assertEquals(900, normalized.length());
        assertFalse(normalized.endsWith("..."));
    }

    @Test
    void shortDescriptionsEstimateBelowScrollThreshold() {
        assertTrue(TaskCard.estimatedTooltipDescriptionHeight("short task note") < 220);
    }
}
