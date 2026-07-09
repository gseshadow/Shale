package com.shale.core.caseupdates;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detects case-update notes that appear to mention requesting or ordering medical records.
 */
public final class MedicalRecordRequestKeywordMatcher {
    private static final List<String> PHRASES = List.of(
            "medical record",
            "medical records",
            "records ordered",
            "ordered records",
            "records requested",
            "requested records",
            "request records",
            "records request",
            "authorization",
            "authorisation",
            "hipaa",
            "release",
            "release of information");

    private static final Pattern AUTH_TOKEN = Pattern.compile("(^|\\s)auth($|\\s)");
    private static final Pattern ROI_TOKEN = Pattern.compile("(^|\\s)roi($|\\s)");

    public boolean matches(String noteText) {
        String normalized = normalize(noteText);
        if (normalized.isBlank()) {
            return false;
        }
        for (String phrase : PHRASES) {
            if (normalized.contains(phrase)) {
                return true;
            }
        }
        return AUTH_TOKEN.matcher(normalized).find() || ROI_TOKEN.matcher(normalized).find();
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{Alnum}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
