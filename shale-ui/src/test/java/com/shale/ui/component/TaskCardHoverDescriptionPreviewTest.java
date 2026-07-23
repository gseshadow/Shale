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
    void shortDescriptionsDisplayInFullAndStayCompact() {
        String shortDescription = "Review the signed intake packet.";

        assertEquals(shortDescription, TaskCard.descriptionForTooltip(shortDescription));
        assertTrue(TaskCard.wrappedDescriptionLineCount(shortDescription) <= 2);
    }

    @Test
    void mediumDescriptionsExpandNaturallyWithoutTruncation() {
        String mediumDescription = "Confirm the client uploaded the medical release, then send the records request to the provider before Friday afternoon.";

        assertEquals(mediumDescription, TaskCard.descriptionForTooltip(mediumDescription));
        assertTrue(TaskCard.wrappedDescriptionLineCount(mediumDescription) > 1);
    }

    @Test
    void longDescriptionsAreTruncatedToWrappedLineLimitWithEllipsis() {
        String longDescription = "Long task description. ".repeat(80);

        String displayed = TaskCard.descriptionForTooltip(longDescription);

        assertTrue(displayed.endsWith("..."));
        assertTrue(displayed.length() < longDescription.length());
        assertTrue(TaskCard.wrappedDescriptionLineCount(displayed) <= 8);
    }
}
