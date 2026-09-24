package com.shale.ui.controller.support;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
final class FieldConfirmationSelectionTest {
 @Test void newIntakeSelectionOwnsOnlyPresenceRequirement() throws Exception {
  String model=Files.readString(Path.of("src/main/java/com/shale/ui/controller/support/NewIntakeDatesConfiguration.java"));
  assertAll(()->assertTrue(model.contains("record Selection(EffectiveCaseDateTypeDto type, boolean required)")),
   ()->assertFalse(model.contains("requiresConfirmation")),()->assertFalse(model.contains("roleDefinitionId")));
 }
}
