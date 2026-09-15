package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

final class SettingsCaseDateTypeAdministrationTest {
    private static String read(String path) { try { return Files.readString(Path.of(path)); } catch (Exception ex) { throw new AssertionError(ex); } }
    private static final String SETTINGS = read("src/main/java/com/shale/ui/controller/SettingsController.java");
    private static final String FXML = read("src/main/resources/fxml/settings.fxml");

    @Test void settingsUsesCompactCaseDatesLauncherInsteadOfEagerDefinitionList() {
        assertTrue(FXML.contains("title=\"Case Dates\""));
        assertTrue(FXML.contains("fx:id=\"caseDatesRow\""));
        assertTrue(SETTINGS.contains("bind(caseDatesRow, this::onManageCaseDateTypes)"));
        assertFalse(FXML.contains("fx:id=\"caseDateTypeCardsContainer\""));
        assertFalse(FXML.contains("fx:id=\"caseDateTypeActionRow\""));
        assertFalse(SETTINGS.replaceAll("\\s+", "").contains("loadAdminSectionsAsync(null);"));
        assertTrue(SETTINGS.contains("new CaseDateTypeManagementLauncher"));
    }

    @Test void semanticMappingsRemainASeparateSettingsConcern() {
        assertTrue(FXML.contains("Protected Case Date Mappings"));
        assertTrue(FXML.contains("fx:id=\"caseDateRoleMappingsContainer\""));
        assertTrue(SETTINGS.contains("loadCaseDateRoleMappingsAsync"));
        assertTrue(SETTINGS.contains("listCaseDateSemanticRoleMappings"));
    }

    @Test void launcherAndMappingSectionRemainAdminGated() {
        assertTrue(SETTINGS.contains("if (!requireAdminLookupManagement(\"Case Date Types\")"));
        assertTrue(SETTINGS.contains("caseDateTypeAdministrationSection.setVisible(visible)"));
        assertTrue(SETTINGS.contains("caseDateRoleMappingsSection.setVisible(visible)"));
    }
}
