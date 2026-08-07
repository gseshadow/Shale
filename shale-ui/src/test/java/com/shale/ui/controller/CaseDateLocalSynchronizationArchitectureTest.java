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

    @Test void actualOverviewButtonsRouteToAggregateAndInitializeFromAuthoritativeSnapshot() throws Exception {
        String source = Files.readString(CONTROLLER);
        String handlers = source.substring(source.indexOf("private void onEditIncidentDateField"),
                source.indexOf("private void showTextFieldDialog"));
        assertTrue(handlers.contains("authoritativeDate(MigratedCaseDateKey.DATE_OF_INJURY)"));
        assertTrue(handlers.contains("saveAuthoritativeDate(MigratedCaseDateKey.DATE_OF_INJURY, value)"));
        assertTrue(handlers.contains("saveAuthoritativeDate(MigratedCaseDateKey.DATE_OF_MEDICAL_NEGLIGENCE, value)"));
        assertTrue(handlers.contains("saveAuthoritativeDate(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS, value)"));
        assertTrue(handlers.contains("saveAuthoritativeDate(MigratedCaseDateKey.TORT_NOTICE_DEADLINE, value)"));
        assertFalse(handlers.contains("saveCoreOverviewField"));
        assertFalse(handlers.contains("saveDetailDateOverviewField"));
        assertFalse(handlers.contains("currentOverview.getIncidentDate"));
        assertFalse(handlers.contains("current.getDateOfMedicalNegligence"));
    }

    @Test void generalHydrationCannotOverwriteAnyFixedDateControl() throws Exception {
        String source = Files.readString(CONTROLLER);
        String renderer = source.substring(source.indexOf("private final class CaseOverviewRenderer"),
                source.indexOf("private final class CaseOverviewSaveCoordinator"));
        assertFalse(renderer.contains("getDateOfMedicalNegligence()"));
        String details = source.substring(source.indexOf("private final class CaseDetailsEditor"));
        for (String field : new String[] {"detCallerDateValue", "detDateOfMedicalNegligenceValue",
                "detDateMedicalNegligenceWasDiscoveredValue", "detDateOfInjuryValue",
                "detStatuteOfLimitationsValue", "detTortNoticeDeadlineValue", "detDiscoveryDeadlineValue",
                "detDateFeeAgreementSignedValue", "detDateNonEngagementLetterSentValue"}) {
            assertFalse(details.contains(field + ".setText"), field);
        }
        assertTrue(source.contains("renderCompatibilityDates()"));
    }

    @Test void unrelatedDesktopSavesUseNonMigratedDaoBoundaries() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertTrue(source.contains("caseDao.updateCaseNonDate("));
        assertTrue(source.contains("caseDao.updateCaseDetailsNonMigrated("));
        assertFalse(source.contains("caseDao.updateCase("));
        assertFalse(source.contains("caseDao.updateCaseDetails("));
        String detailsEditor = source.substring(source.indexOf("private final class CaseDetailsEditor"));
        assertFalse(detailsEditor.contains("LocalDate.now()"), "workflow flags must not fabricate fixed dates");
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
