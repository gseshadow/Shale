package com.shale.ui.component;

import com.shale.ui.component.spellcheck.LocalSpellChecker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichTextExpandedEditorTest {
    @Test void spellingRefreshDoesNotConsumeOrClearRedoHistory() {
        RichTextExpandedEditor editor = new RichTextExpandedEditor(
                "", new LocalSpellChecker(List.of("valid")), true);

        editor.area().replaceText("asdf");
        editor.refreshSpelling();
        editor.area().undo();
        assertEquals("", editor.area().getText());

        editor.refreshSpelling();
        assertTrue(editor.area().isRedoAvailable());
        editor.area().redo();
        assertEquals("asdf", editor.area().getText());
    }
}
