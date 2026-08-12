package com.shale.ui.controller;

import com.shale.ui.util.ControlStyles;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CasesToolbarCssRuntimeTest {
    @Test
    void realStylesheetRendersCasesClassificationsWithoutCssWarnings() throws Exception {
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), Probe.class.getName())
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Cases toolbar CSS probe timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertFalse(output.contains("CSS Error"), output);
        assertFalse(output.contains("CssStyleHelper"), output);
        assertFalse(output.contains("ClassCastException"), output);
        assertFalse(output.contains("cannot be cast to javafx.css.Size"), output);
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Platform.startup(started::countDown);
            require(started.await(10, TimeUnit.SECONDS), "JavaFX probe did not start");
            Platform.runLater(() -> {
                try {
                    TextField search = ControlStyles.formControl(new TextField());
                    MenuButton status = ControlStyles.formControl(new MenuButton("Case Status"));
                    ChoiceBox<String> intakeDate = ControlStyles.formControl(new ChoiceBox<>());
                    intakeDate.getItems().add("Intake Date (newest first)");
                    intakeDate.getSelectionModel().selectFirst();

                    ToggleButton cards = ControlStyles.apply(new ToggleButton("Cards"),
                            ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
                    ToggleButton list = ControlStyles.apply(new ToggleButton("List"),
                            ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
                    cards.setSelected(true);
                    HBox segmented = new HBox(cards, list);
                    segmented.getStyleClass().add("shale-segmented-control");

                    MenuButton columns = ControlStyles.apply(new MenuButton("Customize Columns"),
                            ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
                    MenuButton export = ControlStyles.apply(new MenuButton("Export..."),
                            ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
                    FlowPane toolbar = new FlowPane(10, 8, segmented, search, status, intakeDate, columns, export);
                    Scene scene = new Scene(toolbar, 1100, 100);
                    scene.getStylesheets().add(requireStylesheet());
                    toolbar.applyCss();
                    toolbar.layout();

                    require(close(search.getPrefHeight(), 36), "search height");
                    require(close(status.getPrefHeight(), search.getPrefHeight()), "status/search height alignment");
                    require(close(intakeDate.getPrefHeight(), search.getPrefHeight()), "intake-date/search height alignment");
                    require(close(backgroundRadius(status), 8), "status shared form radius");
                    require(close(backgroundRadius(intakeDate), 8), "intake-date shared form radius");
                    require(backgroundRadius(status) < status.getHeight() / 2, "status must not be a capsule");
                    require(backgroundRadius(intakeDate) < intakeDate.getHeight() / 2, "intake date must not be a capsule");
                    require(close(columns.getPrefHeight(), export.getPrefHeight()), "action height alignment");
                    require(close(cards.getPrefHeight(), list.getPrefHeight()), "segment height alignment");
                    require(cards.isSelected() && !list.isSelected(), "segmented selected state");
                    require(toolbar.getRowValignment() != null, "FlowPane wrapping remains available");
                } catch (Throwable thrown) {
                    failure.set(thrown);
                } finally {
                    finished.countDown();
                }
            });
            require(finished.await(15, TimeUnit.SECONDS), "JavaFX probe did not finish");
            Platform.exit();
            if (failure.get() != null) throw new AssertionError("Cases toolbar CSS probe failed", failure.get());
        }

        private static String requireStylesheet() {
            var resource = Probe.class.getResource("/css/app.css");
            if (resource == null) throw new AssertionError("Missing /css/app.css");
            return resource.toExternalForm();
        }

        private static double backgroundRadius(javafx.scene.control.Control control) {
            return control.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius();
        }

        private static boolean close(double actual, double expected) {
            return Math.abs(actual - expected) < 0.01;
        }

        private static void require(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
        }
    }
}
