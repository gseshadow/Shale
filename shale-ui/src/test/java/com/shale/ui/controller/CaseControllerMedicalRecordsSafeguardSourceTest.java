package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseControllerMedicalRecordsSafeguardSourceTest {
    @Test
    void caseUpdateSaveStillPersistsBeforeSafeguardPromptRuns() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        int save = source.indexOf("caseDao.addCaseNote(activeCaseId, activeClientId, trimmedText, createdByUserId)");
        int prompt = source.indexOf("handleMedicalRecordsRequestedSafeguardAfterSavedUpdate(activeCaseId, activeClientId, trimmedText)");

        assertTrue(save >= 0, "Existing case update save path should still call CaseDao.addCaseNote");
        assertTrue(prompt > save, "Medical-record safeguard should run only after the case update save succeeds");
    }

    @Test
    void confirmationUsesRequiredCopyAndRefreshesAfterYes() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        assertTrue(source.contains("\"Medical Records Requested\""));
        assertTrue(source.contains("This update appears to mention medical records. Would you like to mark Medical Records Requested as true?"));
        assertTrue(source.contains("DialogAction.of(\"Yes\", true"));
        assertTrue(source.contains("DialogAction.cancel(\"No\", false)"));
        assertTrue(source.contains("if (updated)"));
        assertTrue(source.contains("reloadCurrentCaseForViewMode();"));
    }
}
