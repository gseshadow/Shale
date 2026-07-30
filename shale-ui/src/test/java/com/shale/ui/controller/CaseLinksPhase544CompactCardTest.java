package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinksPhase544CompactCardTest {
    private static final Path CARD = Path.of("src/main/java/com/shale/ui/component/factory/CaseLinkCardFactory.java");
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path FXML = Path.of("src/main/resources/fxml/case.fxml");
    private static final Path CSS = Path.of("src/main/resources/css/foundation/cards.css");

    @Test
    void overviewStillUsesFactoryCompactVariantInsideSeparateSectionSurface() throws Exception {
        String controller = normalize(Files.readString(CONTROLLER));
        String fxml = normalize(Files.readString(FXML));

        assertTrue(controller.contains("caseLinkCardFactory.create(link, CaseLinkCardFactory.Variant.COMPACT"));
        assertTrue(fxml.contains("fx:id=\"ovPrimaryLinkSection\""));
        assertTrue(fxml.contains("fx:value=\"shale-surface-section\""));
        assertTrue(fxml.contains("text=\"Primary Link\""));
        assertTrue(fxml.contains("fx:id=\"ovPrimaryLinkBox\" spacing=\"6\""));
    }

    @Test
    void compactUsesDenseContentSizedHierarchyWithoutFooterOrVerticalSpacer() throws Exception {
        String card = normalize(Files.readString(CARD));
        String compact = methodBody(card, "buildCompactCard");

        assertTrue(compact.contains("new HBox(6)"));
        assertTrue(compact.contains("titleLabel(link, Variant.COMPACT)"));
        assertTrue(compact.contains("LinkTypeIndicatorFactory.createLinkTypePill"));
        assertTrue(compact.contains("link.primary()") && compact.contains("primaryBadge()"));
        assertTrue(compact.contains("new HBox(8)"));
        assertTrue(compact.contains("descriptionLabel(link, true)"));
        assertTrue(compact.contains("semantic(\"Edit\", ControlStyles.Purpose.GHOST"));
        assertTrue(compact.contains("case-link-card-compact-edit"));
        assertFalse(compact.contains("compactFooter"));
        assertFalse(compact.contains("case-link-card-footer"));
        assertFalse(compact.contains("new Region()"));
        assertFalse(compact.contains("Priority.ALWAYS); summaryRow.getChildren().addAll"));
        assertFalse(compact.contains("setMinHeight(120"));
        assertFalse(compact.contains("setPrefHeight(120"));
    }

    @Test
    void compactSharedWithIsConditionalInlineWrappingAndKeepsEmbeddedMiniContactCards() throws Exception {
        String card = normalize(Files.readString(CARD));
        String shared = methodBody(card, "addSharedWith");
        String embedded = methodBody(card, "embeddedShareCard");

        assertTrue(shared.contains("link.shares() == null") && shared.contains("link.shares().isEmpty()") && shared.contains("return"));
        assertTrue(shared.contains("new FlowPane(compact ? 4 : 6, compact ? 4 : 6)"));
        assertTrue(shared.contains("case-link-card-shared-contact-flow-compact"));
        assertTrue(shared.contains("flow.getChildren().add(label)"));
        assertTrue(shared.contains("card.getChildren().add(flow)") && shared.contains("return"));
        assertTrue(embedded.contains("ContactCardFactory.Variant.MINI"));
        assertTrue(embedded.contains("case-link-embedded-contact-card"));
        assertTrue(embedded.contains("card.setInteractive(navigable)"));
    }

    @Test
    void clickAndChildEventIsolationRemainCallbackDriven() throws Exception {
        String card = normalize(Files.readString(CARD));

        assertTrue(card.contains("safeActions.open().run(); event.consume();"));
        assertTrue(card.contains("event.getTarget() == card"));
        assertTrue(card.contains("button.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume)"));
        assertTrue(card.contains("card.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume)"));
        assertTrue(card.contains("card.addEventHandler(KeyEvent.KEY_PRESSED, event -> { if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) event.consume(); })"));
        assertFalse(card.contains("ExternalBrowserHelper"));
    }

    @Test
    void fullAndMiniCompositionAndVisualIdentityRemainPresent() throws Exception {
        String card = normalize(Files.readString(CARD));
        String css = normalize(Files.readString(CSS));
        String fullMini = methodBody(card, "buildFullOrMiniCard");

        assertTrue(fullMini.contains("Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS)"));
        assertTrue(fullMini.contains("if (variant == Variant.FULL) addSharedWith(card, link, false, onOpenContact)"));
        assertTrue(fullMini.contains("if (showManagementActions && variant == Variant.FULL) card.getChildren().add(fullFooter(link, safeActions))"));
        assertTrue(card.contains("case MINI -> card.getStyleClass().addAll(\"shale-entity-card-inline\", \"shale-density-dense\")"));
        assertTrue(css.contains("linear-gradient(to right, -shale-link-type-wash"));
        assertTrue(css.contains(".case-link-card-full { -fx-padding: 16 16 16 18; }"));
        assertTrue(css.contains(".case-link-card-compact { -fx-padding: 8 10 8 12; -fx-spacing: 4; -fx-border-width: 1px 1px 1px 4px; }"));
        assertTrue(css.contains(".case-link-card-mini { -fx-padding: 7 9 7 11; -fx-spacing: 4; -fx-border-width: 1px 1px 1px 3px; }"));
        assertTrue(card.contains("primaryBadge()"));
    }

    private static String normalize(String source) {
        return source.replace("\r\n", "\n").replace('\r', '\n').replaceAll("\\s+", " ");
    }

    private static String methodBody(String normalizedSource, String methodName) {
        int signature = normalizedSource.indexOf("private static void " + methodName + "(");
        if (signature < 0) signature = normalizedSource.indexOf("private static ContactCard " + methodName + "(");
        assertTrue(signature >= 0, "Expected method " + methodName);
        int open = normalizedSource.indexOf('{', signature);
        assertTrue(open >= 0, "Expected body for " + methodName);
        int depth = 0;
        for (int i = open; i < normalizedSource.length(); i++) {
            char c = normalizedSource.charAt(i);
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (depth == 0) return normalizedSource.substring(open + 1, i);
        }
        throw new AssertionError("Unbalanced method body for " + methodName);
    }
}
