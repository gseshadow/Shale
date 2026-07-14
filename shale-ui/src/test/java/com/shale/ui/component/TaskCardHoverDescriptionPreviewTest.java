package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TaskCardHoverDescriptionPreviewTest {
    @Test
    void blankDescriptionsRemainHidden() {
        assertEquals("", TaskCard.buildHoverDescriptionPreview(null));
        assertEquals("", TaskCard.buildHoverDescriptionPreview("   \n\t  "));
    }

    @Test
    void newlineHeavyDescriptionsPreserveUsefulLineBreaksWithoutEllipsisOnlyLine() {
        String preview = TaskCard.buildHoverDescriptionPreview("first line\n\n\nsecond line\nthird line");

        assertEquals("first line\n\nsecond line\nthird line", preview);
        assertFalse(preview.endsWith("\n..."));
    }

    @Test
    void longConsoleStyleDescriptionsAreBoundedAndStillUseful() {
        StringBuilder console = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            console.append("[INFO] compiling module-").append(i).append(" with a long diagnostic message").append('\n');
        }

        String preview = TaskCard.buildHoverDescriptionPreview(console.toString());

        assertTrue(preview.startsWith("[INFO] compiling module-1"));
        assertTrue(preview.contains("[INFO] compiling module-2"));
        assertTrue(preview.endsWith("..."));
        assertTrue(preview.length() <= 523, "Preview should be bounded before JavaFX line-height clipping is applied.");
        assertFalse(preview.endsWith("\n..."));
    }

    @Test
    void singleLongLinesAreTrimmedToABoundedPreview() {
        String singleLine = "A".repeat(900);

        String preview = TaskCard.buildHoverDescriptionPreview(singleLine);

        assertTrue(preview.endsWith("..."));
        assertTrue(preview.length() <= 523);
        assertFalse(preview.contains("\n"));
    }
}
