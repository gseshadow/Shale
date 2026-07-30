package com.shale.ui.util;

import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.Control;

import java.util.List;
import java.util.Objects;

/** Opt-in semantic styling for ordinary JavaFX controls. */
public final class ControlStyles {
    public enum Purpose {
        PRIMARY("shale-control-primary"),
        SECONDARY("shale-control-secondary"),
        GHOST("shale-control-ghost"),
        DANGER("shale-control-danger"),
        NAVIGATION("shale-control-navigation");

        private final String styleClass;
        Purpose(String styleClass) { this.styleClass = styleClass; }
    }

    public enum Size {
        SMALL("shale-control-small"),
        STANDARD("shale-control-standard");

        private final String styleClass;
        Size(String styleClass) { this.styleClass = styleClass; }
    }

    public static final String BUTTON_BASE = "shale-control-button";
    public static final String FORM_CONTROL = "shale-form-control";
    public static final String ICON_ONLY = "shale-control-icon-only";
    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private static final List<String> PURPOSE_CLASSES = List.of(Purpose.values()).stream().map(p -> p.styleClass).toList();
    private static final List<String> SIZE_CLASSES = List.of(Size.values()).stream().map(s -> s.styleClass).toList();

    private ControlStyles() { }

    public static <T extends Button> T apply(T button, Purpose purpose) {
        return apply(button, purpose, Size.STANDARD);
    }

    public static <T extends Button> T apply(T button, Purpose purpose, Size size) {
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(size, "size");
        replace(button, PURPOSE_CLASSES, purpose.styleClass);
        replace(button, SIZE_CLASSES, size.styleClass);
        addOnce(button, BUTTON_BASE);
        return button;
    }

    public static <T extends Button> T small(T button) {
        Objects.requireNonNull(button, "button");
        replace(button, SIZE_CLASSES, Size.SMALL.styleClass);
        addOnce(button, BUTTON_BASE);
        return button;
    }

    public static <T extends Button> T standard(T button) {
        Objects.requireNonNull(button, "button");
        replace(button, SIZE_CLASSES, Size.STANDARD.styleClass);
        addOnce(button, BUTTON_BASE);
        return button;
    }

    public static <T extends Button> T iconOnly(T button) {
        Objects.requireNonNull(button, "button");
        addOnce(button, BUTTON_BASE);
        addOnce(button, ICON_ONLY);
        return button;
    }

    public static <T extends Control> T formControl(T control) {
        Objects.requireNonNull(control, "control");
        addOnce(control, FORM_CONTROL);
        return control;
    }

    public static void setInvalid(Control control, boolean invalid) {
        Objects.requireNonNull(control, "control").pseudoClassStateChanged(INVALID, invalid);
    }

    private static void replace(Control control, List<String> conflicts, String selected) {
        control.getStyleClass().removeIf(conflicts::contains);
        addOnce(control, selected);
    }

    private static void addOnce(Control control, String styleClass) {
        if (!control.getStyleClass().contains(styleClass)) control.getStyleClass().add(styleClass);
    }
}
