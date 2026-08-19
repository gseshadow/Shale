package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseDateOccurrenceRemovalContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/ui/" + path));
    }

    @Test void removeIsEditOnlyConfirmedAndSharesTheMutationGuard() throws Exception {
        String dialog = source("component/dialog/CaseDateOccurrenceDialog.java");
        assertTrue(dialog.contains("existing == null ? null : ActionButtonFactory.semantic(\"Remove\""));
        assertTrue(dialog.contains("ControlStyles.Purpose.DANGER"));
        assertTrue(dialog.contains("AppDialogs.showConfirmation(stage, \"Remove Date\""));
        assertTrue(dialog.contains("off active Calendar and Case views"));
        assertTrue(dialog.contains("submitting.getAndSet(true)"));
        assertTrue(dialog.contains("setMutationControlsDisabled(true, save, remove, cancel, reload, typeBox)"));
    }

    @Test void cancellationAndFailuresRetainTheEditorWhileSuccessClosesIt() throws Exception {
        String dialog = source("component/dialog/CaseDateOccurrenceDialog.java");
        assertTrue(dialog.contains("|| submitting.getAndSet(true)) return"));
        assertTrue(dialog.contains("if (displayed == null || displayed.isBlank()) stage.close()"));
        assertTrue(dialog.contains("showError(error, displayed)"));
        assertTrue(dialog.contains("reload.setVisible(true)"));
    }

    @Test void launcherRoutesOnlyAuthoritativeLoadedIdentityAndRowVersion() throws Exception {
        String launcher = source("controller/CaseDateOccurrenceEditorLauncher.java");
        assertTrue(launcher.contains("caseService.deleteCaseDate(new DeleteCaseDateCommand(captured.tenantId(), captured.actorId(),"));
        assertTrue(launcher.contains("captured.caseId(), existing.id(), existing.rowVer()"));
        assertTrue(launcher.contains("sameContext(captured, now)") && launcher.contains("matches(existing, captured, existing.id())"));
        assertFalse(launcher.contains("CalendarService"));
        assertFalse(launcher.contains("CalendarEvents.CaseDateId"));
    }

    @Test void eachSourcePublishesRemovalAndRefreshesItsExistingSurface() throws Exception {
        String calendar = source("controller/CalendarController.java");
        String cases = source("controller/CaseController.java");
        assertTrue(calendar.contains("result.removed() ? LiveUpdateEvents.CHANGE_REMOVED"));
        assertTrue(calendar.substring(calendar.indexOf("private void caseDateSaved"), calendar.indexOf("private static boolean isEmbeddedAction")).contains("loadCurrentRange(false)"));
        assertTrue(cases.contains("result.removed() ? LiveUpdateEvents.CHANGE_REMOVED"));
        assertTrue(cases.contains("loadCaseDatesAsync();"));
    }
}
