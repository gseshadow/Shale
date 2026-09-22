package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.ui.controller.support.PartyAddWorkflowDialog;
import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.theme.Theme;
import com.shale.ui.theme.ThemeManager;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Runtime coverage for the production FXML/controller hierarchy that went blank after 61fb4598. */
final class NewIntakeRuntimeLayoutRegressionTest {
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
                    assertRenderedProductionHierarchy(fixture);
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
