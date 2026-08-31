package com.shale.ui.component;

import com.shale.ui.component.spellcheck.LocalSpellChecker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RichTextExpandedEditorTest {
    @Test void spellingDecorationUpdatesWithinTheSameEditorSession() {
        RichTextExpandedEditor editor = new RichTextExpandedEditor(
                "This is a test", new LocalSpellChecker(List.of("this", "is", "a", "test")), true);

        assertFalse(editor.area().getStyleOfChar(10).contains("underline-color"));

        editor.area().replaceText("This is a tset");
        editor.refreshSpelling();
        assertTrue(editor.area().getStyleOfChar(10).contains("underline-color"));

        editor.area().replaceText("This is a test");
        editor.refreshSpelling();
        assertFalse(editor.area().getStyleOfChar(10).contains("underline-color"));
    }
}
