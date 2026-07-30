package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class CaseLinksSemanticControlMigrationTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path CARD = Path.of("src/main/java/com/shale/ui/component/factory/CaseLinkCardFactory.java");

    @Test void classifiesTabDialogCardAndShareActions() throws Exception {
        String controller = Files.readString(CONTROLLER);
        String card = Files.readString(CARD);
        assertTrue(controller.contains("ControlStyles.apply(addCaseLinkButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD)"));
        assertTrue(controller.contains("ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD"));
        assertTrue(controller.contains("ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD"));
        assertTrue(card.contains("semantic(\"Set Primary\", ControlStyles.Purpose.SECONDARY"));
        assertTrue(card.contains("semantic(\"Delete\", ControlStyles.Purpose.DANGER"));
        assertTrue(card.contains("semantic(\"Edit\", ControlStyles.Purpose.GHOST"));
        assertTrue(card.contains("ControlStyles.Size.SMALL"));
        assertTrue(controller.contains("caseLinkAction(\"Clear\", ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL"));
        assertTrue(controller.contains("caseLinkAction(share.shareId > 0 ? \"Unshare\" : \"Remove\", ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL"));
        assertFalse(controller.contains("ActionButtonFactory.cardAction(\"Share Link\""));
        assertFalse(card.contains("ActionButtonFactory.cardAction"));
        assertFalse(card.contains("ActionButtonFactory.danger"));
    }

    @Test void optsDialogFieldsIntoFormAndInvalidStateWithoutChangingUrlRules() throws Exception {
        String source = Files.readString(CONTROLLER);
        for (String field : new String[]{"type", "name", "url", "description", "notes", "date", "time"}) {
            assertTrue(source.contains("ControlStyles.formControl(" + field + ")"), field);
        }
        assertTrue(source.contains("ControlStyles.setInvalid(type"));
        assertTrue(source.contains("ControlStyles.setInvalid(name"));
        assertTrue(source.contains("ControlStyles.setInvalid(url"));
        assertTrue(source.contains("CaseLinkUrlNormalizer.normalize(text)"));
        assertTrue(source.contains("validationVisible.set(false); updateInvalid.run()"));
        String links = source.substring(source.indexOf("private Optional<CaseLinkInput> showCaseLinkDialog"), source.indexOf("private record CaseLinkShareLiveChange"));
        assertFalse(links.contains("setStyle(\"-fx-background-color"));
    }

    @Test void preservesClickableCardNavigationAndEmbeddedActionIsolation() throws Exception {
        String card = Files.readString(CARD);
        assertTrue(card.contains("safeActions.open().run(); event.consume()"));
        assertTrue(card.contains("if (!event.isConsumed())"));
        assertTrue(card.contains("button.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume)"));
        assertTrue(card.contains("event.getTarget() == card"));
        assertTrue(card.contains("External") || Files.readString(CONTROLLER).contains("externalBrowserHelper.openHttpOrHttps(link.url())"));
        assertTrue(card.contains("LinkTypeIndicatorFactory.createLinkTypePill"));
        assertTrue(card.contains("applyTypeColorStyle(card, link.linkTypeColor(), variant)"));
    }
}
