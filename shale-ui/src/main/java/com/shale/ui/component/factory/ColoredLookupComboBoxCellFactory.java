package com.shale.ui.component.factory;

import java.util.function.Function;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;

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
                    Label pill = LinkTypeIndicatorFactory.createLinkTypePill(displayName, color.apply(item), LinkTypeIndicatorFactory.PillSize.COMPACT);
                    Label display = new Label(displayName);
                    HBox content = new HBox(getGraphicTextGap(), pill, display);
                    content.setAlignment(Pos.CENTER_LEFT);
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
