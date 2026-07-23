package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class TaskDetailUserSelectorMigrationTest {

    @Test
    void taskDetailAddAssigneeUsesSharedUserSelectorThroughPickerAndKeepsServiceBoundary() throws Exception {
        String taskDetail = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/TaskDetailDialog.java"));
        String picker = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/AssignedUserPickerDialog.java"));

        assertTrue(taskDetail.contains("loadAssignableUsersForTask.apply(model.taskId())"),
                "Task Detail should continue loading candidates through its supplied service boundary.");
        assertTrue(taskDetail.contains("assignmentEditor.addAndReload(user.id())"),
                "Selecting an eligible user should continue invoking the existing assignment workflow by stable ID.");
        assertTrue(taskDetail.contains("Failed to add assigned user."),
                "Task Detail should keep its established assignment error path.");
        assertTrue(picker.contains("new UserSelector<>("),
                "The Add Assignee picker should be backed by the shared UserSelector component.");
        assertFalse(picker.contains("renderUsers("),
                "The old picker-local user rendering method should be removed.");
        assertFalse(picker.contains("matchesSearch("),
                "Search behavior should live in UserSelector rather than the dialog.");
    }
}
