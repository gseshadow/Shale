package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CalendarCaseDateInPlaceEditorContractTest {
    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/ui/controller/" + file));
    }

    @Test void everyMainCalendarCardSurfaceUsesTheInPlaceSourceSpecificRoute() throws Exception {
        String calendar = source("CalendarController.java");
        assertTrue(calendar.contains("case CASE_DATES -> openCaseDateEditor(target.caseId(), target.id())"));
        assertFalse(calendar.contains("case CASE_DATES -> onOpenCaseDates.accept"));
        assertTrue(calendar.contains("case CALENDAR_EVENT -> openEditEventDialog"));
        assertTrue(calendar.contains("evt.isStillSincePress()"));
        assertTrue(calendar.contains("isEmbeddedAction(evt.getTarget(), card)"));
        assertTrue(calendar.contains("KeyCode.ENTER") && calendar.contains("KeyCode.SPACE"));
    }

    @Test void launcherReloadsAndValidatesStableAuthorityBeforeDisplayAndSave() throws Exception {
        String launcher = source("CaseDateOccurrenceEditorLauncher.java");
        assertTrue(launcher.contains("caseService.getCaseDate(caseDateId, captured.tenantId(), captured.actorId())"));
        assertTrue(launcher.contains("caseService.listEffectiveCaseDateTypes"));
        assertTrue(launcher.contains("date.id() == id") && launcher.contains("date.caseId() == context.caseId()"));
        assertTrue(launcher.contains("date.shaleClientId() == context.tenantId()"));
        assertTrue(launcher.contains("actual.valid()") && launcher.contains("expected.actorId() == actual.actorId()"));
        assertTrue(launcher.contains("opening.putIfAbsent(caseDateId, generation)"));
        assertTrue(launcher.contains("CaseDateOccurrenceDialog.show"));
    }

    @Test void updatePropagatesTitleRowVersionAndAllFieldsThroughOnlyCaseService() throws Exception {
        String launcher = source("CaseDateOccurrenceEditorLauncher.java");
        assertTrue(launcher.contains("caseService.updateCaseDate(new UpdateCaseDateCommand"));
        assertTrue(launcher.contains("input.caseDateTypeId(), input.title(), input.startsAt()"));
        assertTrue(launcher.contains("input.endsAt(), input.allDay(), input.notes(), existing.rowVer()"));
        assertFalse(launcher.contains("CalendarService"));
        assertFalse(launcher.contains("CalendarEvent"));
        assertFalse(launcher.contains("CaseDateId"));
    }

    @Test void successInvalidatesAndRefreshesWithoutMutatingCalendarPositionOrFilters() throws Exception {
        String calendar = source("CalendarController.java");
        assertTrue(calendar.contains("runtimeBridge.publishCaseDatesChanged"));
        assertTrue(calendar.contains("loadCurrentRange(false)"));
        String callback = calendar.substring(calendar.indexOf("private void caseDateSaved"), calendar.indexOf("private void openEditEventDialog"));
        assertFalse(callback.contains("selectedDate ="));
        assertFalse(callback.contains("selectedCaseId ="));
        assertFalse(callback.contains("selectedEventTypeKey ="));
        assertFalse(callback.contains("navigate"));
    }

    @Test void caseAndCalendarShareLauncherWhileNormalCaseDatesNavigationRemains() throws Exception {
        String calendar = source("CalendarController.java");
        String cases = source("CaseController.java");
        assertTrue(calendar.contains("new CaseDateOccurrenceEditorLauncher"));
        assertTrue(cases.contains("new CaseDateOccurrenceEditorLauncher"));
        assertTrue(cases.contains("showCaseDatesTab();"));
        assertTrue(cases.contains("caseDateOccurrenceEditorLauncher.open(caseId, caseDateId)"));
    }
}
