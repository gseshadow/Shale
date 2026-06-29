package com.shale.ui.util;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Centralized screen-aware sizing for JavaFX top-level windows.
 */
public final class WindowSizingUtil {
    public static final double MAIN_WIDTH_RATIO = 0.85;
    public static final double MAIN_HEIGHT_RATIO = 0.80;
    public static final double MODAL_OWNER_RATIO = 0.85;
    public static final double MODAL_SCREEN_RATIO = 0.85;
    public static final double MAIN_PREFERRED_MIN_WIDTH = 1200;
    public static final double MAIN_PREFERRED_MIN_HEIGHT = 760;
    public static final double DIALOG_MAX_WIDTH = 1180;
    public static final double DIALOG_MAX_HEIGHT = 820;

    private WindowSizingUtil() {
    }

    public static void sizeMainStage(Stage stage) {
        if (stage == null) {
            return;
        }
        Rectangle2D visualBounds = getScreenForWindow(stage).getVisualBounds();
        double width = clamp(visualBounds.getWidth() * MAIN_WIDTH_RATIO, 0, visualBounds.getWidth());
        double height = clamp(visualBounds.getHeight() * MAIN_HEIGHT_RATIO, 0, visualBounds.getHeight());
        stage.setMinWidth(Math.min(MAIN_PREFERRED_MIN_WIDTH, visualBounds.getWidth()));
        stage.setMinHeight(Math.min(MAIN_PREFERRED_MIN_HEIGHT, visualBounds.getHeight()));
        stage.setWidth(width);
        stage.setHeight(height);
        centerOnBounds(stage, visualBounds, width, height);
    }

    public static void sizeModalStage(Stage stage, Window owner, double preferredWidth, double preferredHeight) {
        sizeModalStage(stage, owner, preferredWidth, preferredHeight, 0, 0);
    }

    public static void sizeModalStage(
            Stage stage,
            Window owner,
            double preferredWidth,
            double preferredHeight,
            double minimumWidth,
            double minimumHeight) {
        if (stage == null) {
            return;
        }
        Rectangle2D visualBounds = getScreenForWindow(owner).getVisualBounds();
        double maxWidth = Math.min(DIALOG_MAX_WIDTH, visualBounds.getWidth() * MODAL_SCREEN_RATIO);
        double maxHeight = Math.min(DIALOG_MAX_HEIGHT, visualBounds.getHeight() * MODAL_SCREEN_RATIO);
        double safeMinWidth = clamp(minimumWidth, 0, maxWidth);
        double safeMinHeight = clamp(minimumHeight, 0, maxHeight);
        if (hasUsableSize(owner)) {
            double ownerMaxWidth = owner.getWidth() * MODAL_OWNER_RATIO;
            double ownerMaxHeight = owner.getHeight() * MODAL_OWNER_RATIO;
            if (ownerMaxWidth >= safeMinWidth) {
                maxWidth = Math.min(maxWidth, ownerMaxWidth);
            }
            if (ownerMaxHeight >= safeMinHeight) {
                maxHeight = Math.min(maxHeight, ownerMaxHeight);
            }
        }
        double width = clamp(preferredWidth, safeMinWidth, maxWidth);
        double height = clamp(preferredHeight, safeMinHeight, maxHeight);
        stage.setMinWidth(Math.min(safeMinWidth, width));
        stage.setMinHeight(Math.min(safeMinHeight, height));
        stage.setWidth(width);
        stage.setHeight(height);
        centerModal(stage, owner, visualBounds, width, height);
    }

    public static void constrainToVisualBounds(Stage stage, Window owner) {
        if (stage == null) {
            return;
        }
        Rectangle2D visualBounds = getScreenForWindow(owner == null ? stage : owner).getVisualBounds();
        double width = clamp(stage.getWidth(), 0, visualBounds.getWidth());
        double height = clamp(stage.getHeight(), 0, visualBounds.getHeight());
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(clamp(stage.getX(), visualBounds.getMinX(), visualBounds.getMaxX() - width));
        stage.setY(clamp(stage.getY(), visualBounds.getMinY(), visualBounds.getMaxY() - height));
    }

    public static Screen getScreenForWindow(Window owner) {
        if (owner != null && hasUsableSize(owner)) {
            double centerX = owner.getX() + owner.getWidth() / 2.0;
            double centerY = owner.getY() + owner.getHeight() / 2.0;
            return Screen.getScreensForRectangle(centerX, centerY, 1, 1).stream()
                    .findFirst()
                    .orElse(Screen.getPrimary());
        }
        return Screen.getPrimary();
    }

    public static double cappedModalWidth(Window owner, double preferredWidth) {
        Rectangle2D visualBounds = getScreenForWindow(owner).getVisualBounds();
        double maxWidth = Math.min(DIALOG_MAX_WIDTH, visualBounds.getWidth() * MODAL_SCREEN_RATIO);
        if (hasUsableSize(owner)) {
            maxWidth = Math.min(maxWidth, owner.getWidth() * MODAL_OWNER_RATIO);
        }
        return clamp(preferredWidth, 0, maxWidth);
    }

    public static double cappedModalHeight(Window owner, double preferredHeight) {
        Rectangle2D visualBounds = getScreenForWindow(owner).getVisualBounds();
        double maxHeight = Math.min(DIALOG_MAX_HEIGHT, visualBounds.getHeight() * MODAL_SCREEN_RATIO);
        if (hasUsableSize(owner)) {
            maxHeight = Math.min(maxHeight, owner.getHeight() * MODAL_OWNER_RATIO);
        }
        return clamp(preferredHeight, 0, maxHeight);
    }

    private static boolean hasUsableSize(Window window) {
        return window != null && window.getWidth() > 0 && window.getHeight() > 0;
    }

    private static void centerModal(Stage stage, Window owner, Rectangle2D visualBounds, double width, double height) {
        if (hasUsableSize(owner)) {
            double x = owner.getX() + (owner.getWidth() - width) / 2.0;
            double y = owner.getY() + (owner.getHeight() - height) / 2.0;
            stage.setX(clamp(x, visualBounds.getMinX(), visualBounds.getMaxX() - width));
            stage.setY(clamp(y, visualBounds.getMinY(), visualBounds.getMaxY() - height));
            return;
        }
        centerOnBounds(stage, visualBounds, width, height);
    }

    private static void centerOnBounds(Stage stage, Rectangle2D bounds, double width, double height) {
        stage.setX(bounds.getMinX() + (bounds.getWidth() - width) / 2.0);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - height) / 2.0);
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }
}
