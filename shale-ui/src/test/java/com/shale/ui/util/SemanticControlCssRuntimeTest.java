package com.shale.ui.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class SemanticControlCssRuntimeTest {
    @Test void stylesheetAppliesSizesPaddingAndRadiiWithoutCssConversionWarnings() throws Exception {
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), Probe.class.getName())
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS), "JavaFX CSS probe timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertFalse(output.contains("ClassCastException"), output);
        assertFalse(output.contains("CssStyleHelper"), output);
        assertFalse(output.contains("cannot be cast to javafx.css.Size"), output);
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Platform.startup(started::countDown);
            if (!started.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX probe did not start");
            Platform.runLater(() -> {
                try {
                    Button standard = ControlStyles.apply(new Button("A deliberately long action label"), ControlStyles.Purpose.PRIMARY);
                    Button small = ControlStyles.apply(new Button("Remove"), ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
                    VBox root = new VBox(standard, small);
                    Scene scene = new Scene(root, 500, 160);
                    scene.getStylesheets().add(requireStylesheet());
                    root.applyCss();
                    root.layout();
                    require(close(standard.getMinHeight(), 40) && close(standard.getPrefHeight(), 40), "standard height");
                    require(close(small.getMinHeight(), 32) && close(small.getPrefHeight(), 32), "small height");
                    Insets padding = standard.getPadding();
                    require(close(padding.getLeft(), 16) && close(padding.getRight(), 16), "horizontal padding");
                    double radius = standard.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius();
                    require(close(radius, 10), "background radius");
                    require(standard.getBorder() != null && close(standard.getBorder().getStrokes().getFirst().getRadii().getTopLeftHorizontalRadius(), 10), "border radius");
                    require(standard.prefWidth(-1) > 180, "long label computed width");
                    require(standard.getMaxWidth() < 0 || standard.getMaxWidth() >= standard.prefWidth(-1), "no clipping max width");
                } catch (Throwable thrown) {
                    failure.set(thrown);
                } finally {
                    finished.countDown();
                }
            });
            if (!finished.await(15, TimeUnit.SECONDS)) throw new AssertionError("JavaFX probe did not finish");
            Platform.exit();
            if (failure.get() != null) throw new AssertionError("JavaFX probe failed", failure.get());
        }

        private static String requireStylesheet() throws IOException {
            var resource = Probe.class.getResource("/css/app.css");
            if (resource == null) throw new IOException("Missing /css/app.css");
            return resource.toExternalForm();
        }
        private static boolean close(double actual, double expected) { return Math.abs(actual - expected) < 0.01; }
        private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    }
}
