package com.shale.ui.component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.util.ControlStyles;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.stage.Window;

/** Shared presentation-only MINI selection. Domain wrappers supply their existing card factory. */
public class MiniCardSelector<T> extends FlowPane {
    private final ObjectProperty<T> selectedValue = new SimpleObjectProperty<>();
    private Consumer<T> onSelected = ignored -> { };

    public MiniCardSelector(String fieldLabel, List<T> options, T currentValue, Function<T, Integer> identity,
            Function<T, String> name, Function<T, javafx.scene.Node> renderer) {
        super(8, 8);
        getStyleClass().add("mini-card-selector");
        for (T option : options) {
            javafx.scene.Node card = Objects.requireNonNull(renderer.apply(option), "card renderer result");
            // The native button owns mouse, keyboard and accessibility activation.
            card.setMouseTransparent(true);
            Button button = new Button();
            button.getStyleClass().add("mini-card-selector-card");
            button.setGraphic(card);
            button.setAccessibleText(name.apply(option));
            Runnable updateSelection = () -> {
                boolean selected = getSelectedValue() != null
                        && Objects.equals(identity.apply(option), identity.apply(getSelectedValue()));
                card.getStyleClass().remove("shale-card-selected");
                if (selected) card.getStyleClass().add("shale-card-selected");
                button.setAccessibleHelp((selected ? "Selected " : "Select ") + fieldLabel);
            };
            selectedValue.addListener((observable, oldValue, newValue) -> updateSelection.run());
            button.setOnAction(event -> {
                setSelectedValue(option);
                onSelected.accept(option);
            });
            getChildren().add(button);
            updateSelection.run();
        }
        setSelectedValue(currentValue);
    }

    public ObjectProperty<T> selectedValueProperty() { return selectedValue; }
    public T getSelectedValue() { return selectedValue.get(); }
    public void setSelectedValue(T value) { selectedValue.set(value); }
    public void setOnSelected(Consumer<T> handler) { onSelected = Objects.requireNonNull(handler); }

    /** Card activation accepts the exact typed option; all dismissal paths leave caller state intact. */
    protected static <T> Optional<T> showPicker(Window owner, String fieldLabel, MiniCardSelector<T> selector) {
        Dialog<T> dialog = new Dialog<>();
        AppDialogs.applySecondaryDialogShell(dialog, "Select " + fieldLabel);
        dialog.initOwner(owner);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.setResultConverter(buttonType -> null);
        selector.setOnSelected(value -> { dialog.setResult(value); dialog.close(); });
        ScrollPane scroll = new ScrollPane(selector);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(420);
        scroll.setPrefViewportHeight(240);
        dialog.getDialogPane().setContent(scroll);
        ControlStyles.apply((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL),
                ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
        return dialog.showAndWait();
    }
}
