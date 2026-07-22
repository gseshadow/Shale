package com.shale.ui.component;

import java.util.Objects;
import java.util.function.Function;

import com.shale.ui.component.factory.LinkTypeIndicatorFactory;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

/**
 * Generic Shale ComboBox for database-backed lookup values that display with an assigned color.
 *
 * @param <T> lookup item type supplied by the caller
 */
public class ColorCodedComboBox<T> extends ComboBox<T> {
    private static final Insets POPUP_ROW_PADDING = new Insets(4, 10, 4, 10);
    private static final Insets BUTTON_CELL_PADDING = new Insets(3, 28, 3, 8);

    private final Function<T, String> displayTextExtractor;
    private final Function<T, String> colorExtractor;

    public ColorCodedComboBox(Function<T, String> displayTextExtractor, Function<T, String> colorExtractor) {
        this.displayTextExtractor = Objects.requireNonNull(displayTextExtractor, "displayTextExtractor");
        this.colorExtractor = Objects.requireNonNull(colorExtractor, "colorExtractor");
        configureColorCodedRendering();
    }

    public Function<T, String> displayTextExtractor() {
        return displayTextExtractor;
    }

    public Function<T, String> colorExtractor() {
        return colorExtractor;
    }

    private void configureColorCodedRendering() {
        setMaxWidth(Double.MAX_VALUE);
        setConverter(new StringConverter<>() {
            @Override public String toString(T item) { return displayText(item); }
            @Override public T fromString(String value) { return null; }
        });
        setCellFactory(list -> createColorCodedCell());
        setButtonCell(createColorCodedButtonCell());
    }

    public ListCell<T> createColorCodedCell() {
        return createColorCodedCell(false);
    }

    private ListCell<T> createColorCodedButtonCell() {
        return createColorCodedCell(true);
    }

    private ListCell<T> createColorCodedCell(boolean buttonCell) {
        return new ListCell<>() {
            {
                setAlignment(Pos.CENTER_LEFT);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setPadding(buttonCell ? BUTTON_CELL_PADDING : POPUP_ROW_PADDING);
                setMaxWidth(Double.MAX_VALUE);
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(buttonCell ? getComboBoxPromptText() : null);
                    setContentDisplay(buttonCell ? ContentDisplay.TEXT_ONLY : ContentDisplay.GRAPHIC_ONLY);
                    return;
                }
                setText(null);
                setGraphic(createDisplayNode(item));
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        };
    }

    public HBox createDisplayNode(T item) {
        String displayText = displayText(item);
        Label pill = LinkTypeIndicatorFactory.createLinkTypePill(displayText, color(item), LinkTypeIndicatorFactory.PillSize.COMPACT);
        HBox content = new HBox(pill);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxWidth(Double.MAX_VALUE);
        content.setMinWidth(Region.USE_PREF_SIZE);
        pill.setMinWidth(Region.USE_PREF_SIZE);
        return content;
    }

    private String getComboBoxPromptText() {
        String prompt = getPromptText();
        return prompt == null ? "" : prompt;
    }

    private String displayText(T item) {
        if (item == null) return "";
        String value = displayTextExtractor.apply(item);
        return value == null ? "" : value;
    }

    private String color(T item) {
        return item == null ? null : colorExtractor.apply(item);
    }
}
