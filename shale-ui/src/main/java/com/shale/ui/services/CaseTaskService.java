package com.shale.ui.services;

import java.util.List;
import java.util.Objects;

import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.data.dao.TaskDao;
import com.shale.data.dao.UserDao;

/**
 * Thin case-task service facade for UI.
 */
public final class CaseTaskService {

    private final TaskDao taskDao;
    private final UserDao userDao;

    public CaseTaskService(TaskDao taskDao, UserDao userDao) {
        this.taskDao = Objects.requireNonNull(taskDao, "taskDao");
        this.userDao = Objects.requireNonNull(userDao, "userDao");
    }

    public List<CaseTaskListItemDto> loadTasksForCase(long caseId, int shaleClientId) {
        return taskDao.listActiveTasksForCase(caseId, shaleClientId);
    }

    public long createTask(CreateTaskRequest request) {
        Objects.requireNonNull(request, "request");
        long taskId = taskDao.createTask(
                request.shaleClientId(),
                request.caseId(),
                request.title(),
                request.description(),
                request.dueAt(),
                request.priorityId(),
                request.createdByUserId());
        Integer assigneeUserId = request.assigneeUserId();
        if (assigneeUserId != null && assigneeUserId > 0) {
            taskDao.assignPrimaryUserToTask(
                    taskId,
                    request.shaleClientId(),
                    assigneeUserId,
                    request.createdByUserId());
        }
        return taskId;
    }

    public List<TaskPriorityOptionDto> loadActivePriorities(int shaleClientId) {
        return taskDao.listActivePriorities(shaleClientId);
    }

    public void completeTask(long taskId, int shaleClientId) {
        taskDao.markTaskCompleted(taskId, shaleClientId);
    }

    public void uncompleteTask(long taskId, int shaleClientId) {
        taskDao.clearTaskCompleted(taskId, shaleClientId);
    }

    public void deleteTask(long taskId, int shaleClientId) {
        taskDao.softDeleteTask(taskId, shaleClientId);
    }

    public void assignUserToTask(long taskId, int shaleClientId, int userId, int assignedByUserId) {
        taskDao.assignPrimaryUserToTask(taskId, shaleClientId, userId, assignedByUserId);
    }

    public void clearTaskAssignee(long taskId, int shaleClientId) {
        taskDao.clearPrimaryUserAssignment(taskId, shaleClientId);
    }

    public List<AssignableUserOption> loadAssignableUsers(int shaleClientId) {
        return userDao.listUsersForTenant(shaleClientId).stream()
                .map(row -> new AssignableUserOption(row.id(), row.displayName(), row.color()))
                .toList();
    }

    public record CreateTaskRequest(
            int shaleClientId,
            long caseId,
            String title,
            String description,
            java.time.LocalDateTime dueAt,
            Integer priorityId,
            Integer assigneeUserId,
            int createdByUserId) {
    }

    public record AssignableUserOption(
            int id,
            String displayName,
            String color) {
    }
}
