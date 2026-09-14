package com.shale.ui.component;

import com.shale.ui.component.spellcheck.LocalSpellChecker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RichTextExpandedEditorTest {
    @Test void resolvesOnlyTheMisspelledWordAtThePointerCharacter() {
        LocalSpellChecker checker = new LocalSpellChecker(List.of("this", "bad", "works"));
        var plain = checker.misspellingRanges("This patint works");
        var punctuation = checker.misspellingRanges("This patint, works");
        var multiple = checker.misspellingRanges("bad patint tset");

        assertEquals(new LocalSpellChecker.Misspelling(5, 11, "patint"),
                RichTextExpandedEditor.misspellingAt(plain, 8));
        assertEquals(new LocalSpellChecker.Misspelling(5, 11, "patint"),
                RichTextExpandedEditor.misspellingAt(punctuation, 10));
        assertEquals(new LocalSpellChecker.Misspelling(4, 10, "patint"),
                RichTextExpandedEditor.misspellingAt(multiple, 7));
        assertNull(RichTextExpandedEditor.misspellingAt(plain, 11));
    }

    @Test void aRangeReplacementChangesOnlyTheTokenAndPreservesPunctuation() {
        String text = "The patint, was seen.";
        LocalSpellChecker.Misspelling hit = new LocalSpellChecker(List.of("the", "patient", "was", "seen"))
                .misspellingRanges(text).getFirst();

        String replaced = text.substring(0, hit.start()) + "patient" + text.substring(hit.end());

        assertEquals("The patient, was seen.", replaced);
    }

    @Test void ignoreAndCustomDictionaryRemoveContextMenuEligibility() {
        LocalSpellChecker checker = new LocalSpellChecker(List.of("patient"));
        checker.ignore("patint");
        checker.addToCustomDictionary("workng");

        assertNull(RichTextExpandedEditor.misspellingAt(checker.misspellingRanges("patint workng"), 2));
        assertNull(RichTextExpandedEditor.misspellingAt(checker.misspellingRanges("patint workng"), 9));
        assertNull(RichTextExpandedEditor.misspellingAt(checker.misspellingRanges("patient"), 2));
    }

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
