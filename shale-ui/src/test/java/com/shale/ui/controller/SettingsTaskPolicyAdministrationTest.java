package com.shale.ui.controller;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class SettingsTaskPolicyAdministrationTest {
 private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();
 @Test void settingsOffersHumanReadableFirmWideChoicesAndSaveState()throws Exception{String text=Files.readString(ROOT.resolve("shale-ui/src/main/resources/fxml/settings.fxml"));String source=Files.readString(ROOT.resolve("shale-ui/src/main/java/com/shale/ui/controller/SettingsController.java"));assertAll(()->assertTrue(text.contains("Task due dates")),()->assertTrue(text.contains("all users in the firm")),()->assertTrue(text.contains("Optional")),()->assertTrue(text.contains("Warn")),()->assertTrue(text.contains("Required")),()->assertTrue(source.contains("selected==loadedTaskPolicy.dueDatePolicy()")),()->assertTrue(source.contains("loadedTaskPolicy.rowVer()")),()->assertTrue(source.contains("isAdminUser()")));}
}
