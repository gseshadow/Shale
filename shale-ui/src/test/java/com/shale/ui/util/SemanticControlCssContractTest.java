package com.shale.ui.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

final class SemanticControlCssContractTest {
    private static final Path BUTTONS = Path.of("src/main/resources/css/foundation/buttons.css");

    @Test void lookedUpDimensionsKeepJavaFxSizeUnits() throws Exception {
        String css = Files.readString(BUTTONS);
        for (String token : new String[]{"height-standard", "height-small", "button-radius", "padding-horizontal", "icon-text-gap"}) {
            Matcher declaration = Pattern.compile("-shale-control-" + token + "\\s*:\\s*([^;]+);").matcher(css);
            assertTrue(declaration.find(), "Missing semantic dimension token " + token);
            assertTrue(declaration.group(1).trim().matches("[0-9]+(?:\\.[0-9]+)?px"),
                    () -> token + " must resolve to a JavaFX Size with px units, but was " + declaration.group(1));
        }
    }

    @Test void ordinarySemanticButtonsHaveNoFixedMaximumWidth() throws Exception {
        String css = Files.readString(BUTTONS);
        String base = rule(css, ".button.shale-control-button");
        assertFalse(base.contains("-fx-max-width"));
        assertTrue(base.contains("-fx-padding: 0px 16px 0px 16px"));
        assertTrue(base.contains("-fx-background-radius: 10px"));
        assertTrue(base.contains("-fx-border-radius: 10px"));
    }

    private static String rule(String css, String selector) {
        int start = css.indexOf(selector);
        assertTrue(start >= 0, "Missing selector " + selector);
        int open = css.indexOf('{', start);
        int close = css.indexOf('}', open);
        return css.substring(open + 1, close);
    }
}
