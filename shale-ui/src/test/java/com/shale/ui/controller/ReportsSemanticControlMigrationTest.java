package com.shale.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.FlowPane;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReportsSemanticControlMigrationTest {
    @Test
    void reportsOwnEveryExplicitSemanticClassificationAndSharedFormOptIn() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/ReportsController.java"));
        String fxml = Files.readString(Path.of("src/main/resources/fxml/reports.fxml"));

        assertTrue(source.contains("ControlStyles.formControl(startDatePicker)"));
        assertTrue(source.contains("ControlStyles.formControl(endDatePicker)"));
        assertTrue(source.contains("ControlStyles.formControl(statusFilterMenuButton)"));
        assertTrue(source.contains("ControlStyles.apply(refreshButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD)"));
        assertTrue(source.contains("ControlStyles.apply(showAllResultsButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD)"));
        assertTrue(source.contains("ControlStyles.apply(exportButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD)"));
        assertTrue(source.contains("ControlStyles.apply(drillExport, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD)"));
        assertTrue(source.contains("ControlStyles.apply(closeButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD)"));
        assertEquals(1, count(source, "ControlStyles.Purpose.PRIMARY"), "Reports has one local Primary action");
        assertFalse(fxml.contains("app-toolbar-button"), "migrated actions must not retain legacy toolbar geometry");
        assertFalse(fxml.contains("shale-date-picker"), "report dates must not retain the legacy capsule class");
        assertTrue(fxml.contains("<FlowPane fx:id=\"reportFilterToolbar\" hgap=\"10\" vgap=\"8\""));
        assertTrue(source.contains("-fx-pie-color:"), "status colors remain scoped to chart data");
        assertFalse(source.contains("ControlStyles.Purpose.DANGER"), "Reports has no destructive action");
    }

    @Test
    void productionFxmlAndCssRenderAtNormalAndNarrowWidthsWithoutWarnings() throws Exception {
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), Probe.class.getName())
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(25, TimeUnit.SECONDS), "Reports rendering probe timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertFalse(output.contains("CSS Error"), output);
        assertFalse(output.contains("CssStyleHelper"), output);
        assertFalse(output.contains("ClassCastException"), output);
        assertFalse(output.contains("cannot be cast to javafx.css.Size"), output);
    }

    private static int count(String text, String value) {
        int count = 0;
        for (int at = text.indexOf(value); at >= 0; at = text.indexOf(value, at + value.length())) count++;
        return count;
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Platform.startup(started::countDown);
            require(started.await(10, TimeUnit.SECONDS), "JavaFX did not start");
            Platform.runLater(() -> {
                try {
                    Parent root = FXMLLoader.load(requireResource("/fxml/reports.fxml"));
                    Scene scene = new Scene(root, 1280, 800);
                    scene.getStylesheets().add(requireResource("/css/app.css").toExternalForm());
                    assertLayout(root, 1280);
                    assertLayout(root, 640);
                } catch (Throwable thrown) {
                    failure.set(thrown);
                } finally {
                    finished.countDown();
                }
            });
            require(finished.await(20, TimeUnit.SECONDS), "JavaFX rendering did not finish");
            Platform.exit();
            if (failure.get() != null) throw new AssertionError("Reports rendering failed", failure.get());
        }

        private static void assertLayout(Parent root, double width) {
            root.resize(width, 800);
            root.applyCss();
            root.layout();
            DatePicker start = (DatePicker) root.lookup("#startDatePicker");
            DatePicker end = (DatePicker) root.lookup("#endDatePicker");
            MenuButton statuses = (MenuButton) root.lookup("#statusFilterMenuButton");
            Button apply = (Button) root.lookup("#refreshButton");
            Button showAll = (Button) root.lookup("#showAllResultsButton");
            Button export = (Button) root.lookup("#exportButton");
            FlowPane toolbar = (FlowPane) root.lookup("#reportFilterToolbar");
            require(List.of(start, end, statuses).stream().allMatch(c -> c.getStyleClass().contains("shale-form-control")), "shared form classes");
            require(radius(start) == 8 && radius(end) == 8 && radius(statuses) == 8, "rounded rectangle form geometry");
            require(apply.getStyleClass().contains("shale-control-primary"), "Apply primary");
            require(showAll.getStyleClass().contains("shale-control-secondary") && export.getStyleClass().contains("shale-control-secondary"), "supporting actions secondary");
            require(apply.getHeight() == showAll.getHeight() && showAll.getHeight() == export.getHeight(), "action height alignment");
            for (var child : toolbar.getChildren()) {
                Bounds bounds = child.getBoundsInParent();
                require(bounds.getMinX() >= -0.01 && bounds.getMaxX() <= toolbar.getWidth() + 0.01, "toolbar child clipped at " + width);
            }
        }

        private static double radius(javafx.scene.control.Control control) {
            return control.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius();
        }

        private static java.net.URL requireResource(String path) {
            java.net.URL resource = Probe.class.getResource(path);
            if (resource == null) throw new AssertionError("Missing " + path);
            return resource;
        }

        private static void require(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
        }
    }
}
