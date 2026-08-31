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
}
