package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Popup;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

final class TaskCardHoverTooltipLayoutRegressionTest {
    private final AtomicReference<Stage> stageRef = new AtomicReference<>();

    @BeforeAll
    static void startJavaFxToolkit() {
        assumeTrue(hasDisplay(), "JavaFX tooltip layout regression test requires a graphical display.");
        JavaFxTestSupport.ensureToolkitStarted();
    }

    @AfterEach
    void closeStage() throws Exception {
        Stage stage = stageRef.get();
        if (stage != null) {
            runFxAndWait(stage::close);
        }
    }

    @Test
    void actualTaskCardTooltipShowsTitleBeforeCompleteShortDescription() throws Exception {
        TooltipMetrics shortMetrics = showTaskTooltipAndMeasure("Review intake packet.");
        int titleIndex = shortMetrics.tooltipText().indexOf("Measured task hover title");
        int descriptionIndex = shortMetrics.tooltipText().indexOf("Review intake packet.");
        assertTrue(titleIndex >= 0, "Task hover popup must include the task title.");
        assertTrue(descriptionIndex >= 0, "Task hover popup must include the short description.");
        assertTrue(titleIndex < descriptionIndex, "Task title must precede its description in the hover popup.");
        assertTrue(shortMetrics.visibleContentHeight() > 0, shortMetrics.diagnostics());
    }

    @Test
    void actualTaskCardTooltipShowsMeaningfulPortionOfAuthoritativeCappedPreview() throws Exception {
        String longDescription = "Long task description. ".repeat(80);
        TooltipMetrics longMetrics = showTaskTooltipAndMeasure(longDescription);
        assertEquals(TaskCard.buildTaskDetailsTooltipText("Measured task hover title", longDescription),
                longMetrics.tooltipText(), "Rendered popup must use the authoritative task-tooltip preview projection.");
        assertTrue(longMetrics.visibleContentHeight() > 0, longMetrics.diagnostics());
        assertTrue(longMetrics.visibleContentHeight() < longMetrics.contentHeight(),
                "The intentionally capped long preview should expose a meaningful portion without expanding to its full laid-out height. "
                        + longMetrics.diagnostics());
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
            assertNotNull(label, "Task details popup must expose its description label.");
            double contentHeight = label.getLayoutBounds().getHeight();
            String popupText = label instanceof javafx.scene.control.Label labeled ? labeled.getText() : "";
            double visibleContentHeight = Math.min(label.getBoundsInParent().getMaxY(), tooltipRoot.getLayoutBounds().getMaxY())
                    - Math.max(label.getBoundsInParent().getMinY(), tooltipRoot.getLayoutBounds().getMinY());
            result.set(new TooltipMetrics(contentHeight, visibleContentHeight, popupText, describeNodeTree(tooltipRoot, "")));
            popup.hide();
        });
        return result.get();
    }

    private static void runFxAndWait(Runnable action) throws Exception {
        JavaFxTestSupport.runAndWait(action::run);
    }

    private static String describeNodeTree(Node node, String indent) {
        StringBuilder description = new StringBuilder(indent)
                .append(node.getClass().getName())
                .append(" styleClasses=").append(node.getStyleClass())
                .append(" minH=").append(node.minHeight(-1))
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

    private record TooltipMetrics(double contentHeight, double visibleContentHeight, String tooltipText, String nodeTree) {
        private String diagnostics() {
            return "contentHeight=" + contentHeight + ", visibleContentHeight=" + visibleContentHeight + "\n" + nodeTree;
        }
    }
}
