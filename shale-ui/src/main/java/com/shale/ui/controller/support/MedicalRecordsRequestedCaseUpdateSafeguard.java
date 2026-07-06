package com.shale.ui.controller.support;

import java.util.Objects;

import com.shale.core.caseupdates.MedicalRecordRequestKeywordMatcher;

/**
 * Coordinates the always-on desktop prompt shown after a saved case update appears to request medical records.
 */
public final class MedicalRecordsRequestedCaseUpdateSafeguard {
    private final MedicalRecordRequestKeywordMatcher matcher;
    private final Confirmation confirmation;
    private final MedicalRecordsUpdater updater;

    public MedicalRecordsRequestedCaseUpdateSafeguard(
            MedicalRecordRequestKeywordMatcher matcher,
            Confirmation confirmation,
            MedicalRecordsUpdater updater) {
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.confirmation = Objects.requireNonNull(confirmation, "confirmation");
        this.updater = Objects.requireNonNull(updater, "updater");
    }

    public boolean handleSavedCaseUpdate(long caseId, int shaleClientId, String savedNoteText, boolean medicalRecordsRequested) {
        if (medicalRecordsRequested || !matcher.matches(savedNoteText)) {
            return false;
        }
        if (!confirmation.confirmMarkRequested()) {
            return false;
        }
        updater.markMedicalRecordsRequested(caseId, shaleClientId);
        return true;
    }

    @FunctionalInterface
    public interface Confirmation {
        boolean confirmMarkRequested();
    }

    @FunctionalInterface
    public interface MedicalRecordsUpdater {
        void markMedicalRecordsRequested(long caseId, int shaleClientId);
    }
}
