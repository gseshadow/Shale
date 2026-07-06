package com.shale.core.caseupdates;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MedicalRecordRequestKeywordMatcherTest {
    private final MedicalRecordRequestKeywordMatcher matcher = new MedicalRecordRequestKeywordMatcher();

    @Test
    void detectsAllListedPhrasesCaseInsensitively() {
        String[] phrases = {
                "medical record", "medical records", "records ordered", "ordered records",
                "records requested", "requested records", "request records", "records request",
                "authorization", "authorisation", "auth", "hipaa", "release", "ROI",
                "release of information"
        };
        for (String phrase : phrases) {
            assertTrue(matcher.matches("Please note: " + phrase.toUpperCase() + " today"), phrase);
        }
    }

    @Test
    void handlesPunctuationAndExtraWhitespaceForPhraseMatches() {
        assertTrue(matcher.matches("Need medical-records from provider."));
        assertTrue(matcher.matches("Need medical   records from provider."));
        assertTrue(matcher.matches("Sent release-of-information form."));
        assertTrue(matcher.matches("records, requested from hospital"));
    }

    @Test
    void treatsRoiAsStandaloneToken() {
        assertTrue(matcher.matches("Sent ROI to client."));
        assertTrue(matcher.matches("roi received"));
        assertFalse(matcher.matches("android note only"));
    }

    @Test
    void treatsAuthAsStandaloneTokenOnly() {
        assertTrue(matcher.matches("Need auth before requesting records."));
        assertFalse(matcher.matches("The author called back."));
        assertFalse(matcher.matches("Authentication failed."));
    }
}
