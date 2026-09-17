package com.shale.ui.component;

import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.theme.Theme;
import com.shale.ui.theme.ThemeManager;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.VBox;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CaseCardComputedBackgroundTest {
    private static final String GOLD = "#D39A18";
    private static final String PURPLE = "#7048B8";

    @Test
    void productionThemesComputeDistinctPracticeAreaBackgroundsAndMutedFallbacks() {
        JavaFxTestSupport.runAndWait(() -> {
            ThemeManager themes = new ThemeManager();
            for (Theme theme : Theme.values()) {
                themes.setActiveTheme(theme);
                CaseCard gold = card("Gold", GOLD, "Open");
                CaseCard purple = card("Purple", PURPLE, "Open");
                CaseCard fallback = card("Fallback", "invalid", "Open");
                CaseCard closed = card("Closed", GOLD, "Closed");
                VBox root = new VBox(gold, purple, fallback, closed);
                Scene scene = new Scene(root, 900, 700);
                themes.register(scene);

                root.applyCss();
                root.layout();

                Background goldBackground = requiredBackground(gold, theme, "gold");
                Background purpleBackground = requiredBackground(purple, theme, "purple");
                Background fallbackBackground = requiredBackground(fallback, theme, "fallback");
                Background closedBackground = assertDoesNotThrow(
                        () -> requiredBackground(closed, theme, "closed"),
                        "Closed CaseCards must compute their muted identity background.");

                System.out.printf("%s gold=%s%n", theme, describe(goldBackground));
                System.out.printf("%s purple=%s%n", theme, describe(purpleBackground));
                System.out.printf("%s fallback=%s%n", theme, describe(fallbackBackground));
                System.out.printf("%s closed=%s%n", theme, describe(closedBackground));

                assertGradient(goldBackground, theme, "gold");
                assertGradient(purpleBackground, theme, "purple");
                assertGradient(fallbackBackground, theme, "neutral fallback");
                assertGradient(closedBackground, theme, "closed/muted");
                assertNotEquals(describe(goldBackground), describe(purpleBackground),
                        "Different authoritative Practice Area colors must compute different card backgrounds in " + theme + ".");
                assertNotEquals(describe(goldBackground), describe(closedBackground),
                        "Closed cards must retain identity while using a distinct muted background in " + theme + ".");
                assertTrue(fallback.getStyle().contains("-shale-case-accent: #CBD5E1"),
                        "Invalid Practice Area colors must retain the centralized neutral fallback.");
            }
        });
    }

    private static CaseCard card(String name, String practiceAreaColor, String status) {
        CaseCardFactory factory = new CaseCardFactory(id -> { });
        return assertInstanceOf(CaseCard.class, factory.create(new CaseCardModel(
                name.hashCode(), name, null, null, null, "Attorney", "#27856F", false,
                status, "#3E78B2", practiceAreaColor), CaseCardFactory.Variant.COMPACT));
    }

    private static Background requiredBackground(CaseCard card, Theme theme, String identity) {
        Background background = card.getBackground();
        assertTrue(background != null && !background.getFills().isEmpty(),
                "Expected a computed " + identity + " CaseCard background in " + theme + ".");
        return background;
    }

    private static void assertGradient(Background background, Theme theme, String identity) {
        assertTrue(background.getFills().size() >= 2,
                "Expected the " + identity + " CaseCard to layer its tint over the canonical surface in " + theme
                        + ", but computed " + describe(background));
        Paint tint = background.getFills().getLast().getFill();
        assertInstanceOf(LinearGradient.class, tint,
                "Expected the topmost BackgroundFill to be a visible data-driven LinearGradient for the "
                        + identity + " CaseCard in " + theme
                        + ", but computed " + describe(background));
    }

    private static String describe(Background background) {
        return background.getFills().stream()
                .map(BackgroundFill::getFill)
                .map(Paint::toString)
                .collect(Collectors.joining(" -> ", "paint-order[", "]"));
    }
}
