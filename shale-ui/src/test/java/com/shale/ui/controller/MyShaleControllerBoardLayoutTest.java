package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class MyShaleControllerBoardLayoutTest {

    @Test
    void caseRadarReplacesPlaceholderAndKeepsUrgentRowsFirst() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));

        assertTrue(source.contains("buildCaseRadarWidget()"),
                "Overview dashboard should render the live Case Radar widget instead of a placeholder");
        assertTrue(source.contains("Overdue tasks"),
                "Case Radar should include overdue tasks as the first attention row");
        assertTrue(source.indexOf("Overdue tasks") < source.indexOf("SOL due ≤ 14 days"),
                "Overdue tasks should appear before SOL warning rows");
        assertTrue(source.contains("activeAssignedCaseRadarSource"),
                "Case Radar should reuse loaded assigned case board data with terminal status filtering");
        assertTrue(source.contains("TODO: Add inactive/recently-updated radar rows"),
                "Unavailable activity metrics should remain an explicit follow-up hook");
    }

    @Test
    void importantDatesWidgetUsesLoadedSourcesAndKeepsChronologicalCap() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));

        assertTrue(source.contains("buildImportantDatesWidget()"),
                "Overview dashboard should render the live Important Dates widget instead of a placeholder");
        assertTrue(source.contains("IMPORTANT_DATES_WINDOW_DAYS = 30"),
                "Important Dates should use the requested today-through-30-days window");
        assertTrue(source.contains("IMPORTANT_DATES_ROW_LIMIT = 10"),
                "Important Dates rendering should be capped at 10 visible rows");
        assertTrue(source.contains("overviewEligibleTasks(myTasks)"),
                "Important Dates should reuse already loaded assigned task data");
        assertTrue(source.contains("activeAssignedCaseRadarSource()"),
                "Important Dates should reuse active assigned case data with terminal status filtering");
        assertTrue(source.contains("TORT_NOTICE"),
                "Important Dates should include Tort Notice deadlines from the assigned case model");
        assertTrue(source.contains("TODO: Add Calendar important dates"),
                "Calendar integration should remain a TODO until a reliable My Shale loaded path exists");
        assertTrue(source.contains("DashboardWidgetFactory.widget"),
                "Important Dates should be built with DashboardWidgetFactory");
    }

    @Test
    void notificationsWidgetReusesCenterServiceAndKeepsCompactUnreadFirstBriefing() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));
        String sceneManager = Files.readString(Path.of("src/main/java/com/shale/ui/navigation/SceneManager.java"));

        assertTrue(source.contains("buildNotificationsWidget()"),
                "Overview dashboard should render the live Notifications widget instead of the placeholder");
        assertTrue(source.contains("NotificationCenterService notificationCenterService"),
                "Notifications widget should reuse the existing NotificationCenterService path");
        assertTrue(source.contains("getNotificationsNewestFirst()"),
                "Notifications widget should reuse the existing hydrated notification list instead of issuing duplicate queries");
        assertTrue(source.contains("NOTIFICATIONS_ROW_LIMIT = 10"),
                "Notifications widget should cap visible rows at 10");
        assertTrue(source.contains("Comparator.comparing(AppNotification::isUnread).reversed()"),
                "Notifications widget should prefer unread notifications first");
        assertTrue(source.contains("notificationCenterService.getUnreadCount()"),
                "Notifications widget should display the existing unread badge count");
        assertTrue(source.contains("You’re all caught up."),
                "Notifications widget should keep the requested empty state");
        assertTrue(source.contains("TODO: Add recent-read durable notifications"),
                "Recent-read support should remain an explicit TODO until the existing service exposes it");
        assertTrue(source.contains("notificationCenterService.markRead(notification)"),
                "Notification row clicks should reuse the existing mark-read behavior");
        assertTrue(sceneManager.contains("notificationCenterService, this::openNotificationCenterFromDashboard"),
                "My Shale should receive the existing notification center service and View All route from SceneManager");
    }

    @Test
    void myCasesBoardUsesWiderStatusColumnsAndHorizontalScroll() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));

        assertTrue(source.contains("MY_CASES_STATUS_COLUMN_MIN_WIDTH = 320"),
                "My Cases board lane minimum width should be widened from the previous 245px value");
        assertTrue(source.contains("MY_CASES_STATUS_COLUMN_PREF_WIDTH = 360"),
                "My Cases board lane preferred width should be widened from the previous 280px value");
        assertTrue(source.contains("MY_CASES_STATUS_COLUMN_MAX_WIDTH = 400"),
                "My Cases board lane maximum width should be widened from the previous 320px value");
        assertTrue(source.contains("myCasesBoardScroll.setFitToWidth(false)"),
                "The board should horizontally scroll instead of fitting/compressing all status lanes into the viewport");
        assertTrue(source.contains("myCasesBoardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED)"));
        assertTrue(source.contains("body.setFillWidth(true)"));
        assertTrue(source.contains("buildMyCasesBoardCard"));
        assertTrue(source.contains("region.setMaxWidth(Double.MAX_VALUE)"),
                "Board cards should be allowed to fill the widened status lane body");
    }
}
