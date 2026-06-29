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
            double minimumWidth,
            double minimumHeight) {
        if (stage == null || root == null) {
            return;
        }

        root.setMinWidth(minimumWidth);
        root.setPrefWidth(minimumWidth);
        root.setMinHeight(minimumHeight);

        double initialHeight = preferredHeight(root, minimumWidth, minimumHeight);
        WindowSizingUtil.sizeModalStage(stage, owner, minimumWidth, initialHeight, minimumWidth, minimumHeight);

        stage.setOnShown(event -> {
            double width = Math.max(minimumWidth, stage.getWidth());
            double height = preferredHeight(root, width, minimumHeight);
            WindowSizingUtil.sizeModalStage(stage, owner, width, height, minimumWidth, minimumHeight);
            stage.sizeToScene();
            if (stage.getWidth() < minimumWidth) {
                stage.setWidth(minimumWidth);
            }
            if (stage.getHeight() < minimumHeight) {
                stage.setHeight(minimumHeight);
            }
            WindowSizingUtil.constrainToVisualBounds(stage, owner);
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
