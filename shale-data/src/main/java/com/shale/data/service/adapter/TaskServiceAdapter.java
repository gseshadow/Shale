package com.shale.data.service.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.core.dto.TaskStatusOptionDto;
import com.shale.core.service.TaskServicePort;
import com.shale.data.dao.TaskDao;

/**
 * Thin TaskServicePort adapter over existing TaskDao operations.
 */
public final class TaskServiceAdapter implements TaskServicePort {

	private final TaskDao taskDao;

	public TaskServiceAdapter(TaskDao taskDao) {
		this.taskDao = Objects.requireNonNull(taskDao, "taskDao");
	}

	@Override
	public List<CaseTaskListItemDto> listCaseTasks(long caseId, int shaleClientId) {
		return taskDao.listActiveTasksForCase(caseId, shaleClientId, TaskDao.CaseTaskSort.DEFAULT);
	}

	@Override
	public List<CaseTaskListItemDto> listAssignedTasks(int assignedUserId, int shaleClientId) {
		return taskDao.listActiveTasksAssignedToUser(shaleClientId, assignedUserId, TaskDao.MyTaskSort.DEFAULT);
	}

	@Override
	public Optional<TaskDetailDto> getTaskDetail(long taskId, int shaleClientId) {
		return Optional.ofNullable(taskDao.findTaskDetail(taskId, shaleClientId));
	}

	@Override
	public List<TaskPriorityOptionDto> listPriorities(int shaleClientId) {
		return taskDao.listActivePriorities(shaleClientId);
	}

	@Override
	public List<TaskStatusOptionDto> listStatuses(int shaleClientId) {
		return taskDao.listActiveTaskStatuses(shaleClientId);
	}

	@Override
	public long createTask(CreateTaskCommand command) {
		Objects.requireNonNull(command, "command");
		if (command.statusId() != null) {
			throw new UnsupportedOperationException(
					"TODO: TaskServiceAdapter.createTask needs a TaskDao create contract that accepts explicit statusId.");
		}
		long taskId = taskDao.createTask(
				command.shaleClientId(),
				command.caseId(),
				command.title(),
				command.description(),
				command.dueAt(),
				command.priorityId(),
				command.createdByUserId());
		if (command.assignedUserId() != null) {
			taskDao.addTaskAssignment(taskId, command.shaleClientId(), command.assignedUserId(), command.createdByUserId());
		}
		return taskId;
	}

	@Override
	public void updateTask(UpdateTaskCommand command) {
		Objects.requireNonNull(command, "command");
		TaskDetailDto current = taskDao.findTaskDetail(command.taskId(), command.shaleClientId());
		if (current == null) {
			throw new IllegalArgumentException("Task not found: " + command.taskId());
		}
		taskDao.updateTask(
				command.taskId(),
				command.shaleClientId(),
				command.title(),
				command.description(),
				command.dueAt(),
				command.statusId(),
				command.priorityId(),
				current.completedAt() != null,
				command.actorUserId());
	}

	@Override
	public void assignTask(long taskId, int shaleClientId, int userId, int assignedByUserId) {
		taskDao.addTaskAssignment(taskId, shaleClientId, userId, assignedByUserId);
	}

	@Override
	public void removeTaskAssignment(long taskId, int shaleClientId, int userId, int actorUserId) {
		taskDao.removeTaskAssignment(taskId, shaleClientId, userId);
	}
}
