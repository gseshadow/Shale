package com.shale.ui.controller;

import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MyShaleSectionNavigationSelectionTest {
    private static final String ACTIVE_CLASS = "app-section-tab-active";

    @Test
    void initialOverviewPresentationAndSelectionAgreeWithoutStartingASecondLoad() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));
        assertTrue(source.contains("setupSections();\n\t\tapplySectionSelectionState(activeSection);"));
        assertFalse(source.contains("setupSections();\n\t\tonSectionSelected(SECTION_OVERVIEW);"),
                "Initialization must establish presentation state without invoking navigation/loading.");

        JavaFxTestSupport.runAndWait(() -> {
            LoadedMyShale loaded = load();
            assertSelectionAndContent(loaded, "Overview");
            assertEquals(1, selectedCount(loaded.tabs()));
        });
    }

    @Test
    void navigationTransfersSelectionAndReturningRestoresOverview() {
        JavaFxTestSupport.runAndWait(() -> {
            LoadedMyShale loaded = load();

            loaded.tabs().get("My Cases").fire();
            assertSelectionAndContent(loaded, "My Cases");
            assertEquals(1, selectedCount(loaded.tabs()));

            loaded.tabs().get("Overview").fire();
            assertSelectionAndContent(loaded, "Overview");
            assertEquals(1, selectedCount(loaded.tabs()));
        });
    }

    private static LoadedMyShale load() throws Exception {
        FXMLLoader loader = new FXMLLoader(MyShaleSectionNavigationSelectionTest.class.getResource("/fxml/my-shale.fxml"));
        Parent root = loader.load();
        MyShaleController controller = loader.getController();
        @SuppressWarnings("unchecked")
        Map<String, Button> tabs = (Map<String, Button>) field(controller, "sectionTabs");
        return new LoadedMyShale(root, controller, tabs);
    }

    private static void assertSelectionAndContent(LoadedMyShale loaded, String expected) throws Exception {
        for (Map.Entry<String, Button> entry : loaded.tabs().entrySet()) {
            assertEquals(entry.getKey().equals(expected), entry.getValue().getStyleClass().contains(ACTIVE_CLASS), entry.getKey());
        }
        assertEquals(expected.equals("Overview"), visibleManaged(loaded.controller(), "overviewSectionPane"));
        assertEquals(expected.equals("My Tasks"), visibleManaged(loaded.controller(), "tasksSectionPane"));
        assertEquals(expected.equals("My Cases"), visibleManaged(loaded.controller(), "myCasesSectionPane"));
    }

    private static long selectedCount(Map<String, Button> tabs) {
        return tabs.values().stream().filter(button -> button.getStyleClass().contains(ACTIVE_CLASS)).count();
    }

    private static boolean visibleManaged(Object target, String name) throws Exception {
        Pane pane = (Pane) field(target, name);
        return pane.isVisible() && pane.isManaged();
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private record LoadedMyShale(Parent root, MyShaleController controller, Map<String, Button> tabs) {
    }
}
