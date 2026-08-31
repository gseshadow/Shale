package com.shale.ui.component;

import javafx.beans.property.SimpleStringProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnhancedTextAreaTest {
    @Test void preservesPlainStringPropertyAndTextAreaCompatibility() {
        EnhancedTextArea area = new EnhancedTextArea();
        SimpleStringProperty model = new SimpleStringProperty("initial");
        area.textProperty().bindBidirectional(model);
        assertEquals("initial", area.getText());
        area.setText("plain <not-markup> text");
        assertEquals("plain <not-markup> text", model.get());
        area.setText("**persisted** narrative");
        assertEquals("**persisted** narrative", model.get(), "projection must never replace the persisted Markdown property");
    }

    @Test void titleIsConfigurableWithSensibleFallback() {
        EnhancedTextArea area = new EnhancedTextArea();
        assertEquals("Edit text", area.expandedDialogTitle());
        area.setEditorTitle("Summary");
        assertEquals("Edit Summary", area.expandedDialogTitle());
    }

    @Test void applyHonorsEditableDisabledAndExpandableState() {
        EnhancedTextArea area = new EnhancedTextArea();
        area.setText("original");
        ExpandedTextEdit edit = area.createExpandedEdit();
        edit.setDraft("applied");
        area.applyExpandedEdit(edit);
        assertEquals("applied", area.getText());

        area.setEditable(false);
        edit.setDraft("rejected");
        area.applyExpandedEdit(edit);
        assertEquals("applied", area.getText());
        assertFalse(area.canExpand());

        area.setEditable(true);
        area.setDisable(true);
        assertFalse(area.canExpand());
    }

    @Test void resolvesWordAtCaretForContextMenuSuggestions() {
        assertEquals("physcian", EnhancedTextArea.wordAt("the physcian spoke", 8).word());
        assertNull(EnhancedTextArea.wordAt("two words", 3));
    }
}
