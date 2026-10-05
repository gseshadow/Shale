package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Advisory rendered-layout coverage for the production login hierarchy. */
final class LoginRuntimeLayoutTest {
    @BeforeAll
    static void startJavaFxToolkit() {
        assumeTrue(hasDisplay(), "Login runtime layout requires a graphical display");
        JavaFxTestSupport.ensureToolkitStarted();
    }

    @Test
    void tallWindowCentersContentWithoutStretchingTheSignInCard() {
        Fixture fixture = JavaFxTestSupport.runAndWait(() -> open(1920, 900));
        try {
            JavaFxTestSupport.runAndWait(() -> {
                layout(fixture);
                double cardHeight = fixture.card.getHeight();
                fixture.stage.setHeight(1300);
                layout(fixture);

                assertEquals(cardHeight, fixture.card.getHeight(), 1.0,
                        "extra window height must remain outside the content-sized sign-in card");
                assertEquals(fixture.card.prefHeight(fixture.card.getWidth()), fixture.card.getHeight(), 1.0,
                        "the sign-in card must render at its CSS-derived preferred content height");
                assertCentered(fixture.viewport, fixture.content, "the complete two-column composition");
                assertCentered(fixture.brandPane, fixture.brandGroup, "the branding group");
                assertCentered(fixture.content, fixture.card, "the sign-in card alongside the branding group");
            });
        } finally {
            JavaFxTestSupport.runAndWait(() -> close(fixture));
        }
    }

    @Test
    void shortWindowKeepsTheCompleteFormScrollable() {
        Fixture fixture = JavaFxTestSupport.runAndWait(() -> open(800, 560));
        try {
            JavaFxTestSupport.runAndWait(() -> {
                layout(fixture);
                assertFalse(fixture.brandPane.isManaged(),
                        "responsive collapse must remove branding before reducing form access");
                assertTrue(fixture.formScroll.getViewportBounds().getHeight() < fixture.centeringPane.prefHeight(-1),
                        "the short viewport must be smaller than the complete form content");
                fixture.formScroll.setVvalue(fixture.formScroll.getVmax());
                layout(fixture);
                double viewportBottom = fixture.formScroll.localToScene(fixture.formScroll.getBoundsInLocal()).getMaxY();
                double cardBottom = fixture.card.localToScene(fixture.card.getBoundsInLocal()).getMaxY();
                assertTrue(cardBottom <= viewportBottom + 2.0,
                        "scrolling to the end must make the bottom of the sign-in card accessible");
            });
        } finally {
            JavaFxTestSupport.runAndWait(() -> close(fixture));
        }
    }

    private static Fixture open(double width, double height) throws Exception {
        System.setProperty("shale.ui.reduceMotion", "true");
        FXMLLoader loader = new FXMLLoader(LoginRuntimeLayoutTest.class.getResource("/fxml/login.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(LoginRuntimeLayoutTest.class.getResource("/css/app.css").toExternalForm());
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.setWidth(width);
        stage.setHeight(height);
        stage.show();
        return new Fixture(loader, root, stage,
                node(loader, "loginViewport", StackPane.class),
                node(loader, "loginContent", HBox.class),
                node(loader, "brandPane", StackPane.class),
                findByStyle(node(loader, "brandPane", StackPane.class), "login-brand-group", VBox.class),
                node(loader, "loginFormScroll", ScrollPane.class),
                node(loader, "loginCardCenteringPane", StackPane.class),
                node(loader, "loginCard", VBox.class));
    }

    private static void close(Fixture fixture) {
        ((LoginController) fixture.loader.getController()).dispose();
        fixture.stage.close();
        System.clearProperty("shale.ui.reduceMotion");
    }

    private static void layout(Fixture fixture) {
        fixture.root.applyCss();
        fixture.root.layout();
        fixture.root.applyCss();
        fixture.root.layout();
    }

    private static void assertCentered(Region parent, Region child, String description) {
        double parentCenter = parent.localToScene(parent.getBoundsInLocal()).getCenterY();
        double childCenter = child.localToScene(child.getBoundsInLocal()).getCenterY();
        assertEquals(parentCenter, childCenter, 2.0, description + " must be vertically centered");
    }

    private static <T> T node(FXMLLoader loader, String id, Class<T> type) {
        return type.cast(loader.getNamespace().get(id));
    }

    private static <T extends Region> T findByStyle(Parent parent, String styleClass, Class<T> type) {
        if (parent.getStyleClass().contains(styleClass)) return type.cast(parent);
        for (var child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Parent nested) {
                T match = findByStyle(nested, styleClass, type);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static boolean hasDisplay() {
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;
    }

    private record Fixture(FXMLLoader loader, Parent root, Stage stage, StackPane viewport, HBox content,
            StackPane brandPane, VBox brandGroup, ScrollPane formScroll, StackPane centeringPane, VBox card) {
    }
}
