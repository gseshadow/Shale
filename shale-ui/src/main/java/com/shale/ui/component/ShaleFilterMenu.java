package com.shale.ui.component;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

/** A Shale-owned multi-select filter: both trigger and popup avoid native menu chrome. */
public final class ShaleFilterMenu extends Button {
    public record Option(int id, String label, String color) {}

    private final Popup popup = new Popup();
    private final VBox optionRows = new VBox(2);
    private final Label caption = new Label();

    public ShaleFilterMenu() {
        HBox trigger = new HBox(8, caption, new Label("▾"));
        trigger.setAlignment(Pos.CENTER);
        trigger.getStyleClass().add("shale-filter-trigger-content");
        setGraphic(trigger);
        getStyleClass().add("shale-filter-trigger");
        optionRows.getStyleClass().add("shale-filter-popup");
        popup.getContent().add(optionRows);
        popup.setAutoHide(true);
        setOnAction(event -> {
            if (popup.isShowing()) popup.hide();
            else {
                optionRows.getStylesheets().setAll(getScene().getStylesheets());
                var anchor = localToScreen(0, getHeight());
                popup.show(this, anchor.getX(), anchor.getY());
            }
        });
    }

    public void setCaption(String value) { caption.setText(value); }

    public void setOptions(List<Option> options, Set<Integer> selected, BiConsumer<Integer, Boolean> changed) {
        optionRows.getChildren().clear();
        for (Option option : options) {
            ToggleButton row = new ToggleButton(option.label());
            row.setSelected(selected.contains(option.id()));
            row.getStyleClass().add("shale-filter-option");
            if (option.color() != null && option.color().matches("#[0-9a-fA-F]{6}")) {
                row.setStyle("-shale-definition-color: " + option.color() + ";");
            }
            row.setMaxWidth(Double.MAX_VALUE);
            row.setOnAction(event -> changed.accept(option.id(), row.isSelected()));
            optionRows.getChildren().add(row);
        }
    }

    Popup popupForTesting() { return popup; }
}
