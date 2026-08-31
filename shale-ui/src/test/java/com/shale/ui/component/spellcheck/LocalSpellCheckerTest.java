package com.shale.ui.component.spellcheck;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LocalSpellCheckerTest {
    @Test void bundledDictionaryAcceptsOrdinaryEnglishAndRejectsNonsense() {
        LocalSpellChecker checker = ShaleDictionary.create();
        assertEquals(List.of(), checker.misspellings("This is a test of the spell checking detection."));
        assertEquals(List.of("asdfasdf", "tset"), checker.misspellings("asdfasdf tset"));
    }

    @Test void acceptsCommonWordFormsAndIgnoresNonWordValues() {
        LocalSpellChecker checker = ShaleDictionary.create();
        assertEquals(List.of(), checker.misspellings(
                "test tests tested testing 2026 8/31/2026 12:30 john@example.com https://example.com"));
    }

    @Test void punctuationAndContractionsRetainExactWordRanges() {
        LocalSpellChecker checker = new LocalSpellChecker(List.of("test", "don't", "can't", "patient's"));
        assertTrue(checker.misspellingRanges("test, test. (test) \"test\" test: don't can't patient's").isEmpty());
    }

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

    @Test void rangesExcludeWhitespacePunctuationAndRemainIndependent() {
        LocalSpellChecker checker = new LocalSpellChecker(List.of("this", "is", "valid", "works"));
        assertRanges(checker, "asdf this is valid", new int[][]{{0, 4}});
        assertRanges(checker, "this asdf works", new int[][]{{5, 9}});
        assertRanges(checker, "this asdf, works", new int[][]{{5, 9}});
        assertRanges(checker, "asdf valid qwer", new int[][]{{0, 4}, {11, 15}});
    }

    @Test void rangesUseDocumentOffsetsAcrossNewlines() {
        LocalSpellChecker checker = new LocalSpellChecker(List.of("valid", "line", "here"));
        assertRanges(checker, "valid line\nasdf here", new int[][]{{11, 15}});
    }

    private static void assertRanges(LocalSpellChecker checker, String text, int[][] expected) {
        var actual = checker.misspellingRanges(text);
        assertEquals(expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i][0], actual.get(i).start());
            assertEquals(expected[i][1], actual.get(i).end());
        }
    }
}
