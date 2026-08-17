package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class CalendarSourceSpecificRoutingTest {
    @Test
    void calendarSurfacesKeepCaseDatesAndCalendarEventsOnSeparateEditors() throws Exception {
        String calendar = source("CalendarController.java");
        String caseController = source("CaseController.java");
        String agenda = Files.readString(Path.of("src/main/java/com/shale/ui/component/ScheduleAgendaPane.java"));
        String navigation = Files.readString(Path.of("src/main/java/com/shale/ui/navigation/SceneManager.java"));

        assertTrue(calendar.contains("case CALENDAR_EVENT -> openEditEventDialog"));
        assertTrue(calendar.contains("case CASE_DATES -> onOpenCaseDates.accept(target.caseId(), target.id())"));
        assertTrue(caseController.contains("case CALENDAR_EVENT -> openCaseCalendarEventEditor"));
        assertTrue(caseController.contains("case CASE_DATES -> openAuthoritativeCaseDate(target.id())"));
        assertTrue(agenda.contains("handlers.onCaseDates().accept(target.caseId(), target.id())"));
        assertTrue(navigation.contains("openCaseProfile(caseId, \"DATES\")"));
        assertFalse(calendar.substring(calendar.indexOf("configureCalendarCardClick"), calendar.indexOf("private void openEditEventDialog"))
                .contains("NewCalendarEventDialog"));
    }

    @Test
    void authoritativeOccurrenceReloadFailsClosedAndActivationIsConsistent() throws Exception {
        String calendar = source("CalendarController.java");
        String caseController = source("CaseController.java");

        assertTrue(caseController.contains("caseService.getCaseDate(caseDateId, tenantId, actorId)"));
        assertTrue(caseController.contains("loaded.get().caseId() != expectedCaseId"));
        assertTrue(caseController.contains("loaded.get().shaleClientId() != tenantId"));
        assertTrue(caseController.contains("generation != caseDateOpenGeneration"));
        assertTrue(caseController.contains("caseDateOpenInFlight.compareAndSet(false, true)"));
        assertTrue(calendar.contains("evt.isStillSincePress()"));
        assertTrue(calendar.contains("isEmbeddedAction(evt.getTarget(), card)"));
        assertTrue(calendar.contains("KeyCode.ENTER") && calendar.contains("KeyCode.SPACE"));
    }

    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/ui/controller/" + file));
    }
}
