package com.shale.ui.controller;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
final class FieldConfirmationPhase2CUiContractTest {
 @Test void typeSettingsOwnPolicyAndAllDateViewsShareSavedStatus() throws Exception {
  String settings=Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseDateTypeManagementPane.java"));
  String intake=Files.readString(Path.of("src/main/java/com/shale/ui/controller/NewIntakeController.java"));
  String dates=Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
  assertAll(()->assertTrue(settings.contains("Case Date Type Settings")),()->assertTrue(settings.contains("Requires confirmation")),
   ()->assertTrue(settings.contains("listConfirmationRoles")),()->assertTrue(settings.contains("SetFieldConfirmationPolicyCommand")),
   ()->assertFalse(intake.contains("SetFieldConfirmationPolicyCommand")),()->assertTrue(dates.contains("CaseDateConfirmationView.create")));
 }
 @Test void stableIdentityUsesOverlaySystemKeyOrTenantTypeIdAndNotSemanticsOrNames() throws Exception {
  String settings=Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseDateTypeManagementPane.java"));
  assertAll(()->assertTrue(settings.contains("SYSTEM:")),()->assertTrue(settings.contains("TYPE:")),
   ()->assertFalse(settings.substring(settings.indexOf("static String policyKey")).contains("CaseDateSemanticRole")));
 }
}
