package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CalendarWorkflowWindowPresentationContractTest {
    private static final Path WIZARD = Path.of("src/main/java/com/shale/ui/component/dialog/NewEventWizard.java");
    private static final Path GENERAL = Path.of("src/main/java/com/shale/ui/component/dialog/NewCalendarEventDialog.java");
    private static final Path OCCURRENCE = Path.of("src/main/java/com/shale/ui/component/dialog/CaseDateOccurrenceDialog.java");
    private static final Path FOUNDATION = Path.of("src/main/resources/css/foundation/calendar-windows.css");

    @Test
    void calendarFamilyUsesOneSemanticFoundationAndThemeAwareSizing() throws Exception {
        String wizard = Files.readString(WIZARD);
        String general = Files.readString(GENERAL);
        String occurrence = Files.readString(OCCURRENCE);
        String css = Files.readString(FOUNDATION);

        for (String source : new String[] { wizard, general, occurrence }) {
            assertTrue(source.contains("calendar-dialog-root"), "every production calendar window needs the shared A.2 root");
            assertTrue(source.contains("calendar-dialog-scroll"), "long window content must scroll independently");
            assertTrue(source.contains("calendar-dialog-footer"), "actions must remain in a stable footer");
            assertTrue(source.contains("WindowSizingUtil.sizeModalStage"), "windows must size against the owner's screen");
        }
        assertTrue(css.contains("-shale-color-overlay-dialog"));
        assertTrue(css.contains("-shale-color-section-surface"));
        assertTrue(css.contains("-shale-color-validation-error"));
        assertFalse(css.contains("#"), "calendar windows must use semantic tokens instead of hard-coded paint");
    }

    @Test
    void canonicalShellTitleIsNotDuplicatedInsideGeneralEventContent() throws Exception {
        String general = Files.readString(GENERAL);
        assertFalse(general.contains("new VBox(12, heading, message"),
                "the custom window shell owns the one canonical title");
        assertTrue(general.contains("new VBox(12, message, formScroll, actions)"));
    }

    @Test
    void requiredAndStatusControlsExposeAccessibleNames() throws Exception {
        String wizard = Files.readString(WIZARD);
        String occurrence = Files.readString(OCCURRENCE);
        assertTrue(wizard.contains("Start date, required"));
        assertTrue(wizard.contains("Event validation status"));
        assertTrue(wizard.contains("Press Enter or Space to select a case"));
        assertTrue(occurrence.contains("Case Date type, required"));
        assertTrue(occurrence.contains("Case Date validation status"));
        assertTrue(occurrence.contains("calendar-associated-case"));
    }
}
