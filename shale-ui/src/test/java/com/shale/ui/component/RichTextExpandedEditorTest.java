package com.shale.ui.component;

import com.shale.ui.component.spellcheck.LocalSpellChecker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichTextExpandedEditorTest {
    @Test void defaultRichTextManagerUndoesAndRedoesTextAndFormatting() {
        RichTextExpandedEditor editor = new RichTextExpandedEditor(
                "abc", new LocalSpellChecker(List.of("abc", "d")), false);

        editor.area().appendText("d");
        editor.area().undo();
        assertEquals("abc", editor.area().getText());
        editor.area().redo();
        assertEquals("abcd", editor.area().getText());

        editor.area().setStyle(0, 3, "-fx-font-weight: bold;");
        editor.area().undo();
        assertFalse(editor.area().getStyleOfChar(0).contains("font-weight: bold"));
        editor.area().redo();
        assertTrue(editor.area().getStyleOfChar(0).contains("font-weight: bold"));
    }

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
