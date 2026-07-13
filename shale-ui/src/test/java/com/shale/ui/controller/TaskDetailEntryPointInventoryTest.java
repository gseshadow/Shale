package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDetailEntryPointInventoryTest {
    @Test
    void desktopTaskClickEntryPointsUseTaskDetailDialogAsTheOnlySpecificTaskDetailWindow() throws Exception {
        String myShale = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));
        String caseView = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        String userView = Files.readString(Path.of("src/main/java/com/shale/ui/controller/UserController.java"));
        String sceneManager = Files.readString(Path.of("src/main/java/com/shale/ui/navigation/SceneManager.java"));
        String notificationDialog = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/NotificationCenterDialog.java"));
        String taskCardFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/TaskCardFactory.java"));

        assertTrue(myShale.contains("private void openTask(Long taskId)") && myShale.contains("TaskDetailDialog.showAndWait"),
                "My Shale/My Tasks task cards open the TaskDetailDialog-specific task detail window.");
        assertTrue(caseView.contains("private void openTask(Long taskId)") && caseView.contains("TaskDetailDialog.showAndWait"),
                "Case View Tasks task cards open the TaskDetailDialog-specific task detail window.");
        assertTrue(userView.contains("private void openTask(Long taskId)") && userView.contains("TaskDetailDialog.showAndWait"),
                "User View assigned task cards open the TaskDetailDialog-specific task detail window.");
        assertTrue(notificationDialog.contains("onOpenTask.accept(taskId)"),
                "Notification task links route through the app-level task opener for the specific TaskId.");
        assertTrue(taskCardFactory.contains("card.setOnOpen(onOpenTask)"),
                "TaskCardFactory delegates task-card clicks to the shared task opener callback with the card TaskId.");
        assertTrue(sceneManager.contains("taskId -> openTaskProfile(taskId, c::refreshCurrentRange)"),
                "Calendar projected tasks use the same app-level specific-task opener and add only a Calendar refresh callback.");
    }
}
