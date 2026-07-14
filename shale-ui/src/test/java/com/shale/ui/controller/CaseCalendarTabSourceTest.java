package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class CaseCalendarTabSourceTest {
    @Test
    void caseCalendarTabIsPlacedBetweenTasksAndTimelineAndUsesSharedFlows() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        String fxml = Files.readString(Path.of("src/main/resources/fxml/case.fxml"));

        assertTrue(controller.contains("\"Tasks\",\n\t\t\t\"Calendar\",\n\t\t\t\"Timeline\""));
        assertTrue(controller.contains("case \"Calendar\" -> showCalendarTab()"));
        assertTrue(controller.contains("calendarService.listCalendarFeedForCase"));
        assertTrue(controller.contains("CalendarFeedSourceFilter.caseCalendarDefaults()"));
        assertTrue(controller.contains("NewCalendarEventDialog.showAndWait"));
        assertTrue(controller.contains("onAddTask()"));
        assertTrue(controller.contains("openTask(target.id())"));
        assertTrue(controller.contains("openCaseCalendarEventEditor"));
        assertTrue(controller.contains("No case calendar layers selected."));
        assertTrue(fxml.contains("fx:id=\"caseCalendarTabPane\""));
        assertTrue(fxml.contains("fx:id=\"caseCalendarUpdatesHost\""));
    }
}
