package com.shale.ui.controller;

import com.shale.ui.component.EnhancedTextArea;
import com.shale.ui.testutil.JavaFxTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ContactNarrativeEditorTest {
    @Test void contactNarrativeFieldsUseSharedTransactionalEditorContract() {
        JavaFxTestSupport.runAndWait(() -> {
            EnhancedTextArea condition = ContactViewController.contactNarrativeEditor("legacy plain text", "Condition", 4);
            EnhancedTextArea notes = ContactViewController.contactNarrativeEditor("original notes", "Contact Notes", 6);

            assertAll(
                    () -> assertEquals("legacy plain text", condition.getText()),
                    () -> assertEquals("Edit Condition", condition.expandedDialogTitle()),
                    () -> assertEquals(4, condition.getPrefRowCount()),
                    () -> assertTrue(condition.isSpellCheckEnabled()),
                    () -> assertTrue(condition.isExpandable()),
                    () -> assertEquals("Edit Contact Notes", notes.expandedDialogTitle()),
                    () -> assertEquals(6, notes.getPrefRowCount()));

            var appliedDraft = condition.createExpandedEdit();
            appliedDraft.setDraft("**formatted** condition");
            condition.applyExpandedEdit(appliedDraft);
            assertEquals("**formatted** condition", condition.getText(), "popup Apply updates the form draft");

            var cancelledDraft = notes.createExpandedEdit();
            cancelledDraft.setDraft("discarded notes");
            assertEquals("original notes", notes.getText(), "popup Cancel/close leaves the form draft unchanged");
        });
    }
}
