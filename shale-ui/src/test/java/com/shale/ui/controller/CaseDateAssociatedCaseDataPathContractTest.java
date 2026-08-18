package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseDateAssociatedCaseDataPathContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/ui/" + relative));
    }

    @Test void sharedDialogReusesEstablishedMiniFactoryBeforeEveryOccurrenceField() throws Exception {
        String dialog = source("component/dialog/CaseDateOccurrenceDialog.java");
        assertTrue(dialog.contains("new CaseCardFactory(id -> {}).create(associatedCase, CaseCardFactory.Variant.MINI)"));
        assertTrue(dialog.contains("new VBox(12, caseSection, grid, error, footer)"));
        assertFalse(dialog.contains("ComboBox<Case"));
        assertFalse(dialog.contains("ChoiceBox<Case"));
    }

    @Test void caseDatesReuseAlreadyLoadedAuthoritativeOverviewForNewAndEdit() throws Exception {
        String controller = source("controller/CaseController.java");
        assertTrue(controller.contains("currentOverview.getCaseId() != caseId"));
        assertTrue(controller.contains("CaseDateOccurrenceEditorLauncher.toCaseCardModel(currentOverview)"));
        assertTrue(controller.contains("existing == null ? \"Add Date\" : \"Edit Date\""));
        assertFalse(controller.contains("listActiveForCalendar"));
    }

    @Test void calendarLoadsOnlyExactStableCaseAndRejectsMismatchesAndStaleResults() throws Exception {
        String launcher = source("controller/CaseDateOccurrenceEditorLauncher.java");
        assertTrue(launcher.contains("caseService.getCaseOverview(captured.caseId(), captured.tenantId())"));
        assertTrue(launcher.contains("caseOverview.get().getCaseId() != captured.caseId()"));
        assertTrue(launcher.contains("captured.valid()") && launcher.contains("expected.actorId() == actual.actorId()"));
        assertTrue(launcher.contains("isCurrent(captured, caseDateId, generation)"));
        assertTrue(launcher.contains("date.shaleClientId() == context.tenantId()"));
        assertFalse(launcher.contains("listActiveForCalendar"));
        assertFalse(launcher.contains("CaseSummaryDao"));
        assertFalse(launcher.contains("CaseDao"));
    }

    @Test void cardProjectionPreservesStableIdAndPresentationWithoutSensitiveLogging() throws Exception {
        String launcher = source("controller/CaseDateOccurrenceEditorLauncher.java");
        assertTrue(launcher.contains("new CaseCardModel(overview.getCaseId(), overview.getCaseName()"));
        assertTrue(launcher.contains("overview.getCaseStatus()"));
        assertFalse(launcher.contains("LOG."));
    }
}
