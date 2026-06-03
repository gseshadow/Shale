package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.core.dto.TaskStatusOptionDto;
import com.shale.core.service.TaskServicePort.CreateTaskCommand;
import com.shale.data.dao.TaskDao;

class TaskServiceAdapterTest {

	@Test
	void listCaseTasksDelegatesWithDefaultSort() {
		CaseTaskListItemDto task = taskItem(10);
		FakeTaskGateway gateway = new FakeTaskGateway(List.of(task));
		TaskServiceAdapter adapter = new TaskServiceAdapter(gateway);

		List<CaseTaskListItemDto> actual = adapter.listCaseTasks(123, 42);

		assertEquals(123, gateway.lastCaseId);
		assertEquals(42, gateway.lastShaleClientId);
		assertEquals(TaskDao.CaseTaskSort.DEFAULT, gateway.lastCaseTaskSort);
		assertEquals(List.of(task), actual);
	}

	@Test
	void createTaskWithDefaultStatusDelegatesAndAssignsUser() {
		FakeTaskGateway gateway = new FakeTaskGateway(List.of());
		gateway.createdTaskId = 700;
		TaskServiceAdapter adapter = new TaskServiceAdapter(gateway);

		long taskId = adapter.createTaskWithDefaultStatus(
				new CreateTaskCommand(123, 42, 7, "Title", "Description", null, 3, 4));

		assertEquals(700, taskId);
		assertEquals(42, gateway.lastCreateShaleClientId);
		assertEquals(123, gateway.lastCreateCaseId);
		assertEquals("Title", gateway.lastCreateTitle);
		assertEquals("Description", gateway.lastCreateDescription);
		assertEquals(3, gateway.lastCreatePriorityId);
		assertEquals(7, gateway.lastCreateCreatedByUserId);
		assertEquals(700, gateway.lastAssignedTaskId);
		assertEquals(4, gateway.lastAssignedUserId);
	}

	@Test
	void updateTaskFailsWhenTaskCannotBeLoaded() {
		TaskServiceAdapter adapter = new TaskServiceAdapter(new FakeTaskGateway(List.of()));

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> adapter.updateTask(new com.shale.core.service.TaskServicePort.UpdateTaskCommand(
						404, 42, 7, "Title", "Description", null, null, null)));

		assertEquals("Task not found: 404", error.getMessage());
	}

	private static CaseTaskListItemDto taskItem(long id) {
		return new CaseTaskListItemDto(id, 42, 123, "Case", "Attorney", null, null,
				"Title", "Description", 1, "#fff", null, null, null, null, null,
				7, "Creator", LocalDateTime.now(), LocalDateTime.now(), false);
	}

	private static final class FakeTaskGateway implements TaskServiceAdapter.TaskGateway {
		private final List<CaseTaskListItemDto> caseTasks;
		private long lastCaseId;
		private int lastShaleClientId;
		private TaskDao.CaseTaskSort lastCaseTaskSort;
		private long createdTaskId;
		private int lastCreateShaleClientId;
		private long lastCreateCaseId;
		private String lastCreateTitle;
		private String lastCreateDescription;
		private Integer lastCreatePriorityId;
		private int lastCreateCreatedByUserId;
		private long lastAssignedTaskId;
		private int lastAssignedUserId;

		private FakeTaskGateway(List<CaseTaskListItemDto> caseTasks) {
			this.caseTasks = caseTasks;
		}

		@Override
		public List<CaseTaskListItemDto> listActiveTasksForCase(long caseId, int shaleClientId, TaskDao.CaseTaskSort sort) {
			lastCaseId = caseId;
			lastShaleClientId = shaleClientId;
			lastCaseTaskSort = sort;
			return caseTasks;
		}

		@Override
		public List<CaseTaskListItemDto> listActiveTasksAssignedToUser(int shaleClientId, int assignedUserId, TaskDao.MyTaskSort sort) {
			return List.of();
		}

		@Override
		public TaskDetailDto findTaskDetail(long taskId, int shaleClientId) {
			return null;
		}

		@Override
		public List<TaskPriorityOptionDto> listActivePriorities(int shaleClientId) {
			return List.of();
		}

		@Override
		public List<TaskStatusOptionDto> listActiveTaskStatuses(int shaleClientId) {
			return List.of();
		}

		@Override
		public long createTask(int shaleClientId, long caseId, String title, String description,
				LocalDateTime dueAt, Integer priorityId, int createdByUserId) {
			lastCreateShaleClientId = shaleClientId;
			lastCreateCaseId = caseId;
			lastCreateTitle = title;
			lastCreateDescription = description;
			lastCreatePriorityId = priorityId;
			lastCreateCreatedByUserId = createdByUserId;
			return createdTaskId;
		}

		@Override
		public boolean addTaskAssignment(long taskId, int shaleClientId, int userId, int assignedByUserId) {
			lastAssignedTaskId = taskId;
			lastAssignedUserId = userId;
			return true;
		}

		@Override
		public void updateTask(long taskId, int shaleClientId, String title, String description,
				LocalDateTime dueAt, Integer statusId, Integer priorityId, boolean completed, Integer updatedByUserId) {
		}

		@Override
		public void removeTaskAssignment(long taskId, int shaleClientId, int userId) {
		}
	}
}
