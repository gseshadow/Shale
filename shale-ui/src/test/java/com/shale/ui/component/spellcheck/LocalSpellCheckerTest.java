package com.shale.ui.component.spellcheck;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LocalSpellCheckerTest {
    @Test void findsUniqueMisspellingsAndOffersSuggestions() {
        LocalSpellChecker checker = new LocalSpellChecker(List.of("client", "condition", "matter"));
        assertEquals(List.of("clinet"), checker.misspellings("client clinet client clinet"));
        assertEquals("client", checker.suggestions("clinet", 1).getFirst());
    }

    @Test void ignoreAndCustomDictionaryAreLocalAndCaseInsensitive() {
        LocalSpellChecker checker = new LocalSpellChecker(List.of("client"));
        checker.ignore("Doe");
        checker.addToCustomDictionary("Fibromyalgia");
        assertFalse(checker.isMisspelled("DOE"));
        assertFalse(checker.isMisspelled("fibromyalgia"));
        assertTrue(checker.customDictionary().contains("fibromyalgia"));
    }

    @Test void reportsExactRangesAndIgnoreAndCustomDictionary() {
        LocalSpellChecker checker = new LocalSpellChecker(List.of("the", "client"));
        var range = checker.misspellingRanges("the physcian").getFirst();
        assertEquals(4, range.start()); assertEquals(12, range.end());
        checker.ignore("physcian"); assertTrue(checker.misspellingRanges("physcian").isEmpty());
        checker.addToCustomDictionary("counselname"); assertFalse(checker.isMisspelled("CounselName"));
    }
}
