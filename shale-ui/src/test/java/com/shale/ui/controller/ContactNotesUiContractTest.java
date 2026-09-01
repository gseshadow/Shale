package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class ContactNotesUiContractTest {
    @Test void notesEditorAndBottomViewPreserveMultilinePresentation() throws Exception {
        String controller=Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactViewController.java"));
        String fxml=Files.readString(Path.of("src/main/resources/fxml/contact.fxml"));
        assertTrue(controller.contains("new TextArea(safe(p.notes()))"));
        assertTrue(controller.contains("notes.setWrapText(true)"));
        assertTrue(controller.contains("notes.getText().length()>ContactServicePort.CONTACT_NOTES_MAX_CHARS"));
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
