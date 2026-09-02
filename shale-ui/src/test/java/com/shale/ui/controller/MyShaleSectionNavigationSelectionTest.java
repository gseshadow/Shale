package com.shale.ui.controller;

import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.paint.Color;
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

    @Test
    void navigationResolvesTheSameSharedSelectedAndInactiveSurfacesAsCaseView() {
        JavaFxTestSupport.runAndWait(() -> {
            LoadedMyShale myShale = load();
            FXMLLoader caseLoader = new FXMLLoader(getClass().getResource("/fxml/case.fxml"));
            Parent caseRoot = caseLoader.load();
            CaseController caseController = caseLoader.getController();
            @SuppressWarnings("unchecked")
            Map<String, Button> caseTabs = (Map<String, Button>) field(caseController, "sectionTabs");

            Parent comparisonRoot = new HBox(myShale.root(), caseRoot);
            Scene scene = new Scene(comparisonRoot, 1800, 700);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            comparisonRoot.applyCss();
            comparisonRoot.layout();

            Button mySelected = myShale.tabs().get("Overview");
            Button myInactive = myShale.tabs().get("My Tasks");
            Button caseSelected = caseTabs.get("Overview");
            Button caseInactive = caseTabs.get("Details");

            assertTabPaintEquals(caseSelected, mySelected, "selected");
            assertTabPaintEquals(caseInactive, myInactive, "inactive");
            for (Button tab : myShale.tabs().values()) {
                assertFalse(tab.isDisabled(), tab.getText() + " must remain enabled.");
                assertFalse(tab.getStyleClass().contains("shale-control-button"),
                        tab.getText() + " must not also opt into the competing ordinary-button hierarchy.");
                for (Parent parent = tab.getParent(); parent != null; parent = parent.getParent()) {
                    assertEquals(1.0, parent.getOpacity(), 0.001,
                            tab.getText() + " must not be washed out by reduced parent opacity.");
                    assertFalse(parent.isDisabled(), tab.getText() + " must not inherit a disabled parent state.");
                }
            }
        });
    }

    private static void assertTabPaintEquals(Button expected, Button actual, String state) {
        Color expectedFill = (Color) expected.getBackground().getFills().getFirst().getFill();
        Color actualFill = (Color) actual.getBackground().getFills().getFirst().getFill();
        assertEquals(expectedFill, actualFill, "My Shale " + state + " surface must resolve from the shared tab CSS.");
        assertEquals(expected.getTextFill(), actual.getTextFill(),
                "My Shale " + state + " text must resolve from the shared tab CSS.");
        assertEquals(expected.getBorder().getStrokes().getFirst().getTopStroke(),
                actual.getBorder().getStrokes().getFirst().getTopStroke(),
                "My Shale " + state + " border must resolve from the shared tab CSS.");
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
