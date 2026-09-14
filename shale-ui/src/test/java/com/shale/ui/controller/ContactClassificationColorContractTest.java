package com.shale.ui.controller;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
class ContactClassificationColorContractTest {
 @Test void editorUsesRequiredSharedPickerAndCommandsCarryColor()throws Exception{
  String s=Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactClassificationAdminPane.java"));
  assertTrue(s.contains("new ColorPicker(existing == null ? Color.rgb(108,117,125) : Color.web(existing.color()))"));
  assertTrue(s.contains("new Label(\"Color *\")")); assertTrue(s.contains("Required definition color"));
  assertTrue(s.contains("toDatabaseColor(color.getValue())"));
 }
 @Test void cardsUseCompactBorderedSwatch()throws Exception{
  String java=Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactClassificationAdminPane.java"));
  String css=Files.readString(Path.of("src/main/resources/css/app.css"));
  assertTrue(java.contains("contact-classification-color-swatch"));
  assertTrue(css.contains(".contact-classification-color-swatch")); assertTrue(css.contains("-fx-border-width: 2px"));
 }
 @Test void databaseConversionIsUppercaseOpaqueRgb(){assertEquals("#A1B2C3",ContactClassificationAdminPane.toDatabaseColor(Color.web("#a1b2c3")));}
}
