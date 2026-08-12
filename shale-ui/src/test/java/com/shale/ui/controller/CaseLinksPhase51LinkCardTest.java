package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinksPhase51LinkCardTest {
    private static final Path CARD = Path.of("src/main/java/com/shale/ui/component/factory/CaseLinkCardFactory.java");
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path CSS = Path.of("src/main/resources/css/foundation/cards.css");
    private static final Path COLOR = Path.of("src/main/java/com/shale/ui/util/ColorUtil.java");

    @Test
    void factoryDefinesOfficialVariantsAndRemainsDisplayOnly() throws Exception {
        String card = Files.readString(CARD);
        assertTrue(card.contains("public enum Variant { FULL, COMPACT, MINI }"));
        assertTrue(card.contains("public record Actions(Runnable open, Runnable edit, Runnable setPrimary, Runnable delete)"));
        assertTrue(card.contains("create(CaseLinkDto link, Variant variant, Actions actions)"));
        assertFalse(card.contains("AppState"));
        assertFalse(card.contains("CaseServicePort"));
        assertFalse(card.contains("CaseDao"));
        assertFalse(card.contains("ExternalBrowserHelper"));
    }

    @Test
    void variantsExposeRequiredContentAndActionsWithoutRawUrlBody() throws Exception {
        String card = Files.readString(CARD);
        assertTrue(card.contains("case-link-card-title"));
        assertTrue(card.contains("No description"));
        assertTrue(card.contains("case-link-card-description-empty"));
        assertTrue(card.contains("LinkTypeIndicatorFactory.createLinkTypePill(link.linkTypeName(), link.linkTypeColor()"));
        assertTrue(card.contains("if (variant != Variant.MINI && link.primary())"));
        assertTrue(card.contains("if (variant == Variant.FULL && !blank(link.notes()))"));
        assertTrue(card.contains("if (!link.primary()) footer.getChildren().add"));
        assertTrue(card.contains("semantic(\"Delete\", ControlStyles.Purpose.DANGER"));
        assertTrue(card.contains("semantic(\"Edit\", ControlStyles.Purpose.GHOST"));
        assertFalse(card.contains("new Label(blankTo(link.url()"));
        assertFalse(card.contains("Open Link"));
        assertFalse(card.contains("Move Up"));
        assertFalse(card.contains("Move Down"));
    }

    @Test
    void cardClickAndChildActionsAreIsolated() throws Exception {
        String card = Files.readString(CARD);
        assertTrue(card.contains("shale-entity-card-clickable"));
        assertTrue(card.contains("card.setFocusTraversable(true)"));
        assertTrue(card.contains("KeyCode.ENTER || event.getCode() == KeyCode.SPACE"));
        assertTrue(card.contains("safeActions.open().run(); event.consume();"));
        assertTrue(card.contains("button.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume)"));
        assertTrue(card.contains("button.addEventHandler(KeyEvent.KEY_PRESSED"));
    }

    @Test
    void controllerUsesFullAndCompactFactoryVariantsAndGroupsByLinkType() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertTrue(source.contains("CaseLinkCardFactory.Variant.COMPACT"));
        assertTrue(source.contains("CaseLinkCardFactory.Variant.FULL"));
        assertTrue(source.contains("onEditCaseLink(link)"));
        assertTrue(source.contains("groupCaseLinksByType(caseLinks)"));
        assertTrue(source.contains("new LinkTypeGroup(link.linkTypeId()"));
        assertTrue(source.contains("Comparator.comparing(LinkTypeGroup::name, String.CASE_INSENSITIVE_ORDER).thenComparingInt(LinkTypeGroup::id)"));
        assertTrue(source.contains("Comparator.comparing(CaseLinkDto::primary).reversed()"));
        assertTrue(source.contains("thenComparingInt(CaseLinkDto::sortOrder)"));
        assertTrue(source.contains("thenComparingLong(CaseLinkDto::caseLinkId)"));
        assertFalse(source.contains("ActionButtonFactory.cardAction(\"Manage Links\""));
        assertFalse(source.contains("ActionButtonFactory.cardAction(\"Open Link\""));
        assertFalse(source.contains("onMoveCaseLink(index"));
    }

    @Test
    void typeColorGradientAccentAndFormatsAreSupported() throws Exception {
        String card = Files.readString(CARD);
        String css = Files.readString(CSS);
        String color = Files.readString(COLOR);
        assertTrue(card.contains("ColorUtil.toCssBackgroundColor(storedColor)"));
        assertTrue(card.contains("ColorUtil.toCssRgba(storedColor"));
        assertTrue(card.contains("-shale-link-type-accent"));
        assertTrue(css.contains("linear-gradient(to right, -shale-link-type-wash"));
        assertFalse(card.contains("-shale-link-type-rail-width"));
        assertFalse(css.contains("-fx-border-width: 1 1 1 -shale-link-type-rail-width"));
        assertTrue(css.contains("-fx-border-width: 1px 1px 1px 5px"));
        assertTrue(css.contains("-fx-border-width: 1px 1px 1px 4px"));
        assertTrue(css.contains("-fx-border-width: 1px 1px 1px 3px"));
        assertTrue(color.contains("normalizeStoredColor"));
        assertTrue(color.contains("normalized.startsWith(\"#\")"));
        assertTrue(color.contains("normalized.startsWith(\"0x\") || normalized.startsWith(\"0X\")"));
        assertTrue(color.contains("rgba(%d,%d,%d,%.3f)"));
    }
}
