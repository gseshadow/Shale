package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.shale.core.model.MigratedCaseDateKey;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseDateLocalSynchronizationArchitectureTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path DIALOG = Path.of("src/main/java/com/shale/ui/component/dialog/CaseDateOccurrenceDialog.java");

    @Test void everySharedMappingIsRenderedAndSavedThroughTheAuthoritativeContract() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertEquals(9, MigratedCaseDateKey.values().length);
        for (MigratedCaseDateKey key : MigratedCaseDateKey.values()) {
            assertTrue(source.contains("MigratedCaseDateKey." + key.name()), key.name());
        }
        assertTrue(source.contains("MigratedCaseDateKey.require(systemKey)"));
        assertFalse(source.contains("SystemKeys"));
    }

    @Test void fixedSuccessInvalidatesDatesWithoutIssuingAnotherMutation() throws Exception {
        String source = Files.readString(CONTROLLER);
        String save = source.substring(source.indexOf("private void saveAuthoritativeDate"),
                source.indexOf("private void refreshOverviewAndDetailsAfterStructuralPatchAsync"));
        assertTrue(save.contains("compatibilityDates.replace(result)"));
        assertTrue(save.contains("synchronizeCaseDatesAfterLocalMutation(activeCaseId, false)"));
        assertEquals(1, occurrences(save, "mutateMigratedCompatibilityDates(command)"));
        assertFalse(save.contains("loadMigratedCompatibilityDateSnapshot"));
    }

    @Test void allGenericMutationsReloadACompleteSnapshotOnlyForMappedTypes() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertTrue(source.contains("compatibilityAffected = isMigratedCaseDateType(input.caseDateTypeId())"));
        assertTrue(source.contains("existing != null && isMigratedCaseDateSystemKey(existing.typeSystemKey())"));
        assertEquals(2, occurrences(source, "isMigratedCaseDateSystemKey(d.typeSystemKey())"), "remove and restore");
        String sync = source.substring(source.indexOf("private void synchronizeCaseDatesAfterLocalMutation"),
                source.indexOf("private void renderCompatibilityDates"));
        assertTrue(sync.contains("loadCompatibilityDatesAsync(activeCaseId)"));
        assertFalse(sync.contains("compatibilityDates.replace"), "generic mutations must not patch visible values/tokens");
    }

    @Test void serviceWorkIsOffFxAndResponsesHaveOrderingAndCaseGuards() throws Exception {
        String source = Files.readString(CONTROLLER);
        String dialog = Files.readString(DIALOG);
        assertTrue(source.contains("CompletableFuture.supplyAsync"));
        assertTrue(source.contains("caseDateExecutor"));
        assertTrue(dialog.contains("whenComplete"));
        assertTrue(dialog.contains("Platform.runLater"));
        assertTrue(source.contains("isCompatibilityDatesCurrent(activeCaseId, generation)"));
        assertTrue(source.contains("final int generation = ++compatibilityDatesGeneration"));
        assertTrue(source.contains("caseId.longValue() != activeCaseId"));
        assertTrue(source.contains("overviewScrollPane.getScene() != null"));
    }

    @Test void synchronizationDoesNotUseLegacyWritersOrWorkflowFabrication() throws Exception {
        String source = Files.readString(CONTROLLER);
        String syncRegion = source.substring(source.indexOf("private void synchronizeCaseDatesAfterLocalMutation"),
                source.indexOf("private void refreshOverviewAndDetailsAfterStructuralPatchAsync"));
        assertFalse(syncRegion.contains("updateCase("));
        assertFalse(syncRegion.contains("updateCaseDetails("));
        assertFalse(syncRegion.contains("LocalDate.now"));
        assertFalse(syncRegion.contains("FeeAgreementSigned.setSelected"));
        assertFalse(syncRegion.contains("NonEngagementLetterSent.setSelected"));
    }

    private static int occurrences(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }
}
