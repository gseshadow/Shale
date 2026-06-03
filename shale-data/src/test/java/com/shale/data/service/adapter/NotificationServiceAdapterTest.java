package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.service.NotificationServicePort.CreateNotificationCommand;
import com.shale.core.service.NotificationServicePort.NotificationSummary;
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
				false,
				createdAt,
				"event-key")));
		NotificationServiceAdapter adapter = new NotificationServiceAdapter(gateway);

		List<NotificationSummary> summaries = adapter.listUnreadNotifications(42, 7);

		assertEquals(42, gateway.lastListShaleClientId);
		assertEquals(7, gateway.lastListUserId);
		assertEquals(List.of(new NotificationSummary(9, 42, 7, "TASK", "Assigned", "Task assigned", createdAt)), summaries);
	}

	@Test
	void createNotificationKeepsClearTodoPlaceholder() {
		NotificationServiceAdapter adapter = new NotificationServiceAdapter(new FakeNotificationGateway(List.of()));

		UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
				() -> adapter.createNotification(new CreateNotificationCommand(42, 7, 9, "TASK", "Title", "Body", "event")));

		assertTrue(error.getMessage().contains("TODO: NotificationServiceAdapter.createNotification"));
	}

	private static final class FakeNotificationGateway implements NotificationServiceAdapter.NotificationGateway {
		private final List<NotificationDao.NotificationRow> rows;
		private int lastListShaleClientId;
		private int lastListUserId;

		private FakeNotificationGateway(List<NotificationDao.NotificationRow> rows) {
			this.rows = rows;
		}

		@Override
		public List<NotificationDao.NotificationRow> listUnreadNotificationsForUser(int shaleClientId, int userId) {
			lastListShaleClientId = shaleClientId;
			lastListUserId = userId;
			return rows;
		}

		@Override
		public void markNotificationRead(int shaleClientId, int userId, long notificationId) {
		}

		@Override
		public void markNotificationDismissed(int shaleClientId, int userId, long notificationId) {
		}
	}
}
