package com.shale.ui.util;

import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Defensive sizing for custom modal dialog stages.
 */
public final class DialogSizingUtil {
    private DialogSizingUtil() {
    }

    public static void applyConfirmationDialogSizing(
            Stage stage,
            Window owner,
            Region root,
            double preferredWidth,
            double minimumWidth,
            double minimumHeight) {
        if (stage == null || root == null) {
            return;
        }

        root.setPrefWidth(preferredWidth);

        double initialWidth = Math.max(preferredWidth, minimumWidth);
        double initialHeight = preferredHeight(root, initialWidth, minimumHeight);
        WindowSizingUtil.sizeModalStage(stage, owner, initialWidth, initialHeight, minimumWidth, minimumHeight);

        stage.setOnShown(event -> {
            stage.sizeToScene();
            double width = Math.max(preferredWidth, stage.getWidth());
            double height = Math.max(stage.getHeight(), preferredHeight(root, width, minimumHeight));
            // Apply the bounded size last. Calling sizeToScene after this point can grow the
            // window beyond the visual bounds and leave its non-scrolling footer clipped.
            WindowSizingUtil.sizeModalStage(stage, owner, width, height, minimumWidth, minimumHeight);
        });
    }

    private static double preferredHeight(Region root, double width, double minimumHeight) {
        Scene scene = root.getScene();
        if (scene != null) {
            root.applyCss();
            root.layout();
        }
        double preferredHeight = root.prefHeight(width);
        if (Double.isNaN(preferredHeight) || preferredHeight <= 0) {
            preferredHeight = minimumHeight;
        }
        return Math.max(minimumHeight, preferredHeight);
    }
}
