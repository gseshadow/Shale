package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class NewIntakeConfiguredDatesStep3UiContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/ui/controller/NewIntakeController.java"));
    }

    @Test void submitsStableConfiguredInputIdentityAndDefensiveConfigurationToken() throws Exception {
        String s=source();
        assertTrue(s.contains("configuredDateInputs.values().stream().map(input -> new CaseDao.ConfiguredDateValue("));
        assertTrue(s.contains("input.fieldKey(), input.caseDateTypeId(), input.required(), input.value()"));
        assertTrue(s.contains("configurationRowVer.clone()"));
        assertFalse(s.contains("field.type().name(), input.value()"));
    }

    @Test void failedSubmissionKeepsControlsAndStaleSubmissionRequiresReload() throws Exception {
        String s=source();
        String failure=s.substring(s.indexOf("private void handleCreateFailure"), s.indexOf("private boolean shouldBlockCreateForOfflinePreflight"));
        assertFalse(failure.contains("clearForm"));
        assertFalse(failure.contains("configuredDateInputs.clear"));
        assertTrue(failure.contains("datesReloadRequired = true"));
        assertTrue(s.contains("if (datesReloadRequired)"));
        assertTrue(s.contains("Reload the form before submitting again."));
    }

    @Test void customizationUsesSharedRequiredControlAndCancelDiscardsDraft() throws Exception {
        String s=source();
        assertTrue(s.contains("ControlStyles.formControl(new CheckBox(\"Required\"))"));
        assertTrue(s.contains("NewIntakeDatesConfiguration.withRequired(selection, newValue)"));
        assertTrue(s.contains("new Selection(selector.getValue(), false)"));
        assertTrue(s.contains("private void cancelDatesCustomization()"));
        assertTrue(s.contains("stagedDateSelections.clear();"));
    }

    @Test void requiredDatesAreMarkedValidatedAndFocusedBeforeCreate() throws Exception {
        String s=source();
        assertTrue(s.contains("field.type().name() + (field.required() ? \" *\" : \"\")"));
        assertTrue(s.contains("ControlStyles.setInvalid(input.input(), true)"));
        assertTrue(s.contains("focusFirstMissingConfiguredDate();"));
        assertTrue(s.contains("input.input().requestFocus()"));
        assertTrue(s.indexOf("List<String> errors = validateRequiredFields();") < s.indexOf("setSaving(true);"));
    }

    @Test void publishesOnePhiSafeCaseDatesInvalidationOnlyFromCommittedSuccess() throws Exception {
        String s=source();
        String success=s.substring(s.indexOf("private void handleCreateSuccess"), s.indexOf("private void handleCreateFailure"));
        String failure=s.substring(s.indexOf("private void handleCreateFailure"), s.indexOf("private boolean shouldBlockCreateForOfflinePreflight"));
        assertTrue(success.contains("result.createdCaseDateCount() > 0"));
        assertTrue(success.contains("runtimeBridge.publishCaseDatesChanged(result.caseId(), tenantId, appState.getUserId()"));
        assertTrue(success.contains("LiveUpdateEvents.CHANGE_CREATED"));
        assertFalse(success.contains("input.value()"));
        assertFalse(failure.contains("publishCaseDatesChanged"));
        assertEquals(1, s.split("publishCaseDatesChanged\\(", -1).length - 1);
    }
}
