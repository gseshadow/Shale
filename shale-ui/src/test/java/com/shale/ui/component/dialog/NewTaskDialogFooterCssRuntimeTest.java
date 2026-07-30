package com.shale.ui.component.dialog;

import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class NewTaskDialogFooterCssRuntimeTest {
    @Test void backgroundOwningFooterKeepsShellRadiiAfterResize() {
        JavaFxTestSupport.runAndWait(() -> {
            HBox footer = new HBox(new Button("Cancel"), new Button("Create Task"));
            footer.getStyleClass().addAll("app-dialog-action-bar", "new-task-dialog-action-bar");
            Region content = new Region();
            VBox.setVgrow(content, Priority.ALWAYS);
            VBox root = new VBox(content, footer);
            root.getStyleClass().add("app-dialog-root");
            Scene scene = new Scene(root, 560, 680);
            scene.getStylesheets().add(Objects.requireNonNull(
                    NewTaskDialog.class.getResource("/css/app.css")).toExternalForm());

            assertFooterRadii(root, footer, 560, 680);
            assertFooterRadii(root, footer, 460, 420);
            assertFooterRadii(root, footer, 720, 760);
        });
    }

    private static void assertFooterRadii(VBox root, HBox footer, double width, double height) {
        root.resize(width, height);
        root.applyCss();
        root.layout();
        CornerRadii radii = footer.getBackground().getFills().getFirst().getRadii();
        assertEquals(0, radii.getTopLeftHorizontalRadius(), 0.01);
        assertEquals(0, radii.getTopRightHorizontalRadius(), 0.01);
        assertEquals(16, radii.getBottomLeftHorizontalRadius(), 0.01);
        assertEquals(16, radii.getBottomRightHorizontalRadius(), 0.01);
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        WritableImage rendered = root.snapshot(parameters, null);
        assertEquals(0, rendered.getPixelReader().getColor(0, (int) rendered.getHeight() - 1).getOpacity(), 0.01);
        assertEquals(0, rendered.getPixelReader().getColor((int) rendered.getWidth() - 1,
                (int) rendered.getHeight() - 1).getOpacity(), 0.01);
    }
}
