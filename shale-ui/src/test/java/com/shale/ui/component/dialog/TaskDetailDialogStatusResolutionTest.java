package com.shale.ui.component.dialog;

import java.util.List;

import com.shale.core.dto.TaskStatusOptionDto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TaskDetailDialogStatusResolutionTest {

    @Test
    void isCompletedStatus_recognizesStandardCompletionSystemKeys() {
        assertTrue(TaskDetailDialog.isCompletedStatus(status(10, "Done", "done")));
        assertTrue(TaskDetailDialog.isCompletedStatus(status(11, "Complete", "complete")));
        assertTrue(TaskDetailDialog.isCompletedStatus(status(12, "Completed", "completed")));
        assertTrue(TaskDetailDialog.isCompletedStatus(status(13, "Closed", "closed")));
        assertFalse(TaskDetailDialog.isCompletedStatus(status(14, "Open", "open")));
    }

    @Test
    void findIncompleteFallbackStatus_prefersLastNonCompletedSelectionWhenAvailable() {
        TaskStatusOptionDto open = status(1, "Open", "open");
        TaskStatusOptionDto inProgress = status(2, "In progress", "in_progress");
        TaskStatusOptionDto done = status(3, "Done", "done");

        TaskStatusOptionDto fallback = TaskDetailDialog.findIncompleteFallbackStatus(
                List.of(open, inProgress, done),
                2);

        assertEquals(2, fallback.id());
    }

    @Test
    void findIncompleteFallbackStatus_fallsBackToOpenThenFirstNonCompleted() {
        TaskStatusOptionDto blocked = status(4, "Blocked", "blocked");
        TaskStatusOptionDto done = status(5, "Done", "done");
        TaskStatusOptionDto open = status(6, "Open", "open");

        TaskStatusOptionDto fallbackWithOpen = TaskDetailDialog.findIncompleteFallbackStatus(
                List.of(done, blocked, open),
                null);
        assertEquals(6, fallbackWithOpen.id());

        TaskStatusOptionDto fallbackFirstNonCompleted = TaskDetailDialog.findIncompleteFallbackStatus(
                List.of(done, blocked),
                null);
        assertEquals(4, fallbackFirstNonCompleted.id());
    }

    private static TaskStatusOptionDto status(int id, String name, String systemKey) {
        return new TaskStatusOptionDto(id, name, id, "#FFFFFF", systemKey);
    }
}
