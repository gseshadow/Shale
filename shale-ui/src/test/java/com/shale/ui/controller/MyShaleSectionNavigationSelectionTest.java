package com.shale.ui.controller;

import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
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
        assertTrue(source.matches("(?s).*setupSections\\(\\);\\s+applySectionSelectionState\\(activeSection\\);.*"));
        assertFalse(source.matches("(?s).*setupSections\\(\\);\\s+onSectionSelected\\(SECTION_OVERVIEW\\);.*"),
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

    @Test
    void navigationRowProvidesRoomForTheCompleteSharedTabPills() {
        JavaFxTestSupport.runAndWait(() -> {
            LoadedMyShale loaded = load();
            Scene scene = new Scene(loaded.root(), 900, 700);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            loaded.root().applyCss();
            loaded.root().layout();

            ScrollPane scroll = (ScrollPane) loaded.root().lookup(".app-section-tabs-scroll");
            HBox row = (HBox) scroll.getContent();
            assertEquals(6, row.getPadding().getTop(), 0.01,
                    "My Shale must use the shared navigation row's vertical breathing room.");
            assertEquals(6, row.getPadding().getBottom(), 0.01,
                    "My Shale must use the shared navigation row's vertical breathing room.");
            for (Button tab : loaded.tabs().values()) {
                var tabInViewport = scroll.getViewportBounds().contains(
                        scroll.sceneToLocal(tab.localToScene(tab.getBoundsInLocal())));
                assertTrue(tabInViewport, tab.getText() + " must be fully contained without vertical clipping.");
            }
        });
    }

    private static LoadedMyShale load() throws Exception {
        FXMLLoader loader = new FXMLLoader(MyShaleSectionNavigationSelectionTest.class.getResource("/fxml/my-shale.fxml"));
        // FXMLLoader otherwise falls back to the current thread's context loader. The
        // shared JavaFX application thread may legitimately have no context loader
        // after other tests have used the toolkit, so do not depend on that global
        // thread state when resolving the controller and imported FXML types.
        loader.setClassLoader(MyShaleSectionNavigationSelectionTest.class.getClassLoader());
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
