package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class UserWindowsPhase8IPresentationContractTest {
    private static String production(String path) throws Exception {
        return Files.readString(Path.of("src/main/", path));
    }

    @Test
    void productionUserEditorsOptIntoTheSharedA2Vocabulary() throws Exception {
        String pane = production("java/com/shale/ui/controller/UserManagementPane.java");
        String css = production("resources/css/foundation/user-windows.css");
        String app = production("resources/css/app.css");

        assertTrue(app.contains("@import \"foundation/user-windows.css\";"),
                "The production theme entry point must attach the User-window stylesheet.");
        assertTrue(pane.contains("\"user-window-root\",\"user-management-window\""));
        assertTrue(pane.contains("\"user-create-window\""));
        assertTrue(pane.contains("\"user-admin-edit-window\""));
        assertTrue(pane.contains("\"user-security-window\""));
        assertTrue(css.contains(".user-window-section"));
        assertTrue(css.contains(".user-window-role-row:selected"));
        assertTrue(css.contains(".user-window-color-preview"));
        assertTrue(css.contains(".user-window-footer"));
        assertTrue(css.contains("-shale-color-section-surface"),
                "User-window paint must be sourced from semantic theme tokens.");
        assertFalse(css.matches("(?s).*(#[0-9a-fA-F]{3,8}|rgba?\\().*"),
                "The focused User-window stylesheet must not add hard-coded paint.");
    }

    @Test
    void migrationPreservesAuthoritativeIdentityTenantRolesAndSecurityBoundaries() throws Exception {
        String pane = production("java/com/shale/ui/controller/UserManagementPane.java");

        assertTrue(pane.contains("new UserDao.UserUpdateRequest(row.id(), row.rowVer()"),
                "Administrator edits must retain the stable User ID and RowVer.");
        assertTrue(pane.contains("RoleSemantics.ROLE_ADMIN"));
        assertTrue(pane.contains("RoleSemantics.ROLE_ATTORNEY"));
        assertFalse(pane.contains("CaseTeamRoleDefinitions"),
                "Application roles must never use Case Team role authority.");
        assertFalse(pane.contains("Default Organization"));
        assertFalse(pane.contains("MFA"));
        assertTrue(pane.contains("tenantId") && pane.contains("actorUserId"));
        assertTrue(pane.contains("userDao.resetPassword(selected.id(), newPassword)"),
                "Password mutation remains on its existing dedicated DAO path.");
    }

    @Test
    void colorAndValidationHaveTextualAccessiblePresentation() throws Exception {
        String pane = production("java/com/shale/ui/controller/UserManagementPane.java");

        assertTrue(pane.contains("setAccessibleText(\"User color preview and selector\")"));
        assertTrue(pane.contains("emailValidation.setWrapText(true)"));
        assertTrue(pane.contains("validation.setWrapText(true)"));
        assertTrue(pane.contains("ControlStyles.setInvalid(first"));
        assertTrue(pane.contains("ControlStyles.setInvalid(last"));
        assertTrue(pane.contains("ControlStyles.setInvalid(email"));
    }
}
