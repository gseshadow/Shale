package com.shale.ui.component.factory;

import java.util.function.Function;

import com.shale.ui.component.ColorCodedComboBox;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

/** Shared Shale colored lookup ComboBox setup used by Add Link Link Type and matching lookup selectors. */
public final class ColoredLookupComboBoxCellFactory {
    private ColoredLookupComboBoxCellFactory() {}

    public static <T> void configure(ComboBox<T> comboBox, Function<T, String> name, Function<T, String> color) {
        ColorCodedComboBox<T> renderer = new ColorCodedComboBox<>(name, color);
        comboBox.setMaxWidth(renderer.getMaxWidth());
        comboBox.setConverter(renderer.getConverter());
        comboBox.setCellFactory(list -> renderer.createColorCodedCell());
        comboBox.setButtonCell(renderer.createColorCodedCell());
    }

    public static <T> ListCell<T> popupCell(Function<T, String> name, Function<T, String> color) {
        return new ColorCodedComboBox<>(name, color).createColorCodedCell();
    }

    public static <T> ListCell<T> buttonCell(Function<T, String> name) {
        return new ColorCodedComboBox<>(name, item -> null).createColorCodedCell();
    }
}
