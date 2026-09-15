package com.shale.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;

final class ThemeManagerTest {
    @Test
    void installsInOrderWithoutDuplicatesAndPreservesUnrelatedStylesheets() {
        JavaFxTestSupport.runAndWait(() -> {
            ThemeManager manager = new ThemeManager();
            Scene scene = new Scene(new StackPane());
            scene.getStylesheets().addAll("test:first.css", "test:second.css");

            manager.register(scene);
            manager.register(scene);

            assertEquals(List.of("test:first.css", "test:second.css", resource("/css/app.css"),
                    resource("/css/theme/light.css")), scene.getStylesheets());
        });
    }

    @Test
    void switchingUpdatesExistingAndNewSceneDialogAndPopupStyleTargets() {
        JavaFxTestSupport.runAndWait(() -> {
            ThemeManager manager = new ThemeManager();
            Scene main = new Scene(new StackPane());
            StackPane dialogPane = new StackPane();
            manager.register(main);
            manager.register(dialogPane);

            assertEquals(Theme.LIGHT, manager.getActiveTheme());
            manager.setActiveTheme(Theme.DARK);
            assertEquals(Theme.DARK, manager.activeThemeProperty().get());
            assertTheme(main.getStylesheets(), "/css/theme/dark.css");
            assertTheme(dialogPane.getStylesheets(), "/css/theme/dark.css");

            StackPane popupRoot = new StackPane();
            manager.register(popupRoot);
            assertTheme(popupRoot.getStylesheets(), "/css/theme/dark.css");
            manager.setActiveTheme(Theme.LIGHT);
            assertTheme(main.getStylesheets(), "/css/theme/light.css");
            assertTheme(dialogPane.getStylesheets(), "/css/theme/light.css");
            assertTheme(popupRoot.getStylesheets(), "/css/theme/light.css");
        });
    }

    @Test
    void unregisterStopsPropagationAndCallsOffFxThreadFailClearly() {
        StackPane root = JavaFxTestSupport.runAndWait(StackPane::new);
        ThemeManager manager = new ThemeManager();
        JavaFxTestSupport.runAndWait(() -> {
            manager.register(root);
            manager.unregister(root);
            manager.setActiveTheme(Theme.DARK);
            assertFalse(root.getStylesheets().contains(resource("/css/theme/dark.css")));
        });
        assertThrows(IllegalStateException.class, () -> manager.setActiveTheme(Theme.LIGHT));
    }

    @Test
    void missingRequiredResourceHasActionableFailure() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new ThemeManager(resource -> null));
        assertTrue(failure.getMessage().contains("missing from the classpath"));
        assertTrue(failure.getMessage().contains("/css/app.css"));
    }

    private static void assertTheme(List<String> stylesheets, String themeResource) {
        assertEquals(resource("/css/app.css"), stylesheets.get(stylesheets.size() - 2));
        assertEquals(resource(themeResource), stylesheets.get(stylesheets.size() - 1));
        assertEquals(1, stylesheets.stream().filter(resource("/css/app.css")::equals).count());
        assertEquals(1, stylesheets.stream().filter(resource(themeResource)::equals).count());
    }

    private static String resource(String path) {
        URL resource = ThemeManagerTest.class.getResource(path);
        assertTrue(resource != null, "Expected packaged classpath resource " + path);
        return resource.toExternalForm();
    }
}
