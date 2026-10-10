package com.shale.core.dto;

import com.shale.core.service.CaseReadException;

/** Additive read-only wire allowlist. Names remain sensitive; timestamps are stored local text. */
public record MinimizedCaseOverview(long caseId, String caseNumber, String caseName,
        CaseReadStatus status, CaseReadPracticeArea practiceArea, CaseReadUser responsibleAttorney,
        CaseReadUser primaryLegalAssistant, String updatedAt) {
    public MinimizedCaseOverview {
        positive(caseId);
        text(caseName, 255, false);
        text(caseNumber, 200, true);
    }
    public record CaseReadStatus(int id, String name, String color) {
        public CaseReadStatus { positive(id); text(name, 255, false); text(color, 7, true); }
    }
    public record CaseReadPracticeArea(int id, String name) {
        public CaseReadPracticeArea { positive(id); text(name, 255, false); }
    }
    public record CaseReadUser(int userId, String displayName) {
        public CaseReadUser { positive(userId); text(displayName, 255, false); }
    }
    private static void positive(long id) {
        if (id <= 0 || id > Integer.MAX_VALUE)
            throw new CaseReadException(CaseReadException.Kind.READ_UNAVAILABLE);
    }
    private static void text(String value, int maximum, boolean nullable) {
        if (value == null && !nullable)
            throw new CaseReadException(CaseReadException.Kind.READ_UNAVAILABLE);
        if (value != null && value.length() > maximum)
            throw new CaseReadException(CaseReadException.Kind.OVERSIZED);
    }
}
