package com.shale.ui.services;

import java.util.List;
import java.util.Objects;

import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.data.dao.TaskDao;
import com.shale.data.dao.UserDao;

/**
 * Thin case-task service facade for UI.
 */
public final class CaseTaskService {
    public enum MyTasksSortOption {
        DEFAULT,
        DUE_DATE_ASC,
        DUE_DATE_DESC
    }

    public enum CaseTasksSortOption {
        DEFAULT,
        DUE_DATE_ASC,
        DUE_DATE_DESC,
        PRIORITY_ASC,
        PRIORITY_DESC
    }

    private final TaskDao taskDao;
    private final UserDao userDao;

    public CaseTaskService(TaskDao taskDao, UserDao userDao) {
        this.taskDao = Objects.requireNonNull(taskDao, "taskDao");
        this.userDao = Objects.requireNonNull(userDao, "userDao");
    }

    public List<CaseTaskListItemDto> loadTasksForCase(long caseId, int shaleClientId) {
        return loadTasksForCase(caseId, shaleClientId, CaseTasksSortOption.DEFAULT);
    }

    public List<CaseTaskListItemDto> loadTasksForCase(long caseId, int shaleClientId, CaseTasksSortOption sortOption) {
        TaskDao.CaseTaskSort daoSort = switch (sortOption == null ? CaseTasksSortOption.DEFAULT : sortOption) {
            case DUE_DATE_ASC -> TaskDao.CaseTaskSort.DUE_DATE_ASC;
            case DUE_DATE_DESC -> TaskDao.CaseTaskSort.DUE_DATE_DESC;
            case PRIORITY_ASC -> TaskDao.CaseTaskSort.PRIORITY_ASC;
            case PRIORITY_DESC -> TaskDao.CaseTaskSort.PRIORITY_DESC;
            case DEFAULT -> TaskDao.CaseTaskSort.DEFAULT;
        };
        return taskDao.listActiveTasksForCase(caseId, shaleClientId, daoSort);
    }

    public List<CaseTaskListItemDto> loadMyTasks(int shaleClientId, int currentUserId, MyTasksSortOption sortOption) {
        TaskDao.MyTaskSort daoSort = switch (sortOption == null ? MyTasksSortOption.DEFAULT : sortOption) {
            case DUE_DATE_ASC -> TaskDao.MyTaskSort.DUE_DATE_ASC;
            case DUE_DATE_DESC -> TaskDao.MyTaskSort.DUE_DATE_DESC;
            case DEFAULT -> TaskDao.MyTaskSort.DEFAULT;
        };
        return taskDao.listActiveTasksAssignedToUser(shaleClientId, currentUserId, daoSort);
    }

    public TaskDetailDto loadTaskDetail(long taskId, int shaleClientId) {
        return taskDao.findTaskDetail(taskId, shaleClientId);
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

    public void updateTask(UpdateTaskRequest request) {
        Objects.requireNonNull(request, "request");
        taskDao.updateTask(
                request.taskId(),
                request.shaleClientId(),
                request.title(),
                request.description(),
                request.dueAt(),
                request.priorityId(),
                request.completed());
        if (request.assigneeUserId() != null && request.assigneeUserId() > 0) {
            taskDao.assignPrimaryUserToTask(
                    request.taskId(),
                    request.shaleClientId(),
                    request.assigneeUserId(),
                    request.changedByUserId());
        } else {
            taskDao.clearPrimaryUserAssignment(request.taskId(), request.shaleClientId());
        }
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

    public record UpdateTaskRequest(
            long taskId,
            int shaleClientId,
            String title,
            String description,
            java.time.LocalDateTime dueAt,
            Integer priorityId,
            Integer assigneeUserId,
            boolean completed,
            int changedByUserId) {
    }
}
