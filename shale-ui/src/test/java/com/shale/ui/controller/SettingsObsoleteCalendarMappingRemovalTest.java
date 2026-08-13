package com.shale.ui.controller;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
final class SettingsObsoleteCalendarMappingRemovalTest {
 @Test void obsoleteMappingSectionAndControllerWiringAreAbsent() throws Exception {String fxml=Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));String controller=Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));assertFalse(fxml.contains("Calendar / Case Date Mappings"));assertFalse(fxml.contains("calendarCaseDateMapping"));assertFalse(controller.contains("CalendarCaseDateTypeMapping"));assertTrue(fxml.contains("Case Date Types"));assertTrue(fxml.contains("Protected role mappings"));}
}
