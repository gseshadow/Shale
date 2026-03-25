package com.shale.ui.services;

import java.util.List;
import java.util.Objects;

import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.data.dao.TaskDao;

/**
 * Thin case-task service facade for UI.
 */
public final class CaseTaskService {

    private final TaskDao taskDao;

    public CaseTaskService(TaskDao taskDao) {
        this.taskDao = Objects.requireNonNull(taskDao, "taskDao");
    }

    public List<CaseTaskListItemDto> loadTasksForCase(long caseId, int shaleClientId) {
        return taskDao.listActiveTasksForCase(caseId, shaleClientId);
    }

    public long createTask(CreateTaskRequest request) {
        Objects.requireNonNull(request, "request");
        return taskDao.createTask(
                request.shaleClientId(),
                request.caseId(),
                request.title(),
                request.description(),
                request.dueAt(),
                request.createdByUserId());
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

    public record CreateTaskRequest(
            int shaleClientId,
            long caseId,
            String title,
            String description,
            java.time.LocalDateTime dueAt,
            int createdByUserId) {
    }
}
