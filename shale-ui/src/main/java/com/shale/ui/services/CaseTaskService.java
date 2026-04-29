package com.shale.ui.services;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.core.dto.TaskStatusOptionDto;
import com.shale.data.dao.TaskDao;
import com.shale.data.dao.UserDao;
import com.shale.data.dao.NotificationDao;
import com.shale.ui.util.PerfLog;

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
        long startNanos = PerfLog.start();
        PerfLog.log("DAO", "start", "method=listActiveTasksForCase page=case_view caseId=" + caseId + " organizationId=" + shaleClientId);
        List<CaseTaskListItemDto> rows = taskDao.listActiveTasksForCase(caseId, shaleClientId, daoSort);
        PerfLog.logDone("DAO", "method=listActiveTasksForCase page=case_view caseId=" + caseId + " organizationId=" + shaleClientId + " rows=" + (rows == null ? 0 : rows.size()), startNanos);
        return rows;
    }

    public List<CaseTaskListItemDto> loadMyTasks(int shaleClientId, int currentUserId, MyTasksSortOption sortOption) {
        return loadMyTasks(shaleClientId, currentUserId, sortOption, false);
    }

    public List<CaseTaskListItemDto> loadMyTasks(
            int shaleClientId,
            int currentUserId,
            MyTasksSortOption sortOption,
            boolean includeCompleted) {
        TaskDao.MyTaskSort daoSort = switch (sortOption == null ? MyTasksSortOption.DEFAULT : sortOption) {
            case DUE_DATE_ASC -> TaskDao.MyTaskSort.DUE_DATE_ASC;
            case DUE_DATE_DESC -> TaskDao.MyTaskSort.DUE_DATE_DESC;
            case DEFAULT -> TaskDao.MyTaskSort.DEFAULT;
        };
        long startNanos = PerfLog.start();
        PerfLog.log("DAO", "start", "method=listActiveTasksAssignedToUser page=my_shale userId=" + currentUserId + " organizationId=" + shaleClientId);
        List<CaseTaskListItemDto> rows = taskDao.listActiveTasksAssignedToUser(shaleClientId, currentUserId, daoSort, includeCompleted);
        PerfLog.logDone("DAO", "method=listActiveTasksAssignedToUser page=my_shale userId=" + currentUserId + " organizationId=" + shaleClientId + " rows=" + (rows == null ? 0 : rows.size()), startNanos);
        return rows;
    }

    public List<CaseTaskListItemDto> loadTasksCreatedByUser(
            int shaleClientId,
            int currentUserId,
            MyTasksSortOption sortOption,
            boolean includeCompleted) {
        TaskDao.MyTaskSort daoSort = switch (sortOption == null ? MyTasksSortOption.DEFAULT : sortOption) {
            case DUE_DATE_ASC -> TaskDao.MyTaskSort.DUE_DATE_ASC;
            case DUE_DATE_DESC -> TaskDao.MyTaskSort.DUE_DATE_DESC;
            case DEFAULT -> TaskDao.MyTaskSort.DEFAULT;
        };
        long startNanos = PerfLog.start();
        PerfLog.log("DAO", "start", "method=listTasksCreatedByUserForBoard page=my_shale userId=" + currentUserId + " organizationId=" + shaleClientId);
        List<CaseTaskListItemDto> rows = taskDao.listTasksCreatedByUserForBoard(shaleClientId, currentUserId, daoSort, includeCompleted);
        PerfLog.logDone("DAO", "method=listTasksCreatedByUserForBoard page=my_shale userId=" + currentUserId + " organizationId=" + shaleClientId + " rows=" + (rows == null ? 0 : rows.size()), startNanos);
        return rows;
    }

    public TaskDetailDto loadTaskDetail(long taskId, int shaleClientId) {
        return taskDao.findTaskDetail(taskId, shaleClientId);
    }

    public List<AssignedTaskUserOption> loadAssignedUsersForTask(long taskId, int shaleClientId) {
        return taskDao.listAssignedUsersForTask(taskId, shaleClientId).stream()
                .map(row -> new AssignedTaskUserOption(row.userId(), row.displayName(), row.color()))
                .toList();
    }
    public List<TaskAssignedUsersByTask> loadAssignedUsersForTasks(List<Long> taskIds, int shaleClientId) {
        long startNanos = PerfLog.start();
        PerfLog.log("DAO", "start", "method=listAssignedUsersForTasks organizationId=" + shaleClientId);
        List<TaskAssignedUsersByTask> rows = taskDao.listAssignedUsersForTasks(taskIds, shaleClientId).stream()
                .map(row -> new TaskAssignedUsersByTask(row.taskId(), row.userId(), row.displayName(), row.color()))
                .toList();
        PerfLog.logDone("DAO", "method=listAssignedUsersForTasks organizationId=" + shaleClientId + " rows=" + rows.size(), startNanos);
        return rows;
    }

    public List<TaskActivityItem> loadTaskActivity(long taskId, int shaleClientId) {
        if (taskId <= 0 || shaleClientId <= 0) {
            return List.of();
        }
        return taskDao.listTaskTimelineEvents(taskId).stream()
                .filter(row -> row.shaleClientId() == shaleClientId)
                .map(row -> new TaskActivityItem(
                        row.id(),
                        row.taskId(),
                        row.caseId(),
                        row.shaleClientId(),
                        row.taskTitle(),
                        row.eventType(),
                        row.actorUserId(),
                        normalizeActorDisplayName(row.actorDisplayName()),
                        row.title(),
                        row.body(),
                        row.occurredAt()))
                .toList();
    }

    public List<TaskActivityItem> loadCaseTaskActivity(int caseId, int shaleClientId) {
        if (caseId <= 0 || shaleClientId <= 0) {
            return List.of();
        }
        return taskDao.listCaseTaskTimelineEvents(caseId).stream()
                .filter(row -> row.shaleClientId() == shaleClientId)
                .map(row -> new TaskActivityItem(
                        row.id(),
                        row.taskId(),
                        row.caseId(),
                        row.shaleClientId(),
                        row.taskTitle(),
                        row.eventType(),
                        row.actorUserId(),
                        normalizeActorDisplayName(row.actorDisplayName()),
                        row.title(),
                        row.body(),
                        row.occurredAt()))
                .toList();
    }

    public List<TaskNoteItem> loadTaskNotes(long taskId, int shaleClientId) {
        if (taskId <= 0 || shaleClientId <= 0) {
            return List.of();
        }
        return taskDao.listTaskUpdates(taskId).stream()
                .filter(row -> row.shaleClientId() == shaleClientId)
                .map(row -> new TaskNoteItem(
                        row.id(),
                        row.taskId(),
                        row.caseId(),
                        row.shaleClientId(),
                        row.userId(),
                        row.userDisplayName(),
                        row.userColor(),
                        row.body(),
                        row.createdAt(),
                        row.updatedAt(),
                        row.isDeleted()))
                .toList();
    }

    public void addTaskNote(long taskId, int shaleClientId, int userId, String body) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be > 0");
        }
        if (shaleClientId <= 0) {
            throw new IllegalArgumentException("shaleClientId must be > 0");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be > 0");
        }
        TaskDetailDto detail = taskDao.findTaskDetail(taskId, shaleClientId);
        if (detail == null) {
            throw new IllegalArgumentException("Task not found for note insert");
        }
        long taskUpdateId = taskDao.addTaskUpdate(taskId, Math.toIntExact(detail.caseId()), shaleClientId, userId, body);
        publishTaskNoteAddedEvents(
                taskId,
                taskUpdateId,
                shaleClientId,
                userId,
                detail.title(),
                detail.caseId(),
                detail.caseName(),
                body);
    }

    public boolean updateTaskNote(long taskUpdateId, int shaleClientId, int userId, String body) {
        return taskDao.updateTaskUpdate(taskUpdateId, shaleClientId, userId, body);
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
        Integer actorUserId = request.createdByUserId() > 0 ? request.createdByUserId() : null;
        taskDao.addTaskTimelineEvent(
                taskId,
                Math.toIntExact(request.caseId()),
                request.shaleClientId(),
                TaskDao.TaskTimelineEventTypes.TASK_CREATED,
                actorUserId,
                "Task created",
                "Task created");
        List<Integer> assignedUserIds = request.assignedUserIds() == null ? List.of() : request.assignedUserIds();
        for (Integer assignedUserId : new LinkedHashSet<>(assignedUserIds)) {
            if (assignedUserId == null || assignedUserId <= 0) {
                continue;
            }
            boolean inserted = taskDao.addTaskAssignment(
                    taskId,
                    request.shaleClientId(),
                    assignedUserId,
                    request.createdByUserId());
            if (!inserted) {
                continue;
            }
            publishTaskAssignmentEvent(
                    taskId,
                    request.shaleClientId(),
                    request.createdByUserId(),
                    request.title(),
                    request.caseId(),
                    null,
                    assignedUserId,
                    null);
            taskDao.addTaskTimelineEvent(
                    taskId,
                    Math.toIntExact(request.caseId()),
                    request.shaleClientId(),
                    TaskDao.TaskTimelineEventTypes.TASK_ASSIGNMENT_ADDED,
                    actorUserId,
                    "Assignee added",
                    "Assigned to " + resolveUserDisplayName(taskId, request.shaleClientId(), assignedUserId, true));
        }
        return taskId;
    }

    public List<TaskPriorityOptionDto> loadActivePriorities(int shaleClientId) {
        return taskDao.listActivePriorities(shaleClientId);
    }

    public List<TaskStatusOptionDto> loadActiveTaskStatuses(int shaleClientId) {
        return taskDao.listActiveTaskStatuses(shaleClientId);
    }

    public void completeTask(long taskId, int shaleClientId) {
        completeTask(taskId, shaleClientId, null);
    }

    public void uncompleteTask(long taskId, int shaleClientId) {
        uncompleteTask(taskId, shaleClientId, null);
    }

    public void deleteTask(long taskId, int shaleClientId) {
        deleteTask(taskId, shaleClientId, null);
    }

    public void completeTask(long taskId, int shaleClientId, Integer actorUserId) {
        TaskDetailDto before = taskDao.findTaskDetail(taskId, shaleClientId);
        taskDao.markTaskCompleted(taskId, shaleClientId);
        TaskDetailDto after = taskDao.findTaskDetail(taskId, shaleClientId);
        if (before != null && before.completedAt() == null && after != null && after.completedAt() != null) {
            long timelineEventId = taskDao.addTaskTimelineEvent(
                    taskId,
                    Math.toIntExact(after.caseId()),
                    shaleClientId,
                    TaskDao.TaskTimelineEventTypes.TASK_COMPLETED,
                    normalizeActorUserId(actorUserId),
                    "Task marked complete",
                    "Task marked complete");
            publishTimelineEventNotifications(
                    TaskDao.TaskTimelineEventTypes.TASK_COMPLETED,
                    timelineEventId,
                    taskId,
                    shaleClientId,
                    normalizeActorUserId(actorUserId),
                    after.title(),
                    after.caseId(),
                    after.caseName(),
                    "Task marked complete");
        }
    }

    public void uncompleteTask(long taskId, int shaleClientId, Integer actorUserId) {
        TaskDetailDto before = taskDao.findTaskDetail(taskId, shaleClientId);
        taskDao.clearTaskCompleted(taskId, shaleClientId);
        TaskDetailDto after = taskDao.findTaskDetail(taskId, shaleClientId);
        if (before != null && before.completedAt() != null && after != null && after.completedAt() == null) {
            long timelineEventId = taskDao.addTaskTimelineEvent(
                    taskId,
                    Math.toIntExact(after.caseId()),
                    shaleClientId,
                    TaskDao.TaskTimelineEventTypes.TASK_REOPENED,
                    normalizeActorUserId(actorUserId),
                    "Task reopened",
                    "Task reopened");
            publishTimelineEventNotifications(
                    TaskDao.TaskTimelineEventTypes.TASK_REOPENED,
                    timelineEventId,
                    taskId,
                    shaleClientId,
                    normalizeActorUserId(actorUserId),
                    after.title(),
                    after.caseId(),
                    after.caseName(),
                    "Task reopened");
        }
    }

    public void deleteTask(long taskId, int shaleClientId, Integer actorUserId) {
        TaskDetailDto before = taskDao.findTaskDetail(taskId, shaleClientId);
        taskDao.softDeleteTask(taskId, shaleClientId);
        if (before != null) {
            taskDao.addTaskTimelineEvent(
                    taskId,
                    Math.toIntExact(before.caseId()),
                    shaleClientId,
                    TaskDao.TaskTimelineEventTypes.TASK_DELETED,
                    normalizeActorUserId(actorUserId),
                    "Task deleted",
                    "Task deleted");
        }
    }

    public void updateTask(UpdateTaskRequest request) {
        Objects.requireNonNull(request, "request");
        TaskDetailDto before = taskDao.findTaskDetail(request.taskId(), request.shaleClientId());
        taskDao.updateTask(
                request.taskId(),
                request.shaleClientId(),
                request.title(),
                request.description(),
                request.dueAt(),
                request.statusId(),
                request.priorityId(),
                request.completed(),
                request.changedByUserId());
        TaskDetailDto after = taskDao.findTaskDetail(request.taskId(), request.shaleClientId());
        if (before == null || after == null) {
            return;
        }
        Integer actorUserId = normalizeActorUserId(request.changedByUserId());
        int caseId = Math.toIntExact(after.caseId());
        if (!Objects.equals(normalizeTitle(before.title()), normalizeTitle(after.title()))) {
            long timelineEventId = taskDao.addTaskTimelineEvent(
                    request.taskId(),
                    caseId,
                    request.shaleClientId(),
                    TaskDao.TaskTimelineEventTypes.TASK_TITLE_CHANGED,
                    actorUserId,
                    "Title changed",
                    "Title changed from " + quoteForBody(before.title()) + " to " + quoteForBody(after.title()));
            publishTimelineEventNotifications(
                    TaskDao.TaskTimelineEventTypes.TASK_TITLE_CHANGED,
                    timelineEventId,
                    request.taskId(),
                    request.shaleClientId(),
                    actorUserId,
                    after.title(),
                    after.caseId(),
                    after.caseName(),
                    "Title changed");
        }
        if (!Objects.equals(normalizeText(before.description()), normalizeText(after.description()))) {
            long timelineEventId = taskDao.addTaskTimelineEvent(
                    request.taskId(),
                    caseId,
                    request.shaleClientId(),
                    TaskDao.TaskTimelineEventTypes.TASK_DESCRIPTION_CHANGED,
                    actorUserId,
                    "Description changed",
                    "Description changed");
            publishTimelineEventNotifications(
                    TaskDao.TaskTimelineEventTypes.TASK_DESCRIPTION_CHANGED,
                    timelineEventId,
                    request.taskId(),
                    request.shaleClientId(),
                    actorUserId,
                    after.title(),
                    after.caseId(),
                    after.caseName(),
                    "Description changed");
        }
        if (!Objects.equals(before.dueAt(), after.dueAt())) {
            long timelineEventId = taskDao.addTaskTimelineEvent(
                    request.taskId(),
                    caseId,
                    request.shaleClientId(),
                    TaskDao.TaskTimelineEventTypes.TASK_DUE_DATE_CHANGED,
                    actorUserId,
                    "Due date changed",
                    "Due date changed from " + formatDueDate(before.dueAt()) + " to " + formatDueDate(after.dueAt()));
            publishTimelineEventNotifications(
                    TaskDao.TaskTimelineEventTypes.TASK_DUE_DATE_CHANGED,
                    timelineEventId,
                    request.taskId(),
                    request.shaleClientId(),
                    actorUserId,
                    after.title(),
                    after.caseId(),
                    after.caseName(),
                    "Due date changed");
        }
        if (!Objects.equals(before.priorityId(), after.priorityId())) {
            Map<Integer, String> priorityLabels = loadPriorityLabels(request.shaleClientId());
            long timelineEventId = taskDao.addTaskTimelineEvent(
                    request.taskId(),
                    caseId,
                    request.shaleClientId(),
                    TaskDao.TaskTimelineEventTypes.TASK_PRIORITY_CHANGED,
                    actorUserId,
                    "Priority changed",
                    "Priority changed from " + formatPriority(before.priorityId(), priorityLabels)
                            + " to " + formatPriority(after.priorityId(), priorityLabels));
            publishTimelineEventNotifications(
                    TaskDao.TaskTimelineEventTypes.TASK_PRIORITY_CHANGED,
                    timelineEventId,
                    request.taskId(),
                    request.shaleClientId(),
                    actorUserId,
                    after.title(),
                    after.caseId(),
                    after.caseName(),
                    "Priority changed");
        }
        if (!Objects.equals(before.statusId(), after.statusId())) {
            Map<Integer, String> statusLabels = loadTaskStatusLabels(request.shaleClientId());
            taskDao.addTaskTimelineEvent(
                    request.taskId(),
                    caseId,
                    request.shaleClientId(),
                    TaskDao.TaskTimelineEventTypes.TASK_STATUS_CHANGED,
                    actorUserId,
                    "Status changed",
                    "Changed from " + formatStatus(before.statusId(), statusLabels)
                            + " to " + formatStatus(after.statusId(), statusLabels));
        }
        if (before.completedAt() == null && after.completedAt() != null) {
            long timelineEventId = taskDao.addTaskTimelineEvent(
                    request.taskId(),
                    caseId,
                    request.shaleClientId(),
                    TaskDao.TaskTimelineEventTypes.TASK_COMPLETED,
                    actorUserId,
                    "Task marked complete",
                    "Task marked complete");
            publishTimelineEventNotifications(
                    TaskDao.TaskTimelineEventTypes.TASK_COMPLETED,
                    timelineEventId,
                    request.taskId(),
                    request.shaleClientId(),
                    actorUserId,
                    after.title(),
                    after.caseId(),
                    after.caseName(),
                    "Task marked complete");
        } else if (before.completedAt() != null && after.completedAt() == null) {
            long timelineEventId = taskDao.addTaskTimelineEvent(
                    request.taskId(),
                    caseId,
                    request.shaleClientId(),
                    TaskDao.TaskTimelineEventTypes.TASK_REOPENED,
                    actorUserId,
                    "Task reopened",
                    "Task reopened");
            publishTimelineEventNotifications(
                    TaskDao.TaskTimelineEventTypes.TASK_REOPENED,
                    timelineEventId,
                    request.taskId(),
                    request.shaleClientId(),
                    actorUserId,
                    after.title(),
                    after.caseId(),
                    after.caseName(),
                    "Task reopened");
        }
    }

    public List<AssignableUserOption> loadAssignableUsersForTask(long taskId, int shaleClientId) {
        return taskDao.listAssignableUsersForTask(taskId, shaleClientId).stream()
                .map(row -> new AssignableUserOption(row.id(), row.displayName(), row.color()))
                .toList();
    }

    public void addTaskAssignment(long taskId, int shaleClientId, int userId, int assignedByUserId) {
        boolean inserted = taskDao.addTaskAssignment(taskId, shaleClientId, userId, assignedByUserId);
        if (!inserted) {
            return;
        }
        TaskDetailDto detail = taskDao.findTaskDetail(taskId, shaleClientId);
        publishTaskAssignmentEvent(
                taskId,
                shaleClientId,
                assignedByUserId,
                detail == null ? null : detail.title(),
                detail == null ? null : detail.caseId(),
                detail == null ? null : detail.caseName(),
                userId,
                null);
        if (detail != null) {
            taskDao.addTaskTimelineEvent(
                    taskId,
                    Math.toIntExact(detail.caseId()),
                    shaleClientId,
                    TaskDao.TaskTimelineEventTypes.TASK_ASSIGNMENT_ADDED,
                    normalizeActorUserId(assignedByUserId),
                    "Assignee added",
                    "Assigned to " + resolveUserDisplayName(taskId, shaleClientId, userId, true));
        }
    }
    public void removeTaskAssignment(long taskId, int shaleClientId, int userId) {
        removeTaskAssignment(taskId, shaleClientId, userId, null);
    }
    public void removeTaskAssignment(long taskId, int shaleClientId, int userId, Integer actorUserId) {
        String removedDisplayName = resolveUserDisplayName(taskId, shaleClientId, userId, false);
        taskDao.removeTaskAssignment(taskId, shaleClientId, userId);
        TaskDetailDto detail = taskDao.findTaskDetail(taskId, shaleClientId);
        if (detail != null && removedDisplayName != null) {
            long timelineEventId = taskDao.addTaskTimelineEvent(
                    taskId,
                    Math.toIntExact(detail.caseId()),
                    shaleClientId,
                    TaskDao.TaskTimelineEventTypes.TASK_ASSIGNMENT_REMOVED,
                    normalizeActorUserId(actorUserId),
                    "Assignee removed",
                    "Unassigned " + removedDisplayName);
            publishTimelineEventNotifications(
                    TaskDao.TaskTimelineEventTypes.TASK_ASSIGNMENT_REMOVED,
                    timelineEventId,
                    taskId,
                    shaleClientId,
                    normalizeActorUserId(actorUserId),
                    detail.title(),
                    detail.caseId(),
                    detail.caseName(),
                    "Assignee removed");
        }
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

    private void publishTaskNoteAddedEvents(
            long taskId,
            long taskUpdateId,
            int shaleClientId,
            int actorUserId,
            String taskTitle,
            Long caseId,
            String caseName,
            String noteBody) {
        LinkedHashSet<Integer> recipientUserIds = taskDao.listAssignedUsersForTask(taskId, shaleClientId).stream()
                .map(TaskDao.TaskAssignedUserRow::userId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        recipientUserIds.remove(actorUserId);
        if (recipientUserIds.isEmpty()) {
            return;
        }
        String message = buildTaskNoteAddedMessage(taskTitle, caseId, caseName, taskId, noteBody);
        for (Integer recipientUserId : recipientUserIds) {
            String eventKey = "task-note-added:" + taskId + ":" + taskUpdateId + ":" + recipientUserId;
            Long durableId = notificationDao.createTaskNoteAddedNotification(
                    shaleClientId,
                    recipientUserId,
                    "New note added to task",
                    message,
                    taskId,
                    actorUserId,
                    eventKey);
            StringBuilder patch = new StringBuilder("{");
            patch.append("\"notificationType\":\"TASK_NOTE_ADDED\"");
            patch.append(",\"recipientUserId\":").append(recipientUserId);
            patch.append(",\"eventKey\":\"").append(escapeJson(eventKey)).append('"');
            patch.append(",\"taskUpdateId\":").append(taskUpdateId);
            if (durableId != null) {
                patch.append(",\"durableNotificationId\":").append(durableId);
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
            runtimeBridge.publishEntityUpdated("Task", taskId, shaleClientId, actorUserId, patch.toString());
        }
    }

    private void publishTimelineEventNotifications(
            String eventType,
            long timelineEventId,
            long taskId,
            int shaleClientId,
            Integer actorUserId,
            String taskTitle,
            Long caseId,
            String caseName,
            String eventLabel) {
        List<Integer> recipients = taskDao.listAssignedUsersForTask(taskId, shaleClientId).stream()
                .map(TaskDao.TaskAssignedUserRow::userId)
                .toList();
        publishTimelineEventNotificationsToUsers(
                eventType,
                timelineEventId,
                taskId,
                shaleClientId,
                actorUserId,
                taskTitle,
                caseId,
                caseName,
                recipients,
                eventLabel);
    }

    private void publishTimelineEventNotificationsToUsers(
            String eventType,
            long timelineEventId,
            long taskId,
            int shaleClientId,
            Integer actorUserId,
            String taskTitle,
            Long caseId,
            String caseName,
            List<Integer> recipientCandidates,
            String eventLabel) {
        LinkedHashSet<Integer> recipients = new LinkedHashSet<>();
        if (recipientCandidates != null) {
            for (Integer candidate : recipientCandidates) {
                if (candidate != null && candidate > 0) {
                    recipients.add(candidate);
                }
            }
        }
        if (actorUserId != null && actorUserId > 0) {
            recipients.remove(actorUserId);
        }
        if (recipients.isEmpty()) {
            return;
        }
        String notificationTitle = timelineNotificationTitle(eventType, eventLabel);
        String notificationMessage = timelineNotificationMessage(actorUserId, shaleClientId, eventType, taskTitle, caseId, caseName, taskId);
        for (Integer recipientUserId : recipients) {
            String eventKey = "task-action:" + taskId + ":" + eventType + ":" + timelineEventId + ":" + recipientUserId;
            Long durableId = notificationDao.createTaskActionNotification(
                    shaleClientId,
                    recipientUserId,
                    notificationTitle,
                    notificationMessage,
                    taskId,
                    actorUserId == null ? 0 : actorUserId,
                    eventType,
                    eventKey);
            StringBuilder patch = new StringBuilder("{");
            patch.append("\"notificationType\":\"TASK_TIMELINE_EVENT\"");
            patch.append(",\"recipientUserId\":").append(recipientUserId);
            patch.append(",\"eventType\":\"").append(escapeJson(eventType)).append('"');
            patch.append(",\"eventKey\":\"").append(escapeJson(eventKey)).append('"');
            patch.append(",\"notificationTitle\":\"").append(escapeJson(notificationTitle)).append('"');
            patch.append(",\"notificationMessage\":\"").append(escapeJson(notificationMessage)).append('"');
            patch.append(",\"actionType\":\"").append(escapeJson(eventType)).append('"');
            patch.append(",\"timelineEventId\":").append(timelineEventId);
            if (durableId != null) {
                patch.append(",\"durableNotificationId\":").append(durableId);
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
            runtimeBridge.publishEntityUpdated("Task", taskId, shaleClientId, actorUserId == null ? 0 : actorUserId, patch.toString());
        }
    }

    private static String buildTaskAssignedMessage(String title, Long caseId, String caseName, long taskId) {
        return "A task was assigned to you.";
    }

    private static String timelineNotificationTitle(String eventType, String eventLabel) {
        if (TaskDao.TaskTimelineEventTypes.TASK_ASSIGNMENT_ADDED.equals(eventType)) {
            return "Task assigned to you";
        }
        if (eventLabel != null && !eventLabel.isBlank()) {
            return eventLabel.trim();
        }
        return "Task updated";
    }

    private String timelineNotificationMessage(Integer actorUserId, int shaleClientId, String eventType, String taskTitle, Long caseId, String caseName, long taskId) {
        if (TaskDao.TaskTimelineEventTypes.TASK_ASSIGNMENT_ADDED.equals(eventType)) {
            return "A task was assigned to you.";
        }
        if (TaskDao.TaskTimelineEventTypes.TASK_COMPLETED.equals(eventType)) {
            return "A task assigned to you was marked complete.";
        }
        if (TaskDao.TaskTimelineEventTypes.TASK_REOPENED.equals(eventType)) {
            return "A task assigned to you was reopened.";
        }
        if (TaskDao.TaskTimelineEventTypes.TASK_DUE_DATE_CHANGED.equals(eventType)) {
            return "A task assigned to you had its due date updated.";
        }
        if (TaskDao.TaskTimelineEventTypes.TASK_PRIORITY_CHANGED.equals(eventType)) {
            return "A task assigned to you had its priority updated.";
        }
        if (TaskDao.TaskTimelineEventTypes.TASK_ASSIGNMENT_REMOVED.equals(eventType)) {
            return "Task assignments were updated.";
        }
        return "A task assigned to you was updated.";
    }

    private static String buildTaskNoteAddedMessage(String title, Long caseId, String caseName, long taskId, String noteBody) {
        return "A task assigned to you has a new note.";
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String resolveUserDisplayName(long taskId, int shaleClientId, int userId, boolean fallbackWhenMissing) {
        String resolved = taskDao.listAssignedUsersForTask(taskId, shaleClientId).stream()
                .filter(row -> row.userId() == userId)
                .map(row -> row.displayName() == null || row.displayName().isBlank() ? "User #" + userId : row.displayName().trim())
                .findFirst()
                .orElse(null);
        if (resolved != null) {
            return resolved;
        }
        return fallbackWhenMissing ? "User #" + userId : null;
    }

    private Map<Integer, String> loadPriorityLabels(int shaleClientId) {
        Map<Integer, String> labels = new HashMap<>();
        for (TaskPriorityOptionDto option : taskDao.listActivePriorities(shaleClientId)) {
            if (option == null) {
                continue;
            }
            labels.put(option.id(), option.name());
        }
        return labels;
    }

    private Map<Integer, String> loadTaskStatusLabels(int shaleClientId) {
        Map<Integer, String> labels = new HashMap<>();
        for (TaskStatusOptionDto option : taskDao.listActiveTaskStatuses(shaleClientId)) {
            if (option == null) {
                continue;
            }
            labels.put(option.id(), option.name());
        }
        return labels;
    }

    private static String formatPriority(Integer priorityId, Map<Integer, String> labels) {
        if (priorityId == null) {
            return "None";
        }
        String name = labels.get(priorityId);
        if (name == null || name.isBlank()) {
            return "Priority #" + priorityId;
        }
        return name.trim();
    }

    private static String formatStatus(Integer statusId, Map<Integer, String> labels) {
        if (statusId == null) {
            return "None";
        }
        String name = labels.get(statusId);
        if (name == null || name.isBlank()) {
            return "Status #" + statusId;
        }
        return name.trim();
    }

    private static Integer normalizeActorUserId(Integer actorUserId) {
        return actorUserId != null && actorUserId > 0 ? actorUserId : null;
    }

    private static String quoteForBody(String text) {
        String normalized = normalizeTitle(text);
        return "\"" + normalized + "\"";
    }

    private static String normalizeTitle(String text) {
        String normalized = text == null ? "" : text.trim();
        return normalized.isBlank() ? "(empty)" : normalized;
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static String formatDueDate(LocalDateTime dueAt) {
        if (dueAt == null) {
            return "none";
        }
        return dueAt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static String normalizeActorDisplayName(String actorDisplayName) {
        String normalized = actorDisplayName == null ? "" : actorDisplayName.trim();
        return normalized.isBlank() ? null : normalized;
    }

    public record CreateTaskRequest(
            int shaleClientId,
            long caseId,
            String title,
            String description,
            java.time.LocalDateTime dueAt,
            Integer priorityId,
            List<Integer> assignedUserIds,
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
    public record TaskAssignedUsersByTask(
            long taskId,
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
            Integer statusId,
            Integer priorityId,
            boolean completed,
            int changedByUserId) {
    }

    public record TaskActivityItem(
            long id,
            long taskId,
            int caseId,
            int shaleClientId,
            String taskTitle,
            String eventType,
            Integer actorUserId,
            String actorDisplayName,
            String title,
            String body,
            LocalDateTime occurredAt) {
    }

    public record TaskNoteItem(
            long id,
            long taskId,
            int caseId,
            int shaleClientId,
            int userId,
            String userDisplayName,
            String userColor,
            String body,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean isDeleted) {
    }
}
