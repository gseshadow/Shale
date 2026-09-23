package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseDateCardPresentationTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path CARDS_CSS = Path.of("src/main/resources/css/foundation/cards.css");

    @Test void productionCardsUseSemanticCardAndTextClassesWithoutHardCodedWhiteSurface() throws Exception {
        String method = method(Files.readString(CONTROLLER), "private Node createCaseDateCard");

        assertTrue(method.contains("\"case-date-card\""), "Case Date rows must use the semantic card class");
        assertTrue(method.contains("\"case-date-card--removed\"") && method.contains("\"case-date-card--active\""),
                "active and removed lifecycle states must remain explicit");
        for (String styleClass : new String[] { "case-date-card__title", "case-date-card__metadata",
                "case-date-card__notes", "case-date-card__historical" }) {
            assertTrue(method.contains("\"" + styleClass + "\""), styleClass + " must be applied in production");
        }
        assertFalse(method.contains("rgba(248,250,252,0.96)"), "the Light-only white card surface must be removed");
        assertFalse(method.contains("-fx-font-") || method.contains("-fx-opacity"),
                "shared text and removed-state presentation belongs in semantic CSS");
    }

    @Test void cardCssStartsFromThemeSurfaceAndUsesOnlySemanticTextAndBorderTokens() throws Exception {
        String css = Files.readString(CARDS_CSS);
        String rules = css.substring(css.indexOf(".case-date-card {"), css.indexOf("/* Entity Card inline variant"));

        assertTrue(rules.contains("-shale-color-card-surface 0%"), "the gradient must begin on the themed card plane");
        assertTrue(rules.contains("-shale-case-date-type-wash 100%"), "the type color must remain a restrained terminal wash");
        assertTrue(rules.contains("-shale-case-date-type-wash: -shale-color-info-wash"),
                "missing type colors need a semantic fallback");
        assertTrue(rules.contains("-fx-border-color: -shale-color-card-border"));
        assertTrue(rules.contains("-fx-text-fill: -shale-color-text-primary"));
        assertTrue(rules.contains("-fx-text-fill: -shale-color-text-secondary"));
        assertTrue(rules.contains("-fx-text-fill: -shale-color-text-muted"));
        assertFalse(rules.contains("#") || rules.contains("rgba("), "shared card styling must remain theme-token driven");
    }

    @Test void authoritativeDtoColorIsNormalizedBeforeBecomingTheOnlyDynamicCssValue() {
        assertEquals("-shale-case-date-type-wash: rgba(185,28,28,0.120);",
                CaseController.caseDateCardAccentStyle(" #b91c1c "));
        assertEquals("", CaseController.caseDateCardAccentStyle(null));
        assertEquals("", CaseController.caseDateCardAccentStyle("red; -fx-opacity: 0"));
    }

    @Test void activeAndRemovedActionsRemainEditRemoveAndRestore() throws Exception {
        String method = method(Files.readString(CONTROLLER), "private Node createCaseDateCard");
        assertTrue(method.contains("ActionButtonFactory.semantic(\"Edit\", e -> openCaseDateDialog(date)"));
        assertTrue(method.contains("ActionButtonFactory.semantic(\"Remove\", e -> onRemoveCaseDate(date)"));
        assertTrue(method.contains("ActionButtonFactory.semantic(\"Restore\", e -> onRestoreCaseDate(date)"));
        assertTrue(method.contains("removed ? new HBox(6, restore) : new HBox(6, edit, remove)"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new AssertionError("Missing method: " + signature);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            else if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("Unbalanced method: " + signature);
    }
}
