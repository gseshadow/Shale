package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class UserAssignedTasksRenderingTest {

    @Test
    void userAssignedTasksReuseMyShaleFullSizedTaskCardFactoryPath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/UserController.java"));

        assertTrue(source.contains("new TaskCardFactory(this::openTask, this::onToggleAssignedTaskComplete, onOpenCase, this::onOpenUserFromTask)"),
                "User assigned tasks should keep the shared task factory wired to task detail, completion, case, and user navigation handlers.");
        assertTrue(source.contains("taskCardFactory.create(model, TaskCardFactory.Variant.MY_TASKS, true)"),
                "User assigned tasks should use the same full-sized MY_TASKS variant used by the My Shale task grid.");
        assertFalse(source.contains("create(model, TaskCardFactory.Variant.COMPACT_FLUID"),
                "User assigned tasks should not use the old compact-fluid card path.");
    }

    @Test
    void userAssignedTasksPassActualTitleToSharedCardModel() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/UserController.java"));
        int methodStart = source.indexOf("private Node createAssignedTaskCard(AssignedUserTaskRow row)");
        int methodEnd = source.indexOf("private String normalizedAssignedTaskQuery()", methodStart);
        assertTrue(methodStart >= 0 && methodEnd > methodStart, "Expected createAssignedTaskCard method to exist.");
        String method = source.substring(methodStart, methodEnd);

        assertTrue(method.contains("row.title(),"),
                "The actual assigned task title should be passed into TaskCardModel as the card title.");
        assertFalse(method.contains("\"Task #\" + row.taskId()"),
                "Task number should not be used as a fallback heading when a valid title exists.");
    }

    @Test
    void filteringAndClickBehaviorRemainConnected() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/UserController.java"));

        assertTrue(source.contains("filteredAssignedTasks(query).stream()")
                        && source.contains(".map(this::createAssignedTaskCard)"),
                "Assigned task rendering should continue to flow through the existing filtering pipeline.");
        assertTrue(source.contains("private void openTask(long taskId)") || source.contains("private void openTask(Long taskId)"),
                "Assigned task cards should still be wired to the existing task-detail opener.");
    }

    @Test
    void toolbarSearchPromptAndLayoutAvoidTruncation() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/user.fxml"));

        assertTrue(fxml.contains("promptText=\"Search assigned tasks\""),
                "Search prompt should be the clear, non-truncated assigned-task prompt.");
        assertTrue(fxml.contains("<FlowPane hgap=\"8\" vgap=\"8\"")
                        && fxml.contains("fx:id=\"assignedTasksSearchField\"")
                        && fxml.contains("prefWidth=\"220\"")
                        && fxml.contains("minWidth=\"190\""),
                "Assigned task toolbar should wrap and keep the search field wide enough to show its prompt.");
    }
}
