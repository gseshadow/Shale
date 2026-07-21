package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.ui.util.ExternalBrowserHelper;

final class CaseLinksPhase4UiTest {
    @Test
    void caseLinksTabUsesExistingCaseNavigationAndLifecycleGuards() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        String fxml = Files.readString(Path.of("src/main/resources/fxml/case.fxml"));

        assertTrue(source.contains("\"Links\""));
        assertTrue(source.contains("case \"Links\" -> showLinksTab();"));
        assertTrue(source.contains("case \"Links\" -> \"LINKS\";"));
        assertTrue(fxml.contains("fx:id=\"caseLinksTabPane\""));
        assertTrue(fxml.contains("fx:id=\"addCaseLinkButton\""));
        assertTrue(source.contains("caseService.listCaseLinks(activeCaseId, tenantId)"));
        assertTrue(source.contains("caseLinkExecutor.submit(() ->"));
        assertTrue(source.contains("Platform.runLater(() ->"));
        assertTrue(source.contains("generation != caseLinksLoadGeneration"));
        assertTrue(source.contains("caseId == null || caseId != activeCaseId"));
        assertTrue(source.contains("resetCaseLinksState();"));
    }

    @Test
    void caseLinkCommandsForwardAppStateIdsCaseIdsAndRowVersions() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        assertTrue(source.contains("appState.getShaleClientId()"));
        assertTrue(source.contains("appState.getUserId()"));
        assertTrue(source.contains("new CaseServicePort.CreateCaseLinkWithSharesCommand(tenantId, actorId, activeCaseId"));
        assertTrue(source.contains("input.shareAdds()"));
        assertTrue(source.contains("new CaseServicePort.UpdateCaseLinkWithSharesCommand(tenantId, actorId, activeCaseId, link.caseLinkId(), link.externalLinkId()"));
        assertTrue(source.contains("null, input.notes(), null, link.caseLinkRowVer(), link.externalLinkRowVer(), input.shareAdds(), input.shareUpdates(), input.shareRemovals()"));
        assertTrue(source.contains("new CaseServicePort.SetPrimaryCaseLinkCommand(tenantId, actorId, activeCaseId, link.caseLinkId())"));
        assertTrue(source.contains("new CaseServicePort.DeleteCaseLinkCommand(tenantId, actorId, activeCaseId, link.caseLinkId(), link.caseLinkRowVer())"));
        assertTrue(source.contains("new CaseServicePort.ReorderCaseLinksCommand(tenantId, actorId, activeCaseId, ids)"));
        assertTrue(source.contains("caseService.listLinkTypes(tenantId, false)"));
        assertTrue(source.contains("input.displayName()"));
        assertTrue(source.contains("trimLimit(name, \"Display name\", 255, true)"));
        assertTrue(source.contains("trimLimit(url, \"URL\", 2048, true)"));
        assertTrue(source.contains("trimLimit(notes, \"Notes\", 2000, false)"));
        assertTrue(source.contains("if (!selected.active()) throw new IllegalArgumentException"));
    }

    @Test
    void primaryDeleteAndReorderRelyOnServiceRefresh() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        String card = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/CaseLinkCardFactory.java"));
        assertTrue(card.contains("if (!link.primary()) footer.getChildren().add"));
        assertFalse(card.contains("Move Up"));
        assertFalse(card.contains("Move Down"));
        assertTrue(source.contains("caseService.listCaseLinks(activeCaseId, tenantId)"));
        assertTrue(source.contains("ids.stream().distinct().count() != ids.size()"));
        assertTrue(source.contains("java.util.Collections.swap(ids, index, target)"));
        assertTrue(source.contains("AppDialogs.showConfirmation"));
    }

    @Test
    void urlSafetyAllowsOnlySafeHttpBrowserLaunches() {
        List<URI> opened = new ArrayList<>();
        ExternalBrowserHelper helper = new ExternalBrowserHelper(opened::add);
        helper.openHttpOrHttps(" https://Example.com/path?q=One#Frag ");
        assertEquals(URI.create("https://Example.com/path?q=One#Frag"), opened.getFirst());
        assertThrows(IllegalArgumentException.class, () -> ExternalBrowserHelper.validateHttpOrHttps("file:///tmp/x"));
        assertThrows(IllegalArgumentException.class, () -> ExternalBrowserHelper.validateHttpOrHttps("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> ExternalBrowserHelper.validateHttpOrHttps("data:text/plain,hi"));
        assertThrows(IllegalArgumentException.class, () -> ExternalBrowserHelper.validateHttpOrHttps("https://user:pass@example.com"));
        assertThrows(IllegalArgumentException.class, () -> ExternalBrowserHelper.validateHttpOrHttps("https://example.com/\nnext"));
        assertThrows(IllegalArgumentException.class, () -> ExternalBrowserHelper.validateHttpOrHttps("https:///missing-host"));
        ExternalBrowserHelper failing = new ExternalBrowserHelper(uri -> { throw new UnsupportedOperationException("No browser"); });
        assertThrows(IllegalStateException.class, () -> failing.openHttpOrHttps("https://example.com"));
    }

    @Test
    void linkTypePresentationUsesDatabaseColorPills() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        String card = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/CaseLinkCardFactory.java"));
        assertTrue(card.contains("LinkTypeIndicatorFactory.createLinkTypePill(link.linkTypeName(), link.linkTypeColor()"));
        assertTrue(source.contains("ColoredLookupComboBoxCellFactory.configure(type, LinkTypeDto::name, LinkTypeDto::color)"));
        assertTrue(source.contains("currentLink.linkTypeName() + \" (unavailable)\""));
    }
}
