package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseDetailsTimelineCoverageTest {
    private static final String SOURCE = read();

    @Test
    void everyNonDateDetailsFieldHasOneExistingTimelineMappingIncludingPreviouslyMissingNonEngagementFlag() {
        String worker = between("private void runSaveWorker(DetailsSaveRequest request)", "private void handleSaveResult");
        for (String event : new String[]{"ACCEPTED_DATE_CHANGED", "CLOSED_DATE_CHANGED", "DENIED_DATE_CHANGED",
                "ESTATE_CASE_CHANGED", "MEDICAL_RECORDS_REQUESTED_CHANGED", "FEE_AGREEMENT_SIGNED_CHANGED",
                "NON_ENGAGEMENT_LETTER_SENT_CHANGED", "ACCEPTED_CHRONOLOGY_CHANGED",
                "CONSULTANT_EXPERT_SEARCH_CHANGED", "TESTIFYING_EXPERT_SEARCH_CHANGED",
                "MEDICAL_LITERATURE_CHANGED", "DENIED_CHRONOLOGY_CHANGED", "RECEIVED_UPDATES_CHANGED",
                "CASE_NAME_CHANGED", "CASE_NUMBER_CHANGED", "OFFICE_CASE_CODE_CHANGED",
                "SUMMARY_UPDATED", "ACCEPTED_DETAIL_UPDATED", "DENIED_DETAIL_UPDATED"}) {
            assertEquals(1, count(worker, "CaseDao.CaseTimelineEventTypes." + event), event + " must have one mapping");
        }
        assertEquals(1, count(worker, "addDescriptionChangedTimelineEvent("));
        assertEquals(1, count(worker, "addPracticeAreaChangedTimelineEvent("));
    }

    @Test
    void overviewDoesNotDuplicateAuthoritativeCaseDateEvents() {
        String worker = between("private void runSaveWorker(SaveRequest request)", "private Integer resolveSavedPrimaryStatusId");
        assertFalse(worker.contains("CaseTimelineEventTypes.INCIDENT_DATE_CHANGED"));
        assertFalse(worker.contains("CaseTimelineEventTypes.SOL_DATE_CHANGED"));
        assertFalse(worker.contains("CaseTimelineEventTypes.TORT_NOTICE_DEADLINE_CHANGED"));
        assertTrue(worker.contains("Authoritative Case Date mutations append their own single transaction-bound event"));
    }

    @Test
    void manualCaseUpdatesRemainIndependentFromTimeline() {
        String updates = between("private final class CaseUpdatesPanelController", "private enum CaseUpdatesPlacement");
        assertFalse(updates.contains("addCaseTimelineEvent"));
    }

    private static String between(String start, String end) {
        int from = SOURCE.indexOf(start);
        int to = SOURCE.indexOf(end, from + start.length());
        return SOURCE.substring(from, to);
    }

    private static int count(String value, String needle) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }

    private static String read() {
        try { return Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java")); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
