package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class RequestedFromWorkflowDialogTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test void requestedFromPreservesModeThenTypeFlowAndOmitsCasePartyFields() throws Exception {
        String s = read("src/main/java/com/shale/ui/controller/support/RequestedFromWorkflowDialog.java");
        assertTrue(s.contains("Select Existing or Create New"));
        assertTrue(s.contains("Select Existing"));
        assertTrue(s.contains("Create New"));
        assertTrue(s.contains("Contact or Organization"));
        assertTrue(s.contains("Contact"));
        assertTrue(s.contains("Organization"));
        assertFalse(s.contains("Party Role"));
        assertFalse(s.contains("Affiliation"));
        assertFalse(s.contains("Primary"));
        assertFalse(s.contains("Case Party notes"));
    }

    @Test void selectExistingUsesSearchPromptsAndMiniCardsInsteadOfPlainTextRows() throws Exception {
        String s = read("src/main/java/com/shale/ui/controller/support/RequestedFromWorkflowDialog.java");
        assertTrue(s.contains("Search contacts"));
        assertTrue(s.contains("Search organizations"));
        assertTrue(s.contains("ContactCardFactory.Variant.MINI"));
        assertTrue(s.contains("OrganizationCardFactory.Variant.MINI"));
        assertTrue(s.contains("setGraphic(card)"));
        assertFalse(s.contains("setText(empty || item == null ? null : item.label())"));
    }

    @Test void requestedFromLoadsCompleteTenantDirectoryAndKeepsAddPartyFilteringSeparate() throws Exception {
        String c = read("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
        String d = read("../shale-data/src/main/java/com/shale/data/dao/CaseDao.java");
        assertTrue(c.contains("caseDao.findSelectableContactsForTenant()"));
        assertTrue(c.contains("caseDao.findSelectableOrganizationsForTenant()"));
        assertFalse(c.contains("findLinkableContacts(cid())"));
        assertFalse(c.contains("findLinkableOrganizations(cid())"));
        assertTrue(d.substring(d.indexOf("public List<SelectableContactRow> findSelectableContactsForTenant()"), d.indexOf("public List<PartyRoleRow> listPartyRoles()"))
                .contains("ct.ShaleClientId = ?"));
        assertTrue(d.substring(d.indexOf("public List<SelectableContactRow> findSelectableContactsForTenant()"), d.indexOf("public List<PartyRoleRow> listPartyRoles()"))
                .contains("ct.IsDeleted = 0 OR ct.IsDeleted IS NULL"));
        assertFalse(d.substring(d.indexOf("public List<SelectableContactRow> findSelectableContactsForTenant()"), d.indexOf("public List<PartyRoleRow> listPartyRoles()"))
                .contains("NOT EXISTS"));
    }

    @Test void searchIsResponsiveCaseInsensitivePartialAndAsync() throws Exception {
        String s = read("src/main/java/com/shale/ui/controller/support/RequestedFromWorkflowDialog.java");
        assertTrue(s.contains("Task<DirectoryData>"));
        assertTrue(s.contains("executor.execute(task)"));
        assertTrue(s.contains("toLowerCase(Locale.ROOT)"));
        assertTrue(s.contains("haystack(o).contains(q)"));
        assertTrue(s.contains("Loading…"));
        assertTrue(s.contains("No records match the search."));
        assertTrue(s.contains("Loading failed."));
    }

    @Test void addReturnsSelectedOrCreatedEntityWithoutCasePartyOrMaterialRequestMutation() throws Exception {
        String s = read("src/main/java/com/shale/ui/controller/support/RequestedFromWorkflowDialog.java");
        String c = read("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
        assertTrue(s.contains("new Selection(state.entityType,state.selected.id(),state.selected.label(),false"));
        assertTrue(c.contains("return id==null?null:newRequestedFromSelection(r,id,label)"));
        assertFalse(s.contains("addCaseParty"));
        assertFalse(c.contains("caseDao.addCaseParty"));
        assertFalse(c.contains("createMaterialRequest"));
        assertTrue(c.contains("Button save=ActionButtonFactory.primary(\"Save\",e->{ })"));
    }


    @Test void requestedFromPresentationLayoutAndSelectionStylesAreScoped() throws Exception {
        String c = read("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
        String d = read("src/main/java/com/shale/ui/controller/support/RequestedFromWorkflowDialog.java");
        String css = read("src/main/resources/css/app.css");
        assertTrue(c.contains("root.setPrefSize(NEW_REQUEST_WIDTH,NEW_REQUEST_HEIGHT)"));
        assertTrue(c.contains("root.setMinSize(NEW_REQUEST_MIN_WIDTH,NEW_REQUEST_MIN_HEIGHT)"));
        assertTrue(c.indexOf("requestedFromDisplay.getChildren().addAll(card,requestedFromActions)") > 0);
        assertTrue(c.contains("else requestedFromDisplay.getChildren().add(requestedFromActions)"));
        assertTrue(c.contains("removeRequestedFrom.setVisible(v!=null)"));
        assertTrue(c.contains("applyNewRequestStageSize(stage,(Region)stage.getScene().getRoot())"));
        assertTrue(d.contains("results.setPrefHeight(420)"));
        assertTrue(d.contains("results.setMinHeight(300)"));
        assertTrue(d.contains("VBox.setVgrow(results, Priority.ALWAYS)"));
        assertTrue(d.contains("applyWorkflowScreenSizing(dialog, owner, state.step, state.mode, box)"));
        assertTrue(d.contains("WindowSizingUtil.sizeModalStage(stage, owner, prefWidth, prefHeight, minWidth, minHeight)"));
        assertTrue(d.contains("Platform.runLater(() ->"));
        assertTrue(d.contains("stage.setResizable(true)"));
        assertTrue(css.contains(".requested-from-results .list-cell:filled:selected"));
        assertTrue(css.contains("-fx-background-color: transparent;"));
        assertTrue(css.contains("-fx-border-color: rgba(74, 131, 210, 0.82)"));
        assertFalse(css.contains(".list-cell:filled:selected {\n    -fx-background-color: white"));
    }


    @Test void workflowSizingUpdatesActualStageAfterContentIsInstalled() throws Exception {
        String d = read("src/main/java/com/shale/ui/controller/support/RequestedFromWorkflowDialog.java");
        int renderContent = d.indexOf("box.getChildren().addAll(search, status, results)");
        int sizing = d.indexOf("applyWorkflowScreenSizing(dialog, owner, state.step, state.mode, box)");
        int helper = d.indexOf("static void applyWorkflowScreenSizing");
        assertTrue(renderContent >= 0 && sizing > renderContent);
        assertTrue(helper > sizing);
        assertTrue(d.contains("boolean selectFinal = step == 3 && \"select\".equals(mode)"));
        assertTrue(d.contains("boolean createFinal = step == 3 && \"create\".equals(mode)"));
        assertTrue(d.contains("double prefHeight = selectFinal ? 700 : createFinal ? 520 : 300"));
        assertTrue(d.contains("double minHeight = selectFinal ? 600 : createFinal ? 440 : 280"));
        assertTrue(d.contains("Window window = pane.getScene() == null ? null : pane.getScene().getWindow()"));
        assertTrue(d.contains("pane.resize(stage.getWidth(), stage.getHeight())"));
        assertTrue(d.contains("WindowSizingUtil.constrainToVisualBounds(stage, owner)"));
        assertTrue(d.contains("dialog.setOnShown(e -> applyWorkflowScreenSizing"));
    }

    @Test void normalAddPartyStillUsesLinkableCandidatesAndCasePartyFields() throws Exception {
        String c = read("src/main/java/com/shale/ui/controller/CaseController.java");
        String p = read("src/main/java/com/shale/ui/controller/support/PartyAddWorkflowDialog.java");
        assertTrue(c.contains("caseDao.addCaseParty("));
        assertTrue(p.contains("Party Role"));
        assertTrue(p.contains("Affiliation"));
        assertTrue(p.contains("Primary"));
        assertTrue(p.contains("Notes"));
        assertTrue(p.contains("AddPartyDraft"));
    }
}
