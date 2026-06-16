package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseStatusDto;

import javafx.scene.paint.Color;

final class SettingsControllerLifecycleTest {

    @Test
    void initializeLoadsCaseStatusesWhenServiceWasInjectedBeforeFxmlInjection() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        String initialize = source.substring(source.indexOf("\t@FXML\n\tprivate void initialize()"),
                source.indexOf("\n\tpublic void init", source.indexOf("\t@FXML\n\tprivate void initialize()")));

        assertTrue(initialize.contains("if (caseService != null)"),
                "SceneManager injects SettingsController dependencies through the controller factory before FXML initialize(); initialize must load statuses after TableView injection.");
        assertTrue(initialize.contains("loadCaseStatuses();"),
                "SettingsController.initialize() should populate Settings > Case Statuses when service injection already happened.");
    }

    @Test
    void statusColorRoundTripsDatabaseHexFormat() {
        Color color = SettingsController.dbColorToFx("0x28A745FF");

        assertEquals(0x28 / 255.0, color.getRed(), 0.0001);
        assertEquals(0xA7 / 255.0, color.getGreen(), 0.0001);
        assertEquals(0x45 / 255.0, color.getBlue(), 0.0001);
        assertEquals(1.0, color.getOpacity(), 0.0001);
        assertEquals("0x28A745FF", SettingsController.fxColorToDb(color));
    }

    @Test
    void colorConversionUsesSafeDefaultForBlankValues() {
        assertEquals("0x6C757DFF", SettingsController.fxColorToDb(SettingsController.dbColorToFx("")));
    }

    @Test
    void protectedStatusKeysArePreservedOnEditAndOmittedForNewStatuses() {
        CaseStatusDto existing = new CaseStatusDto(7, "Accepted", true, 20, "0x28A745FF", "accepted", "accepted", 7);

        assertEquals("accepted", SettingsController.lifecycleKeyForSave(existing));
        assertEquals("accepted", SettingsController.systemKeyForSave(existing));
        assertNull(SettingsController.lifecycleKeyForSave(null));
        assertNull(SettingsController.systemKeyForSave(null));
    }

    @Test
    void statusDialogPreservesExistingSortOrderAndOmitsSortForNewStatuses() {
        CaseStatusDto existing = new CaseStatusDto(7, "Accepted", true, 20, "0x28A745FF", "accepted", "accepted", 7);

        assertEquals(20, SettingsController.sortOrderForSave(existing));
        assertNull(SettingsController.sortOrderForSave(null));
    }

    @Test
    void statusDialogUsesSecondaryShellAndDoesNotExposeSortOrderEditor() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        String methodStart = "\tprivate Optional<CaseStatusInput> showCaseStatusDialog";
        String method = source.substring(source.indexOf(methodStart),
                source.indexOf("\n\tprivate CaseStatusViewRow selectedStatusRow", source.indexOf(methodStart)));

        assertTrue(method.contains("AppDialogs.applySecondaryDialogShell"),
                "Case status dialogs should use the same secondary dialog shell as existing Shale dialogs instead of the default JavaFX window chrome/icon.");
        assertTrue(!method.contains("new Label(\"Sort Order\")"),
                "Sort Order should remain table/reorder-button driven and not be a manual dialog field.");
    }

    @Test
    void addUserFlowIsAdminOnlyAndUsesUserDaoCreateRequestWithoutTenantField() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        String fxml = Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));

        assertTrue(fxml.contains("fx:id=\"userAdministrationSection\""));
        assertTrue(fxml.contains("text=\"Add User\""));
        assertTrue(source.contains("if (!isAdminUser())"),
                "Settings Add User handler must block non-admin users before opening or saving the dialog.");
        assertTrue(source.contains("new UserDao.UserCreateRequest("));
        assertTrue(!source.contains("shaleClientId,"),
                "The Add User form must not provide a ShaleClientId value; UserDao derives it from session context.");
        assertTrue(!source.contains("Default Organization"),
                "The Add User form should not expose organization fields until user organization editing is supported in the UI.");
        assertTrue(!source.contains("new Label(\"Organization\")"),
                "The Add User form should not expose raw organization ids.");
    }


    @Test
    void userManagementSectionIncludesListFilterAndActions() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        String fxml = Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));

        assertTrue(fxml.contains("fx:id=\"userManagementTable\""));
        assertTrue(fxml.contains("fx:id=\"showInactiveUsersCheck\""));
        assertTrue(fxml.contains("onAction=\"#onDeactivateUser\""));
        assertTrue(fxml.contains("onAction=\"#onReactivateUser\""));
        assertTrue(fxml.contains("onAction=\"#onResetUserPassword\""));
        assertTrue(source.contains("focusedProperty().addListener"),
                "Email duplicate validation should run when the Add User email field loses focus.");
        assertTrue(source.contains("findExistingEmailForCurrentTenant"),
                "UI duplicate validation should use the DAO normalization/tenant-aware lookup.");
    }

}
