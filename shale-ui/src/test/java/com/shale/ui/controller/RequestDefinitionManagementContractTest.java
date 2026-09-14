package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

final class RequestDefinitionManagementContractTest {
    private static String read(String p){try{return Files.readString(Path.of(p));}catch(Exception e){throw new AssertionError(e);}}
    private static final String FXML=read("src/main/resources/fxml/settings.fxml");
    private static final String SETTINGS=read("src/main/java/com/shale/ui/controller/SettingsController.java");
    private static final String PANE=read("src/main/java/com/shale/ui/controller/RequestDefinitionAdminPane.java");
    private static final String MATERIALS=read("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
    @Test void settingsUsesOneLazyRequestFieldsRow(){
        assertTrue(FXML.contains("text=\"Request Fields\""));
        assertTrue(FXML.contains("Manage material types, request methods, and request statuses."));
        assertTrue(FXML.contains("fx:id=\"manageRequestFieldsButton\""));
        assertFalse(FXML.contains("materialTypeCardsContainer"));
        assertFalse(SETTINGS.substring(SETTINGS.indexOf("private void loadAdminSectionsAsync"),SETTINGS.indexOf("private void onManageRequestFields")).contains("loadRequestLookupsAsync"));
    }
    @Test void paneOwnsThreeSemanticCategoriesAndSpecificFields(){
        assertTrue(PANE.contains("tab(\"Material Types\"")); assertTrue(PANE.contains("tab(\"Request Methods\"")); assertTrue(PANE.contains("tab(\"Request Statuses\""));
        assertTrue(PANE.contains("category==Category.MATERIAL_TYPE)form.addRow"));
        assertTrue(PANE.contains("category!=Category.REQUEST_METHOD)form.addRow"));
        assertTrue(PANE.contains("requestMethodCreateCommand")); assertTrue(PANE.contains("existing.order()"));
        assertTrue(PANE.contains("Reset Override")); assertTrue(PANE.contains("Remove Custom"));
    }
    @Test void bothContextsUseSameLauncherAndContextRefreshIsGuarded(){
        assertTrue(SETTINGS.contains("new RequestDefinitionManagementLauncher"));
        assertTrue(MATERIALS.contains("new RequestDefinitionManagementLauncher"));
        assertTrue(MATERIALS.contains("if(!result.changed())return"));
        assertTrue(MATERIALS.contains("cid()==capturedCase&&tenant()==capturedTenant&&gen.get()==capturedGeneration"));
        assertTrue(MATERIALS.contains("svc.listEffectiveMaterialTypes(capturedTenant)"));
    }
    @Test void contextualActionPreservesTheTypedRequestHeaderContract(){
        assertTrue(MATERIALS.contains("static VBox section(Label t,Button b,Label s,VBox list)"));
        assertTrue(MATERIALS.contains("VBox requestSection=section(title,newRequestButton,status,list)"));
        assertTrue(MATERIALS.contains("HBox headerContainer=(HBox)requestSection.getChildren().get(0)"));
        assertTrue(MATERIALS.contains("if(state!=null&&state.isAdmin())"));
        assertTrue(MATERIALS.contains("headerContainer.getChildren().add(manageRequestFieldsButton)"));
        assertFalse(MATERIALS.contains("section(title,headerActions,status,list)"));
    }
}
