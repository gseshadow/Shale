package com.shale.ui.component.factory;

import java.util.function.Function;

import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

/** Shared Shale colored lookup ComboBox setup used by Add Link Link Type and matching lookup selectors. */
public final class ColoredLookupComboBoxCellFactory {
    private ColoredLookupComboBoxCellFactory() {}

    public static <T> void configure(ComboBox<T> comboBox, Function<T, String> name, Function<T, String> color) {
        comboBox.setMaxWidth(Double.MAX_VALUE);
        comboBox.setConverter(new StringConverter<>() {
            @Override public String toString(T item) { return item == null ? "" : name.apply(item); }
            @Override public T fromString(String value) { return null; }
        });
        comboBox.setCellFactory(list -> popupCell(name, color));
        comboBox.setButtonCell(buttonCell(name));
    }

    public static <T> ListCell<T> popupCell(Function<T, String> name, Function<T, String> color) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String displayName = name.apply(item);
                    Label pill = LinkTypeIndicatorFactory.createLinkTypePill(displayName, color.apply(item), LinkTypeIndicatorFactory.PillSize.COMPACT);
                    Label display = new Label(displayName);
                    HBox content = new HBox(getGraphicTextGap(), pill, display);
                    content.setAlignment(Pos.CENTER_LEFT);
                    content.setMinWidth(Region.USE_PREF_SIZE);
                    pill.setMinWidth(Region.USE_PREF_SIZE);
                    display.setMinWidth(Region.USE_PREF_SIZE);
                    setText(null);
                    setGraphic(content);
                }
            }
        };
    }

    public static <T> ListCell<T> buttonCell(Function<T, String> name) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : name.apply(item));
                setGraphic(null);
            }
        };
    }
}
