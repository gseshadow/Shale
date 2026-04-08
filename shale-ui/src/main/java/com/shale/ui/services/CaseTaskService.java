package com.shale.ui.services;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.data.dao.TaskDao;
import com.shale.data.dao.UserDao;
import com.shale.data.dao.NotificationDao;

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
    private final UiRuntimeBridge runtimeBridge;
    private final NotificationDao notificationDao;

    public CaseTaskService(TaskDao taskDao, UserDao userDao, UiRuntimeBridge runtimeBridge, NotificationDao notificationDao) {
        this.taskDao = Objects.requireNonNull(taskDao, "taskDao");
        this.userDao = Objects.requireNonNull(userDao, "userDao");
        this.runtimeBridge = Objects.requireNonNull(runtimeBridge, "runtimeBridge");
        this.notificationDao = Objects.requireNonNull(notificationDao, "notificationDao");
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

    public List<AssignedTaskUserOption> loadAssignedUsersForTask(long taskId, int shaleClientId) {
        return taskDao.listAssignedUsersForTask(taskId, shaleClientId).stream()
                .map(row -> new AssignedTaskUserOption(row.userId(), row.displayName(), row.color()))
                .toList();
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
            publishTaskAssignmentEvent(
                    taskId,
                    request.shaleClientId(),
                    request.createdByUserId(),
                    request.title(),
                    request.caseId(),
                    null,
                    assigneeUserId,
                    null);
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
    }

    public List<AssignableUserOption> loadAssignableUsersForTask(long taskId, int shaleClientId) {
        return taskDao.listAssignableUsersForTask(taskId, shaleClientId).stream()
                .map(row -> new AssignableUserOption(row.id(), row.displayName(), row.color()))
                .toList();
    }

    public void addTaskAssignment(long taskId, int shaleClientId, int userId, int assignedByUserId) {
        taskDao.addTaskAssignment(taskId, shaleClientId, userId, assignedByUserId);
    }
    public void removeTaskAssignment(long taskId, int shaleClientId, int userId) {
        taskDao.removeTaskAssignment(taskId, shaleClientId, userId);
    }

    public List<AssignableUserOption> loadAssignableUsers(int shaleClientId) {
        return userDao.listUsersForTenant(shaleClientId).stream()
                .map(row -> new AssignableUserOption(row.id(), row.displayName(), row.color()))
                .toList();
    }

    private void publishTaskAssignmentEvent(
            long taskId,
            int shaleClientId,
            int updatedByUserId,
            String title,
            Long caseId,
            String caseName,
            Integer assigneeUserId,
            Integer previousAssigneeUserId) {
        if (assigneeUserId == null || assigneeUserId <= 0) {
            return;
        }
        if (updatedByUserId > 0 && updatedByUserId == assigneeUserId) {
            return;
        }
        String eventKey = "task-assigned:" + taskId + ":" + (previousAssigneeUserId == null ? 0 : previousAssigneeUserId)
                + ":" + assigneeUserId + ":" + updatedByUserId;
        String message = buildTaskAssignedMessage(title, caseId, caseName, taskId);
        Long durableId = notificationDao.createTaskAssignedNotification(
                shaleClientId,
                assigneeUserId,
                "Task assigned to you",
                message,
                taskId,
                updatedByUserId,
                eventKey);

        StringBuilder patch = new StringBuilder("{");
        patch.append("\"assigneeUserId\":").append(assigneeUserId);
        if (previousAssigneeUserId != null) {
            patch.append(",\"previousAssigneeUserId\":").append(previousAssigneeUserId);
        }
        patch.append(",\"eventKey\":\"").append(escapeJson(eventKey)).append('"');
        if (durableId != null) {
            patch.append(",\"durableNotificationId\":").append(durableId);
        }
        if (title != null && !title.isBlank()) {
            patch.append(",\"title\":\"").append(escapeJson(title)).append('"');
        }
        if (caseId != null && caseId > 0) {
            patch.append(",\"caseId\":").append(caseId);
        }
        if (caseName != null && !caseName.isBlank()) {
            patch.append(",\"caseName\":\"").append(escapeJson(caseName)).append('"');
        }
        patch.append(",\"updatedAtUtc\":\"")
                .append(LocalDateTime.now().atOffset(ZoneOffset.UTC))
                .append('"');
        patch.append('}');
        runtimeBridge.publishEntityUpdated("Task", taskId, shaleClientId, updatedByUserId, patch.toString());
    }

    private static String buildTaskAssignedMessage(String title, Long caseId, String caseName, long taskId) {
        String trimmedTitle = title == null ? "" : title.trim();
        if (trimmedTitle.isBlank()) {
            trimmedTitle = "Task #" + taskId;
        }
        if (caseName != null && !caseName.isBlank()) {
            return "Task: " + trimmedTitle + " • Case: " + caseName;
        }
        if (caseId != null && caseId > 0) {
            return "Task: " + trimmedTitle + " • Case #" + caseId;
        }
        return "Task: " + trimmedTitle;
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
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

    public record AssignedTaskUserOption(
            int userId,
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
            boolean completed,
            int changedByUserId) {
    }
}
