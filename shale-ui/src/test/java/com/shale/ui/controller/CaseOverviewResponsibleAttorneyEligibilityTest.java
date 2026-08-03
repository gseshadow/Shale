package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CaseOverviewResponsibleAttorneyEligibilityTest {
    @Test
    void onlyResponsibleAttorneyUsesAttorneyCandidatesAndOtherEditorsRemainIsolated() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        String attorney = method(source, "private void onEditResponsibleAttorneyField");
        String assistant = method(source, "private void onEditPrimaryLegalAssistantField");
        String practiceArea = method(source, "private void onEditPracticeAreaField");
        String status = method(source, "private void onEditStatusField");

        assertTrue(attorney.contains("caseDao.listAttorneysForTenant(appState.getShaleClientId())"));
        assertTrue(attorney.contains("showUserCardChoice(\"Edit Responsible Attorney\""));
        assertTrue(attorney.contains("eligibleAttorneys, false"));
        assertTrue(attorney.contains("CaseDao.UserRow(currentOverview.getResponsibleAttorneyUserId()"),
                "persisted ineligible attorney must remain representable outside candidates");
        assertTrue(attorney.contains("saveResponsibleAttorneyField(v.id())"));
        assertFalse(attorney.contains("listUsersForTenant"));

        assertTrue(assistant.contains("caseDao.listUsersForTenant"));
        assertFalse(assistant.contains("listAttorneysForTenant"));
        assertTrue(practiceArea.contains("practiceAreasForTenantCached"));
        assertTrue(status.contains("statusesForTenantCached"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method: " + signature);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        fail("Unterminated method: " + signature);
        return "";
    }
}
