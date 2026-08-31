package com.shale.ui.component.spellcheck;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Loads the bundled offline dictionary. No text or telemetry leaves the application. */
public final class ShaleDictionary {
    private ShaleDictionary() { }

    public static LocalSpellChecker create() {
        LocalSpellChecker checker = new LocalSpellChecker(load("/spellcheck/en_US.txt"));
        checker.addDictionaryLayer(load("/spellcheck/shale-legal.txt"));
        checker.addDictionaryLayer(load("/spellcheck/shale-medical.txt"));
        return checker;
    }

    private static List<String> load(String resource) {
        try (var stream = ShaleDictionary.class.getResourceAsStream(resource)) {
            if (stream == null) return List.of();
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
            }
        } catch (IOException exception) { return List.of(); }
    }
}
