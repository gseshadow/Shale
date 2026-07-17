package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinkSharesPhase533ContactLoadingUiTest {
    private static final Path CASE_CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");

    @Test
    void shareModalLoadsCaseAndAllContactsIndependently() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);
        String modal = source.substring(source.indexOf("private Optional<List<StagedShare>> showShareSelectionDialog"),
                source.indexOf("private void toggleWorking", source.indexOf("private Optional<List<StagedShare>> showShareSelectionDialog")));

        assertTrue(modal.contains("op=list-case-contacts"));
        assertTrue(modal.contains("op=list-all-contacts"));
        assertTrue(modal.contains("listCaseLinkShareCaseContacts(caseIdSnapshot, tenantId)"));
        assertTrue(modal.contains("listCaseLinkShareContacts(tenantId)"));
        assertFalse(modal.contains("searchCaseLinkShareContacts(tenantId, \"\", 100)"));
        assertTrue(modal.contains("Unable to load Case Contacts."));
        assertTrue(modal.contains("Unable to load All Contacts."));
    }

    @Test
    void shareModalUsesLocalFilteringAndDistinctEmptyStates() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);
        String modal = source.substring(source.indexOf("private Optional<List<StagedShare>> showShareSelectionDialog"),
                source.indexOf("private void toggleWorking", source.indexOf("private Optional<List<StagedShare>> showShareSelectionDialog")));

        assertTrue(modal.contains("new javafx.collections.transformation.FilteredList<>(allOptions"));
        assertTrue(modal.contains("safeText(o.displayName()).toLowerCase(Locale.ROOT).contains(q)"));
        assertTrue(modal.contains("Loading Case Contacts..."));
        assertTrue(modal.contains("No available Case Contacts."));
        assertTrue(modal.contains("Loading Contacts..."));
        assertTrue(modal.contains("No available Contacts."));
        assertTrue(modal.contains("No Contacts match this search."));
    }
}
