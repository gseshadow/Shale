package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.theme.Theme;
import com.shale.ui.theme.ThemeManager;

import javafx.scene.control.Alert;
import javafx.stage.Stage;

final class ThemeLifecycleIntegrationTest {
    @Test
    void confirmedPreviouslyUnstyledClientStagesReceiveTheCompleteTheme() {
        JavaFxTestSupport.runAndWait(() -> {
            ClientAssignmentDialog assignment = new ClientAssignmentDialog(null, List.of(), List.of(),
                    (first, last) -> null);
            NewClientDialog newClient = new NewClientDialog(null);

            assertCompleteTheme(stageOf(assignment));
            assertCompleteTheme(stageOf(newClient));
        });
    }

    @Test
    void chromeOnlyAlertsNowReceiveCompleteAuthorStylesheets() {
        JavaFxTestSupport.runAndWait(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            AppDialogs.applySecondaryWindowChrome(alert);

            assertEquals(resource("/css/app.css"), alert.getDialogPane().getStylesheets().get(0));
            assertEquals(resource(Theme.LIGHT.stylesheetResource()),
                    alert.getDialogPane().getStylesheets().get(1));
        });
    }

    private static Stage stageOf(Object dialog) {
        try {
            Field stage = dialog.getClass().getDeclaredField("stage");
            stage.setAccessible(true);
            return (Stage) stage.get(dialog);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Expected dialog stage lifecycle field", failure);
        }
    }

    private static void assertCompleteTheme(Stage stage) {
        assertTrue(stage.getScene() != null, "secondary dialog must own a scene");
        List<String> stylesheets = stage.getScene().getStylesheets();
        assertEquals(resource("/css/app.css"), stylesheets.get(stylesheets.size() - 2));
        assertEquals(resource(Theme.LIGHT.stylesheetResource()), stylesheets.get(stylesheets.size() - 1));
    }

    private static String resource(String path) {
        return ThemeManager.class.getResource(path).toExternalForm();
    }
}
