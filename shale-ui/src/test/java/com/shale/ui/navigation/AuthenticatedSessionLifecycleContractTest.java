package com.shale.ui.navigation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class AuthenticatedSessionLifecycleContractTest {
    @Test
    void logoutInvalidatesAndStopsEveryProducerBeforeClearingRuntimeAndIdentity() throws Exception {
        String scenes = source("src/main/java/com/shale/ui/navigation/SceneManager.java");
        String logout = method(scenes, "public void logout()");

        assertOrdered(logout,
                "stopSessionOwnedWork()",
                "runtimeBridge.onLogout()",
                "appState.setUserId(0)",
                "showLoginSurface()");

        String stop = method(scenes, "private void stopSessionOwnedWork()");
        assertOrdered(stop,
                "authenticatedProducersActive = false",
                "notificationStartupGeneration.incrementAndGet()",
                "startupFuture.cancel(true)",
                "liveUpdateNotificationBridge.stop()",
                "taskDueDateNotificationGenerator.stop()",
                "notificationPollingService.stop()",
                "updatePollingService.stop()",
                "notificationCenterService.clearAll()");
    }

    @Test
    void shellLogoutHasOneLifecycleOwnerAndAppearanceResetRemainsLast() throws Exception {
        String controller = source("src/main/java/com/shale/ui/controller/MainController.java");
        String logout = method(controller, "private void onLogout()");
        assertTrue(logout.contains("sceneManager.logout()"), "shell logout must delegate to the lifecycle owner");
        assertFalse(logout.contains("runtimeBridge.onLogout()"), "shell logout must not partially tear down runtime state");
        assertFalse(logout.contains("appState.setUserId"), "shell logout must not partially clear identity");

        String scenes = source("src/main/java/com/shale/ui/navigation/SceneManager.java");
        String presentation = method(scenes, "private void showLoginSurface()");
        assertTrue(presentation.contains("ThemeManager.application().setActiveTheme(Theme.LIGHT)"),
                "the unauthenticated surface must still reset Appearance to Light");
    }

    @Test
    void notificationBootstrapChecksGenerationBeforeEachDatabasePhaseAndBeforeApply() throws Exception {
        String scenes = source("src/main/java/com/shale/ui/navigation/SceneManager.java");
        String bootstrap = method(scenes, "private void startNotificationBootstrapAsync()");
        int beforeDue = bootstrap.indexOf("if (!isActiveSession");
        int due = bootstrap.indexOf("taskDueDateNotificationGenerator.runOnce()", beforeDue);
        int afterDue = bootstrap.indexOf("if (!isActiveSession", due);
        int unread = bootstrap.indexOf("durableNotificationService.listUnread", afterDue);
        int afterUnread = bootstrap.indexOf("if (!isActiveSession", unread);
        int apply = bootstrap.indexOf("durableNotificationService.pushLoaded", afterUnread);
        assertTrue(beforeDue >= 0 && due > beforeDue && afterDue > due && unread > afterDue
                        && afterUnread > unread && apply > afterUnread,
                "bootstrap must guard both DAO phases and stale UI publication");
        assertTrue(bootstrap.contains("notificationStartupFuture = notificationStartupExecutor.submit"),
                "the full bootstrap must retain its cancellable Future");
    }

    @Test
    void dueDateAndUpdateSchedulesAreExplicitlyCancelledAndGenerationGuarded() throws Exception {
        String due = source("src/main/java/com/shale/ui/notification/TaskDueDateNotificationGenerator.java");
        assertTrue(due.contains("ScheduledFuture<?> scheduled"));
        assertTrue(due.contains("scheduled.cancel(true)"));
        assertTrue(due.contains("session = null"));
        assertTrue(due.contains("if (!active(token)) return"));
        assertFalse(due.contains("System.err.println(\"Task due-date generator failed"));

        String updates = source("src/main/java/com/shale/ui/services/UpdatePollingService.java");
        assertTrue(updates.contains("ScheduledFuture<?> scheduled"));
        assertTrue(updates.contains("scheduled.cancel(true)"));
        assertTrue(updates.contains("if (!active(token)) return"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new AssertionError("Missing method: " + signature);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '{') depth++;
            if (value == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("Unclosed method: " + signature);
    }

    private static void assertOrdered(String source, String... fragments) {
        int previous = -1;
        for (String fragment : fragments) {
            int next = source.indexOf(fragment, previous + 1);
            assertTrue(next > previous, "Expected lifecycle step in order: " + fragment);
            previous = next;
        }
    }
}
