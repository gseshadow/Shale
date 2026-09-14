package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.LinkTypeDto;

final class LinkTypeManagementContractTest {
    private static String read(String path) { try { return Files.readString(Path.of(path)); } catch (Exception ex) { throw new AssertionError(ex); } }
    private static final String PANE=read("src/main/java/com/shale/ui/controller/LinkTypeManagementPane.java");
    private static final String SETTINGS=read("src/main/java/com/shale/ui/controller/SettingsController.java");
    private static final String CASE=read("src/main/java/com/shale/ui/controller/CaseController.java");

    @Test void settingsIsCompactLazyAndBothContextsShareTheLauncher() {
        String fxml=read("src/main/resources/fxml/settings.fxml");
        assertTrue(fxml.contains("Manage case-link types, colors, and availability."));
        assertTrue(fxml.contains("fx:id=\"manageLinkTypesButton\""));
        assertFalse(fxml.contains("fx:id=\"linkTypeCardsContainer\""));
        assertTrue(SETTINGS.contains("new LinkTypeManagementLauncher"));
        assertTrue(CASE.contains("new LinkTypeManagementLauncher"));
        assertTrue(read("src/main/java/com/shale/ui/controller/LinkTypeManagementLauncher.java").contains("new LinkTypeManagementPane"));
        assertFalse(SETTINGS.contains("listLinkTypesForAdministration"));
    }

    @Test void overlayRowsPreserveMaskingAndExposeDistinctActions() {
        var rows=LinkTypeManagementPane.buildRows(List.of(type(1,null,"shared",true,false,"Global"),type(2,7,"shared",false,false,"Override"),
                type(3,7,null,true,false,"Custom"),type(4,8,null,true,false,"Other")),7);
        assertEquals(List.of("Custom","Override"),rows.stream().map(LinkTypeManagementPane.ViewRow::name).toList());
        assertTrue(rows.get(0).custom()); assertFalse(rows.get(1).custom()); assertFalse(rows.get(1).global());
        assertTrue(PANE.contains("Remove Custom")); assertTrue(PANE.contains("Reset Override"));
        assertTrue(PANE.contains("Customize"));
    }

    @Test void commandPreservesContextIdentitySystemKeyStateAndRowVersion() {
        byte[] rowVer={1,2}; var input=new LinkTypeManagementPane.Input("Name","#112233",false);
        var command=LinkTypeManagementPane.command(9,input,"stable_key",rowVer,7,8);
        assertAll(()->assertEquals(9,command.id()),()->assertEquals(7,command.shaleClientId()),()->assertEquals(8,command.actorUserId()),
                ()->assertEquals("stable_key",command.systemKey()),()->assertEquals("Name",command.name()),()->assertEquals("#112233",command.color()),
                ()->assertFalse(command.active()),()->assertArrayEquals(rowVer,command.expectedRowVer()));
    }

    @Test void lifecycleGuardsPublicationAndContextRefresh() {
        assertTrue(PANE.contains("executor.execute")); assertTrue(PANE.contains("Platform.runLater"));
        assertTrue(PANE.replace(" ", "").contains("compareAndSet(false,true)")); assertTrue(PANE.contains("changed.markCommitted()"));
        assertTrue(PANE.indexOf("changed.markCommitted()") < PANE.indexOf("publisher.publish"));
        assertTrue(PANE.contains("generation==loadGeneration")); assertTrue(PANE.contains("disposed"));
        assertTrue(CASE.contains("!appState.isAdmin()")); assertTrue(CASE.contains("!result.changed()"));
        assertTrue(CASE.contains("caseId != openingCaseId")); assertTrue(CASE.contains("loadCaseLinksAsync(\"Link types refreshed.\")"));
        String method=CASE.substring(CASE.indexOf("private void openLinkTypeManagement"),CASE.indexOf("private void resetCaseDatesState"));
        assertFalse(method.contains("createCaseLink")); assertFalse(method.contains("updateCaseLink")); assertFalse(method.contains("deleteCaseLink"));
    }

    private static LinkTypeDto type(int id,Integer tenant,String key,boolean active,boolean deleted,String name){return new LinkTypeDto(id,tenant,name,"#112233",active,deleted,key,new byte[]{1});}
}
