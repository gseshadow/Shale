package com.shale.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

final class ThemeResourceContractTest {
    private static final Pattern IMPORT = Pattern.compile("@import\\s+\"([^\"]+)\"");

    @Test
    void productionAndThemeResourcesResolveFromTheClasspath() throws IOException {
        String app = read("/css/app.css");
        read(Theme.LIGHT.stylesheetResource());
        read(Theme.DARK.stylesheetResource());

        Matcher imports = IMPORT.matcher(app);
        int importCount = 0;
        while (imports.find()) {
            importCount++;
            assertNotNull(getClass().getResource("/css/" + imports.group(1)),
                    "app.css import must resolve from the classpath: " + imports.group(1));
        }
        assertEquals(9, importCount, "the stable production entry point retains its nine foundation imports");
    }

    @Test
    void themeCssHasBalancedBlocksAndJavaFxLookedUpColorSyntax() throws IOException {
        for (Theme theme : Theme.values()) {
            String css = read(theme.stylesheetResource());
            assertEquals(css.chars().filter(c -> c == '{').count(), css.chars().filter(c -> c == '}').count(),
                    theme + " CSS must have balanced blocks");
            assertTrue(css.contains(".root"), theme + " must define root-level looked-up colors");
            assertTrue(css.contains("-shale-color-text-primary:"), theme + " must define readable primary text");
            assertTrue(css.contains("-shale-color-dialog-surface:"), theme + " must define dialog surfaces");
            assertTrue(css.contains("-shale-control-focus-border:" ) || theme == Theme.LIGHT,
                    theme + " must provide focus-state tokens or preserve the production fallback");
            assertTrue(!css.contains("--"), "JavaFX CSS must not use browser custom properties");
        }
    }

    private String read(String path) throws IOException {
        var resource = getClass().getResource(path);
        assertNotNull(resource, "Expected classpath resource " + path);
        try (var input = resource.openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
