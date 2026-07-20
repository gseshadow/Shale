package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseOverviewPrimaryLegalAssistantViewTest {
    @Test
    void primaryLegalAssistantRowAppearsImmediatelyBelowResponsibleAttorneyAndAboveDescription() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/case.fxml"));

        int responsible = fxml.indexOf("text=\"Responsible attorney\"");
        int legalAssistant = fxml.indexOf("text=\"Primary legal assistant\"");
        int description = fxml.indexOf("text=\"Description\"", legalAssistant);

        assertTrue(responsible >= 0, "Responsible attorney row should exist");
        assertTrue(legalAssistant > responsible, "Primary legal assistant should follow Responsible attorney");
        assertTrue(description > legalAssistant, "Description should follow Primary legal assistant");
        assertTrue(fxml.contains("fx:id=\"ovPrimaryLegalAssistantHost\""));
        assertTrue(fxml.contains("text=\"Primary legal assistant\" styleClass=\"case-overview-row-label\" GridPane.rowIndex=\"10\""));
        assertTrue(fxml.contains("text=\"Description\" styleClass=\"case-overview-row-label\" GridPane.rowIndex=\"11\""));
    }

    @Test
    void controllerRendersPrimaryLegalAssistantWithSharedUserCardFactoryAndEmptyState() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        String renderer = method(source, "private void renderPrimaryLegalAssistantMini", "private void renderPrimaryStatusMini");

        assertTrue(renderer.contains("new UserCardFactory"));
        assertTrue(renderer.contains("new UserCardModel"));
        assertTrue(renderer.contains("displayName == null || displayName.isBlank()) ? \"—\" : displayName"));
        assertTrue(renderer.contains("userCardFactory.create(model, Variant.COMPACT)"));
        assertTrue(source.contains("dto.getPrimaryLegalAssistantUserId()"));
        assertTrue(source.contains("dto.getPrimaryLegalAssistant()"));
        assertTrue(source.contains("dto.getPrimaryLegalAssistantColor()"));
    }


    @Test
    void primaryLegalAssistantEditorUsesResponsibleAttorneyEditPattern() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        assertTrue(source.contains("changePrimaryLegalAssistantButton.setOnAction(e -> onEditPrimaryLegalAssistantField())"));
        assertTrue(source.contains("setVisibleManaged(changePrimaryLegalAssistantButton, true)"));
        assertTrue(source.contains("changePrimaryLegalAssistantButton.setDisable(busy)"));

        String editor = method(source, "private void onEditPrimaryLegalAssistantField", "private Optional<String> showChoiceFieldDialog");
        assertTrue(editor.contains("caseDao.listUsersForTenant(tenantId)"));
        assertTrue(editor.contains("\"Edit primary legal assistant\""));
        assertTrue(editor.contains("currentOverview.getPrimaryLegalAssistant()"), "Dialog should preselect the current assistant display value");
        assertTrue(editor.contains("showPrimaryLegalAssistantDialog("));
        assertTrue(editor.contains("savePrimaryLegalAssistantField(row.id())"));

        String saver = method(source, "private void savePrimaryLegalAssistantField", "private void onChangeResponsibleAttorney");
        assertTrue(saver.contains("caseDao.setPrimaryLegalAssistant(activeCaseId, appState.getShaleClientId(), userId)"));
        assertTrue(saver.contains("catch (Exception ex)"));
        assertTrue(saver.contains("publishCaseFieldUpdated(activeCaseId, \"primaryLegalAssistantUserId\", userId)"));
        assertTrue(saver.contains("reloadCurrentCaseForViewMode()"), "Overview should refresh after save without navigating away");
    }

    @Test
    void temporaryPrimaryLegalAssistantSaveDiagnosticsAreRemoved() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        assertFalse(source.contains("[PRIMARY_LEGAL_ASSISTANT " + "SAVE ERROR]"));
        assertFalse(source.contains("DAO_" + "MUTATION"));
        assertFalse(source.contains("LIVE_" + "PUBLISH"));
        assertFalse(source.contains("UI_" + "REFRESH"));
        assertFalse(source.contains("logPrimaryLegalAssistantSaveThrowable"));
    }

    @Test
    void primaryLegalAssistantEditorOffersRemoveOnlyWhenCurrentAssistantExistsAndPublishesSafeClear() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        String editor = method(source, "private Optional<PrimaryLegalAssistantDialogAction> showPrimaryLegalAssistantDialog", "private Optional<String> showChoiceFieldDialog");
        assertTrue(editor.contains("boolean hasPrimaryLegalAssistant"));
        assertTrue(editor.contains("ButtonType removeType = new ButtonType(\"Remove primary legal assistant\""));
        assertTrue(editor.contains("if (hasPrimaryLegalAssistant)"));
        assertTrue(editor.contains("dialog.getDialogPane().getButtonTypes().add(removeType)"));
        assertTrue(editor.contains("app-dialog-button-danger"), "Remove should follow destructive-secondary styling convention");
        assertTrue(editor.contains("ButtonType.CANCEL, saveType"), "Save and Cancel should remain available for changing users");

        String remover = method(source, "private void removePrimaryLegalAssistantField", "private void savePrimaryLegalAssistantField");
        assertTrue(remover.contains("caseDao.removePrimaryLegalAssistant(activeCaseId, appState.getShaleClientId())"));
        assertTrue(remover.contains("publishCaseFieldUpdated(activeCaseId, \"primaryLegalAssistantUserId\", null)"));
        assertTrue(remover.contains("reloadCurrentCaseForViewMode()"));
        assertFalse(remover.contains("Map.of"), "Clearing publish path must not use Map.of with a null value");
    }

    @Test
    void practiceTeamDisplayDeduplicatesByStableUserIdButLeavesTeamEditorAssignmentsUntouched() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        String deduper = method(source, "private List<CaseDao.CaseUserTeamRow> deduplicatePracticeTeamRowsForDisplay", "private String roleLabel");
        assertTrue(deduper.contains("java.util.LinkedHashMap<Integer, CaseDao.CaseUserTeamRow> byUserId"));
        assertTrue(deduper.contains("byUserId.putIfAbsent(row.userId(), row)"), "Display dedupe should key by Users.Id, not display name");
        assertTrue(deduper.contains("isPrimaryResponsibleAttorney"));
        assertTrue(deduper.contains("isPrimaryLegalAssistant"));
        assertTrue(deduper.contains("thenComparingInt(CaseDao.CaseUserTeamRow::roleId)"));
        assertTrue(deduper.contains("thenComparingInt(CaseDao.CaseUserTeamRow::userId)"));
        assertTrue(source.contains("renderTeamCardsFromTeamRows(rows)"), "Draft/team editor path should still pass all underlying assignments before display-only consolidation");
        assertTrue(source.contains("assignedRoles = caseDao.listCaseUserRoles(activeCaseId)"), "Team editor should still receive all role rows from DAO");
        assertFalse(deduper.contains("displayName()).distinct"));
        assertFalse(deduper.contains("SELECT DISTINCT"));
    }

    private static String method(String source, String startNeedle, String endNeedle) {
        int start = source.indexOf(startNeedle);
        int end = source.indexOf(endNeedle, start + startNeedle.length());
        assertTrue(start >= 0, "Missing start: " + startNeedle);
        assertTrue(end > start, "Missing end: " + endNeedle);
        return source.substring(start, end);
    }
}
