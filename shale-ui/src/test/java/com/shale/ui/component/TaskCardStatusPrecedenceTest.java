package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.ui.component.factory.TaskCardFactory;

final class TaskCardStatusPrecedenceTest {

    @Test
    void completedTaskWithOpenHydratedStatusDisplaysCompleted() {
        TaskCardFactory.TaskStatusPresentation status = TaskCardFactory.resolveTaskStatusPresentation(true, "Open", "#111111");

        assertEquals("Completed", status.name());
        assertEquals(TaskCardFactory.COMPLETED_STATUS_FALLBACK_COLOR, status.colorHex());
    }

    @Test
    void completedTaskWithWaitingHydratedStatusDisplaysCompleted() {
        TaskCardFactory.TaskStatusPresentation status = TaskCardFactory.resolveTaskStatusPresentation(true, "Waiting", "#222222");

        assertEquals("Completed", status.name());
        assertEquals(TaskCardFactory.COMPLETED_STATUS_FALLBACK_COLOR, status.colorHex());
    }

    @Test
    void nonCompletedTaskKeepsHydratedStatus() {
        TaskCardFactory.TaskStatusPresentation status = TaskCardFactory.resolveTaskStatusPresentation(false, "In Progress", "#333333");

        assertEquals("In Progress", status.name());
        assertEquals("#333333", status.colorHex());
    }

    @Test
    void configuredCompletedStatusColorIsUsedWhenHydratedAsCompleted() {
        TaskCardFactory.TaskStatusPresentation status = TaskCardFactory.resolveTaskStatusPresentation(true, "Completed", "#16A34A");

        assertEquals("Completed", status.name());
        assertEquals("#16A34A", status.colorHex());
    }

    @Test
    void missingCompletedStatusColorUsesSharedFallback() {
        TaskCardFactory.TaskStatusPresentation status = TaskCardFactory.resolveTaskStatusPresentation(true, "Completed", " ");

        assertEquals("Completed", status.name());
        assertEquals(TaskCardFactory.COMPLETED_STATUS_FALLBACK_COLOR, status.colorHex());
    }

    @Test
    void allTaskCardVariantsUseFactoryResolverBeforeRendering() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/TaskCardFactory.java"));
        assertTrue(source.contains("TaskStatusPresentation status = resolveTaskStatusPresentation("));
        assertTrue(source.contains("card.setTaskStatus(status.name(), status.colorHex())"));
        assertTrue(source.contains("case FULL -> card.applyFull()"));
        assertTrue(source.contains("case MY_TASKS -> card.applyMyTasks()"));
        assertTrue(source.contains("case COMPACT -> card.applyCompact()"));
        assertTrue(source.contains("case COMPACT_FLUID -> card.applyCompactFluid()"));
        assertTrue(source.contains("case MINI -> card.applyMini()"));
    }

    @Test
    void historicalInconsistentRowsResolveVisuallyFromCompletedAt() {
        TaskCardFactory.TaskCardModel staleOpenCompletedTask = new TaskCardFactory.TaskCardModel(
                1L, null, null, null, null, null, null, null, null,
                "Done task", null, null, "Open", "#64748B", null,
                null, LocalDateTime.now(), List.of());

        TaskCardFactory.TaskStatusPresentation status = TaskCardFactory.resolveTaskStatusPresentation(
                staleOpenCompletedTask.completedAt() != null,
                staleOpenCompletedTask.taskStatusName(),
                staleOpenCompletedTask.taskStatusColorHex());

        assertEquals("Completed", status.name());
    }
}
