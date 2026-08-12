package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class SettingsNotificationAuditSemanticControlTest {
    private static final String SOURCE = read("src/main/java/com/shale/ui/controller/SettingsController.java");
    private static final String FXML = read("src/main/resources/fxml/settings.fxml");

    @Test
    void notificationAndAuditButtonsUseExplicitSemanticPurposesAndStandardSize() {
        String styling = method("configureSettingsSemanticButtons");

        assertTrue(styling.contains("applyNotificationPreferencesButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD"));
        assertTrue(styling.contains("resetNotificationPreferencesButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD"));
        assertTrue(styling.contains("viewAuditLogButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD"));
        assertEquals(1, occurrences(styling, "ControlStyles.Purpose.PRIMARY"),
                "Notifications must have exactly one Primary action.");
    }

    @Test
    void migratedFxmlButtonsRetainHandlersWithoutLegacyOrDefaultStyling() {
        String apply = element("applyNotificationPreferencesButton");
        String reset = element("resetNotificationPreferencesButton");
        String audit = element("viewAuditLogButton");

        assertTrue(apply.contains("onAction=\"#onApplyNotificationPreferences\""));
        assertTrue(reset.contains("onAction=\"#onResetNotificationPreferences\""));
        assertTrue(audit.contains("onAction=\"#onViewAuditLog\""));
        assertFalse(apply.contains("app-toolbar-button"));
        assertFalse(reset.contains("app-toolbar-button"));
        assertFalse(audit.contains("app-toolbar-button"));
    }

    @Test
    void handlersPreserveSaveResetAndAuthorizationPaths() {
        String apply = method("onApplyNotificationPreferences");
        String reset = method("onResetNotificationPreferences");
        String audit = method("onViewAuditLog");

        assertEquals(1, occurrences(apply, "notificationPreferencesService.setForCurrentUser(preferences)"));
        assertFalse(reset.contains("setForCurrentUser"), "Reset must not persist unintended values.");
        assertTrue(reset.contains("loadFromPreferences();"));
        assertTrue(audit.contains("if (!isAdminUser() || onOpenAuditLog == null)"));
        assertEquals(1, occurrences(audit, "onOpenAuditLog.run();"));
    }

    @Test
    void userManagementUsesUnifiedSemanticControls() {
        assertTrue(FXML.contains("text=\"Add User\" onAction=\"#onAddUser\" styleClass=\"shale-control-button shale-control-primary shale-control-standard\""));
        assertTrue(FXML.contains("fx:id=\"editUserButton\" text=\"Edit User\""));
        assertTrue(FXML.contains("fx:id=\"deactivateUserButton\" text=\"Deactivate User\""));
        assertTrue(FXML.contains("fx:id=\"reactivateUserButton\" text=\"Reactivate User\""));
        assertTrue(FXML.contains("fx:id=\"resetPasswordButton\" text=\"Reset Password\""));
        assertTrue(FXML.contains("fx:id=\"removeUserButton\" text=\"Remove from Tenant\""));
    }

    private static String element(String fxId) {
        int id = FXML.indexOf("fx:id=\"" + fxId + "\"");
        int start = FXML.lastIndexOf("<Button", id);
        int end = FXML.indexOf("/>", id);
        assertTrue(start >= 0 && end >= 0, fxId);
        return FXML.substring(start, end + 2);
    }

    private static String method(String name) {
        int nameAt = SOURCE.indexOf(" " + name + "(");
        assertTrue(nameAt >= 0, name);
        int brace = SOURCE.indexOf('{', nameAt);
        int depth = 0;
        for (int i = brace; i < SOURCE.length(); i++) {
            if (SOURCE.charAt(i) == '{') depth++;
            else if (SOURCE.charAt(i) == '}' && --depth == 0) return SOURCE.substring(nameAt, i + 1);
        }
        throw new AssertionError(name);
    }

    private static int occurrences(String value, String needle) {
        return value.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static String read(String path) {
        try { return Files.readString(Path.of(path)); }
        catch (Exception ex) { throw new AssertionError(ex); }
    }
}
