package com.shale.ui.controller;
import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;import org.junit.jupiter.api.Test;
final class UserManagementFirmWideRolesContractTest{
 @Test void editorDefinesAndAssignsSeveralIndependentFirmWideRoles()throws Exception{String s=Files.readString(Path.of("src/main/java/com/shale/ui/controller/UserManagementPane.java"));assertAll(
  ()->assertTrue(s.contains("new Button(\"Define Role\")")),()->assertTrue(s.contains("CreateFirmWideRoleCommand")),
  ()->assertTrue(s.contains("Tenant-defined roles")),()->assertTrue(s.contains("reconcileCustomRoles")),
  ()->assertTrue(s.contains("FirmWideRoleAssignmentLifecycleCommand")),()->assertFalse(s.contains("CaseTeamRole")),()->assertFalse(s.contains("CaseUsers")));
 }
}
