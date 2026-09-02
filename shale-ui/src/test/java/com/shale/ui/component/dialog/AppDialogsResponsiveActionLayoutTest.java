package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;

/** Prevents long confirmation actions from clipping outside the dialog action area. */
final class AppDialogsResponsiveActionLayoutTest {
    @Test
    void duplicateCaseActionsStayContainedAndWrapWhenConstrained() {
        JavaFxTestSupport.runAndWait(() -> {
            List<AppDialogs.DialogAction<Boolean>> actions = List.of(
                    AppDialogs.DialogAction.of("Merge Into Existing Case", true,
                            AppDialogs.DialogActionKind.PRIMARY, true, false),
                    AppDialogs.DialogAction.of("Create Separate Case", false,
                            AppDialogs.DialogActionKind.SECONDARY, false, false),
                    AppDialogs.DialogAction.cancel("Cancel", null));
            FlowPane row = AppDialogs.createActionsRow(actions, ignored -> { });
            Scene scene = new Scene(row, 604, 120);
            scene.getStylesheets().add(Objects.requireNonNull(
                    AppDialogs.class.getResource("/css/app.css")).toExternalForm());

            layoutAt(row, 604);
            assertEquals(1, distinctRows(row), "the preferred 640px dialog should keep all actions on one row");
            assertContained(row, 604);

            layoutAt(row, 300);
            assertTrue(distinctRows(row) > 1, "a constrained action area should wrap instead of clipping");
            assertContained(row, 300);
            for (Node child : row.getChildren()) {
                Button button = (Button) child;
                assertTrue(button.getWidth() >= button.prefWidth(-1) - 0.5,
                        () -> button.getText() + " must retain enough width for its full label");
            }
        });
    }

    private static void layoutAt(FlowPane row, double width) {
        row.setPrefWrapLength(width);
        row.resize(width, row.prefHeight(width));
        row.applyCss();
        row.layout();
    }

    private static long distinctRows(FlowPane row) {
        return row.getChildren().stream()
                .map(node -> Math.round(node.getBoundsInParent().getMinY()))
                .distinct()
                .count();
    }

    private static void assertContained(FlowPane row, double width) {
        for (Node child : row.getChildren()) {
            assertTrue(child.getBoundsInParent().getMinX() >= -0.5, "action must remain inside the left edge");
            assertTrue(child.getBoundsInParent().getMaxX() <= width + 0.5, "action must remain inside the right edge");
        }
    }
}
