package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class MyShaleTaskRefreshRegressionTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java");
    private static final Path FXML = Path.of("src/main/resources/fxml/my-shale.fxml");

    @Test
    void taskEditSaveUsesNonDestructiveCardCollectionRefreshInsteadOfFullMyTasksReload() throws Exception {
        String source = Files.readString(CONTROLLER);
        String saveMethod = source.substring(source.indexOf("private void saveTaskFromDetail("), source.indexOf("private void deleteTaskFromDetail("));
        assertTrue(saveMethod.contains("refreshEditedTaskCollectionAfterMutation(taskId, shaleClientId, currentUserId)"));
        assertFalse(saveMethod.contains("refreshMyTasks(true)"));

        String targetedRefresh = source.substring(source.indexOf("private void refreshEditedTaskCollectionAfterMutation("), source.indexOf("private void showTaskActionError("));
        assertFalse(targetedRefresh.contains("loadingOverview = true"));
        assertFalse(targetedRefresh.contains("loadingMyTasks = true"));
        assertTrue(targetedRefresh.contains("renderActiveTaskViews()"));
        assertTrue(targetedRefresh.contains("Task was saved, but the card could not be refreshed."));
    }

    @Test
    void myTasksStatusFilterOffersExpectedOptions() throws Exception {
        String source = Files.readString(CONTROLLER);
        String fxml = Files.readString(FXML);
        assertTrue(fxml.contains("fx:id=\"myTasksStatusFilterChoice\""));
        assertTrue(source.contains("ALL_ACTIVE_TASK_STATUSES_OPTION"));
        assertTrue(source.contains("COMPLETED_TASK_STATUS_OPTION"));
        assertTrue(source.contains("ALL_TASK_STATUSES_OPTION"));
        assertTrue(source.contains("\"open\".equals(key) || \"waiting\".equals(key)"));
    }
}
