package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class TaskWorkflowWindowPresentationContractTest {
    private static final Path DETAIL = Path.of("src/main/java/com/shale/ui/component/dialog/TaskDetailDialog.java");
    private static final Path CREATE = Path.of("src/main/java/com/shale/ui/component/dialog/NewTaskDialog.java");
    private static final Path FOUNDATION = Path.of("src/main/resources/css/foundation/task-windows.css");

    @Test
    void detailUsesIndependentBoundedPanelsAndAStableFooter() throws Exception {
        String detail = Files.readString(DETAIL);
        assertTrue(detail.contains("new ScrollPane(formContent)"),
                "task fields need their own bounded scrolling owner");
        assertTrue(detail.contains("new ScrollPane(historyList)"),
                "task activity needs an independent bounded scrolling owner");
        assertFalse(detail.contains("new ScrollPane(contentColumns)"),
                "the peer task and activity panels must not share one scrolling page");
        assertTrue(detail.contains("new VBox(14, headerContent, workspace, actions)"),
                "the action footer must remain outside the independently scrolling workspace");
        assertTrue(detail.contains("newWidth.doubleValue() < 760"),
                "the workspace must stack complete panels at narrow supported widths");
    }

    @Test
    void taskFamilyUsesOneSemanticFoundationWithoutInlinePagePaint() throws Exception {
        String detail = Files.readString(DETAIL);
        String create = Files.readString(CREATE);
        String css = Files.readString(FOUNDATION);
        assertTrue(detail.contains("task-window-root") && create.contains("task-window-root"));
        assertTrue(detail.contains("task-window-error") && create.contains("task-window-error"));
        assertTrue(css.contains("-shale-color-overlay-dialog")
                && css.contains("-shale-color-section-surface")
                && css.contains("-shale-color-validation-error"));
        assertFalse(detail.contains("rgba(17,37,66"));
        assertFalse(create.contains("rgba(17,37,66"));
    }

    @Test
    void taskControlsExposeAccessiblePurposeAndSanitizedFailureCopy() throws Exception {
        String detail = Files.readString(DETAIL);
        String create = Files.readString(CREATE);
        for (String accessible : new String[] { "Task status, required", "Task priority, required",
                "Add assigned user", "Save task changes", "Delete task", "Change task completion state" }) {
            assertTrue(detail.contains("setAccessibleText(\"" + accessible + "\")"), accessible);
        }
        assertTrue(create.contains("setAccessibleText(\"Task title, required\")"));
        assertTrue(create.contains("setAccessibleText(\"Create task\")"));
        assertFalse(detail.contains("+ rootCauseMessage(ex)"),
                "ordinary task-window feedback must not reveal internal exception details");
    }
}
