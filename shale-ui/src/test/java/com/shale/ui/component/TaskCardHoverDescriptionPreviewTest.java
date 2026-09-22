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
    void shortDescriptionsDisplayInFull() {
        String shortDescription = "Review the signed intake packet.";

        assertEquals("Task\n\n" + shortDescription, TaskCard.buildTaskDetailsTooltipText("Task", shortDescription));
    }

    @Test
    void mediumDescriptionsRemainComplete() {
        String mediumDescription = "Confirm the client uploaded the medical release, then send the records request to the provider before Friday afternoon.";

        assertTrue(TaskCard.buildTaskDetailsTooltipText("Task", mediumDescription).endsWith(mediumDescription));
    }

    @Test
    void longDescriptionsAreNotTruncated() {
        String longDescription = "Long task description. ".repeat(80);

        assertTrue(TaskCard.buildTaskDetailsTooltipText("Task", longDescription).endsWith(longDescription.trim()));
    }
}
