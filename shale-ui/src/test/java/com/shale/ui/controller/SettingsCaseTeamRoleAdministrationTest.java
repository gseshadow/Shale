package com.shale.ui.controller;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class SettingsCaseTeamRoleAdministrationTest {
 @Test void settingsUsesCardAdministrationAndAllLifecycleOperations()throws Exception{String f=Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));String p=Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseTeamRoleAdminPane.java"));assertTrue(f.contains("Case Team Roles"));assertTrue(f.contains("caseTeamRoleAdministrationContent"));assertFalse(p.toLowerCase().contains("dual-list"));for(String op:new String[]{"createCaseTeamRole","updateCaseTeamRole","removeCaseTeamRole","restoreCaseTeamRole","resetCaseTeamRoleOverride"})assertTrue(p.contains(op),op);assertTrue(p.contains("System role"));assertTrue(p.contains("Custom role"));}
}
