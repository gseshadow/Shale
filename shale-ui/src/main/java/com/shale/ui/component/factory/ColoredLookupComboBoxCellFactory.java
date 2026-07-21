package com.shale.ui.component.factory;

import java.util.function.Function;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;

/** Shared colored lookup ComboBox cells used by Add Link Link Type and matching lookup selectors. */
public final class ColoredLookupComboBoxCellFactory {
    public record PopupRowStructure(String rootType, String pillType, String pillText, String plainText, String color, String pillSize, boolean hasCircle, boolean fixedEqualPillSize) {}
    private ColoredLookupComboBoxCellFactory() {}

    public static PopupRowStructure popupRowStructure(String displayName, String color) {
        return new PopupRowStructure("HBox", "Label", displayName, displayName, color, LinkTypeIndicatorFactory.PillSize.COMPACT.name(), false, false);
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
                    PopupRowStructure structure = popupRowStructure(displayName, color.apply(item));
                    Label pill = LinkTypeIndicatorFactory.createLinkTypePill(structure.pillText(), structure.color(), LinkTypeIndicatorFactory.PillSize.COMPACT);
                    Label display = new Label(structure.plainText());
                    HBox content = new HBox(getGraphicTextGap(), pill, display);
                    content.setAlignment(Pos.CENTER_LEFT);
                    setText(null);
                    setGraphic(content);
                    System.err.println("NEW REQUEST MATERIAL TYPE RENDERER V4 updateItem");
                    System.err.println("item=" + displayName);
                    System.err.println("pillNode=" + pill.getClass().getName());
                    System.err.println("pillText=" + pill.getText());
                    System.err.println("pillStyleClasses=" + pill.getStyleClass());
                    System.err.println("pillInlineStyle=" + pill.getStyle());
                    System.err.println("listCellGraphic=" + content + " children=" + content.getChildren());
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
