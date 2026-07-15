package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.ui.component.factory.TaskCardFactory;

final class TaskCardStatusPrecedenceTest {

    @Test
    void completedTaskWithOpenHydratedStatusDisplaysCompleted() {
        TaskCardFactory.TaskStatusPresentation status = presentationFor(completedTask("Open", "#111111"));

        assertEquals("Completed", status.name());
        assertEquals(TaskCardFactory.COMPLETED_STATUS_FALLBACK_COLOR, status.colorHex());
    }

    @Test
    void completedTaskWithWaitingHydratedStatusDisplaysCompleted() {
        TaskCardFactory.TaskStatusPresentation status = presentationFor(completedTask("Waiting", "#222222"));

        assertEquals("Completed", status.name());
        assertEquals(TaskCardFactory.COMPLETED_STATUS_FALLBACK_COLOR, status.colorHex());
    }

    @Test
    void nonCompletedTaskKeepsHydratedStatus() {
        TaskCardFactory.TaskStatusPresentation status = presentationFor(activeTask("In Progress", "#333333"));

        assertEquals("In Progress", status.name());
        assertEquals("#333333", status.colorHex());
    }

    @Test
    void nonCompletedWaitingTaskKeepsWaitingStatus() {
        TaskCardFactory.TaskStatusPresentation status = presentationFor(activeTask("Waiting", "#FBBF24"));

        assertEquals("Waiting", status.name());
        assertEquals("#FBBF24", status.colorHex());
    }

    @Test
    void nonCompletedOpenTaskKeepsOpenStatus() {
        TaskCardFactory.TaskStatusPresentation status = presentationFor(activeTask("Open", "#64748B"));

        assertEquals("Open", status.name());
        assertEquals("#64748B", status.colorHex());
    }

    @Test
    void configuredCompletedStatusColorIsUsedWhenHydratedAsCompleted() {
        TaskCardFactory.TaskStatusPresentation status = presentationFor(completedTask("Completed", "#16A34A"));

        assertEquals("Completed", status.name());
        assertEquals("#16A34A", status.colorHex());
    }

    @Test
    void missingCompletedStatusColorUsesSharedFallback() {
        TaskCardFactory.TaskStatusPresentation status = presentationFor(completedTask("Completed", " "));

        assertEquals("Completed", status.name());
        assertEquals(TaskCardFactory.COMPLETED_STATUS_FALLBACK_COLOR, status.colorHex());
    }

    @Test
    void historicalInconsistentRowsResolveVisuallyFromCompletedAt() {
        TaskCardFactory.TaskCardModel staleOpenCompletedTask = completedTask("Open", "#64748B");

        TaskCardFactory.TaskStatusPresentation status = presentationFor(staleOpenCompletedTask);

        assertEquals("Completed", status.name());
    }

    private static TaskCardFactory.TaskStatusPresentation presentationFor(TaskCardFactory.TaskCardModel model) {
        return TaskCardFactory.resolveTaskStatusPresentation(
                model.completedAt() != null,
                model.taskStatusName(),
                model.taskStatusColorHex());
    }

    private static TaskCardFactory.TaskCardModel activeTask(String statusName, String statusColorHex) {
        return task(statusName, statusColorHex, null);
    }

    private static TaskCardFactory.TaskCardModel completedTask(String statusName, String statusColorHex) {
        return task(statusName, statusColorHex, LocalDateTime.now());
    }

    private static TaskCardFactory.TaskCardModel task(String statusName, String statusColorHex, LocalDateTime completedAt) {
        return new TaskCardFactory.TaskCardModel(
                1L, null, null, null, null, null, null, null, null,
                "Task", null, null, statusName, statusColorHex, null,
                null, completedAt, List.of());
    }
}
