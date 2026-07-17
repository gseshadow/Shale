package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.LinkTypeDto;

final class CaseLinksPhase511ReliabilityTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path CARD = Path.of("src/main/java/com/shale/ui/component/factory/CaseLinkCardFactory.java");
    private static final Path CSS = Path.of("src/main/resources/css/foundation/cards.css");

    @Test
    void dialogValidationReturnsValidatedInputAndRejectsInvalidWithoutServiceMutation() {
        LinkTypeDto active = new LinkTypeDto(3, null, "Court", "#2563EB", true, false, null, null);
        CaseController.CaseLinkInput input = CaseController.validateCaseLinkDialogInput(active, " Court ", " https://example.com/a ", " desc ", true, " notes ");
        assertEquals(3, input.linkType().id());
        assertEquals("Court", input.displayName());
        assertEquals("https://example.com/a", input.url());
        assertEquals("desc", input.description());
        assertEquals("notes", input.notes());
        assertThrows(IllegalArgumentException.class, () -> CaseController.validateCaseLinkDialogInput(null, "Name", "https://example.com", null, false, null));
        assertThrows(IllegalArgumentException.class, () -> CaseController.validateCaseLinkDialogInput(new LinkTypeDto(3, null, "Court", "#2563EB", false, false, null, null), "Name", "https://example.com", null, false, null));
        assertThrows(IllegalArgumentException.class, () -> CaseController.validateCaseLinkDialogInput(active, " ", "https://example.com", null, false, null));
        assertThrows(IllegalArgumentException.class, () -> CaseController.validateCaseLinkDialogInput(active, "Name", "file:///tmp/x", null, false, null));
    }

    @Test
    void sourceUsesDialogEventFilterAsyncMutationServiceReloadAndStaleGuards() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertTrue(source.contains("lookupButton(ButtonType.OK)"));
        assertTrue(source.contains("addEventFilter(javafx.event.ActionEvent.ACTION"));
        assertTrue(source.contains("event.consume()"));
        assertTrue(source.contains("Case Link dialog validation blocked save"));
        assertTrue(source.contains("caseLinkExecutor.submit(() ->"));
        assertTrue(source.contains("caseLinkMutationInFlight.compareAndSet(false, true)"));
        assertTrue(source.contains("Object result = action.call();"));
        assertTrue(source.contains("caseService.listCaseLinks(activeCaseId, tenantId)"));
        assertTrue(source.contains("renderCaseLinks(successMessage)"));
        assertTrue(source.contains("renderCaseLinks(null)"));
        assertTrue(source.contains("caseId == null || caseId != activeCaseId || requestId != caseLinksLoadGeneration"));
        assertTrue(source.contains("Case Link mutation duplicate blocked"));
        assertTrue(source.contains("LOG.warn(\"Case Link mutation failure"));
        assertFalse(source.contains("action.call();\n\t\t\tinvalidateOverviewPrimaryLinkAfterCaseLinkMutation();\n\t\t\tloadCaseLinksAsync(successMessage);"));
    }

    @Test
    void cssUsesFixedRailWidthsAndCardKeepsDatabaseDrivenColors() throws Exception {
        String card = Files.readString(CARD);
        String css = Files.readString(CSS);
        assertFalse(card.contains("-shale-link-type-rail-width"));
        assertFalse(css.contains("-shale-link-type-rail-width"));
        assertTrue(css.contains("-fx-border-width: 1px 1px 1px 5px"));
        assertTrue(css.contains("-fx-border-width: 1px 1px 1px 4px"));
        assertTrue(css.contains("-fx-border-width: 1px 1px 1px 3px"));
        assertTrue(card.contains("ColorUtil.toCssBackgroundColor(storedColor)"));
        assertTrue(card.contains("ColorUtil.toCssRgba(storedColor"));
        assertTrue(css.contains("-shale-link-type-accent"));
        assertTrue(css.contains("-shale-link-type-wash"));
    }
}
