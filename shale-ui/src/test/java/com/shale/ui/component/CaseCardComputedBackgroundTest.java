package com.shale.ui.component;

import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.theme.Theme;
import com.shale.ui.theme.ThemeManager;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.VBox;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CaseCardComputedBackgroundTest {
    private static final String CYAN_STATUS = "#13A8C7";
    private static final String MAGENTA_STATUS = "#B52A86";
    private static final String GOLD_PRACTICE = "#D39A18";
    private static final String PURPLE_PRACTICE = "#7048B8";

    @Test
    void productionThemesComputeBackgroundFromStatusAndKeepPracticeAreaOnAccent() {
        JavaFxTestSupport.runAndWait(() -> {
            ThemeManager themes = new ThemeManager();
            for (Theme theme : Theme.values()) {
                themes.setActiveTheme(theme);
                CaseCard cyanGold = card("Cyan Gold", "Prelitigation", CYAN_STATUS, GOLD_PRACTICE, false,
                        "Attorney", LocalDate.now(), LocalDate.now().plusDays(60));
                CaseCard magentaGold = card("Magenta Gold", "Testing", MAGENTA_STATUS, GOLD_PRACTICE, false,
                        "Attorney", LocalDate.now(), LocalDate.now().plusDays(60));
                CaseCard cyanPurple = card("Cyan Purple", "Prelitigation", CYAN_STATUS, PURPLE_PRACTICE, false,
                        "Attorney", LocalDate.now(), LocalDate.now().plusDays(60));
                CaseCard fallback = card("Fallback", "Open", "invalid", GOLD_PRACTICE, false,
                        "Attorney", null, null);
                CaseCard closed = card("Closed", "Closed", CYAN_STATUS, GOLD_PRACTICE, false,
                        "Attorney", null, null);
                VBox root = new VBox(cyanGold, magentaGold, cyanPurple, fallback, closed);
                Scene scene = new Scene(root, 900, 900);
                themes.register(scene);

                root.applyCss();
                root.layout();

                Background cyanGoldBackground = requiredBackground(cyanGold, theme, "cyan status / gold practice area");
                Background magentaGoldBackground = requiredBackground(magentaGold, theme, "magenta status / gold practice area");
                Background cyanPurpleBackground = requiredBackground(cyanPurple, theme, "cyan status / purple practice area");
                Background fallbackBackground = requiredBackground(fallback, theme, "fallback status");
                Background closedBackground = requiredBackground(closed, theme, "closed status");

                System.out.printf("%s cyanGold=%s%n", theme, describe(cyanGoldBackground));
                System.out.printf("%s magentaGold=%s%n", theme, describe(magentaGoldBackground));
                System.out.printf("%s cyanPurple=%s%n", theme, describe(cyanPurpleBackground));
                System.out.printf("%s fallback=%s%n", theme, describe(fallbackBackground));
                System.out.printf("%s closed=%s%n", theme, describe(closedBackground));

                assertGradient(cyanGoldBackground, theme, "cyan status");
                assertGradient(magentaGoldBackground, theme, "magenta status");
                assertGradient(fallbackBackground, theme, "neutral status fallback");
                assertGradient(closedBackground, theme, "closed/muted status");
                assertNotEquals(describe(cyanGoldBackground), describe(magentaGoldBackground),
                        "Different authoritative Case Status colors must compute different backgrounds in " + theme + ".");
                assertEquals(describe(cyanGoldBackground), describe(cyanPurpleBackground),
                        "Practice Area color must not alter the status-derived background in " + theme + ".");
                assertNotEquals(accentStyle(cyanGold), accentStyle(cyanPurple),
                        "Different Practice Areas must retain different narrow accent colors.");
                assertTrue(fallback.getStyle().contains("-shale-case-accent: #F1F5F9"),
                        "Invalid Case Status colors must use the canonical neutral status fallback.");
                assertNotEquals(describe(cyanGoldBackground), describe(closedBackground),
                        "Closed cards must compute a muted version of their authoritative status color in " + theme + ".");
            }
        });
    }

    @Test
    void fullCardsKeepOneDeclaredAndRenderedHeightAcrossOptionalContent() {
        JavaFxTestSupport.runAndWait(() -> {
            CaseCard zeroIndicators = card("No indicators", "", CYAN_STATUS, GOLD_PRACTICE, false,
                    "", null, null);
            CaseCard oneIndicator = card("One indicator", "Prelitigation", CYAN_STATUS, GOLD_PRACTICE, false,
                    "Attorney", null, null);
            CaseCard multipleIndicators = card("Multiple indicators", "Need Non-Engagement Letter With Long Status",
                    "#B9363E", GOLD_PRACTICE, true, "Attorney", LocalDate.now(), LocalDate.now().plusDays(10));
            VBox root = new VBox(12, zeroIndicators, oneIndicator, multipleIndicators);
            Scene scene = new Scene(root, 900, 600);
            new ThemeManager().register(scene);

            root.applyCss();
            root.layout();

            for (CaseCard card : new CaseCard[] {zeroIndicators, oneIndicator, multipleIndicators}) {
                assertEquals(CaseCard.FULL_CARD_HEIGHT, card.getMinHeight(), 0.01, "Full card minHeight token");
                assertEquals(CaseCard.FULL_CARD_HEIGHT, card.getPrefHeight(), 0.01, "Full card prefHeight token");
                assertEquals(CaseCard.FULL_CARD_HEIGHT, card.getMaxHeight(), 0.01, "Full card maxHeight token");
                assertEquals(CaseCard.FULL_CARD_HEIGHT, card.getHeight(), 0.51,
                        "Optional indicators, attorney, and deadlines must not change rendered card height");
                System.out.printf("height name=%s min=%.1f pref=%.1f max=%.1f rendered=%.1f%n",
                        card.getAccessibleText(), card.getMinHeight(), card.getPrefHeight(), card.getMaxHeight(), card.getHeight());
            }
        });
    }

    private static CaseCard card(String name, String statusName, String statusColor, String practiceAreaColor,
            boolean nonEngagement, String attorney, LocalDate intake, LocalDate sol) {
        CaseCardFactory factory = new CaseCardFactory(id -> { });
        CaseCard card = assertInstanceOf(CaseCard.class, factory.create(new CaseCardModel(
                name.hashCode(), name, attorney, "#27856F", nonEngagement,
                statusName, statusColor, practiceAreaColor, List.of()), CaseCardFactory.Variant.COMPACT));
        card.setAccessibleText(name);
        return card;
    }

    private static String accentStyle(CaseCard card) {
        Node accent = card.lookup(".case-card__practice-area-bar");
        assertTrue(accent != null, "Production CaseCard must retain its Practice Area accent rail.");
        return accent.getStyle();
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
                "Expected the topmost BackgroundFill to be a status-driven LinearGradient for the "
                        + identity + " CaseCard in " + theme + ", but computed " + describe(background));
    }

    private static String describe(Background background) {
        return background.getFills().stream()
                .map(BackgroundFill::getFill)
                .map(Paint::toString)
                .collect(Collectors.joining(" -> ", "paint-order[", "]"));
    }
}
