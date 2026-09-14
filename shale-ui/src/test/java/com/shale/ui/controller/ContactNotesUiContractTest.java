package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class ContactNotesUiContractTest {
    @Test void notesEditorAndBottomViewPreserveMultilinePresentation() throws Exception {
        String controller=Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactViewController.java"));
        String fxml=Files.readString(Path.of("src/main/resources/fxml/contact.fxml"));
        assertTrue(controller.contains("EnhancedTextArea condition=contactNarrativeEditor(p.condition(),\"Condition\",4)"));
        assertTrue(controller.contains("EnhancedTextArea notes=contactNarrativeEditor(p.notes(),\"Contact Notes\",6)"));
        assertTrue(controller.contains("editor.setSpellCheckEnabled(true)"));
        assertTrue(controller.contains("editor.setExpandable(true)"));
        assertTrue(controller.contains("notes.getText().length()>ContactServicePort.CONTACT_NOTES_MAX_CHARS"));
        assertTrue(controller.contains("birth.getValue(),condition.getText(),notes.getText(),deceased.isSelected()"));
        assertTrue(controller.contains("NarrativeMarkdownCodec.plainText(fallback(condition))"));
        assertTrue(controller.contains("NarrativeMarkdownCodec.plainText(profile.notes())"));
        assertTrue(controller.contains("Your values are retained"));
        assertTrue(controller.contains("classificationProfile=result.profile()"));
        assertTrue(fxml.contains("fx:id=\"notesSection\""));
        assertTrue(fxml.contains("fx:id=\"notesValue\""));
        assertTrue(fxml.contains("text=\"No notes provided.\""));
        assertTrue(fxml.contains("wrapText=\"true\""));
        assertFalse(fxml.contains("fx:id=\"notesValue\" editorTitle="));
        assertTrue(fxml.indexOf("fx:id=\"notesSection\"") > fxml.indexOf("fx:id=\"sharedLinksContainer\""));
    }
}
