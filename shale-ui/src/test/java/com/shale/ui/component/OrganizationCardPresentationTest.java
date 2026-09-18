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
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
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

    @Test void everyFullCardHasTheSameFixedGeometryAndRenderedHeight() {
        JavaFxTestSupport.runAndWait(() -> {
            var primary = new OrganizationCardType(1, 1, "Primary", "#336699", true, 0);
            var secondary = new OrganizationCardType(2, 2, "Secondary", "#AA7733", false, 1);
            List<OrganizationCard> cards=List.of(
                    full(presentation(List.of(), null, null, null, null)),
                    full(presentation(List.of(primary), "555-0100", null, null, null)),
                    full(presentation(List.of(primary), "555-0100", "team@example.com", null, null)),
                    full(presentation(List.of(primary), "555-0100", "team@example.com", "10 Main Street", "example.com")),
                    full(presentation(List.of(primary), "555-0100", null, "An intentionally long address that must remain bounded inside the compact summary box", "https://example.com/an/intentionally/long/path/that/must/not/grow/the/card")),
                    full(presentation(List.of(primary,secondary), "555-0100", "team@example.com", "10 Main Street", "example.com")));
            StackPane root=new StackPane();root.getChildren().addAll(cards);Scene scene=new Scene(root,900,600);new ThemeManager().register(scene);root.applyCss();root.layout();
            CaseCard caseCard=new CaseCard();caseCard.applyFull();root.getChildren().add(caseCard);root.applyCss();root.layout();
            for(OrganizationCard card:cards){
                assertEquals(CaseCard.FULL_CARD_HEIGHT,card.getMinHeight());
                assertEquals(CaseCard.FULL_CARD_HEIGHT,card.getPrefHeight());
                assertEquals(CaseCard.FULL_CARD_HEIGHT,card.getMaxHeight());
                assertEquals(caseCard.getHeight(),card.getHeight(),.51,"full OrganizationCard and CaseCard rendered heights must match");
                assertTrue(card.getBoundsInLocal().contains(card.getLayoutBounds()),"card content must remain inside its fixed bounds");
            }
        });
    }

    @Test void fullCardSummariesAreBoundedReadOnlyAndCardRemainsTheActivationTarget() {
        JavaFxTestSupport.runAndWait(() -> {
            AtomicInteger opens=new AtomicInteger();
            var factory=new OrganizationCardFactory(id->opens.incrementAndGet());
            OrganizationCard card=factory.create(MODEL,presentation(List.of(new OrganizationCardType(1,1,"Hospital","#336699",true,0)),
                    "(555) 010-1000","long-address-recipient@example.com","10 Main Street, A Very Long Municipality, State 12345","https://example.com/a/long/path"),OrganizationCardFactory.Variant.FULL);
            assertEquals(2,card.lookupAll(".organization-card-summary-box").size());
            GridPane summary=(GridPane)card.lookup(".organization-card-contact-summary");
            List<String> summaryKinds=summary.getChildren().stream().map(VBox.class::cast)
                    .map(box->((Label)box.getChildren().getFirst()).getText()).toList();
            assertEquals(List.of("Phone","Email"),summaryKinds,"directory summaries must select the first two populated categories by documented priority");
            assertTrue(card.lookupAll(".organization-card-summary-box .button").isEmpty(),"directory summaries must expose no child actions");
            assertTrue(card.lookupAll(".organization-card-summary-value").stream().map(Label.class::cast).allMatch(label->label.getTooltip()!=null&&!label.getAccessibleText().isBlank()));
            card.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED,1,1,1,1,MouseButton.PRIMARY,1,false,false,false,false,true,false,false,true,false,false,null));
            card.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.SPACE,false,false,false,false));
            assertEquals(2,opens.get(),"mouse and Space must activate the Organization card itself");
            assertTrue(card.getAccessibleText().contains("Hospital"));
        });
    }

    @Test void compactAndMiniDoNotInheritTheFullCardHeight() {
        JavaFxTestSupport.runAndWait(()->{
            var factory=new OrganizationCardFactory(id->{});
            OrganizationCard compact=factory.create(MODEL,OrganizationCardFactory.Variant.COMPACT);
            OrganizationCard mini=factory.create(MODEL,OrganizationCardFactory.Variant.MINI);
            assertNotEquals(CaseCard.FULL_CARD_HEIGHT,compact.getPrefHeight());
            assertNotEquals(CaseCard.FULL_CARD_HEIGHT,mini.getPrefHeight());
            assertEquals(Double.MAX_VALUE,compact.getMaxHeight());
            assertEquals(Double.MAX_VALUE,mini.getMaxHeight());
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
                Paint bluePaint = blue.getBackground().getFills().getLast().getFill();
                Paint goldPaint = gold.getBackground().getFills().getLast().getFill();
                Paint neutralPaint = neutral.getBackground().getFills().getLast().getFill();
                assertNotEquals(bluePaint, goldPaint, "different primary type colors must compute different washes");
                assertNotEquals(bluePaint, neutralPaint, "typed cards must remain visibly distinct from neutral fallback");
                assertFalse(neutral.getBackground().getFills().isEmpty());
                LinearGradient blueGradient=(LinearGradient)bluePaint;
                Color right=blueGradient.getStops().getLast().getColor();
                assertEquals(Color.web("#2266AA").getHue(),right.getHue(),.5,"right edge must remain derived from the primary type hue");
                assertTrue(right.getOpacity()>.05,"right edge must retain a visible primary-type tint rather than becoming transparent/neutral");
            }
        });
    }

    private static OrganizationCard card(List<OrganizationCardType> types) {
        return new OrganizationCardFactory(id -> {}).create(MODEL, presentation(types, null), OrganizationCardFactory.Variant.FULL);
    }
    private static OrganizationCard full(OrganizationCardPresentation presentation){return new OrganizationCardFactory(id->{}).create(MODEL,presentation,OrganizationCardFactory.Variant.FULL);}
    private static OrganizationCardPresentation presentation(List<OrganizationCardType> types, String phone) {
        return new OrganizationCardPresentation(types, phone, phone, null, null, null, null);
    }
    private static OrganizationCardPresentation presentation(List<OrganizationCardType> types,String phone,String email,String address,String website){return new OrganizationCardPresentation(types,phone,phone,"42",email,address,website);}
    private static ClassificationChipGroup chips(OrganizationCard card) {
        return (ClassificationChipGroup) card.lookup(".contact-classification-chip-group");
    }
    private static boolean hasDisplay() {
        String os=System.getProperty("os.name", "").toLowerCase();
        return System.getenv("DISPLAY")!=null||System.getenv("WAYLAND_DISPLAY")!=null||os.contains("win")||os.contains("mac");
    }
}
