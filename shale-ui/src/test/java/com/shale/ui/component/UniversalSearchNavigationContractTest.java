package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UniversalSearchNavigationContractTest {
    @Test void productionPopupUsesExistingRoutesPermissionsAndSessionTeardown() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/navigation/SceneManager.java"));
        String factory = body(source, "public com.shale.ui.component.UniversalSearchPopup createUniversalSearchPopup");
        for (String route : new String[]{"openCaseProfile", "openContactProfile", "openOrganizationProfile",
                "openUserProfile", "openTaskProfile", "openCalendarEventFromNotification", "this::openSearchView"})
            assertTrue(factory.contains(route), "Suggestion navigation must reuse " + route);
        assertTrue(factory.contains("permissions::canViewDeletedCasesInSearch"), "Deleted Case visibility must use existing permission authority");
        assertTrue(body(source, "private void stopSessionOwnedWork").contains("disposeSearchPopup"));
        assertTrue(body(source, "public void shutdown()").contains("disposeSearchPopup"));
        assertTrue(body(source, "private void showRouteInternal").contains("dismissSearchPopup"), "Back navigation must also invalidate suggestions");
        assertTrue(body(source, "private void navigateTo").contains("dismissSearchPopup"), "Duplicate-route navigation must dismiss too");
        String task = body(source, "public void openTaskProfile(Long taskId, Runnable");
        assertTrue(task.contains("dismissSearchPopup"));
        assertTrue(task.contains("taskOpenSession == appState.sessionRevision()"));
        assertEquals(3, java.util.regex.Pattern.compile("if \\(\\s*!?taskSessionCurrent\\.getAsBoolean\\(\\)\\s*\\)")
                .matcher(task).results().count(), "Task navigation success/not-found/failure must reject an old identity");
        String calendar = body(source, "public void openCalendarEventFromNotification");
        assertTrue(calendar.contains("dismissSearchPopup"));
        assertTrue(calendar.contains("navigationManager.currentRoute()"),
                "A retained detached Calendar controller must not receive suggestion navigation from another route");
        assertTrue(source.contains("phiReadAuditService.auditRead(\"Task.Detail.Read\""));
        assertTrue(source.contains("phiReadAuditService.auditRead(\"Task.Activity.Read\""), "Direct Task navigation must retain detail read audits");
    }

    private static String body(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing production seam: " + signature);
        int opening = source.indexOf('{', start), depth = 0;
        for (int i = opening; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            else if (source.charAt(i) == '}' && --depth == 0) return source.substring(opening, i + 1);
        }
        throw new AssertionError("Unbalanced production method " + signature);
    }
}
