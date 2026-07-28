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

	@Test void projectsEveryAllowlistedCategoryAndSafeFallbackWithoutDurableText() {
		assertEquals("You have a new task in Shale.", project("TASK").message());
		assertEquals("A material request requires attention.", project("MATERIAL_REQUEST").message());
		assertEquals("A case requires your attention in Shale.", project("CASE").message());
		assertEquals("You have an upcoming item in Shale.", project("CALENDAR").message());
		NativeNotificationPresentation unknown = project("secret-category");
		assertEquals("You have a new notification in Shale.", unknown.message());
		assertEquals("OTHER", unknown.categoryCode());
		assertFalse(unknown.toString().contains("Sensitive title"));
		assertFalse(unknown.toString().contains("Client Jane Doe"));
	}

	@Test void presentationAndPresenterContractsExposeOnlyRestrictedType() {
		Set<String> fields = Arrays.stream(NativeNotificationPresentation.class.getRecordComponents())
				.map(RecordComponent::getName).collect(Collectors.toSet());
		assertEquals(Set.of("notificationId", "heading", "message", "categoryCode"), fields);
		var present = Arrays.stream(DesktopNotificationPresenter.class.getDeclaredMethods())
				.filter(method -> method.getName().equals("present")).findFirst().orElseThrow();
		assertArrayEquals(new Class<?>[] { NativeNotificationPresentation.class }, present.getParameterTypes());
		assertEquals(PresentationResult.UNSUPPORTED, new NoOpDesktopNotificationPresenter().present(project("TASK")));
	}

	private NativeNotificationPresentation project(String category) {
		return projector.project(new NotificationSummary(42, 7, 9, category,
				"Sensitive title", "Client Jane Doe confidential notes", Instant.EPOCH));
	}
}
