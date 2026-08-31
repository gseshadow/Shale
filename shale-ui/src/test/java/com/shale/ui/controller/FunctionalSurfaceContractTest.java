package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

class FunctionalSurfaceContractTest {
    private static final Path FXML = Path.of("src/main/resources/fxml");

    @Test
    void foundationDefinesTranslucentSemanticFunctionalSurfaces() throws IOException {
        String colors = Files.readString(Path.of("src/main/resources/css/foundation/colors.css"));
        String surfaces = Files.readString(Path.of("src/main/resources/css/foundation/surfaces.css"));
        String app = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertTrue(colors.contains("-shale-color-page-header-surface: rgba(239, 246, 253, 0.90)"));
        assertTrue(colors.contains("-shale-color-functional-content-surface: rgba(242, 248, 255, 0.88)"));
        assertTrue(surfaces.contains(".page-header-surface"));
        assertTrue(surfaces.contains(".primary-content-surface"));
        assertTrue(surfaces.contains(".content-surface"));
        assertTrue(app.contains("radial-gradient("), "the decorative application gradient must remain");
    }

    @Test
    void mainShellOwnsThePrimaryContentReadabilityBoundary() throws IOException {
        String main = read("main.fxml");
        assertTrue(main.contains("styleClass=\"primary-content-surface\""));
        assertTrue(main.indexOf("styleClass=\"primary-content-surface\"") < main.indexOf("fx:id=\"sectionContent\""));
        assertTrue(main.contains("fx:id=\"sectionTitleLabel\""));
        assertTrue(main.contains("fx:id=\"sectionSubtitleLabel\""));
    }

    @Test
    void productionMainFxmlRendersTheSharedSurfaceAroundTheRouteOutlet() {
        JavaFxTestSupport.runAndWait(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 1280, 800);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            root.applyCss();
            root.layout();

            Region surface = (Region) root.lookup(".primary-content-surface");
            StackPane outlet = (StackPane) loader.getNamespace().get("sectionContent");
            assertTrue(surface != null && surface.getBackground() != null);
            assertTrue(surface.getBackground().getFills().getFirst().getFill().isOpaque() == false);
            assertTrue(isAncestor(surface, outlet), "the routed view outlet must be inside the workspace surface");

            for (String view : new String[] {
                    "my-shale.fxml", "cases.fxml", "contacts.fxml", "organizations.fxml",
                    "team.fxml", "reports.fxml", "calendar.fxml", "settings.fxml"
            }) {
                Parent destination = FXMLLoader.load(getClass().getResource("/fxml/" + view));
                outlet.getChildren().setAll(destination);
                root.applyCss();
                root.layout();
                assertTrue(isAncestor(surface, destination), view);
            }
        });
    }

    @Test
    void detailHeadersAndPageNavigationUseSharedHeaderSurface() throws IOException {
        for (String view : new String[] {"case.fxml", "contact.fxml", "organization.fxml", "user.fxml"}) {
            assertTrue(read(view).contains("styleClass=\"page-header-surface\""), view);
        }
        assertTrue(read("case.fxml").contains("styleClass=\"app-section-tabs-scroll page-header-surface\""));
        assertTrue(read("my-shale.fxml").contains("styleClass=\"app-section-tabs-scroll page-header-surface\""));
    }

    @Test
    void everyPrimaryDestinationIsSwappedIntoTheSharedHost() throws IOException {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MainController.java"));
        for (String method : new String[] {
                "showMyShaleView", "showCasesListView", "showContactsListView", "showOrganizationsListView",
                "showTeamListView", "showReportsView", "showCalendarView", "showSettingsView"
        }) {
            int start = controller.indexOf("void " + method + "(");
            assertTrue(start >= 0, method);
            int end = controller.indexOf("\n\t}", start);
            assertTrue(controller.substring(start, end).contains("sectionContent.getChildren().setAll("), method);
        }
    }

    private static String read(String name) throws IOException {
        return Files.readString(FXML.resolve(name));
    }

    private static boolean isAncestor(Parent ancestor, Node node) {
        for (Parent parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == ancestor) return true;
        }
        return false;
    }
}
