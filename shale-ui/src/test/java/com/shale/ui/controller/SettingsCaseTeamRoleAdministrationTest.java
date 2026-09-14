package com.shale.ui.controller;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class SettingsCaseTeamRoleAdministrationTest {
 @Test void settingsUsesLazySharedManagementRow()throws Exception{String f=Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));String c=Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));assertTrue(f.contains("Case Team Roles"));assertTrue(f.contains("Manage team roles, colors, and availability."));assertTrue(f.contains("fx:id=\"manageCaseTeamRolesButton\""));assertFalse(f.contains("caseTeamRoleAdministrationContent"));assertTrue(c.contains("new CaseTeamRoleManagementLauncher"));assertFalse(c.contains("new CaseTeamRoleAdminPane"));assertTrue(c.contains("!appState.isAdmin()"));}
 @Test void settingsAndCaseContextShareLauncher()throws Exception{String settings=Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));String team=Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/TeamEditorDialog.java"));assertTrue(settings.contains("CaseTeamRoleManagementLauncher"));assertTrue(team.contains("CaseTeamRoleManagementLauncher"));assertTrue(team.contains("Save or Cancel the pending Case Team changes"));assertTrue(team.contains("if(result.changed())"));}
}
