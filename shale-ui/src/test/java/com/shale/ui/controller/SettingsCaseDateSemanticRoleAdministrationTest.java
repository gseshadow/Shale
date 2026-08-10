package com.shale.ui.controller;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
final class SettingsCaseDateSemanticRoleAdministrationTest {
 @Test void protectedMappingsUseTypedSharedSelectorsAndButtons()throws Exception{String s=Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));assertTrue(s.contains("ComboBox<EffectiveCaseDateTypeDto>"));assertTrue(s.contains("t.shaleClientId()!=null&&t.active()&&!t.deleted()"));assertTrue(s.contains("selected.id()"));assertTrue(s.contains("Inherited global default"));assertTrue(s.contains("Tenant override"));assertTrue(s.contains("Reset to global default"));assertTrue(s.contains("semanticButton("));assertTrue(s.contains("publishCaseDateTypeChanged"));}
 @Test void fxmlKeepsMappingsInsideExistingManager()throws Exception{String f=Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));assertTrue(f.contains("caseDateTypeAdministrationSection"));assertTrue(f.contains("caseDateRoleMappingsContainer"));}
}
