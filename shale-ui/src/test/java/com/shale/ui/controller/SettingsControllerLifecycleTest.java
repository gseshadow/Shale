package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseStatusDto;

import javafx.scene.paint.Color;

final class SettingsControllerLifecycleTest {

    @Test
    void initializeLoadsCaseStatusesWhenServiceWasInjectedBeforeFxmlInjection() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        String initialize = methodSource(source, "initialize");

        assertTrue(initialize.contains("if (caseService != null && isAdminUser())"),
                "SceneManager injects SettingsController dependencies through the controller factory before FXML initialize(); initialize must only load lookup-management lists for admins.");
        assertTrue(initialize.contains("loadCaseStatuses();"),
                "SettingsController.initialize() should populate Settings > Case Statuses for admins when service injection already happened.");
        assertTrue(initialize.contains("loadPracticeAreas();"),
                "SettingsController.initialize() should populate Settings > Practice Areas for admins when service injection already happened.");
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
        String method = methodSource(source, "showCaseStatusDialog");

        assertTrue(method.contains("AppDialogs.applySecondaryDialogShell"),
                "Case status dialogs should use the same secondary dialog shell as existing Shale dialogs instead of the default JavaFX window chrome/icon.");
        assertTrue(!method.contains("new Label(\"Sort Order\")"),
                "Sort Order should remain table/reorder-button driven and not be a manual dialog field.");
    }

    @Test
    void lookupManagementSectionsAreAdminOnlyButGeneralSettingsRemainVisible() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        String fxml = Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));

        assertTrue(fxml.contains("fx:id=\"caseStatusAdministrationSection\""),
                "Case Statuses must be wrapped in a managed section so non-admins do not see an empty gap.");
        assertTrue(fxml.contains("fx:id=\"practiceAreaAdministrationSection\""),
                "Practice Areas must be wrapped in a managed section so non-admins do not see an empty gap.");
        assertTrue(fxml.contains("fx:id=\"taskAssignedToMeCheck\""));
        assertTrue(fxml.contains("fx:id=\"notificationSettingsStatusLabel\""),
                "General notification settings should remain present for non-admin Settings users.");
        assertTrue(source.contains("caseStatusAdministrationSection.setVisible(visible)"));
        assertTrue(source.contains("caseStatusAdministrationSection.setManaged(visible)"));
        assertTrue(source.contains("practiceAreaAdministrationSection.setVisible(visible)"));
        assertTrue(source.contains("practiceAreaAdministrationSection.setManaged(visible)"));
    }

    @Test
    void lookupManagementLoadsAndActionsRequireAdmin() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));

        assertTrue(source.contains("private boolean requireAdminLookupManagement"),
                "Lookup-management controller paths should share an admin authorization guard.");
        assertTrue(source.contains("if (!requireAdminLookupManagement(\"Case Statuses\"))"),
                "Case status load/edit paths must reject non-admins before service calls.");
        assertTrue(source.contains("if (!requireAdminLookupManagement(\"Practice Areas\"))"),
                "Practice area load/edit paths must reject non-admins before service calls.");
        assertTrue(source.contains("caseService.createCaseStatus"));
        assertTrue(source.contains("caseService.updateCaseStatus"));
        assertTrue(source.contains("caseService.reorderCaseStatuses"));
        assertTrue(source.contains("caseService.createPracticeArea"));
        assertTrue(source.contains("caseService.updatePracticeArea"));
        assertTrue(source.contains("caseService.deactivatePracticeArea"));
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


    @Test
    void resetPasswordValidationUsesInlineMessagesWithoutResultConverterExceptions() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        String method = methodSource(source, "onResetUserPassword");

        assertEquals("Password is required.", SettingsController.resetPasswordValidationMessage("", "anything"));
        assertEquals("Confirm password is required.", SettingsController.resetPasswordValidationMessage("newPassword1", ""));
        assertEquals("Passwords do not match.", SettingsController.resetPasswordValidationMessage("newPassword1", "differentPassword1"));
        assertEquals("", SettingsController.resetPasswordValidationMessage("newPassword1", "newPassword1"));
        assertTrue(method.contains("addEventFilter(javafx.event.ActionEvent.ACTION"));
        assertTrue(method.contains("event.consume()"));
        assertTrue(!method.contains("throw new IllegalArgumentException(\"Passwords"),
                "Reset password validation failures should keep the dialog open with inline feedback, not throw from the result converter.");
    }


    private static String methodSource(String source, String methodName) {
        Pattern signaturePattern = Pattern.compile("(?m)^\\s*(?:@FXML\\s*)?(?:private|public|protected|static|final|\\s)+[^{;=]*\\b"
                + Pattern.quote(methodName) + "\\s*\\([^)]*\\)\\s*\\{");
        Matcher matcher = signaturePattern.matcher(source);
        assertTrue(matcher.find(), () -> "Expected SettingsController source to contain method '" + methodName
                + "' before checking its lifecycle behavior markers.");
        int bodyStart = matcher.end() - 1;
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(matcher.start(), i + 1);
                }
            }
        }
        return fail("Expected SettingsController method '" + methodName + "' to have a complete brace-delimited body.");
    }

}
