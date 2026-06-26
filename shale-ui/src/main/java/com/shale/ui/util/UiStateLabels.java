package com.shale.ui.util;

import javafx.scene.control.Label;

/**
 * Small helper for consistent JavaFX loading, empty, and error labels.
 *
 * <p>This intentionally only applies known style classes and visible/managed
 * state. It does not own page layout or model state.</p>
 */
public final class UiStateLabels {

    public static final String LOADING_STYLE_CLASS = "shale-loading-placeholder";
    public static final String EMPTY_STYLE_CLASS = "shale-empty-state";
    public static final String ERROR_STYLE_CLASS = "error";

    private UiStateLabels() {
    }

    public static void showLoading(Label label) {
        showLoading(label, null);
    }

    public static void showLoading(Label label, String text) {
        show(label, LOADING_STYLE_CLASS, text);
    }

    public static void showEmpty(Label label) {
        showEmpty(label, null);
    }

    public static void showEmpty(Label label, String text) {
        show(label, EMPTY_STYLE_CLASS, text);
    }

    public static void showError(Label label) {
        showError(label, null);
    }

    public static void showError(Label label, String text) {
        show(label, ERROR_STYLE_CLASS, text);
    }

    public static void hide(Label label) {
        if (label == null) {
            return;
        }
        label.setVisible(false);
        label.setManaged(false);
    }

    private static void show(Label label, String styleClass, String text) {
        if (label == null) {
            return;
        }
        if (text != null) {
            label.setText(text);
        }
        applyStyleClass(label, styleClass);
        label.setVisible(true);
        label.setManaged(true);
    }

    private static void applyStyleClass(Label label, String styleClass) {
        if (styleClass == null || styleClass.isBlank() || label.getStyleClass().contains(styleClass)) {
            return;
        }
        label.getStyleClass().add(styleClass);
    }
}
