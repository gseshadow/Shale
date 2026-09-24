package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

final class OrganizationTypeManagementContractTest {
    private static final Path SETTINGS=Path.of("src/main/resources/fxml/settings.fxml");
    private static final Path ORGANIZATION=Path.of("src/main/resources/fxml/organization.fxml");
    private static final Path SETTINGS_CONTROLLER=Path.of("src/main/java/com/shale/ui/controller/SettingsController.java");
    private static final Path ORGANIZATION_CONTROLLER=Path.of("src/main/java/com/shale/ui/controller/OrganizationController.java");

    @Test void settingsIsCompactAndDoesNotEagerlyConstructTheManager() throws Exception {
        var document=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(SETTINGS.toFile());
        Element button=element(document,"organizationTypesRow");
        assertNotNull(button,"Settings must expose the compact Organization Types manager action");
        assertEquals("Manage",button.getAttribute("actionText"));
        String fxml=Files.readString(SETTINGS),controller=Files.readString(SETTINGS_CONTROLLER);
        assertTrue(fxml.contains("Manage organization types, colors, and availability."));
        assertFalse(fxml.contains("organizationTypeAdministrationContent"));
        assertFalse(controller.contains("new OrganizationTypeAdminPane"),"Settings must construct only the launcher on demand");
        assertTrue(controller.contains("new OrganizationTypeManagementLauncher"));
    }

    @Test void organizationContextUsesTheSameAdminGatedLauncherAndNavigationSafeRefresh() throws Exception {
        var document=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ORGANIZATION.toFile());
        assertNotNull(element(document,"manageOrganizationTypesButton"),"management belongs beside the Organization type chips");
        String source=Files.readString(ORGANIZATION_CONTROLLER);
        assertTrue(source.contains("new OrganizationTypeManagementLauncher"));
        assertTrue(source.contains("if(!isAdminUser()"),"direct invocation must retain administrator gating");
        assertTrue(source.contains("final int capturedOrganizationId=organizationId"));
        assertTrue(source.contains("result.changed()&&Objects.equals(organizationId,capturedOrganizationId)"));
        assertTrue(source.contains("listEffectiveOrganizationTypes(requestedTenant)")
                && source.contains("getOrganizationTypeProfile(requestedId,requestedTenant)"),
                "a committed change must reload definitions and the captured assignment profile authoritatively");
    }

    @Test void launcherOwnsOnlyCompositionAndThePaneNeverOwnsItsExecutor() throws Exception {
        String launcher=Files.readString(Path.of("src/main/java/com/shale/ui/controller/OrganizationTypeManagementLauncher.java"));
        String pane=Files.readString(Path.of("src/main/java/com/shale/ui/controller/OrganizationTypeAdminPane.java"));
        assertTrue(launcher.contains("DefinitionManagementSession")&&launcher.contains("new OrganizationTypeAdminPane"));
        assertFalse(pane.contains("Executors.new")||pane.contains("shutdownNow")||pane.contains("OrganizationOrganizationTypes"));
        assertTrue(pane.contains("changed.markCommitted()"),"only committed mutations may accumulate the changed result");
    }

    private static Element element(org.w3c.dom.Document document,String id){
        for(String tag:new String[]{"SettingsManagementRow","Button"}) { var nodes=document.getElementsByTagName(tag);
        for(int i=0;i<nodes.getLength();i++){Element e=(Element)nodes.item(i);if(id.equals(e.getAttribute("fx:id")))return e;} }
        return null;
    }
}
