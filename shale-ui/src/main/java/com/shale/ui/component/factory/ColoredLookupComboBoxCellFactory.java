package com.shale.ui.component.factory;

import java.util.function.Function;

import javafx.scene.control.ListCell;

/** Shared colored lookup ComboBox cells used by Add Link Link Type and matching lookup selectors. */
public final class ColoredLookupComboBoxCellFactory {
    private ColoredLookupComboBoxCellFactory() {}

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
                    setText(displayName);
                    setGraphic(LinkTypeIndicatorFactory.createLinkTypePill(displayName, color.apply(item), LinkTypeIndicatorFactory.PillSize.COMPACT));
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
