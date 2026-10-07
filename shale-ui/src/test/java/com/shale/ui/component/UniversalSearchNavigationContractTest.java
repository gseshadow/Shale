package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UniversalSearchNavigationContractTest {
    @Test void initialFocusUsesExistingNavigationForNewAndReplacedSceneRoots() {
        com.shale.ui.testutil.JavaFxTestSupport.runAndWait(() -> {
            javafx.stage.Stage stage = new javafx.stage.Stage();
            try {
                for (int i = 0; i < 2; i++) {
                    var loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/main.fxml"));
                    javafx.scene.Parent root = loader.load();
                    if (stage.getScene() == null) stage.setScene(new javafx.scene.Scene(root));
                    else stage.getScene().setRoot(root);
                    stage.show();
                    ((com.shale.ui.controller.MainController) loader.getController()).focusInitialPage();
                    assertSame(root.lookup("#navMyShaleButton"), stage.getScene().getFocusOwner(),
                            "New and login-replaced shells must give initial focus to My Shale navigation");
                    assertTrue(root.lookup("#globalSearchField").isFocusTraversable(),
                            "Search must remain reachable with the keyboard");
                }
            } finally { stage.close(); }
        });
    }

    @Test void everyAuthenticatedShellFocusesInitialNavigationAfterInstallingItsRoute() throws Exception {
        String scene = Files.readString(Path.of("src/main/java/com/shale/ui/navigation/SceneManager.java"));
        String shell = body(scene, "private void showMainShell");
        assertTrue(shell.indexOf("mainController.focusInitialPage()") > shell.indexOf("showRouteInternal(AppRoute.myShale())"),
                "The shared manual/remembered sign-in shell must focus navigation after installing My Shale");
        assertEquals(1, java.util.regex.Pattern.compile("focusInitialPage\\(").matcher(scene).results().count(),
                "Initial focus must happen once at shell creation, never on ordinary route changes");
        String main = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MainController.java"));
        String focus = body(main, "public void focusInitialPage");
        assertTrue(focus.contains("navMyShaleButton.requestFocus()"), "Initial focus must use the existing My Shale navigation control");
        assertTrue(focus.contains("dismissSearchPopup()"), "Shell initialization must leave suggestions dismissed");
    }

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
