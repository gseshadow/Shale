package com.shale.ui.controller;
import static org.junit.jupiter.api.Assertions.*; import java.nio.file.*; import org.junit.jupiter.api.Test;
final class SettingsUserManagementNameCellContractTest {
 @Test void popupPaneUsesSharedMiniUserCardsAndSettingsHasNoInlineHost()throws Exception{String pane=Files.readString(Path.of("src/main/java/com/shale/ui/controller/UserManagementPane.java"));String fxml=Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));assertTrue(pane.contains("UserCardFactory.Variant.MINI"));assertTrue(pane.contains("setInactive(row.deleted())"));assertFalse(fxml.contains("userManagementTable"));assertFalse(fxml.contains("userAdministrationSection"));}
}
