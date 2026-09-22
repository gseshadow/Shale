package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class MaterialRequestPhase8EPresentationContractTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
    private static final Path APP_CSS = Path.of("src/main/resources/css/app.css");
    private static final Path FOUNDATION = Path.of("src/main/resources/css/foundation/request-windows.css");

    @Test
    void createAndDetailWindowsOptIntoOneSharedA2Vocabulary() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertTrue(source.contains("request-window-shell"));
        assertTrue(source.contains("request-window-root"));
        assertTrue(source.contains("new-request-window"));
        assertTrue(source.contains("material-request-detail-window"));
        assertTrue(source.contains("request-window-form-surface"));
        assertTrue(source.contains("request-window-field-grid"));
        assertTrue(source.contains("request-window-scroll"));
        assertTrue(source.contains("request-window-validation"));
        assertTrue(source.contains("request-window-footer"));
    }

    @Test
    void requestFoundationUsesSemanticTokensAndIsLoadedByProductionCss() throws Exception {
        String app = Files.readString(APP_CSS);
        String css = Files.readString(FOUNDATION);
        assertTrue(app.contains("@import \"foundation/request-windows.css\";"));
        for (String selector : new String[]{".request-window-root", ".request-window-form-surface",
                ".request-window-section", ".request-window-field-grid", ".request-window-scroll",
                ".request-window-validation", ".request-window-loading", ".request-window-empty",
                ".request-window-failure", ".request-window-concurrency", ".request-window-footer"}) {
            assertTrue(css.contains(selector), selector);
        }
        assertFalse(css.matches("(?s).*(#[0-9a-fA-F]{3,8}|rgba?\\().*"),
                "Request-window paint must come only from semantic theme tokens.");
    }

    @Test
    void exposedFieldsHaveDistinctAccessibleNamesAndScrollingStaysVertical() throws Exception {
        String source = Files.readString(CONTROLLER);
        for (String name : new String[]{"Request title, required", "Material Type, required",
                "Request Method, required", "Request Status, required", "Requested By", "Assigned To"}) {
            assertTrue(source.contains("setAccessibleText(\"" + name + "\")"), name);
        }
        assertTrue(source.contains("setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)"));
        assertTrue(source.contains("setFitToWidth(true)"));
        assertTrue(source.contains("ThemeManager.application().register(scene)"));
    }
}
