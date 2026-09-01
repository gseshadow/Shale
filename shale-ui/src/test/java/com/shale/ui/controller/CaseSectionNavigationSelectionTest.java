package com.shale.ui.controller;

import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.util.AppSectionTabs;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CaseSectionNavigationSelectionTest {
    private static final List<String> ORDER = List.of(
            "Overview", "Details", "Parties", "Tasks", "Calendar", "Dates", "Requests", "Links", "Timeline");

    @Test
    void initialOverviewAndEveryNavigationSelectionStaySynchronized() {
        JavaFxTestSupport.runAndWait(() -> {
            LoadedCase loaded = load(null);
            assertEquals(ORDER, List.copyOf(loaded.tabs().keySet()));
            assertSelectionAndContent(loaded, "Overview");

            for (String section : ORDER) {
                loaded.tabs().get(section).fire(); // Button.fire covers the same action path used by mouse and keyboard activation.
                assertSelectionAndContent(loaded, section);
            }
            loaded.tabs().get("Overview").fire();
            assertSelectionAndContent(loaded, "Overview");
        });
    }

    @Test
    void supportedInitialSectionIsSelectedAndUnavailableOneFallsBackWithoutDuplicateActivation() {
        JavaFxTestSupport.runAndWait(() -> {
            LoadedCase restored = load("REQUESTS");
            assertSelectionAndContent(restored, "Requests");

            LoadedCase unavailable = load("CASE_MATERIALS");
            assertSelectionAndContent(unavailable, "Overview");
            assertEquals(1, selectedCount(unavailable.tabs()));
        });
    }

    @Test
    void initializationUsesTheOrdinarySelectionPathExactlyOnce() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        String setup = methodBody(source, "private void setupSections()");
        assertEquals(1, occurrences(setup, "onSectionSelected(initialSectionName, false)"),
                "Initial presentation must use the normal selection/content path without a second activation or refresh.");
    }

    @Test
    void usesSharedComponentContractAndScrollableSingleRowAtNarrowWidth() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        String fxml = Files.readString(Path.of("src/main/resources/fxml/case.fxml"));
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));
        assertTrue(source.contains("AppSectionTabs.buildTabs("));
        assertTrue(source.contains("AppSectionTabs.setActive("));
        assertFalse(fxml.contains("Case Materials"));
        assertFalse(css.contains(".case-section-tab"));

        JavaFxTestSupport.runAndWait(() -> {
            LoadedCase loaded = load(null);
            Scene scene = new Scene(loaded.root(), 420, 700);
            scene.getStylesheets().add(CaseSectionNavigationSelectionTest.class.getResource("/css/app.css").toExternalForm());
            ScrollPane scroll = (ScrollPane) loaded.root().lookup(".app-section-tabs-scroll");
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, scroll.getHbarPolicy());
            assertEquals(ScrollPane.ScrollBarPolicy.NEVER, scroll.getVbarPolicy());
            assertTrue(scroll.isPannable());
            assertSame(field(loaded.controller(), "sectionTabsBar"), scroll.getContent());
            loaded.root().applyCss();
            loaded.root().layout();
            assertTrue(scroll.getContent().prefWidth(-1) > scroll.getViewportBounds().getWidth(),
                    "The complete, unclipped tab row must remain horizontally scrollable at narrow widths.");
            loaded.tabs().values().forEach(button ->
                    assertTrue(button.getStyleClass().contains(AppSectionTabs.TAB_BUTTON_STYLE_CLASS)));
        });
    }

    @Test
    void caseSectionNavigationProvidesVerticalClearanceWithoutChangingHeaderSpacing() {
        JavaFxTestSupport.runAndWait(() -> {
            LoadedCase loaded = load(null);
            Scene scene = new Scene(loaded.root(), 1100, 700);
            scene.getStylesheets().add(CaseSectionNavigationSelectionTest.class.getResource("/css/app.css").toExternalForm());
            loaded.root().applyCss();
            loaded.root().layout();

            BorderPane caseRoot = (BorderPane) loaded.root().lookup("#caseRootPane");
            VBox caseHeader = (VBox) caseRoot.getTop();
            ScrollPane scroll = (ScrollPane) loaded.root().lookup("#caseSectionNavigationScrollPane");
            Region viewport = (Region) scroll.lookup(".viewport");
            HBox tabRow = (HBox) scroll.getContent();
            double tallestTab = loaded.tabs().values().stream()
                    .mapToDouble(button -> button.getBoundsInParent().getHeight())
                    .max()
                    .orElseThrow();

            assertSame(field(loaded.controller(), "sectionTabsBar"), tabRow,
                    "The measured strip must be the Overview/Details/etc. navigation row.");
            assertEquals(6, tabRow.getPadding().getTop());
            assertEquals(6, tabRow.getPadding().getBottom());
            assertTrue(tabRow.getHeight() >= tallestTab + tabRow.getPadding().getTop() + tabRow.getPadding().getBottom(),
                    "The Case section row must include its tab pills plus vertical clearance.");
            assertTrue(viewport.getHeight() >= tabRow.getHeight(),
                    "The Case section viewport must not clip the padded tab row.");

            assertEquals(8, caseHeader.getPadding().getTop());
            assertEquals(6, caseHeader.getPadding().getBottom());
            assertEquals(48, loaded.root().lookup("#statusTimelineHost").minHeight(-1));
            assertFalse(caseHeader.getStyleClass().contains("app-section-tabs-scroll"),
                    "The Case header/status surface must remain distinct from the section navigation strip.");
        });
    }

    private static LoadedCase load(String initialSection) throws Exception {
        FXMLLoader loader = new FXMLLoader(CaseSectionNavigationSelectionTest.class.getResource("/fxml/case.fxml"));
        if (initialSection != null) {
            loader.setControllerFactory(type -> {
                try {
                    Object controller = type.getDeclaredConstructor().newInstance();
                    type.getMethod("setInitialSection", String.class).invoke(controller, initialSection);
                    return controller;
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
        Parent root = loader.load();
        CaseController controller = loader.getController();
        @SuppressWarnings("unchecked")
        Map<String, Button> tabs = (Map<String, Button>) field(controller, "sectionTabs");
        return new LoadedCase(root, controller, tabs);
    }

    private static void assertSelectionAndContent(LoadedCase loaded, String expected) throws Exception {
        assertEquals(1, selectedCount(loaded.tabs()));
        for (Map.Entry<String, Button> entry : loaded.tabs().entrySet()) {
            assertEquals(entry.getKey().equals(expected),
                    entry.getValue().getStyleClass().contains(AppSectionTabs.TAB_BUTTON_ACTIVE_STYLE_CLASS), entry.getKey());
        }
        assertEquals(expected, field(loaded.controller(), "activeSectionName"));
        assertTrue(activeRoot(loaded.controller(), expected).isVisible());
        assertTrue(activeRoot(loaded.controller(), expected).isManaged());
    }

    private static Node activeRoot(CaseController controller, String section) throws Exception {
        String fieldName = switch (section) {
            case "Overview" -> "overviewScrollPane";
            case "Details" -> "detailsSectionPane";
            case "Tasks" -> "tasksTabPane";
            case "Calendar" -> "caseCalendarTabPane";
            case "Dates" -> "caseDatesTabPane";
            case "Requests" -> "caseRequestsTabPane";
            case "Links" -> "caseLinksTabPane";
            default -> "genericPane";
        };
        return (Node) field(controller, fieldName);
    }

    private static long selectedCount(Map<String, Button> tabs) {
        return tabs.values().stream()
                .filter(button -> button.getStyleClass().contains(AppSectionTabs.TAB_BUTTON_ACTIVE_STYLE_CLASS)).count();
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            if (source.charAt(index) == '{') depth++;
            if (source.charAt(index) == '}' && --depth == 0) return source.substring(open + 1, index);
        }
        throw new AssertionError("Method not found: " + signature);
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(value, index)) >= 0; index += value.length()) count++;
        return count;
    }

    private record LoadedCase(Parent root, CaseController controller, Map<String, Button> tabs) {
    }
}
