package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.data.dao.OrganizationDao.OrganizationCardPresentation;
import com.shale.data.dao.OrganizationDao.OrganizationCardType;
import com.shale.ui.component.factory.OrganizationCardFactory;
import com.shale.ui.component.factory.OrganizationCardFactory.OrganizationCardModel;
import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.theme.ThemeManager;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;

final class OrganizationCardPresentationTest {
    private static final OrganizationCardModel MODEL = new OrganizationCardModel(7, "North Valley Health",
            null, null, null, null, null, null, null, null, null, null, null, null, null);

    @BeforeAll static void toolkit() {
        assumeTrue(hasDisplay(), "Computed Organization Card presentation requires a graphical display.");
        JavaFxTestSupport.ensureToolkitStarted();
    }

    @Test void authoritativePrimaryControlsWashWhileEveryAssignmentRemainsVisible() {
        JavaFxTestSupport.runAndWait(() -> {
            var primary = new OrganizationCardType(12, 2, "Hospital", "#2277AA", true, 4);
            var secondaryA = new OrganizationCardType(11, 1, "Records", "#CC7722", false, 0);
            var secondaryB = new OrganizationCardType(13, 3, "Vendor", "#8844CC", false, 2);
            OrganizationCard first = card(List.of(secondaryA, primary));
            OrganizationCard second = card(List.of(primary, secondaryB));

            assertEquals(first.getStyle(), second.getStyle(),
                    "secondary assignments must not replace primary Organization identity paint");
            assertTrue(first.getStyle().contains("#2277AA"));
            assertEquals(2, chips(first).getChildren().size(), "every assigned Organization Type must render");
            assertEquals(2, chips(second).getChildren().size(), "different secondary types must remain visible");
            assertTrue((Boolean) chips(first).getChildren().getFirst().getProperties().get("classificationPrimary"),
                    "the authoritative primary assignment must be presented first");
        });
    }

    @Test void invalidAndMissingPrimaryColorsUseTheSameThemeNeutralFallback() {
        JavaFxTestSupport.runAndWait(() -> {
            OrganizationCard missing = card(List.of());
            OrganizationCard invalid = card(List.of(new OrganizationCardType(1, 9, "Historic", "not-a-color", true, 0)));
            assertEquals("", missing.getStyle());
            assertEquals(missing.getStyle(), invalid.getStyle());
        });
    }

    @Test void fullCardGeometryAndKeyboardActivationRemainStable() {
        JavaFxTestSupport.runAndWait(() -> {
            AtomicInteger opens = new AtomicInteger();
            var factory = new OrganizationCardFactory(id -> opens.incrementAndGet());
            OrganizationCard sparse = factory.create(MODEL, presentation(List.of(), null), OrganizationCardFactory.Variant.FULL);
            OrganizationCard busy = factory.create(MODEL, presentation(List.of(
                    new OrganizationCardType(1, 1, "Primary", "#336699", true, 0),
                    new OrganizationCardType(2, 2, "Secondary", "#AA7733", false, 1)), "5551234567"),
                    OrganizationCardFactory.Variant.FULL);
            assertEquals(sparse.getMinHeight(), busy.getMinHeight());
            assertEquals(sparse.getPrefWidth(), busy.getPrefWidth());
            sparse.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            assertEquals(1, opens.get(), "Enter must retain Organization navigation");
            assertTrue(sparse.isFocusTraversable());
        });
    }

    @Test void themeManagerComputesDistinctPrimaryWashesAndNeutralFallbackInBothThemes() {
        JavaFxTestSupport.runAndWait(() -> {
            OrganizationCard blue = card(List.of(new OrganizationCardType(1, 1, "Blue", "#2266AA", true, 0)));
            OrganizationCard gold = card(List.of(new OrganizationCardType(2, 2, "Gold", "#CC8822", true, 0)));
            OrganizationCard neutral = card(List.of());
            StackPane root = new StackPane(blue, gold, neutral);
            Scene scene = new Scene(root, 1200, 700);
            ThemeManager themes = new ThemeManager(); themes.register(scene);
            for (var theme : com.shale.ui.theme.Theme.values()) {
                themes.setActiveTheme(theme); root.applyCss(); root.layout();
                Paint bluePaint = blue.getBackground().getFills().getFirst().getFill();
                Paint goldPaint = gold.getBackground().getFills().getFirst().getFill();
                Paint neutralPaint = neutral.getBackground().getFills().getFirst().getFill();
                assertNotEquals(bluePaint, goldPaint, "different primary type colors must compute different washes");
                assertNotEquals(bluePaint, neutralPaint, "typed cards must remain visibly distinct from neutral fallback");
                assertFalse(neutral.getBackground().getFills().isEmpty());
            }
        });
    }

    private static OrganizationCard card(List<OrganizationCardType> types) {
        return new OrganizationCardFactory(id -> {}).create(MODEL, presentation(types, null), OrganizationCardFactory.Variant.FULL);
    }
    private static OrganizationCardPresentation presentation(List<OrganizationCardType> types, String phone) {
        return new OrganizationCardPresentation(types, phone, phone, null, null, null, null);
    }
    private static ClassificationChipGroup chips(OrganizationCard card) {
        return (ClassificationChipGroup) card.lookup(".contact-classification-chip-group");
    }
    private static boolean hasDisplay() {
        String os=System.getProperty("os.name", "").toLowerCase();
        return System.getenv("DISPLAY")!=null||System.getenv("WAYLAND_DISPLAY")!=null||os.contains("win")||os.contains("mac");
    }
}
