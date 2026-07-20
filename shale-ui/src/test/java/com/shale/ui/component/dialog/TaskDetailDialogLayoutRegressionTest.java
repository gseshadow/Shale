package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

final class TaskDetailDialogLayoutRegressionTest {

    @Test
    void bodyHeadingUsesCurrentTaskTitleInsteadOfStaticDuplicateDialogLabel() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/TaskDetailDialog.java"));

        assertTrue(source.contains("Label heading = new Label(taskHeadingText(model))"));
        assertTrue(source.contains("heading.getStyleClass().addAll(\"app-dialog-title\", \"task-detail-dialog-heading\")"));
        assertFalse(source.contains("Label heading = new Label(\"Task details\")"));
        assertTrue(source.contains("AppDialogs.createSecondaryWindowShell(stage, \"Task Details\", stage::close, body)"));
    }

    @Test
    void bodyHeadingFallsBackOnlyWhenTaskTitleIsBlank() {
        TaskDetailDialog.TaskDetailModel titled = modelWithTitle("Prepare deposition outline");
        TaskDetailDialog.TaskDetailModel blank = modelWithTitle("  ");

        assertEquals("Prepare deposition outline", TaskDetailDialog.taskHeadingText(titled));
        assertEquals("Untitled task", TaskDetailDialog.taskHeadingText(blank));
    }

    @Test
    void taskDetailFooterKeepsRoundedBottomCornersWithinRoundedDialogShell() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/TaskDetailDialog.java"));
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertTrue(source.contains("actions.getStyleClass().addAll(\"app-dialog-action-bar\", \"task-detail-dialog-action-bar\")"));
        assertTrue(css.contains(".task-detail-dialog-action-bar"));
        assertTrue(css.contains("-fx-background-radius: 0 0 16 16"));
        assertTrue(css.contains("-fx-border-radius: 0 0 16 16"));
        assertTrue(css.contains(".secondary-window-header"));
        assertTrue(css.contains(".secondary-window-close"));
    }

    private static TaskDetailDialog.TaskDetailModel modelWithTitle(String title) {
        return new TaskDetailDialog.TaskDetailModel(
                1L, 0, "", "", "", false, "", "", "",
                title, "", null, null, null, "Creator", List.of(), List.of(), List.of(), false);
    }
}
