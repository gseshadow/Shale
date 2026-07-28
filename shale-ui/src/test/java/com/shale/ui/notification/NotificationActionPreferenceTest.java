package com.shale.ui.notification;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NotificationActionPreferenceTest {
	@Test void everyTaskActionUsesItsEstablishedPreferenceAndUnknownFallsBackSafely() {
		assertEquals(NotificationPreferenceKey.TASK_DUE_OVERDUE,DurableNotificationService.preferenceKey(NotificationCategory.TASK,"DUE_OVERDUE"));
		assertEquals(NotificationPreferenceKey.TASK_DUE_TODAY,DurableNotificationService.preferenceKey(NotificationCategory.TASK,"DUE_TODAY"));
		assertEquals(NotificationPreferenceKey.TASK_DUE_TOMORROW,DurableNotificationService.preferenceKey(NotificationCategory.TASK,"DUE_TOMORROW"));
		assertEquals(NotificationPreferenceKey.TASK_ASSIGNED_TO_ME,DurableNotificationService.preferenceKey(NotificationCategory.TASK,"ASSIGNED"));
		assertEquals(NotificationPreferenceKey.TASK_ASSIGNED_TO_ME,DurableNotificationService.preferenceKey(NotificationCategory.TASK,"unexpected"));
		assertNull(DurableNotificationService.preferenceKey(NotificationCategory.CASE,"ASSIGNED"));
	}

	@Test void disablingOneActionDoesNotDisableOtherTaskActions() {
		NotificationPreferences preferences=NotificationPreferences.defaults().withEnabled(NotificationPreferenceKey.TASK_DUE_TODAY,false);
		assertFalse(preferences.isEnabled(DurableNotificationService.preferenceKey(NotificationCategory.TASK,"DUE_TODAY")));
		assertTrue(preferences.isEnabled(DurableNotificationService.preferenceKey(NotificationCategory.TASK,"DUE_OVERDUE")));
		assertTrue(preferences.isEnabled(DurableNotificationService.preferenceKey(NotificationCategory.TASK,"ASSIGNED")));
	}
}
