package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.shale.core.service.NotificationServicePort.NotificationSummary;
import com.shale.core.service.NotificationServicePort.NotificationCursor;
import com.shale.core.service.NotificationServicePort.TaskNotificationCommand;
import com.shale.data.dao.NotificationDao;

class NotificationServiceAdapterTest {

	@Test
	void listUnreadNotificationsDelegatesAndMapsRows() {
		Instant createdAt = Instant.parse("2026-06-03T15:00:00Z");
		FakeNotificationGateway gateway = new FakeNotificationGateway(List.of(new NotificationDao.NotificationRow(
				9,
				"TASK",
				"INFO",
				"Assigned",
				"Task assigned",
				"TASK",
				100L,
				"ASSIGNED",
				"Actor",
				"Task title",
				123L,
				"Case",
				"Attorney",
				"#000",
				false,
				"Prelitigation",
				"#2563eb",
				"#f97316",
				false,
				createdAt,
				"event-key")));
		NotificationServiceAdapter adapter = new NotificationServiceAdapter(gateway);

		List<NotificationSummary> summaries = adapter.listUnreadNotifications(42, 7);

		assertEquals(42, gateway.lastListShaleClientId);
		assertEquals(7, gateway.lastListUserId);
		assertEquals(List.of(new NotificationSummary(9, 42, 7, "TASK", "Assigned", "Task assigned", createdAt, false, "TASK")), summaries);
	}

	@Test
	void createTaskAssignedNotificationDelegatesToSpecificDaoContract() {
		FakeNotificationGateway gateway = new FakeNotificationGateway(List.of());
		gateway.createdNotificationId = 88L;
		NotificationServiceAdapter adapter = new NotificationServiceAdapter(gateway);

		Optional<Long> id = adapter.createTaskAssignedNotification(
				new TaskNotificationCommand(42, 7, "Assigned", "Task assigned", 100, 5, "event-key"));

		assertEquals(Optional.of(88L), id);
		assertEquals(42, gateway.lastCreateShaleClientId);
		assertEquals(7, gateway.lastCreateUserId);
		assertEquals("Assigned", gateway.lastCreateTitle);
		assertEquals("Task assigned", gateway.lastCreateMessage);
		assertEquals(100, gateway.lastCreateEntityId);
		assertEquals(5, gateway.lastCreateCreatedByUserId);
		assertEquals("event-key", gateway.lastCreateEventKey);
	}

	@Test
	void createTaskAssignedNotificationMapsNullDaoResultToEmpty() {
		NotificationServiceAdapter adapter = new NotificationServiceAdapter(new FakeNotificationGateway(List.of()));

		Optional<Long> id = adapter.createTaskAssignedNotification(
				new TaskNotificationCommand(42, 7, "Assigned", "Task assigned", 100, 5, ""));

		assertTrue(id.isEmpty());
	}

	@Test void cursorCountAndActivationDelegate() {
		FakeNotificationGateway gateway=new FakeNotificationGateway(List.of());
		gateway.page=new NotificationDao.NotificationPageRow(List.of(new NotificationDao.NotificationCursorRow(12,"TASK","t","b",Instant.EPOCH)),false);
		gateway.unreadCount=4;
		gateway.activation=Optional.of(new NotificationDao.NotificationActivationRow(12,"Task",99,8L,"ASSIGNED"));
		NotificationServiceAdapter adapter=new NotificationServiceAdapter(gateway);
		var page=adapter.listNotifications(41,31,NotificationCursor.after(10),25);
		assertEquals(12,page.items().get(0).id()); assertEquals(12,page.nextCursor().afterNotificationId());
		assertEquals(4,adapter.countUnreadNotifications(41,31));
		assertEquals(99,adapter.findActivationTarget(41,31,12).orElseThrow().entityId());
		assertEquals(101, adapter.notificationHighWaterMark(41, 31));
		assertEquals(41,gateway.newTenant); assertEquals(31,gateway.newUser); assertEquals(12,gateway.activationId);
	}

	private static final class FakeNotificationGateway implements NotificationServiceAdapter.NotificationGateway {
		@Override public long notificationHighWaterMark(int shaleClientId, int userId) { return 101; }
		private final List<NotificationDao.NotificationRow> rows;
		private int lastListShaleClientId;
		private int lastListUserId;
		private Long createdNotificationId;
		private int lastCreateShaleClientId;
		private int lastCreateUserId;
		private String lastCreateTitle;
		private String lastCreateMessage;
		private long lastCreateEntityId;
		private int lastCreateCreatedByUserId;
		private String lastCreateEventKey;
		private NotificationDao.NotificationPageRow page=new NotificationDao.NotificationPageRow(List.of(),false);
		private int unreadCount,newTenant,newUser; private long activationId;
		private Optional<NotificationDao.NotificationActivationRow> activation=Optional.empty();

		private FakeNotificationGateway(List<NotificationDao.NotificationRow> rows) {
			this.rows = rows;
		}

		@Override
		public List<NotificationDao.NotificationRow> listUnreadNotificationsForUser(int shaleClientId, int userId) {
			lastListShaleClientId = shaleClientId;
			lastListUserId = userId;
			return rows;
		}
		@Override public NotificationDao.NotificationPageRow listNotificationsForUser(int tenant,int user,long after,int limit){newTenant=tenant;newUser=user;return page;}
		@Override public int countUnreadNotificationsForUser(int tenant,int user){newTenant=tenant;newUser=user;return unreadCount;}
		@Override public Optional<NotificationDao.NotificationActivationRow> findActivationTarget(int tenant,int user,long id){newTenant=tenant;newUser=user;activationId=id;return activation;}

		@Override
		public void markNotificationRead(int shaleClientId, int userId, long notificationId) {
		}

		@Override
		public void markNotificationDismissed(int shaleClientId, int userId, long notificationId) {
		}

		@Override
		public Long createTaskAssignedNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String eventKey) {
			lastCreateShaleClientId = shaleClientId;
			lastCreateUserId = userId;
			lastCreateTitle = title;
			lastCreateMessage = message;
			lastCreateEntityId = entityId;
			lastCreateCreatedByUserId = createdByUserId;
			lastCreateEventKey = eventKey;
			return createdNotificationId;
		}

		@Override
		public Long createTaskNoteAddedNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String eventKey) {
			return null;
		}

		@Override
		public Long createTaskDueDateNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String actionType, String severity, String eventKey) {
			return null;
		}

		@Override
		public Long createTaskActionNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String actionType, String eventKey) {
			return null;
		}

		@Override
		public Long createCalendarEventAssignedNotification(int shaleClientId, int userId, String title, String message,
				long entityId, int createdByUserId, String actionType, String eventKey) {
			return null;
		}
	}
}
