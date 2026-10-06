package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SharedTableThemeContractTest {
    @Test
    void sharedTablesUseThemeAwareSurfacesWithReadableText() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/css/foundation/tables.css"));
        assertAll(
                () -> assertTrue(css.contains("-fx-background-color: -shale-color-card-surface;"),
                        "Table bodies must follow the active light/dark card surface."),
                () -> assertTrue(css.contains("-fx-background-color: -shale-color-section-surface;"),
                        "Table headers must follow the active section surface."),
                () -> assertTrue(css.contains("-fx-background-color: -shale-color-selection;"),
                        "Selected rows must use the theme selection token."),
                () -> assertTrue(css.contains("-fx-text-fill: -shale-color-text-primary;"),
                        "Table content must use the matching theme foreground."),
                () -> assertFalse(css.contains("rgba(255, 255, 255, 0.72)"),
                        "A hard-coded light row cannot sit behind dark-theme foreground text."));
    }
}
