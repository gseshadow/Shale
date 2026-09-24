package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.ui.controller.support.PartyAddWorkflowDialog;
import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.theme.Theme;
import com.shale.ui.theme.ThemeManager;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/** Runtime coverage for the production FXML/controller hierarchy that went blank after 61fb4598. */
final class NewIntakeRuntimeLayoutRegressionTest {
    private static final Map<String, String> SECTION_CLASSES = Map.of(
            "callerSection", "caller-section-panel",
            "clientSection", "client-section-panel",
            "caseSection", "case-section-panel",
            "partiesSection", "new-intake-parties-section",
            "incidentSection", "incident-section-panel");

    @BeforeAll
    static void startJavaFxToolkit() {
        assumeTrue(hasDisplay(), "New Intake runtime layout requires a graphical display");
        JavaFxTestSupport.ensureToolkitStarted();
    }

    @Test
    void productionViewRendersEverySectionAndCompactPartiesInBothThemes() {
        Fixture fixture = JavaFxTestSupport.runAndWait(() -> open(1180, 760));
        try {
            // Run after initialize()'s responsive-layout callback, then perform a real CSS/layout pass.
            JavaFxTestSupport.runAndWait(() -> {
                for (Theme theme : Theme.values()) {
                    fixture.themes.setActiveTheme(theme);
                    layout(fixture);
                    assertStylesheets(fixture, theme);
                    assertRenderedProductionHierarchy(fixture);
                    assertComputedSectionCards(fixture, theme);
                    assertControlInsets(fixture);
                }
            });
        } finally {
            JavaFxTestSupport.runAndWait(fixture.stage::close);
        }
    }

    @Test
    void populatedPartiesAndNarrowLayoutKeepTheActualProductionNodesAttached() {
        Fixture fixture = JavaFxTestSupport.runAndWait(() -> open(1180, 760));
        try {
            JavaFxTestSupport.runAndWait(() -> {
                @SuppressWarnings("unchecked")
                List<PartyAddWorkflowDialog.AddPartyDraft> drafts =
                        (List<PartyAddWorkflowDialog.AddPartyDraft>) field(fixture.controller, "pendingParties");
                drafts.add(new PartyAddWorkflowDialog.AddPartyDraft(
                        "contact", 10L, "Ada Contact", 1L, "plaintiff", true, "", false,
                        null, null, null, null));
                drafts.add(new PartyAddWorkflowDialog.AddPartyDraft(
                        "organization", 20L, "Acme Organization", 1L, "defendant", false, "", false,
                        null, null, null, null));
                invoke(fixture.controller, "renderPendingParties");
                layout(fixture);
                VBox cards = node(fixture.loader, "partiesListBox", VBox.class);
                assertEquals(2, cards.getChildren().size(), "both production staged-party cards must render");
                cards.getChildren().forEach(card -> assertPositiveBounds(card, "staged-party card"));

                invoke(fixture.controller, "configureResponsiveWorkspace", 700.0);
                fixture.stage.setWidth(700);
                layout(fixture);
                assertRenderedSections(fixture);
                Node caller = node(fixture.loader, "callerSection", Node.class);
                Node client = node(fixture.loader, "clientSection", Node.class);
                Node caseSection = node(fixture.loader, "caseSection", Node.class);
                Node parties = node(fixture.loader, "partiesSection", Node.class);
                Node incident = node(fixture.loader, "incidentSection", Node.class);
                assertTrue(caller.getLayoutY() < client.getLayoutY()
                                && client.getLayoutY() < caseSection.getLayoutY()
                                && caseSection.getLayoutY() < parties.getLayoutY()
                                && parties.getLayoutY() < incident.getLayoutY(),
                        "narrow production order must remain Caller, Client, Case, Parties, Incident");
            });
        } finally {
            JavaFxTestSupport.runAndWait(fixture.stage::close);
        }
    }

    private static Fixture open(double width, double height) throws Exception {
        FXMLLoader loader = new FXMLLoader(NewIntakeRuntimeLayoutRegressionTest.class.getResource("/fxml/new-intake.fxml"));
        BorderPane root = loader.load();
        Stage stage = new Stage();
        Scene scene = new Scene(root, width, height);
        ThemeManager themes = new ThemeManager();
        themes.register(scene);
        stage.setScene(scene);
        stage.setWidth(width);
        stage.setHeight(height);
        stage.show();
        return new Fixture(loader, loader.getController(), root, stage, themes);
    }

    private static void assertRenderedProductionHierarchy(Fixture fixture) {
        ScrollPane scroll = node(fixture.loader, "intakeScrollPane", ScrollPane.class);
        assertNotNull(scroll.getContent(), "the production ScrollPane must retain its form content");
        assertTrue(isAncestor(fixture.root, scroll.getContent()), "ScrollPane content must remain in the displayed root");
        assertRenderedSections(fixture);

        Node client = node(fixture.loader, "clientSection", Node.class);
        Node parties = node(fixture.loader, "partiesSection", Node.class);
        Node incident = node(fixture.loader, "incidentSection", Node.class);
        assertTrue(parties.getBoundsInParent().getHeight() < client.getBoundsInParent().getHeight() * 0.6,
                "empty Parties must be materially shorter than Client");
        assertTrue(Math.abs(parties.getBoundsInParent().getHeight() - ((Region) parties).prefHeight(-1)) < 2.0,
                "empty Parties must remain at its preferred content height");
        assertTrue(Math.abs(incident.getLayoutY()
                        - (parties.getLayoutY() + parties.getBoundsInParent().getHeight() + 16)) < 2.0,
                "Incident must directly follow Parties in the independent right column");

        for (String control : new String[] {"callerFirstNameField", "clientFirstNameField", "practiceAreaHost",
                "statusHost", "addPartyButton", "descriptionArea"}) {
            Node node = node(fixture.loader, control, Node.class);
            assertTrue(isAncestor(scroll.getContent(), node), control + " must remain production form content");
            assertPositiveBounds(node, control);
        }
        Node footer = node(fixture.loader, "intakeActionBar", Node.class);
        assertFalse(isAncestor(scroll, footer), "the fixed footer must remain outside the ScrollPane");
        Node horizontal = scroll.lookup(".scroll-bar:horizontal");
        assertTrue(horizontal == null || !horizontal.isVisible(), "page-level horizontal scrolling must not appear");
    }

    private static void assertRenderedSections(Fixture fixture) {
        ScrollPane scroll = node(fixture.loader, "intakeScrollPane", ScrollPane.class);
        for (String id : new String[] {"callerSection", "clientSection", "caseSection", "partiesSection", "incidentSection"}) {
            Node section = node(fixture.loader, id, Node.class);
            assertTrue(section.isManaged(), id + " must be managed");
            assertTrue(section.isVisible(), id + " must be visible");
            assertNotNull(section.getParent(), id + " must have exactly one JavaFX parent");
            assertTrue(isAncestor(scroll.getContent(), section), id + " must descend from rendered ScrollPane content");
            assertPositiveBounds(section, id);
        }
        assertPositiveBounds(scroll.getContent(), "New Intake form content");
    }

    private static void assertStylesheets(Fixture fixture, Theme theme) {
        assertTrue(fixture.root.getStyleClass().contains("new-intake-root"),
                "the production BorderPane must own the verified Intake root class");
        assertTrue(fixture.stage.getScene().getStylesheets().stream().anyMatch(url -> url.endsWith("/css/app.css")),
                "the production application stylesheet must be attached");
        assertTrue(fixture.stage.getScene().getStylesheets().stream()
                        .anyMatch(url -> url.endsWith(theme.stylesheetResource())),
                "the active " + theme + " stylesheet must be attached");
    }

    private static void assertComputedSectionCards(Fixture fixture, Theme theme) {
        Color rootFill = firstFill(fixture.root);
        java.util.Set<Color> fills = new java.util.HashSet<>();
        for (Map.Entry<String, String> entry : SECTION_CLASSES.entrySet()) {
            VBox section = node(fixture.loader, entry.getKey(), VBox.class);
            assertTrue(section.getStyleClass().contains("shale-section-card"), entry.getKey());
            assertTrue(section.getStyleClass().contains("new-intake-section"), entry.getKey());
            assertTrue(section.getStyleClass().contains(entry.getValue()), entry.getKey());
            assertTrue(isAncestor(fixture.root, section), entry.getKey() + " must inherit from new-intake-root");
            Color fill = firstFill(section);
            fills.add(fill);
            assertTrue(fill.getOpacity() > 0.9, entry.getKey() + " background must be visibly opaque");
            assertTrue(colorDistance(fill, rootFill) > 0.04,
                    entry.getKey() + " background must differ visibly from the content plane");
            assertTrue(section.getPadding().getTop() > 4 && section.getPadding().getRight() > 4
                            && section.getPadding().getBottom() > 4 && section.getPadding().getLeft() > 4,
                    entry.getKey() + " must compute meaningful card padding");
            assertNotNull(section.getBorder(), entry.getKey() + " must compute a border");
            assertFalse(section.getBorder().getStrokes().isEmpty(), entry.getKey() + " border must have a stroke");
            assertNotNull(section.getEffect(), entry.getKey() + " must compute the restrained card elevation");
            assertPositiveBounds(section, entry.getKey());

            Label heading = (Label) section.lookup(".shale-section-title");
            assertNotNull(heading, entry.getKey() + " must retain its production heading");
            assertTrue(contrastRatio((Color) heading.getTextFill(), fill) >= 3.0,
                    entry.getKey() + " heading must remain readable in " + theme);
        }
        assertTrue(fills.size() == SECTION_CLASSES.size(),
                "all five Intake sections must compute distinct semantic fills in " + theme);

        if (theme == Theme.LIGHT) {
            Color caller = firstFill(node(fixture.loader, "callerSection", VBox.class));
            Color client = firstFill(node(fixture.loader, "clientSection", VBox.class));
            Color caseFill = firstFill(node(fixture.loader, "caseSection", VBox.class));
            Color parties = firstFill(node(fixture.loader, "partiesSection", VBox.class));
            Color incident = firstFill(node(fixture.loader, "incidentSection", VBox.class));
            assertTrue(caller.getBlue() > caller.getRed(), "Caller must retain its blue-family fill");
            assertTrue(client.getGreen() > client.getBlue(), "Client must retain its green-family fill");
            assertTrue(caseFill.getRed() > caseFill.getBlue(), "Case must retain its amber-family fill");
            assertTrue(parties.getBlue() > parties.getGreen() && parties.getRed() > parties.getGreen(),
                    "Parties must retain its pale-purple fill");
            assertTrue(incident.getRed() > incident.getGreen(), "Incident must retain its rose-family fill");
            assertTrue(colorDistance(parties, Color.web("#f3edff")) < 0.01,
                    "Parties must compute the established #f3edff Light token");
        }
    }

    private static void assertControlInsets(Fixture fixture) {
        assertInset(fixture, "callerSection", "callerFirstNameField");
        assertInset(fixture, "clientSection", "callerIsClientCheckBox");
        assertInset(fixture, "caseSection", "selectPracticeAreaButton");
        assertInset(fixture, "caseSection", "selectStatusButton");
        assertInset(fixture, "partiesSection", "addPartyButton");
        assertInset(fixture, "partiesSection", "partiesEmptyLabel");
        assertInset(fixture, "incidentSection", "descriptionArea");
        assertInset(fixture, "incidentSection", "summaryArea");

        for (String id : new String[] {"cancelButton", "createIntakeButton"}) {
            Button button = node(fixture.loader, id, Button.class);
            assertTrue(button.getPadding().getLeft() > 4 && button.getPadding().getRight() > 4,
                    id + " must retain semantic horizontal padding");
            assertTrue(button.getHeight() >= 32, id + " must retain a readable minimum height");
        }
    }

    private static void assertInset(Fixture fixture, String sectionId, String childId) {
        Region section = node(fixture.loader, sectionId, Region.class);
        Node child = node(fixture.loader, childId, Node.class);
        javafx.geometry.Bounds bounds = section.sceneToLocal(child.localToScene(child.getBoundsInLocal()));
        assertTrue(bounds.getMinX() > 4 && bounds.getMinY() > 4,
                childId + " must be visibly inset from " + sectionId);
        assertTrue(section.getWidth() - bounds.getMaxX() > 4,
                childId + " must not touch the right edge of " + sectionId);
    }

    private static Color firstFill(Region region) {
        Background background = region.getBackground();
        assertNotNull(background, region.getId() + " must compute a background");
        assertFalse(background.getFills().isEmpty(), region.getId() + " must compute a background fill");
        return (Color) background.getFills().getFirst().getFill();
    }

    private static double colorDistance(Color left, Color right) {
        return Math.sqrt(Math.pow(left.getRed() - right.getRed(), 2)
                + Math.pow(left.getGreen() - right.getGreen(), 2)
                + Math.pow(left.getBlue() - right.getBlue(), 2));
    }

    private static double contrastRatio(Color foreground, Color background) {
        double lighter = Math.max(luminance(foreground), luminance(background));
        double darker = Math.min(luminance(foreground), luminance(background));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen())
                + 0.0722 * channel(color.getBlue());
    }

    private static double channel(double value) {
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static void layout(Fixture fixture) {
        fixture.root.applyCss();
        fixture.root.layout();
        fixture.root.applyCss();
        fixture.root.layout();
    }

    private static void assertPositiveBounds(Node node, String name) {
        assertTrue(node.getBoundsInParent().getWidth() > 0, name + " must have positive rendered width");
        assertTrue(node.getBoundsInParent().getHeight() > 0, name + " must have positive rendered height");
    }

    private static boolean isAncestor(Node ancestor, Node descendant) {
        for (Node current = descendant; current != null; current = current.getParent()) {
            if (current == ancestor) return true;
        }
        return false;
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(name, exception);
        }
    }

    private static void invoke(Object target, String methodName, Object... arguments) {
        try {
            Class<?>[] types = arguments.length == 0 ? new Class<?>[0] : new Class<?>[] {double.class};
            Method method = target.getClass().getDeclaredMethod(methodName, types);
            method.setAccessible(true);
            method.invoke(target, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(methodName, exception);
        }
    }

    private static <T> T node(FXMLLoader loader, String id, Class<T> type) {
        Object value = loader.getNamespace().get(id);
        assertNotNull(value, id + " must exist in production FXML");
        return type.cast(value);
    }

    private static boolean hasDisplay() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null
                || os.contains("win") || os.contains("mac");
    }

    private record Fixture(FXMLLoader loader, NewIntakeController controller, BorderPane root,
            Stage stage, ThemeManager themes) {
    }
}
