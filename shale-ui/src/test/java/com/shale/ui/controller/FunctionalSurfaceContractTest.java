package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.theme.Theme;
import com.shale.ui.theme.ThemeManager;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

class FunctionalSurfaceContractTest {
    private static final Path FXML = Path.of("src/main/resources/fxml");

    @Test
    void foundationDefinesTranslucentSemanticFunctionalSurfaces() throws IOException {
        String colors = Files.readString(Path.of("src/main/resources/css/foundation/colors.css"));
        String surfaces = Files.readString(Path.of("src/main/resources/css/foundation/surfaces.css"));
        String app = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertTrue(colors.contains("-shale-color-page-header-surface: rgba(239, 246, 253, 0.90)"));
        assertTrue(colors.contains("-shale-color-functional-content-surface: rgba(190, 208, 220, 0.94)"));
        assertTrue(surfaces.contains(".page-header-surface"));
        assertTrue(surfaces.contains(".primary-content-surface"));
        assertTrue(surfaces.contains(".content-surface"));
        assertTrue(app.contains("@import \"foundation/shell.css\";"),
                "the stable stylesheet entry point must install the shared A.2 shell foundation");
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
            Color workspaceColor = (Color) surface.getBackground().getFills().getFirst().getFill();
            assertTrue(workspaceColor.equals(Color.rgb(248, 251, 255)),
                    "the shared workspace must use the near-white A.2 content-plane token");
            assertTrue(workspaceColor.isOpaque());
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
    void topLevelRoutesShareOneThemeOwnedPaintedHeaderWithoutRepaintingTheirRoots() {
        JavaFxTestSupport.runAndWait(() -> {
            FXMLLoader shellLoader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Parent shell = shellLoader.load();
            Scene scene = new Scene(shell, 1280, 800);
            ThemeManager themes = new ThemeManager();
            themes.register(scene);

            Region contentPlane = (Region) shell.lookup(".primary-content-surface");
            StackPane outlet = (StackPane) shellLoader.getNamespace().get("sectionContent");
            VBox header = (VBox) shellLoader.getNamespace().get("sectionHeaderBox");
            Label title = (Label) shellLoader.getNamespace().get("sectionTitleLabel");
            Label subtitle = (Label) shellLoader.getNamespace().get("sectionSubtitleLabel");
            Region tokenProbe = new Region();
            tokenProbe.setStyle("-fx-background-color: -shale-color-page-header-surface;");

            for (Theme theme : Theme.values()) {
                themes.setActiveTheme(theme);
                for (String view : new String[] {
                        "my-shale.fxml", "cases.fxml", "contacts.fxml", "organizations.fxml", "team.fxml"
                }) {
                    Parent destination = FXMLLoader.load(getClass().getResource("/fxml/" + view));
                    outlet.getChildren().setAll(destination, tokenProbe);
                    tokenProbe.setVisible(false);
                    tokenProbe.setManaged(false);
                    shell.applyCss();
                    shell.layout();

                    assertTrue(shell.lookupAll(".shell-page-header").size() == 1,
                            view + " must compose exactly one canonical top-level page header");
                    assertTrue(!isTransparent(header.getBackground()),
                            view + " must compute a painted header in " + theme);
                    assertTrue(firstPaint(header.getBackground()).equals(firstPaint(tokenProbe.getBackground())),
                            view + " header paint must resolve from the theme-owned page-header token in " + theme);
                    assertTrue(isTransparent(((Region) destination).getBackground()),
                            view + " routed root must remain transparent in " + theme);
                    assertTrue(!isTransparent(contentPlane.getBackground()),
                            "the shared rounded content plane must remain independently painted");
                    assertTrue(header.getPadding().equals(new javafx.geometry.Insets(12, 14, 12, 14)),
                            view + " must use the shared header padding");
                    assertTrue(header.getBackground().getFills().getFirst().getRadii()
                                    .getTopLeftHorizontalRadius() == 12,
                            view + " must use the shared header radius");
                    assertTrue(readable(title.getTextFill(), firstPaint(header.getBackground())),
                            view + " title must remain readable in " + theme);
                    assertTrue(readable(subtitle.getTextFill(), firstPaint(header.getBackground())),
                            view + " subtitle must remain readable in " + theme);
                }
            }
        });
    }

    @Test
    void directoryRoutesDoNotDuplicateTheShellOwnedTitleAndSubtitle() throws IOException {
        for (String view : new String[] {"my-shale.fxml", "contacts.fxml", "organizations.fxml"}) {
            String fxml = read(view);
            assertTrue(!fxml.contains("shale-page-title"), view + " must not duplicate the shell-owned title");
        }
    }

    @Test
    void detailHeadersAndPageNavigationUseSharedHeaderSurface() throws IOException {
        for (String view : new String[] {"case.fxml", "contact.fxml", "organization.fxml", "user.fxml"}) {
            assertTrue(read(view).contains("styleClass=\"page-header-surface\""), view);
        }
        assertNavigationSurfaceClasses(read("case.fxml"));
    }

    private static void assertNavigationSurfaceClasses(String fxml) {
        assertTrue(fxml.contains("<String fx:value=\"app-section-tabs-scroll\" />"));
        assertTrue(fxml.contains("<String fx:value=\"page-header-surface\" />"));
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

    private static boolean isTransparent(Background background) {
        return background == null || background.getFills().isEmpty()
                || background.getFills().stream().allMatch(fill -> fill.getFill() instanceof Color color
                        && color.getOpacity() == 0);
    }

    private static Paint firstPaint(Background background) {
        assertTrue(background != null && !background.getFills().isEmpty(), "expected a computed background fill");
        return background.getFills().getFirst().getFill();
    }

    private static boolean readable(Paint foreground, Paint background) {
        if (!(foreground instanceof Color text) || !(background instanceof Color surface)) return false;
        double textLuminance = 0.2126 * text.getRed() + 0.7152 * text.getGreen() + 0.0722 * text.getBlue();
        double surfaceLuminance = 0.2126 * surface.getRed() + 0.7152 * surface.getGreen() + 0.0722 * surface.getBlue();
        return Math.abs(textLuminance - surfaceLuminance) >= 0.35;
    }
}
