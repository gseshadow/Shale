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
        try (var stream = ShaleDictionary.class.getResourceAsStream("/spellcheck/en_US.txt")) {
            if (stream == null) return new LocalSpellChecker(List.of());
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return new LocalSpellChecker(reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).toList());
            }
        } catch (IOException exception) {
            return new LocalSpellChecker(List.of());
        }
    }
}
