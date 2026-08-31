package com.shale.ui.component.spellcheck;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/** Loads the bundled offline dictionary. No text or telemetry leaves the application. */
public final class ShaleDictionary {
    private static final Set<String> ENGLISH = load("/spellcheck/en_US.txt");
    private static final Set<String> LEGAL = load("/spellcheck/shale-legal.txt");
    private static final Set<String> MEDICAL = load("/spellcheck/shale-medical.txt");
    private ShaleDictionary() { }

    public static LocalSpellChecker create() {
        LocalSpellChecker checker = new LocalSpellChecker(ENGLISH);
        checker.addDictionaryLayer(LEGAL);
        checker.addDictionaryLayer(MEDICAL);
        return checker;
    }

    static Set<String> baseWords() { return ENGLISH; }
    static Set<String> legalWords() { return LEGAL; }
    static Set<String> medicalWords() { return MEDICAL; }

    static Set<String> load(String resource) {
        try (var stream = ShaleDictionary.class.getResourceAsStream(resource)) {
            if (stream == null) return Set.of();
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                LinkedHashSet<String> words = new LinkedHashSet<>();
                boolean firstEntry = true;
                for (String line; (line = reader.readLine()) != null; ) {
                    String entry = line.strip().replace("\uFEFF", "");
                    if (entry.isEmpty() || entry.startsWith("#")) continue;
                    if (firstEntry && entry.chars().allMatch(Character::isDigit)) {
                        firstEntry = false; // Hunspell .dic files start with an entry count.
                        continue;
                    }
                    firstEntry = false;
                    int metadata = entry.indexOf('\t');
                    if (metadata >= 0) entry = entry.substring(0, metadata);
                    int flags = entry.indexOf('/');
                    if (flags >= 0) entry = entry.substring(0, flags);
                    String normalized = LocalSpellChecker.normalize(entry);
                    if (!normalized.isBlank()) words.add(normalized);
                }
                return Set.copyOf(words);
            }
        } catch (IOException exception) { return Set.of(); }
    }
}
