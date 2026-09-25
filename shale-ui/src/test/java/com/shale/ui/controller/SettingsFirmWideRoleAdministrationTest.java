package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class SettingsFirmWideRoleAdministrationTest {
    @Test void administrationUsesLazyAuthorizedSharedManagementWindow() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        assertAll(
                () -> assertTrue(fxml.contains("fx:id=\"firmWideRolesRow\"")),
                () -> assertTrue(fxml.contains("title=\"Firm-wide Roles\"")),
                () -> assertTrue(controller.contains("new FirmWideRoleManagementLauncher")),
                () -> assertTrue(controller.contains("requireAdminLookupManagement(\"Firm-wide Roles\")")),
                () -> assertTrue(controller.contains("ControlAvailability.apply(manageFirmWideRolesButton")),
                () -> assertFalse(controller.contains("new FirmWideRoleAdminPane")));
    }

    @Test void closingChangedManagerInvalidatesOpenUserEditors() throws Exception {
        String settings = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        String users = Files.readString(Path.of("src/main/java/com/shale/ui/controller/UserManagementPane.java"));
        String view = Files.readString(Path.of("src/main/java/com/shale/ui/controller/UserController.java"));
        assertTrue(settings.contains("if (result.changed()) FirmWideRoleDefinitionRefresh.publish"));
        assertTrue(users.contains("FirmWideRoleDefinitionRefresh.subscribe"));
        assertTrue(view.contains("FirmWideRoleDefinitionRefresh.subscribe"));
    }
}
