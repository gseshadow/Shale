package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class TaskEmbeddedCaseCardReuseTest {

    @Test
    void myTasksCardsUseOverviewEmbeddedCaseCardVariantAndStyleClass() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/TaskCard.java"));

        assertTrue(source.contains("CaseCardFactory.Variant.MINI"),
                "Task cards should use the same embedded CaseCardFactory MINI variant used by Overview task cards.");
        assertTrue(source.contains("caseCard.getStyleClass().add(\"task-related-case-card\")"),
                "Task cards should share the Overview embedded case-card style class.");
        assertFalse(source.contains("CaseCardFactory.Variant.TASK_PREVIEW"),
                "My Tasks should not route through a separate task-preview case-card variant.");
        assertFalse(source.contains("my-tasks-mini-case-card"),
                "My Tasks should not carry a separate embedded case-card style class.");
    }

    @Test
    void taskDetailsUseSharedEmbeddedCaseCardVariantStyleAndCaseColors() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/TaskDetailDialog.java"));

        assertTrue(source.contains("CaseCardFactory.Variant.MINI"),
                "Task Details should render related cases through the shared CaseCardFactory MINI variant.");
        assertTrue(source.contains("caseCard.getStyleClass().add(\"task-related-case-card\")"),
                "Task Details should share the Overview embedded case-card style class.");
        assertTrue(source.contains("model.casePrimaryStatusName()")
                        && source.contains("model.casePrimaryStatusColor()")
                        && source.contains("model.casePracticeAreaColor()"),
                "Task Details should pass case status and practice-area colors into the shared case card path.");
    }

    @Test
    void notificationCenterCaseCardsUseSharedEmbeddedCaseCardVariantAndStyle() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/NotificationCardFactory.java"));
        String dialogSource = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/NotificationCenterDialog.java"));
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertTrue(source.contains("CaseCardFactory.Variant.MINI"),
                "Notification case previews should use the shared embedded CaseCardFactory MINI variant.");
        assertTrue(source.contains("caseCard.getStyleClass().add(\"task-related-case-card\")"),
                "Notification case previews should share the same embedded case-card style class used by My Tasks, Task Details, and Overview.");
        assertFalse(source.contains("notification-row-case-mini"),
                "Notification case previews should not use a notification-only case-card wrapper/style.");
        assertFalse(source.contains("notification-row-case-mini-card"),
                "Notification case previews should not use a notification-only case-card style.");
        assertTrue(dialogSource.contains("hasStyleClassInAncestorChain(node, \"task-related-case-card\")"),
                "Notification row interactions should treat the shared embedded case card as an interactive child.");
        assertFalse(css.contains("notification-row-case-mini"),
                "Notification case-card CSS should not reintroduce notification-only mini case-card styling.");
    }
}
