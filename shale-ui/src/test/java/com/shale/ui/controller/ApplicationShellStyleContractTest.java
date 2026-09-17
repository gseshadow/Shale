package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

final class ApplicationShellStyleContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void mainShellUsesSharedSemanticControlsAndA2CompositionClasses() throws Exception {
        String fxml = Files.readString(RESOURCES.resolve("fxml/main.fxml"));
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MainController.java"));

        for (String styleClass : new String[] {
                "app-brand", "global-search-field", "nav-rail", "primary-content-surface",
                "shell-section-header", "shell-route-outlet", "session-footer", "profile-button",
                "shell-leading-actions", "shell-search-group", "shell-trailing-actions"
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

    @Test
    void sharedShellOwnsSpacingAndKeepsTheRoundedRoutePlaneVisible() throws Exception {
        String fxml = Files.readString(RESOURCES.resolve("fxml/main.fxml"));
        String css = Files.readString(RESOURCES.resolve("css/foundation/shell.css"));

        assertTrue(css.contains("-fx-padding: 10px 12px"),
                "the toolbar must own comfortable outer padding");
        assertTrue(css.contains("-fx-spacing: 12px"),
                "the toolbar must own spacing between its control groups");
        assertTrue(css.contains("-fx-padding: 12px; -fx-spacing: 6px"),
                "the navigation rail must inset and separate its items");
        assertTrue(css.contains(".content-root { -fx-padding: 14px 16px 16px 16px"),
                "the application canvas must remain visible around the route plane");
        assertTrue(css.contains("-fx-background-radius: 14px"),
                "the shared route plane must use the approved corner radius");
        assertTrue(css.contains(".shell-route-outlet > .app-shell { -fx-background-color: transparent; }"),
                "an opaque routed page root must not cover the shared plane corners");
        assertTrue(Pattern.compile("<HBox[^>]*HBox\\.hgrow=\\\"ALWAYS\\\"[^>]*minWidth=\\\"180\\\"[^>]*"
                + "styleClass=\\\"shell-search-group\\\"", Pattern.DOTALL).matcher(fxml).find(),
                "the search group must flex before stable trailing actions are clipped");
        assertTrue(Pattern.compile("<HBox[^>]*minWidth=\\\"-Infinity\\\"[^>]*"
                + "styleClass=\\\"shell-trailing-actions\\\"", Pattern.DOTALL).matcher(fxml).find(),
                "the trailing action group must retain its preferred footprint and right inset");
        assertFalse(css.contains(".shell-route-outlet .root"),
                "route transparency must remain scoped to the established routed page class");
    }

    @Test
    void shellCorrectionPreservesCasesAndCaseOverviewScrollOwnership() throws Exception {
        String cases = Files.readString(RESOURCES.resolve("fxml/cases.fxml"));
        String caseOverview = Files.readString(RESOURCES.resolve("fxml/case.fxml"));
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MainController.java"));

        assertTrue(Pattern.compile("<BorderPane[^>]*styleClass=\\\"app-shell\\\"", Pattern.DOTALL)
                .matcher(cases).find(), "Cases must retain its routed page root");
        assertTrue(cases.contains("fx:id=\"casesScroll\""),
                "Cases must retain its existing card-grid scrolling boundary");
        assertTrue(caseOverview.contains("fx:id=\"overviewScrollPane\""),
                "Case Overview must retain its existing page scrolling boundary");
        assertTrue(caseOverview.contains("fx:id=\"contentHost\""),
                "Case Overview must retain its section content host");
        assertFalse(controller.contains("setClip("),
                "route changes must not accumulate content clips or resize listeners");
        assertTrue(controller.contains("sectionContent.getChildren().setAll("),
                "repeated navigation must continue replacing content without wrapper accumulation");
    }
}
