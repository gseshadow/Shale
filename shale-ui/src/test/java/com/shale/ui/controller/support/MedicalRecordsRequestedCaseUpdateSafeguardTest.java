package com.shale.ui.controller.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.shale.core.caseupdates.MedicalRecordRequestKeywordMatcher;

final class MedicalRecordsRequestedCaseUpdateSafeguardTest {
    @Test
    void noPromptOrUpdateOccursWhenMedicalRecordsRequestedAlreadyTrue() {
        AtomicInteger prompts = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();
        MedicalRecordsRequestedCaseUpdateSafeguard safeguard = newSafeguard(prompts, true, updates);

        assertFalse(safeguard.handleSavedCaseUpdate(7, 42, "medical records requested", true));

        assertEquals(0, prompts.get());
        assertEquals(0, updates.get());
    }

    @Test
    void matchingUpdateWithFalseMedicalRecordsRequestedReachesConfirmationPath() {
        AtomicInteger prompts = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();
        MedicalRecordsRequestedCaseUpdateSafeguard safeguard = newSafeguard(prompts, false, updates);

        assertFalse(safeguard.handleSavedCaseUpdate(7, 42, "ordered records", false));

        assertEquals(1, prompts.get());
        assertEquals(0, updates.get());
    }

    @Test
    void yesUpdatesMedicalRecordsRequestedToTrue() {
        AtomicInteger prompts = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();
        AtomicBoolean receivedCase = new AtomicBoolean();
        MedicalRecordsRequestedCaseUpdateSafeguard safeguard = new MedicalRecordsRequestedCaseUpdateSafeguard(
                new MedicalRecordRequestKeywordMatcher(),
                () -> {
                    prompts.incrementAndGet();
                    return true;
                },
                (caseId, shaleClientId) -> {
                    receivedCase.set(caseId == 7 && shaleClientId == 42);
                    updates.incrementAndGet();
                });

        assertTrue(safeguard.handleSavedCaseUpdate(7, 42, "requested records", false));

        assertEquals(1, prompts.get());
        assertEquals(1, updates.get());
        assertTrue(receivedCase.get());
    }

    @Test
    void noLeavesMedicalRecordsRequestedFalse() {
        AtomicInteger prompts = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();
        MedicalRecordsRequestedCaseUpdateSafeguard safeguard = newSafeguard(prompts, false, updates);

        assertFalse(safeguard.handleSavedCaseUpdate(7, 42, "release of information sent", false));

        assertEquals(1, prompts.get());
        assertEquals(0, updates.get());
    }

    @Test
    void nonMatchingSavedCaseUpdateDoesNotPromptAndStillCompletes() {
        AtomicInteger prompts = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();
        MedicalRecordsRequestedCaseUpdateSafeguard safeguard = newSafeguard(prompts, true, updates);

        assertFalse(safeguard.handleSavedCaseUpdate(7, 42, "Called client about appointment.", false));

        assertEquals(0, prompts.get());
        assertEquals(0, updates.get());
    }

    private static MedicalRecordsRequestedCaseUpdateSafeguard newSafeguard(
            AtomicInteger prompts,
            boolean confirmationResult,
            AtomicInteger updates) {
        return new MedicalRecordsRequestedCaseUpdateSafeguard(
                new MedicalRecordRequestKeywordMatcher(),
                () -> {
                    prompts.incrementAndGet();
                    return confirmationResult;
                },
                (caseId, shaleClientId) -> updates.incrementAndGet());
    }
}
