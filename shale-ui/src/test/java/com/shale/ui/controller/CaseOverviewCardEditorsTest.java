package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseOverviewCardEditorsTest {
    private static final Path SOURCE = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");

    @Test
    void fourOverviewEditorsReuseSharedSelectionFieldAndMiniFactoriesByIdentity() throws Exception {
        String source = Files.readString(SOURCE);
        String editors = source.substring(source.indexOf("private void onEditStatusField()"),
                source.indexOf("private boolean ensureTenantAndCaseForFieldDialog"));

        assertTrue(editors.contains("UserSelectionField<T> selector"));
        assertFalse(editors.contains("ChoiceBox<String>"));
        assertTrue(editors.contains("PracticeAreaCardFactory.Variant.MINI"));
        assertTrue(editors.contains("StatusCardFactory.Variant.MINI"));
        assertTrue(editors.contains("Variant.MINI"));
        assertTrue(editors.contains("CaseDao.StatusRow::id"));
        assertTrue(editors.contains("CaseDao.PracticeAreaRow::id"));
        assertTrue(editors.contains("CaseDao.UserRow::id"));
        assertTrue(editors.contains("caseDao.listAttorneysForTenant"));
        assertTrue(editors.contains("caseDao.listUsersForTenant"));
        assertTrue(editors.contains("ControlStyles.Purpose.PRIMARY"));
        assertTrue(editors.contains("ControlStyles.Purpose.SECONDARY"));
        assertTrue(editors.contains("ControlStyles.Purpose.GHOST"));
        assertTrue(editors.contains("this::removePrimaryLegalAssistantField"));
        assertTrue(editors.contains("picker.setResultConverter(buttonType -> null)"),
                "picker shell dismissal must never leak ButtonType as a typed selection");
    }

    @Test
    void currentPersistedValuesAreSynthesizedWhenFilteredFromCandidates() throws Exception {
        String source = Files.readString(SOURCE);
        String editors = source.substring(source.indexOf("private void onEditStatusField()"),
                source.indexOf("private boolean ensureTenantAndCaseForFieldDialog"));
        assertTrue(editors.contains("orElse(new CaseDao.StatusRow"));
        assertTrue(editors.contains("orElse(new CaseDao.PracticeAreaRow"));
        assertTrue(editors.contains("orElse(new CaseDao.UserRow"));
        assertFalse(editors.contains("select(0)"), "a missing persisted value must not select the first option");
    }
}
