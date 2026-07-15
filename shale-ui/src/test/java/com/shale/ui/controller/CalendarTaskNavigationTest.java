package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarTaskNavigationTest {
    @Test
    void calendarTaskNavigationUsesSceneManagerTaskDetailsAndRefreshCallback() throws Exception {
        String sceneManager = Files.readString(Path.of("src/main/java/com/shale/ui/navigation/SceneManager.java"));
        String calendarController = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));

        assertTrue(sceneManager.contains("taskId -> openTaskProfile(taskId, c::refreshCurrentRange)"),
                "Calendar projected task clicks should route through SceneManager's canonical task details opener with a Calendar refresh callback.");
        assertTrue(sceneManager.contains("public void openTaskProfile(Long taskId, Runnable onTaskChanged)"),
                "SceneManager should expose the existing task details opener with an optional mutation callback instead of adding a Calendar task dialog.");
        assertTrue(sceneManager.contains("TaskDetailDialog.showAndWait"),
                "SceneManager task navigation should continue to use the canonical TaskDetailDialog flow.");
        assertTrue(sceneManager.contains("TaskDetailDto initialDetail = caseTaskService.loadTaskDetail(taskId, shaleClientId)"),
                "The app-level task opener should preload the same tenant-safe TaskDetailDto used by My Shale before constructing the dialog model.");
        assertTrue(sceneManager.contains("initialDetail == null ? 0L : initialDetail.caseId()"),
                "The dialog's initial model should include hydrated case id data so the related Case section is visible immediately.");
        assertTrue(sceneManager.contains("initialDetail == null ? \"\" : initialDetail.caseName()"),
                "The dialog's initial model should include hydrated case name data so Calendar and My Shale display the same Case section.");
        assertTrue(sceneManager.contains("runTaskChangedCallback(onTaskChanged)"),
                "Successful task saves/deletes/assignment edits should trigger the supplied refresh callback.");
        assertTrue(calendarController.contains("public void refreshCurrentRange()"),
                "Calendar should expose a narrow current-range refresh callback that preserves date, view, filters, and layer state.");
    }
}
