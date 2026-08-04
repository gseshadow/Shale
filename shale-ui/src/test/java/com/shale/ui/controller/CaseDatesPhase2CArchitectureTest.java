package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CaseDatesPhase2CArchitectureTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path FXML = Path.of("src/main/resources/fxml/case.fxml");
    private static final Path DIALOG = Path.of("src/main/java/com/shale/ui/component/dialog/CaseDateOccurrenceDialog.java");

    @Test void datesSectionUsesEstablishedCaseViewSectionArchitecture() throws Exception {
        String controller = Files.readString(CONTROLLER);
        String fxml = Files.readString(FXML);
        assertTrue(CaseController.sectionOrderForTesting().contains("Dates"));
        assertEquals("Overview", CaseController.sectionOrderForTesting().get(0));
        assertTrue(fxml.contains("fx:id=\"caseDatesTabPane\""));
        assertTrue(controller.contains("case \"Dates\" -> showCaseDatesTab()"));
        assertTrue(controller.contains("caseDatesTabPane, caseRequestsTabPane"));
        assertTrue(controller.contains("setPaneVisible(root, root == activeRoot)"));
    }

    @Test void asyncLoadingIsGuardedAndUsesServiceLayerOnly() throws Exception {
        String controller = Files.readString(CONTROLLER);
        assertTrue(controller.contains("caseDateExecutor.submit"));
        assertTrue(controller.contains("++caseDatesLoadGeneration"));
        assertTrue(controller.contains("isCaseDatesCurrent(activeCaseId, generation)"));
        assertTrue(controller.contains("caseDatesTabPane.getScene() != null"));
        assertTrue(controller.contains("caseService.listCaseDatesForCase"));
        assertTrue(controller.contains("caseService.listDeletedCaseDatesForCase"));
        assertTrue(controller.contains("logCaseDatesLoadFailure(operation"));
        assertTrue(controller.contains("LOG.error(\"Case dates load failed operation={} tenantId={} actorId={} caseId={} generation={} elapsedMs={}\""));
        assertTrue(controller.contains(", ex);"), "load failures must log the throwable object for a full stack trace");
        assertTrue(controller.contains("renderCaseDatesFailure()"));
        assertTrue(controller.contains("ActionButtonFactory.semantic(\"Retry\", e -> loadCaseDatesAsync()"));
        assertFalse(controller.contains("new CaseDateDao"));
    }

    @Test void occurrenceMutationsCarryActorCaseAndRowVersion() throws Exception {
        String controller = Files.readString(CONTROLLER);
        assertTrue(controller.contains("new CreateCaseDateCommand(tenantId, actorId, caseId"));
        assertTrue(controller.contains("new UpdateCaseDateCommand(tenantId, actorId, caseId, existing.id()"));
        assertTrue(controller.contains("existing.rowVer()"));
        assertTrue(controller.contains("new DeleteCaseDateCommand(tenantId, actorId, caseId, d.id(), d.rowVer())"));
        assertTrue(controller.contains("new RestoreCaseDateCommand(tenantId, actorId, caseId, d.id(), d.rowVer())"));
        assertFalse(controller.contains("DELETE FROM dbo.CaseDates"));
        String datesBlock = controller.substring(controller.indexOf("private void configureCaseDatesControls"), controller.indexOf("private void resetCaseLinksState"));
        assertFalse(datesBlock.contains("CalendarEvents"));
        assertFalse(datesBlock.contains("d.notes()"), "calendar/date-section diagnostics must not write occurrence notes to logs");
    }

    @Test void dialogUsesSharedControlsAndPreservesLocalDateTimeSemantics() throws Exception {
        String dialog = Files.readString(DIALOG);
        assertTrue(dialog.contains("ColorCodedComboBox<TypeChoice>"));
        assertTrue(dialog.contains("ControlStyles.formControl"));
        assertTrue(dialog.contains("ActionButtonFactory.semantic"));
        assertTrue(dialog.contains("LocalDateTime.of(sd.getValue(), parseTime"));
        assertTrue(dialog.contains("sd.getValue().atStartOfDay()"));
        assertTrue(dialog.contains("End must not be before start"));
        assertFalse(dialog.contains("ZoneId"));
        assertFalse(dialog.contains("Instant"));
        assertFalse(dialog.contains("23:59"));
        assertFalse(dialog.contains("plusDays"));
        assertFalse(dialog.contains("minusDays"));
    }
}
