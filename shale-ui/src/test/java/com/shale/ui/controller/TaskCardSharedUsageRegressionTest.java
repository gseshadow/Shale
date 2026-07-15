package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TaskCardSharedUsageRegressionTest {
    @Test
    void knownTaskScreensUseSharedTaskCardFactory() throws Exception {
        for (String file : java.util.List.of(
                "src/main/java/com/shale/ui/controller/MyShaleController.java",
                "src/main/java/com/shale/ui/controller/CaseController.java",
                "src/main/java/com/shale/ui/controller/UserController.java",
                "src/main/java/com/shale/ui/controller/SearchController.java",
                "src/main/java/com/shale/ui/controller/CalendarController.java")) {
            String source = Files.readString(Path.of(file));
            assertTrue(source.contains("TaskCardFactory"), file + " should route task cards through the shared factory.");
            assertFalse(source.contains("new TaskCard()"), file + " must not construct standalone task cards.");
            assertFalse(source.contains("setBorderByDueState"), file + " must not duplicate due-date accent rules.");
        }
    }

    @Test
    void overviewAndMyTasksMapTaskDescriptionIntoSharedTaskCardModel() throws Exception {
        String myShale = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));

        assertTrue(myShale.contains("task.description()"),
                "My Shale task-card paths should pass the canonical DTO description into TaskCardModel.");
        assertTrue(myShale.contains("taskCardFactory.create(model, TaskCardFactory.Variant.COMPACT, true)"),
                "Overview compact task cards should still use the shared factory.");
        assertTrue(myShale.contains("taskCardFactory.create(model, TaskCardFactory.Variant.MY_TASKS, true)"),
                "My Tasks cards should still use the shared factory and the same model description.");
    }

    @Test
    void caseTasksAllowPhiDescriptionInAuthenticatedWorkSurface() throws Exception {
        String caseController = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));

        assertTrue(caseController.contains("caseTaskService.loadTasksForCase("),
                "Case > Tasks should load task DTOs through the case task service.");
        assertTrue(caseController.contains("task.description()"),
                "Case > Tasks should pass the canonical DTO description into TaskCardModel.");
        assertTrue(caseController.contains("factory.create(model, TaskCardFactory.Variant.COMPACT, true)"),
                "Case > Tasks is an authenticated work surface and should explicitly allow task description previews.");
    }

    @Test
    void calendarMiniTaskCardsHydrateDescriptionWithoutHoverQueries() throws Exception {
        String calendarDao = Files.readString(Path.of("../shale-data/src/main/java/com/shale/data/dao/CalendarFeedDao.java"));
        String calendarController = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
        String taskCard = Files.readString(Path.of("src/main/java/com/shale/ui/component/TaskCard.java"));

        assertTrue(calendarDao.contains("t.Description"));
        assertTrue(calendarDao.contains("rs.getString(\"Description\")"));
        assertTrue(calendarController.contains("row.title(), row.description(), row.createdByDisplayName()"));
        assertFalse(taskCard.contains("TaskDao"));
        assertFalse(taskCard.contains("Service"));
    }
}
