package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinksPhase5OverviewTest {
    private static final Path CASE_CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path CASE_FXML = Path.of("src/main/resources/fxml/case.fxml");

    @Test
    void overviewPrimaryLinkSectionUsesExistingOverviewStylesAndReadOnlyActions() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);
        String fxml = Files.readString(CASE_FXML);

        assertTrue(fxml.contains("text=\"Primary Link\""));
        assertTrue(fxml.contains("fx:id=\"ovPrimaryLinkBox\""));
        assertTrue(fxml.contains("fx:id=\"ovPrimaryLinkStatusLabel\""));
        assertTrue(fxml.contains("case-overview-section"));
        assertTrue(fxml.contains("shale-surface-section"));
        assertTrue(source.contains("No primary link has been selected for this case."));
        assertTrue(source.contains("ActionButtonFactory.cardAction(\"Open Link\""));
        assertTrue(source.contains("ActionButtonFactory.cardAction(\"Manage Links\""));
        assertTrue(source.contains("case-overview-primary-link-card"));
        assertFalse(source.contains("ActionButtonFactory.cardAction(\"Edit\", e -> onEditOverviewPrimaryLink"));
        assertFalse(source.contains("ActionButtonFactory.danger(\"Delete\", e -> onDeleteOverviewPrimaryLink"));
        assertFalse(source.contains("onSetPrimaryOverviewPrimaryLink"));
        assertFalse(source.contains("onMoveOverviewPrimaryLink"));
    }

    @Test
    void overviewPrimaryLinkLoadsFocusedPrimaryThroughServicePortWithTenantAndStaleGuards() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);

        assertTrue(source.contains("caseService.getPrimaryCaseLink(activeCaseId, tenantId)"));
        assertTrue(source.contains("Integer tenantId = appState.getShaleClientId()"));
        assertTrue(source.contains("final int activeCaseId = caseId"));
        assertTrue(source.contains("new Thread(() ->"));
        assertTrue(source.contains("case-overview-primary-link-load-"));
        assertTrue(source.contains("Platform.runLater(() ->"));
        assertTrue(source.contains("generation != overviewPrimaryLinkLoadGeneration"));
        assertTrue(source.contains("caseId == null || caseId != activeCaseId"));
        assertTrue(source.contains("resetOverviewPrimaryLinkState();"));
        assertTrue(source.contains("overviewPrimaryLinkLoadedOnce && !overviewPrimaryLinkStale"));
        assertTrue(source.contains("primary == null ? Optional.empty() : primary"));
        assertTrue(source.contains("renderOverviewPrimaryLinkFailure(\"Failed to load primary link. \" + rootMessage(ex))"));
        assertFalse(source.contains("listCaseLinks(activeCaseId, tenantId).stream"));
        assertFalse(source.contains("caseDao.getPrimaryCaseLink"));
    }

    @Test
    void overviewPrimaryLinkPresentationPreservesTypeColorPrimaryTextAndSafeUrlOpening() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);

        assertTrue(source.contains("blankTo(link.displayName(), \"Untitled link\")"));
        assertTrue(source.contains("LinkTypeIndicatorFactory.createLinkTypePill(link.linkTypeName(), link.linkTypeColor(), LinkTypeIndicatorFactory.PillSize.COMPACT)"));
        assertTrue(source.contains("new Label(\"Primary\")"));
        assertTrue(source.contains("blankTo(link.url(), \"—\")"));
        assertTrue(source.contains("url.setWrapText(true)"));
        assertTrue(source.contains("url.setTextOverrun(OverrunStyle.ELLIPSIS)"));
        assertTrue(source.contains("externalBrowserHelper.openHttpOrHttps(link.url())"));
        assertFalse(source.contains("Desktop.getDesktop().browse"));
        assertFalse(source.contains("getHostServices().showDocument"));
    }

    @Test
    void overviewManageLinksUsesExistingSectionRoutingAndPreservesCurrentCase() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);

        assertTrue(source.contains("private void navigateToCaseLinksForManagement()"));
        assertTrue(source.contains("onSectionSelected(\"Links\", true)"));
        assertTrue(source.contains("case \"Links\" -> \"LINKS\";"));
        assertTrue(source.contains("case \"Links\" -> showLinksTab();"));
        assertFalse(source.contains("new Tab(\"Links\")"));
        assertFalse(source.contains("this.caseId = null;\n\t\tonSectionSelected(\"Links\""));
    }

    @Test
    void caseLinkMutationsInvalidateOverviewPrimaryLinkOnlyAfterSuccessfulPersistence() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);

        assertTrue(source.contains("private void invalidateOverviewPrimaryLinkAfterCaseLinkMutation()"));
        assertTrue(source.contains("overviewPrimaryLinkStale = true"));
        assertTrue(source.contains("overviewPrimaryLinkLoadedOnce = false"));
        assertTrue(source.contains("if (\"Overview\".equals(activeSectionName)) loadOverviewPrimaryLinkIfNeeded();"));
        assertTrue(source.contains("action.call();\n\t\t\tinvalidateOverviewPrimaryLinkAfterCaseLinkMutation();\n\t\t\tloadCaseLinksAsync(successMessage);"));
        assertTrue(source.contains("createCaseLink"));
        assertTrue(source.contains("updateCaseLink"));
        assertTrue(source.contains("setPrimaryCaseLink"));
        assertTrue(source.contains("deleteCaseLink"));
        assertTrue(source.contains("catch (Exception ex)"));
    }

    @Test
    void phase5DoesNotAddDeferredApiWebLiveUpdateOrMigrationScope() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);
        assertFalse(source.contains("@GetMapping"));
        assertFalse(source.contains("@PostMapping"));
        assertFalse(source.contains("Case UpdatedAt"));
        assertFalse(source.contains("CREATE TABLE dbo.CaseLinks"));
    }
}
