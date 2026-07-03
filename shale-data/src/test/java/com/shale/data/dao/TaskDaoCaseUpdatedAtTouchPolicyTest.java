package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class TaskDaoCaseUpdatedAtTouchPolicyTest {

    private static String taskDaoSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/TaskDao.java"));
    }

    private static String method(String source, String start, String end) {
        return source.substring(source.indexOf(start), source.indexOf(end, source.indexOf(start)));
    }

    @Test
    void caseLinkedTaskCreationTouchesCaseUpdatedAt() throws Exception {
        String createTask = method(taskDaoSource(), "public long createTask(", "public List<TaskPriorityOptionDto>");

        assertTrue(createTask.contains("touchCaseUpdatedAt(con, caseId, shaleClientId)"),
                "Creating a case-linked task should touch Cases.UpdatedAt through the shared helper");
    }

    @Test
    void taskCompletionAndReopenTouchCaseUpdatedAtOnlyOnTransitions() throws Exception {
        String updateCompletion = method(taskDaoSource(), "private void updateTaskCompletion", "private static void touchTaskCaseUpdatedAt");

        assertTrue(updateCompletion.contains("touchTaskCaseUpdatedAt(con, taskId, shaleClientId)"),
                "Completion/reopen should touch Cases.UpdatedAt through the task-linked helper");
        assertTrue(updateCompletion.contains("AND CompletedAt IS %s NULL"),
                "Completion/reopen should update only when the completed state actually changes");
        assertTrue(updateCompletion.contains("completed ? \"\" : \"NOT\""),
                "Completion should require an incomplete task, and reopen should require a previously completed task");
    }

    @Test
    void routineTaskEditsDoNotTouchCaseUpdatedAt() throws Exception {
        String source = taskDaoSource();
        String updateTask = method(source, "public void updateTask(", "public void markTaskCompleted");
        String addAssignment = method(source, "public boolean addTaskAssignment", "public void removeTaskAssignment");
        String removeAssignment = method(source, "public void removeTaskAssignment", "public void replacePrimaryTaskAssignment");
        String replaceAssignment = method(source, "public void replacePrimaryTaskAssignment", "public List<TaskDueNotificationCandidate>");
        String softDeleteTask = method(source, "public void softDeleteTask", "public long addTaskTimelineEvent");
        String addTimeline = method(source, "public long addTaskTimelineEvent", "public List<TaskTimelineEventRow> listTaskTimelineEvents");
        String addTaskUpdate = method(source, "public long addTaskUpdate", "public boolean updateTaskUpdate");
        String assignPrimary = method(source, "public void assignPrimaryUserToTask", "public void clearPrimaryUserAssignment");

        assertFalse(updateTask.contains("touchTaskCaseUpdatedAt(con, taskId, shaleClientId);")
                        && !updateTask.contains("completionChanged"),
                "Routine title/description/due date/status/priority edits should not unconditionally touch Cases.UpdatedAt");
        assertFalse(addAssignment.contains("UPDATE c"), "Adding assignees should not touch Cases.UpdatedAt");
        assertFalse(removeAssignment.contains("UPDATE c"), "Removing assignees should not touch Cases.UpdatedAt");
        assertFalse(replaceAssignment.contains("UPDATE c"), "Replacing primary assignees should not touch Cases.UpdatedAt");
        assertFalse(assignPrimary.contains("UPDATE c"), "Assigning a primary user should not touch Cases.UpdatedAt");
        assertFalse(softDeleteTask.contains("touchTaskCaseUpdatedAt"), "Routine task deletion metadata should not touch Cases.UpdatedAt");
        assertFalse(addTimeline.contains("touchCaseUpdatedAt"), "Task history/timeline additions should not touch Cases.UpdatedAt");
        assertFalse(addTaskUpdate.contains("touchCaseUpdatedAt"), "Task note additions should not touch Cases.UpdatedAt");
    }
}
