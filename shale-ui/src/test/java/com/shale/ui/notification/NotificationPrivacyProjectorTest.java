package com.shale.ui.notification;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.service.NotificationServicePort.NotificationSummary;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NotificationPrivacyProjectorTest {
	private final NotificationPrivacyProjector projector = new NotificationPrivacyProjector();

	@Test void taskToastsUseTheExactResolvedInAppTitleAndMessage() {
		assertSameDisplay("ASSIGNED", "Task assigned to you", "A task was assigned to you.");
		assertSameDisplay("TASK_TITLE_CHANGED", "Task updated", "A task assigned to you was updated.");
		assertSameDisplay("NOTE_ADDED", "Task note added", "A task assigned to you has a new note.");
		assertSameDisplay("TASK_COMPLETED", "Task updated", "A task assigned to you was marked complete.");
	}

	@Test void incompleteAndUnknownNotificationsUseGenericFallbackWithoutMisclassification() {
		NativeNotificationPresentation incomplete = projector.project(summary("TASK", "UNKNOWN"), app("", "", "UNKNOWN"));
		assertEquals("Shale", incomplete.heading());
		assertEquals("You have a new notification in Shale.", incomplete.message());
		assertNotEquals("You have a new task in Shale.", incomplete.message());
		NativeNotificationPresentation unrecognized = projector.project(summary("TASK", "MYSTERY"),
				app("Task assigned to you", "A task was assigned to you.", "MYSTERY"));
		assertEquals("You have a new notification in Shale.", unrecognized.message());
		NativeNotificationPresentation unknown = projector.project(summary("secret-category", "MYSTERY"), app("Sensitive title", "Client Jane Doe", "MYSTERY"));
		assertEquals("You have a new notification in Shale.", unknown.message());
		assertEquals("OTHER", unknown.categoryCode());
		assertFalse(unknown.toString().contains("Sensitive title"));
		assertFalse(unknown.toString().contains("Client Jane Doe"));
	}

	@Test void projectionDoesNotAlterInAppClickMetadata() {
		AppNotification display = app("Task updated", "A task assigned to you was updated.", "TASK_TITLE_CHANGED");
		projector.project(summary("TASK", "TASK_TITLE_CHANGED"), display);
		assertEquals(99L, display.getEntityId());
		assertEquals("Task", display.getEntityType());
		assertEquals("TASK_TITLE_CHANGED", display.getActionType());
		assertEquals(42L, display.getDurableNotificationId());
	}

	@Test void newlyVisibleProjectionUsesTheSameInAppObjectWithoutDurableRediscovery() {
		AppNotification display = app("Task updated", "A task assigned to you was updated.", "TASK_TITLE_CHANGED");
		NativeNotificationPresentation nativeDisplay = projector.project(display);
		assertEquals(42L, nativeDisplay.notificationId());
		assertEquals(display.getTitle(), nativeDisplay.heading());
		assertEquals(display.getMessage(), nativeDisplay.message());
		assertEquals("TASK", nativeDisplay.categoryCode());
	}

	@Test void presentationAndPresenterContractsExposeOnlyRestrictedType() {
		Set<String> fields = Arrays.stream(NativeNotificationPresentation.class.getRecordComponents())
				.map(RecordComponent::getName).collect(Collectors.toSet());
		assertEquals(Set.of("notificationId", "heading", "message", "categoryCode"), fields);
		var present = Arrays.stream(DesktopNotificationPresenter.class.getDeclaredMethods())
				.filter(method -> method.getName().equals("present")).findFirst().orElseThrow();
		assertArrayEquals(new Class<?>[] { NativeNotificationPresentation.class }, present.getParameterTypes());
		assertEquals(PresentationResult.UNSUPPORTED, new NoOpDesktopNotificationPresenter().present(
				projector.project(summary("TASK", "ASSIGNED"), app("Task assigned to you", "A task was assigned to you.", "ASSIGNED"))));
	}

	private void assertSameDisplay(String action, String title, String message) {
		AppNotification display = app(title, message, action);
		NativeNotificationPresentation nativeDisplay = projector.project(summary("TASK", action), display);
		assertEquals(display.getTitle(), nativeDisplay.heading());
		assertEquals(display.getMessage(), nativeDisplay.message());
	}

	private static AppNotification app(String title, String message, String action) {
		return new AppNotification("db-42", NotificationCategory.TASK, NotificationSeverity.INFO, title, message,
				Instant.EPOCH, true, false, NotificationTargetScope.USER_SCOPED, 42L, "event-42", "Task", 99L,
				"Display task", action);
	}

	private static NotificationSummary summary(String category, String action) {
		return new NotificationSummary(42, 7, 9, category, "INFO", "PRIVATE TITLE", "PRIVATE MESSAGE", "TASK", 99L,
				action, "event-42", "Actor", "PRIVATE ENTITY", 1L, "PRIVATE CASE", null, null, null, null, null, null,
				Instant.EPOCH, false);
	}
}
