package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class TaskEmbeddedCaseCardReuseTest {

    @Test
    void myTasksCardsUseEmbeddedCaseCardVariant() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/TaskCard.java"));

        assertTrue(source.contains("CaseCardFactory.Variant.EMBEDDED"),
                "Task cards should use the explicit embedded CaseCardFactory variant.");
        assertFalse(source.contains("caseCard.getStyleClass().add(\"task-related-case-card\")"),
                "Task cards should not manually mutate a MINI case card into the embedded style.");
        assertFalse(source.contains("CaseCardFactory.Variant.TASK_PREVIEW"),
                "My Tasks should not route through a separate task-preview case-card variant.");
        assertFalse(source.contains("my-tasks-mini-case-card"),
                "My Tasks should not carry a separate embedded case-card style class.");
    }

    @Test
    void taskDetailsUseEmbeddedCaseCardVariantStyleAndCaseColors() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/TaskDetailDialog.java"));

        assertTrue(source.contains("CaseCardFactory.Variant.EMBEDDED"),
                "Task Details should render related cases through the explicit embedded CaseCardFactory variant.");
        assertFalse(source.contains("caseCard.getStyleClass().add(\"task-related-case-card\")"),
                "Task Details should not manually mutate a MINI case card into the embedded style.");
        assertTrue(source.contains("model.casePrimaryStatusName()")
                        && source.contains("model.casePrimaryStatusColor()")
                        && source.contains("model.casePracticeAreaColor()"),
                "Task Details should pass case status and practice-area colors into the shared case card path.");
    }

    @Test
    void calendarAndNewCalendarCasePreviewsRemainStandaloneMini() throws Exception {
        String calendar = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
        String newCalendarDialog = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/NewCalendarEventDialog.java"));

        assertTrue(calendar.contains("CaseCardFactory.Variant.MINI"),
                "Calendar related case previews should remain standalone MINI for now.");
        assertFalse(calendar.contains("CaseCardFactory.Variant.EMBEDDED"),
                "Calendar related case previews should not be migrated to EMBEDDED in this refactor.");
        assertTrue(newCalendarDialog.contains("CaseCardFactory.Variant.MINI"),
                "New Calendar Event selected case previews should remain standalone MINI for now.");
        assertFalse(newCalendarDialog.contains("CaseCardFactory.Variant.EMBEDDED"),
                "New Calendar Event selected case previews should not be migrated to EMBEDDED in this refactor.");
    }

}
