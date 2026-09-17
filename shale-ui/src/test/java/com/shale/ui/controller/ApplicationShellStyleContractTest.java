package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ApplicationShellStyleContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void mainShellUsesSharedSemanticControlsAndA2CompositionClasses() throws Exception {
        String fxml = Files.readString(RESOURCES.resolve("fxml/main.fxml"));
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MainController.java"));

        for (String styleClass : new String[] {
                "app-brand", "global-search-field", "nav-rail", "primary-content-surface",
                "shell-section-header", "shell-route-outlet", "session-footer", "profile-button"
        }) {
            assertTrue(fxml.contains(styleClass), "main shell is missing " + styleClass);
        }
        assertTrue(controller.contains("ControlStyles.formControl(globalSearchField)"));
        assertTrue(controller.contains("ControlStyles.apply(globalSearchButton, ControlStyles.Purpose.SECONDARY"));
        assertTrue(controller.contains("ControlStyles.apply(newIntakeButton, ControlStyles.Purpose.PRIMARY"));
        assertTrue(controller.contains("ControlStyles.apply(logoutButton, ControlStyles.Purpose.SECONDARY"));
        assertTrue(controller.contains("ControlStyles.apply(profileButton, ControlStyles.Purpose.NAVIGATION"));
        assertFalse(fxml.contains("app-toolbar-button"),
                "ordinary shell actions must not retain the parallel legacy toolbar-button vocabulary");
    }

    @Test
    void shellFoundationUsesOnlyCanonicalThemePaintAndRestrainedGeometry() throws Exception {
        String css = Files.readString(RESOURCES.resolve("css/foundation/shell.css"));
        assertFalse(css.matches("(?s).*#[0-9a-fA-F]{3,8}.*"), "shell paint must come from theme tokens");
        assertFalse(css.contains("rgba("), "shell paint must come from theme tokens");
        assertFalse(css.contains("999px"), "shell controls use restrained, non-pill geometry");
        assertTrue(css.contains("-shale-color-application-canvas"));
        assertTrue(css.contains("-shale-color-application-chrome"));
        assertTrue(css.contains("-shale-color-navigation-selected"));
        assertTrue(css.contains("linear-gradient(to right,"),
                "the approved blue-to-purple gradient is reserved for application chrome and selection");
        assertTrue(css.contains(".section-nav-button:focused"));
        assertTrue(css.contains(".global-search-field:focused"));
    }
}
