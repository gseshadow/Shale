package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SettingsCaseDateSemanticRoleAdministrationTest {
    private static final String SOURCE = read("src/main/java/com/shale/ui/controller/SettingsController.java");
    private static final String FXML = read("src/main/resources/fxml/settings.fxml");

    private static String read(String path) {
        try { return Files.readString(Path.of(path)); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private static String method(String signature) {
        int start = SOURCE.indexOf(signature);
        assertTrue(start >= 0, "missing method " + signature);
        int brace = SOURCE.indexOf('{', start), depth = 0;
        for (int i = brace; i < SOURCE.length(); i++) {
            char c = SOURCE.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return SOURCE.substring(start, i + 1);
        }
        throw new AssertionError("unterminated method " + signature);
    }

    @Test void noEligibleTypesRenderOneExplanationAndNoEmptyActionControls() {
        String render = method("private void renderCaseDateRoleMappings");
        String row = method("private VBox buildCaseDateRoleMappingRow");
        assertTrue(render.contains("if (eligible.isEmpty())"));
        assertEquals(1, count(render, "No custom types are available for overrides."));
        assertTrue(row.contains("if (!eligible.isEmpty())"));
        assertTrue(row.indexOf("if (!eligible.isEmpty())") < row.indexOf("new ComboBox<>()"));
        assertTrue(row.indexOf("if (!eligible.isEmpty())") < row.indexOf("\"Save override\""));
    }

    @Test void eligibleTypesUseSharedIdBackedSelectorAndUnifiedAction() {
        String row = method("private VBox buildCaseDateRoleMappingRow");
        assertTrue(row.contains("ComboBox<EffectiveCaseDateTypeDto>"));
        assertTrue(row.contains("ControlStyles.formControl(new ComboBox<>())"));
        assertTrue(row.contains("selector.getItems().setAll(eligible)"));
        assertTrue(row.contains("selected.id()"));
        assertTrue(row.contains("ActionButtonFactory.semantic"));
        assertTrue(row.contains("ControlStyles.Size.SMALL"));
        assertTrue(row.contains("saveCaseDateSemanticRoleMapping"));
    }

    @Test void presentationDistinguishesInheritanceAndOverrideAndConditionallyResets() {
        String row = method("private VBox buildCaseDateRoleMappingRow");
        assertTrue(row.contains("Using the built-in default."));
        assertTrue(row.contains("is currently used for this required date."));
        assertTrue(row.contains("if (mapping.tenantOverride())"));
        assertTrue(row.contains("Reset to global default"));
    }

    @Test void allRolesShareOneCompactResponsiveSection() {
        String render = method("private void renderCaseDateRoleMappings");
        String row = method("private VBox buildCaseDateRoleMappingRow");
        assertTrue(FXML.contains("caseDateTypeAdministrationSection"));
        assertTrue(FXML.contains("caseDateRoleMappingsContainer"));
        assertTrue(render.contains("FlowPane section = new FlowPane(10, 10)"));
        assertTrue(render.contains("for (CaseDateSemanticRoleMappingDto mapping : mappings)"));
        assertTrue(render.contains("caseDateRoleMappingsContainer.getChildren().setAll(section)"));
        assertFalse(render.contains("buildCaseDateRoleMappingCard"));
        assertTrue(row.contains("FlowPane actions"));
        assertTrue(row.contains("actions.setPrefWrapLength(520)"));
        assertTrue(row.contains("setWrapText(true)"));
        assertTrue(row.contains("case-date-built-in-card"));
        assertFalse(row.contains("Global/default"));
        assertFalse(row.contains("Protected system type"));
    }

    private static int count(String value, String needle) {
        int count = 0, offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) { count++; offset += needle.length(); }
        return count;
    }
}
