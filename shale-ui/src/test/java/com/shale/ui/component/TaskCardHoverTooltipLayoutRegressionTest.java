package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Popup;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

final class TaskCardHoverTooltipLayoutRegressionTest {
    private static final AtomicBoolean TOOLKIT_STARTED = new AtomicBoolean();
    private final AtomicReference<Stage> stageRef = new AtomicReference<>();

    @BeforeAll
    static void startJavaFxToolkit() throws Exception {
        assumeTrue(hasDisplay(), "JavaFX tooltip layout regression test requires a graphical display.");
        if (TOOLKIT_STARTED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        }
    }

    @AfterEach
    void closeStage() throws Exception {
        Stage stage = stageRef.get();
        if (stage != null) {
            runFxAndWait(stage::close);
        }
    }

    @Test
    void actualTaskCardTooltipWindowAutosizesToCompactRenderedContent() throws Exception {
        TooltipMetrics shortMetrics = showTaskTooltipAndMeasure("Review intake packet.");
        System.out.println("SHORT_TASK_TOOLTIP_METRICS " + shortMetrics.diagnostics());

        assertTrue(shortMetrics.windowHeight() < 140, shortMetrics.diagnostics());
        assertTrue(shortMetrics.windowHeight() < shortMetrics.sceneHeight() * 0.35, shortMetrics.diagnostics());
        assertTrue(Math.abs(shortMetrics.windowHeight() - shortMetrics.contentHeight()) < 55, shortMetrics.diagnostics());
    }

    @Test
    void actualTaskCardTooltipTruncatesLongRepeatedDescriptionWithoutFullHeightWindow() throws Exception {
        TooltipMetrics longMetrics = showTaskTooltipAndMeasure("Long task description. ".repeat(80));
        System.out.println("LONG_REPEATED_TASK_TOOLTIP_METRICS " + longMetrics.diagnostics());

        assertTrue(longMetrics.tooltipText().endsWith("..."), longMetrics.tooltipText());
        assertTrue(longMetrics.windowHeight() < 240, longMetrics.diagnostics());
        assertTrue(longMetrics.windowHeight() < longMetrics.sceneHeight() * 0.5, longMetrics.diagnostics());
        assertTrue(Math.abs(longMetrics.windowHeight() - longMetrics.contentHeight()) < 70, longMetrics.diagnostics());
    }

    private TooltipMetrics showTaskTooltipAndMeasure(String description) throws Exception {
        AtomicReference<TooltipMetrics> result = new AtomicReference<>();
        runFxAndWait(() -> {
            TaskCard card = new TaskCard();
            card.setTitle("Measured task hover title");
            card.setDescriptionPreview(description);
            card.applyCompact();

            StackPane root = new StackPane(card);
            Scene scene = new Scene(root, 900, 600);
            String stylesheet = TaskCardHoverTooltipLayoutRegressionTest.class.getResource("/css/app.css").toExternalForm();
            scene.getStylesheets().add(stylesheet);
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            stageRef.set(stage);
            root.applyCss();
            root.layout();

            Popup popup = card.getTaskDetailsPopupForTesting();
            popup.show(card, stage.getX() + 120, stage.getY() + 120);
            popup.getScene().getRoot().applyCss();
            popup.getScene().getRoot().autosize();
            popup.getScene().getRoot().layout();
            Node tooltipRoot = popup.getScene().getRoot();
            Node label = tooltipRoot.lookup(".label");
            double contentHeight = label == null ? tooltipRoot.getLayoutBounds().getHeight() : label.getLayoutBounds().getHeight();
            String popupText = label instanceof javafx.scene.control.Label labeled ? labeled.getText() : "";
            result.set(new TooltipMetrics(popup.getScene().getWindow().getHeight(), contentHeight, scene.getHeight(), popupText, describeNodeTree(tooltipRoot, "")));
            popup.hide();
        });
        return result.get();
    }

    private static void runFxAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static String describeNodeTree(Node node, String indent) {
        StringBuilder description = new StringBuilder(indent)
                .append(node.getClass().getName())
                .append(" styleClasses=").append(node.getStyleClass())
                .append(" minH=").append(node.minHeight(-1))
                .append(" prefH=").append(node.prefHeight(-1))
                .append(" maxH=").append(node.maxHeight(-1))
                .append(" layoutH=").append(node.getLayoutBounds().getHeight())
                .append(" parentH=").append(node.getBoundsInParent().getHeight())
                .append('\n');
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                description.append(describeNodeTree(child, indent + "  "));
            }
        }
        return description.toString();
    }

    private static boolean hasDisplay() {
        return System.getenv("DISPLAY") != null
                || System.getenv("WAYLAND_DISPLAY") != null
                || System.getProperty("os.name", "").toLowerCase().contains("win")
                || System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private record TooltipMetrics(double windowHeight, double contentHeight, double sceneHeight, String tooltipText, String nodeTree) {
        private String diagnostics() {
            return "windowHeight=" + windowHeight + ", contentHeight=" + contentHeight + ", sceneHeight=" + sceneHeight + "\n" + nodeTree;
        }
    }
}
