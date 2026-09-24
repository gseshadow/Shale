package com.shale.ui.controller.support;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
final class NewIntakeDatesCustomizationDialogTest {
 @Test void customizationRetainsSelectionOrderingAndRequiredButNoConfirmationPolicy() throws Exception {
  String dialog=Files.readString(Path.of("src/main/java/com/shale/ui/controller/support/NewIntakeDatesCustomizationDialog.java"));
  String controller=Files.readString(Path.of("src/main/java/com/shale/ui/controller/NewIntakeController.java"));
  assertAll(()->assertTrue(dialog.contains("CheckBox(\"Required\")")),()->assertTrue(dialog.contains("Collections.swap")),
   ()->assertTrue(dialog.contains("Remove field")),()->assertFalse(dialog.contains("Requires confirmation")),
   ()->assertFalse(dialog.contains("ConfirmationRole")),()->assertFalse(controller.contains("setFieldConfirmationPolicy")),
   ()->assertTrue(controller.contains("formConfigurationService.replace(command)")));
 }
}
