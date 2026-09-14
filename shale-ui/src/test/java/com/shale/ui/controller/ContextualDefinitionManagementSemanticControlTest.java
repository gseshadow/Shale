package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ContextualDefinitionManagementSemanticControlTest {
    private static final String CASE = compact(read("src/main/java/com/shale/ui/controller/CaseController.java"));
    private static final String CONTACT = compact(read("src/main/java/com/shale/ui/controller/ContactViewController.java"));
    private static final String ORGANIZATION = compact(read("src/main/java/com/shale/ui/controller/OrganizationController.java"));
    private static final String MATERIALS = compact(read("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java"));
    private static final String TEAM = compact(read("src/main/java/com/shale/ui/component/dialog/TeamEditorDialog.java"));
    private static final String SETTINGS = compact(read("src/main/java/com/shale/ui/controller/SettingsController.java"));

    @Test
    void everyContextualManagerUsesTheSmallSecondarySemanticPurpose() {
        assertSemanticApply(CASE, "managePracticeAreasButton");
        assertSemanticApply(CASE, "manageCaseDateTypesButton");
        assertSemanticApply(CASE, "manageLinkTypesButton");
        assertSemanticApply(CONTACT, "manageClassificationsButton");
        assertSemanticApply(ORGANIZATION, "manageOrganizationTypesButton");
        assertTrue(MATERIALS.contains("semanticButton(ControlStyles.Purpose.SECONDARY,ControlStyles.Size.SMALL,\"ManageRequestFields\",null)"),
                "Manage Request Fields must use the shared semantic button factory as a small secondary header action.");
        assertSemanticApply(TEAM, "manageRoles");
    }

    @Test
    void compactSettingsManagersRemainSecondarySemanticActions() {
        for (String button : new String[] {"managePracticeAreasButton", "manageCaseDateTypesButton", "manageLinkTypesButton",
                "manageRequestFieldsButton", "manageContactClassificationsButton", "manageCaseTeamRolesButton",
                "manageOrganizationTypesButton"}) {
            assertTrue(SETTINGS.contains("ControlStyles.apply(" + button + ",ControlStyles.Purpose.SECONDARY,ControlStyles.Size.STANDARD)"),
                    button + " must remain in the shared semantic control system.");
        }
    }

    @Test
    void stylingDoesNotReplaceAdministratorGatingOrActionOwnership() {
        assertTrue(CASE.contains("managePracticeAreasButton.setOnAction(e->openPracticeAreaManagement())"));
        assertTrue(CASE.contains("manageCaseDateTypesButton.setOnAction(e->openCaseDateTypeManagement())"));
        assertTrue(CASE.contains("manageLinkTypesButton.setOnAction(e->openLinkTypeManagement())"));
        assertTrue(CONTACT.contains("manageClassificationsButton.setOnAction(e->openClassificationManagement())"));
        assertTrue(ORGANIZATION.contains("manageOrganizationTypesButton.setOnAction(e->onManageOrganizationTypes())"));
        assertTrue(MATERIALS.contains("if(state!=null&&state.isAdmin())"));
        assertTrue(TEAM.contains("if(administrator&&roleLauncher!=null)"));
        assertTrue(CASE.contains("booleanadmin=appState!=null&&appState.isAdmin()"));
        assertTrue(CONTACT.contains("appState!=null&&appState.isAdmin()"));
        assertTrue(ORGANIZATION.contains("if(!isAdminUser()"));
    }

    @Test
    void affectedManagementButtonsDoNotUseInlineStyling() {
        String combined = String.join("", CASE, CONTACT, ORGANIZATION, MATERIALS, TEAM, SETTINGS);
        for (String variable : new String[] {"managePracticeAreasButton", "manageCaseDateTypesButton", "manageLinkTypesButton",
                "manageClassificationsButton", "manageOrganizationTypesButton", "manageRequestFieldsButton", "manageRoles"}) {
            assertFalse(combined.contains(variable + ".setStyle("),
                    variable + " must not carry inline CSS.");
        }
    }

    private static void assertSemanticApply(String source, String variable) {
        assertTrue(source.contains("ControlStyles.apply(" + variable + ",ControlStyles.Purpose.SECONDARY,ControlStyles.Size.SMALL)"),
                variable + " must be a small secondary Shale action.");
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", "");
    }
}
