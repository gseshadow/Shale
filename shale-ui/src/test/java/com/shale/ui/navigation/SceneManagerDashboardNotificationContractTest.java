package com.shale.ui.navigation;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SceneManagerDashboardNotificationContractTest {
    @Test void dashboardViewAllKeepsTheEstablishedRunnableNotificationCenterRoute() throws Exception {
        String scene = Files.readString(Path.of("src/main/java/com/shale/ui/navigation/SceneManager.java"));
        String dashboard = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));
        String widgets = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/DashboardWidgetFactory.java"));
        String creation = scene.substring(scene.indexOf("public Parent createMyShaleView"),
                scene.indexOf("public Parent createCaseView", scene.indexOf("public Parent createMyShaleView")));
        String compactCreation=creation.replaceAll("\\s+"," ");
        assertTrue(compactCreation.contains("caseDao, caseSummaryDao, caseTaskService, userBoardLanePreferencesDao"));
        assertFalse(compactCreation.contains("caseDao, new CaseServiceAdapter(caseDao), caseTaskService"));
        assertTrue(compactCreation.contains("notificationCenterService, this::openNotificationCenterFromDashboard, onOpenCase, onOpenUser"));
        assertTrue(creation.contains("private void openNotificationCenterFromDashboard()"));
        assertTrue(creation.contains("mainController.openNotificationCenter()"));
        assertTrue(dashboard.contains("Runnable onOpenNotificationCenter"));
        assertTrue(widgets.contains("Runnable viewAllAction"));
        assertTrue(widgets.contains("viewAllAction.run()"));
    }

    @Test void dashboardItemIdentityUsesExistingActivationRoutesRatherThanViewAll() throws Exception {
        String dashboard = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));
        assertTrue(dashboard.contains("handleNotificationBriefingRowClicked(notification)"));
        assertTrue(dashboard.contains("notificationCenterService.markRead(notification)"));
        assertTrue(dashboard.contains("openTask(notification.getEntityId())"));
        assertTrue(dashboard.contains("if (notification == null || notificationCenterService == null)"));
    }
}
