package com.shale.ui.notification;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NotificationPollingLifecycleContractTest {
	@Test void lifecycleUsesBaselineGenerationDaemonBackoffAndNoOpProductionWiring() throws Exception {
		String polling = Files.readString(Path.of("src/main/java/com/shale/ui/notification/NotificationPollingService.java"));
		assertTrue(polling.contains("notificationHighWaterMark"));
		assertTrue(polling.contains("generation"));
		assertTrue(polling.contains("setDaemon(true)"));
		assertTrue(polling.contains("maximumRetryDelay"));
		assertTrue(polling.contains("presentedOrAttempted.add(row.id())"));
		assertFalse(polling.contains("row.title()") || polling.contains("row.body()"));
		String scene = Files.readString(Path.of("src/main/java/com/shale/ui/navigation/SceneManager.java"));
		assertTrue(scene.contains("new NoOpDesktopNotificationPresenter()"));
		assertTrue(scene.contains("notificationPollingService.start"));
		assertTrue(scene.contains("notificationPollingService.stop"));
		assertTrue(scene.contains("notificationPollingService.close"));
	}
}
