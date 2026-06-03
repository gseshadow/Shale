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

	private final TaskGateway taskGateway;

	public TaskServiceAdapter(TaskDao taskDao) {
		this(new DaoTaskGateway(taskDao));
	}

	TaskServiceAdapter(TaskGateway taskGateway) {
		this.taskGateway = Objects.requireNonNull(taskGateway, "taskGateway");
	}

	@Override
	public List<CaseTaskListItemDto> listCaseTasks(long caseId, int shaleClientId) {
		return taskGateway.listActiveTasksForCase(caseId, shaleClientId, TaskDao.CaseTaskSort.DEFAULT);
	}

	@Override
	public List<CaseTaskListItemDto> listAssignedTasks(int assignedUserId, int shaleClientId) {
		return taskGateway.listActiveTasksAssignedToUser(shaleClientId, assignedUserId, TaskDao.MyTaskSort.DEFAULT);
	}

	@Override
	public Optional<TaskDetailDto> getTaskDetail(long taskId, int shaleClientId) {
		return Optional.ofNullable(taskGateway.findTaskDetail(taskId, shaleClientId));
	}

	@Override
	public List<TaskPriorityOptionDto> listPriorities(int shaleClientId) {
		return taskGateway.listActivePriorities(shaleClientId);
	}

	@Override
	public List<TaskStatusOptionDto> listStatuses(int shaleClientId) {
		return taskGateway.listActiveTaskStatuses(shaleClientId);
	}

	@Override
	public long createTaskWithDefaultStatus(CreateTaskCommand command) {
		Objects.requireNonNull(command, "command");
		long taskId = taskGateway.createTask(
				command.shaleClientId(),
				command.caseId(),
				command.title(),
				command.description(),
				command.dueAt(),
				command.priorityId(),
				command.createdByUserId());
		if (command.assignedUserId() != null) {
			taskGateway.addTaskAssignment(taskId, command.shaleClientId(), command.assignedUserId(), command.createdByUserId());
		}
		return taskId;
	}

	@Override
	public void updateTask(UpdateTaskCommand command) {
		Objects.requireNonNull(command, "command");
		TaskDetailDto current = taskGateway.findTaskDetail(command.taskId(), command.shaleClientId());
		if (current == null) {
			throw new IllegalArgumentException("Task not found: " + command.taskId());
		}
		taskGateway.updateTask(
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
		taskGateway.addTaskAssignment(taskId, shaleClientId, userId, assignedByUserId);
	}

	@Override
	public void removeTaskAssignment(long taskId, int shaleClientId, int userId, int actorUserId) {
		taskGateway.removeTaskAssignment(taskId, shaleClientId, userId);
	}

	interface TaskGateway {
		List<CaseTaskListItemDto> listActiveTasksForCase(long caseId, int shaleClientId, TaskDao.CaseTaskSort sort);

		List<CaseTaskListItemDto> listActiveTasksAssignedToUser(int shaleClientId, int assignedUserId, TaskDao.MyTaskSort sort);

		TaskDetailDto findTaskDetail(long taskId, int shaleClientId);

		List<TaskPriorityOptionDto> listActivePriorities(int shaleClientId);

		List<TaskStatusOptionDto> listActiveTaskStatuses(int shaleClientId);

		long createTask(int shaleClientId, long caseId, String title, String description,
				java.time.LocalDateTime dueAt, Integer priorityId, int createdByUserId);

		boolean addTaskAssignment(long taskId, int shaleClientId, int userId, int assignedByUserId);

		void updateTask(long taskId, int shaleClientId, String title, String description,
				java.time.LocalDateTime dueAt, Integer statusId, Integer priorityId,
				boolean completed, Integer updatedByUserId);

		void removeTaskAssignment(long taskId, int shaleClientId, int userId);
	}

	private record DaoTaskGateway(TaskDao taskDao) implements TaskGateway {
		private DaoTaskGateway {
			Objects.requireNonNull(taskDao, "taskDao");
		}

		@Override
		public List<CaseTaskListItemDto> listActiveTasksForCase(long caseId, int shaleClientId, TaskDao.CaseTaskSort sort) {
			return taskDao.listActiveTasksForCase(caseId, shaleClientId, sort);
		}

		@Override
		public List<CaseTaskListItemDto> listActiveTasksAssignedToUser(int shaleClientId, int assignedUserId, TaskDao.MyTaskSort sort) {
			return taskDao.listActiveTasksAssignedToUser(shaleClientId, assignedUserId, sort);
		}

		@Override
		public TaskDetailDto findTaskDetail(long taskId, int shaleClientId) {
			return taskDao.findTaskDetail(taskId, shaleClientId);
		}

		@Override
		public List<TaskPriorityOptionDto> listActivePriorities(int shaleClientId) {
			return taskDao.listActivePriorities(shaleClientId);
		}

		@Override
		public List<TaskStatusOptionDto> listActiveTaskStatuses(int shaleClientId) {
			return taskDao.listActiveTaskStatuses(shaleClientId);
		}

		@Override
		public long createTask(int shaleClientId, long caseId, String title, String description,
				java.time.LocalDateTime dueAt, Integer priorityId, int createdByUserId) {
			return taskDao.createTask(shaleClientId, caseId, title, description, dueAt, priorityId, createdByUserId);
		}

		@Override
		public boolean addTaskAssignment(long taskId, int shaleClientId, int userId, int assignedByUserId) {
			return taskDao.addTaskAssignment(taskId, shaleClientId, userId, assignedByUserId);
		}

		@Override
		public void updateTask(long taskId, int shaleClientId, String title, String description,
				java.time.LocalDateTime dueAt, Integer statusId, Integer priorityId,
				boolean completed, Integer updatedByUserId) {
			taskDao.updateTask(taskId, shaleClientId, title, description, dueAt, statusId, priorityId, completed, updatedByUserId);
		}

		@Override
		public void removeTaskAssignment(long taskId, int shaleClientId, int userId) {
			taskDao.removeTaskAssignment(taskId, shaleClientId, userId);
		}
	}
}
