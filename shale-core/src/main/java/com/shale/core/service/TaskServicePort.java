package com.shale.core.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.core.dto.TaskStatusOptionDto;

/**
 * Shared task application boundary for future desktop/server adapters.
 *
 * <p>Methods are intentionally small and based on the current TaskDao and
 * CaseTaskService capabilities: list, detail, option lookup, assignment, and
 * basic write operations.</p>
 */
public interface TaskServicePort {

	List<CaseTaskListItemDto> listCaseTasks(long caseId, int shaleClientId);

	List<CaseTaskListItemDto> listAssignedTasks(int assignedUserId, int shaleClientId);

	Optional<TaskDetailDto> getTaskDetail(long taskId, int shaleClientId);

	List<TaskPriorityOptionDto> listPriorities(int shaleClientId);

	List<TaskStatusOptionDto> listStatuses(int shaleClientId);

	/**
	 * Creates a task with the tenant's default open status, matching the existing
	 * TaskDao.createTask behavior. Explicit status creation is intentionally not
	 * exposed until the DAO has a safe create contract for it.
	 */
	long createTaskWithDefaultStatus(CreateTaskCommand command);

	void updateTask(UpdateTaskCommand command);

	void completeTask(long taskId, int shaleClientId, int actorUserId);

	void assignTask(long taskId, int shaleClientId, int userId, int assignedByUserId);

	void removeTaskAssignment(long taskId, int shaleClientId, int userId, int actorUserId);

	record CreateTaskCommand(
			long caseId,
			int shaleClientId,
			int createdByUserId,
			String title,
			String description,
			LocalDateTime dueAt,
			Integer priorityId,
			Integer assignedUserId) {
	}

	record UpdateTaskCommand(
			long taskId,
			int shaleClientId,
			int actorUserId,
			String title,
			String description,
			LocalDateTime dueAt,
			Integer statusId,
			Integer priorityId,
			Integer assignedUserId) {
	}
}
