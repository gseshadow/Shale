package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinkSharesPhase535VisualIntegrationTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path CARD = Path.of("src/main/java/com/shale/ui/component/factory/CaseLinkCardFactory.java");
    private static final Path CONTACT_CARD = Path.of("src/main/java/com/shale/ui/component/ContactCard.java");
    private static final Path CSS = Path.of("src/main/resources/css/foundation/cards.css");

    @Test
    void shareModalUsesSelectableMiniContactCardsAndVirtualizedAllContacts() throws Exception {
        String source = Files.readString(CONTROLLER);
        String editor = source.substring(source.indexOf("private final class SharedWithEditor"), source.indexOf("private record ShareDetails"));
        assertTrue(editor.contains("ContactCardFactory.Variant.MINI"));
        assertTrue(editor.contains("case-link-selectable-contact-card"));
        assertTrue(editor.contains("case-link-selection-checkmark"));
        assertTrue(editor.contains("Selected "));
        assertTrue(editor.contains("FlowPane caseRows"));
        assertTrue(editor.contains("new javafx.scene.control.ListView<>(filtered)"));
        assertTrue(editor.contains("allList.setCellFactory"));
        assertTrue(editor.contains("setGraphic(null)"));
        assertTrue(editor.contains("setAccessibleText(null)"));
        assertFalse(editor.contains("ActionButtonFactory.cardAction(o.displayName()"));
    }

    @Test
    void linkDialogUsesDesignSystemSurfacesAndAdaptiveSummarySizing() throws Exception {
        String source = Files.readString(CONTROLLER);
        String css = Files.readString(CSS);
        assertTrue(source.contains("case-link-dialog-form"));
        assertTrue(source.contains("case-link-dialog-scroll"));
        assertTrue(source.contains("case-link-dialog-section"));
        assertTrue(source.contains("adaptiveContactScrollPane(cards, 176)"));
        assertTrue(source.contains("case-link-shared-with-summary"));
        assertTrue(source.contains("if (active.isEmpty())"));
        assertTrue(css.contains("-shale-color-section-surface"));
        assertTrue(css.contains("case-link-dialog-scroll > .viewport"));
    }

    @Test
    void fullAndCompactLinkCardsRenderEmbeddedMiniContactCardsOnly() throws Exception {
        String card = Files.readString(CARD);
        assertTrue(card.contains("if (variant == Variant.FULL) addSharedWith(card, link, false)"));
        assertTrue(card.contains("if (variant == Variant.COMPACT) addSharedWith(card, link, true)"));
        assertFalse(card.contains("if (variant == Variant.MINI) addSharedWith"));
        assertTrue(card.contains("ContactCardFactory.Variant.MINI"));
        assertTrue(card.contains("case-link-embedded-contact-card"));
        assertTrue(card.contains("cardNode.setMouseTransparent(true)"));
        assertFalse(card.contains("Collectors.joining"));
        assertFalse(card.contains("Shared: "));
    }

    @Test
    void contactCardsCanBeDisplayOnlyWithoutNavigating() throws Exception {
        String contact = Files.readString(CONTACT_CARD);
        assertTrue(contact.contains("setInteractive(boolean interactive)"));
        assertTrue(contact.contains("if (interactive && onOpen != null && contactId != null)"));
        assertTrue(contact.contains("setCursor(interactive ? Cursor.HAND : Cursor.DEFAULT)"));
    }
}
