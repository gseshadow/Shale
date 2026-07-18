package com.shale.ui.controller;

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
        assertTrue(editor.contains("changePrimaryLegalAssistantButton).map(options::get).ifPresent(row -> savePrimaryLegalAssistantField(row.id()))"));

        String saver = method(source, "private void savePrimaryLegalAssistantField", "private void onChangeResponsibleAttorney");
        assertTrue(saver.contains("caseDao.setPrimaryLegalAssistant(activeCaseId, appState.getShaleClientId(), userId)"));
        assertTrue(saver.contains("stage = \"LIVE_PUBLISH\""));
        assertTrue(saver.contains("logPrimaryLegalAssistantSaveThrowable(\"LIVE_PUBLISH\", publishFailure)"));
        assertTrue(saver.contains("logPrimaryLegalAssistantSaveThrowable(\"UI_REFRESH\", refreshFailure)"));
        assertTrue(saver.contains("catch (Throwable ex)"));
        assertTrue(saver.contains("publishCaseFieldUpdated(activeCaseId, \"primaryLegalAssistantUserId\", userId)"));
        assertTrue(saver.contains("reloadCurrentCaseForViewMode()"), "Overview should refresh after save without navigating away");
        assertTrue(source.contains("[PRIMARY_LEGAL_ASSISTANT SAVE ERROR] stage="));
        assertTrue(source.contains("throwable.printStackTrace(System.err)"));
    }

    private static String method(String source, String startNeedle, String endNeedle) {
        int start = source.indexOf(startNeedle);
        int end = source.indexOf(endNeedle, start + startNeedle.length());
        assertTrue(start >= 0, "Missing start: " + startNeedle);
        assertTrue(end > start, "Missing end: " + endNeedle);
        return source.substring(start, end);
    }
}
