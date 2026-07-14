package com.shale.ui.component.factory;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;


class TaskCardFactoryDescriptionVisibilityTest {
    @Test
    void overviewCompactAndMyTasksResolveSameDescriptionWhenPhiDescriptionIsAllowed() {
        TaskCardFactory.TaskCardModel model = taskModelWithDescription(42L,
                "line one\nline two\nline three\nline four");

        String overviewCompactDescription = TaskCardFactory.descriptionForCard(model, true);
        String myTasksDescription = TaskCardFactory.descriptionForCard(model, true);

        assertNotNull(overviewCompactDescription);
        assertFalse(overviewCompactDescription.isBlank());
        assertEquals(overviewCompactDescription, myTasksDescription);
        assertTrue(overviewCompactDescription.contains("line two"));
    }

    @Test
    void phiDescriptionGateStillSuppressesDescriptionWhenNotExplicitlyAllowed() {
        TaskCardFactory.TaskCardModel model = taskModelWithDescription(43L, "sensitive task description");

        assertNull(TaskCardFactory.descriptionForCard(model, false));
    }

    private static TaskCardFactory.TaskCardModel taskModelWithDescription(long taskId, String description) {
        return new TaskCardFactory.TaskCardModel(
                taskId,
                100L,
                "Example Case",
                "Open",
                "#2563eb",
                "#0ea5e9",
                "Attorney User",
                "#64748b",
                false,
                "Task with hover details",
                description,
                "Creator User",
                "In Progress",
                "#22c55e",
                "#f97316",
                LocalDateTime.now().plusDays(2),
                null,
                List.of(new TaskCardFactory.AssignedUserModel(7, "Assigned User", "#94a3b8")));
    }
}
