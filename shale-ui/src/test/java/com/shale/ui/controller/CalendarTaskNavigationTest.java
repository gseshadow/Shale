package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarTaskNavigationTest {
    @Test
    void calendarTaskNavigationUsesExistingCaseTasksSurfaceInsteadOfTaskDetailDialog() throws Exception {
        String sceneManager = Files.readString(Path.of("src/main/java/com/shale/ui/navigation/SceneManager.java"));
        String calendarSetup = sceneManager.substring(sceneManager.indexOf("public Parent createCalendarView()"), sceneManager.indexOf("public Parent createSearchView"));
        String calendarTaskNavigation = sceneManager.substring(sceneManager.indexOf("private void openCalendarTaskLocation"), sceneManager.indexOf("public void openTaskProfile"));

        assertTrue(calendarSetup.contains("this::openCalendarTaskLocation"),
                "Calendar projected task clicks should use the Calendar-specific navigation bridge to the existing task surface.");
        assertFalse(calendarSetup.contains("this::openTaskProfile"),
                "Calendar setup must not route projected task clicks through SceneManager.openTaskProfile.");
        assertTrue(calendarTaskNavigation.contains("caseTaskService.loadTaskDetail(taskId, shaleClientId)"),
                "Calendar task navigation should pass the real task id through tenant-safe task loading to locate the existing task surface.");
        assertTrue(calendarTaskNavigation.contains("openCaseProfile((int) caseId, \"TASKS\")"),
                "Case-linked Calendar tasks should navigate to the existing Case View Tasks tab/component.");
        assertTrue(calendarTaskNavigation.contains("openMyShaleView()"),
                "Unlinked Calendar tasks should fall back to the existing My Shale task surface instead of a task dialog.");
        assertFalse(calendarTaskNavigation.contains("TaskDetailDialog.showAndWait"),
                "Calendar task navigation must not open TaskDetailDialog directly or through openTaskProfile.");
    }
}
