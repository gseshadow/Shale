package com.shale.ui.component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.factory.PracticeAreaCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory.PracticeAreaCardModel;
import com.shale.ui.util.ControlStyles;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.stage.Window;

/** Presentation-only, ID-based practice-area MINI selection. Callers own loading and persistence. */
public final class PracticeAreaSelector<T> extends FlowPane {
    private final ObjectProperty<T> selectedValue = new SimpleObjectProperty<>();
    private Consumer<T> onSelected = ignored -> { };

    public PracticeAreaSelector(List<T> options, T currentValue, Function<T, Integer> identity,
            Function<T, String> name, Function<T, String> color) {
        super(8, 8);
        getStyleClass().add("practice-area-selector");
        PracticeAreaCardFactory cards = new PracticeAreaCardFactory(ignored -> { });
        for (T option : options) {
            PracticeAreaCard card = cards.create(new PracticeAreaCardModel(identity.apply(option),
                    name.apply(option), color.apply(option)), PracticeAreaCardFactory.Variant.MINI);
            // The native button owns mouse, keyboard and accessibility activation.
            card.setMouseTransparent(true);
            Button button = new Button();
            button.getStyleClass().add("practice-area-selector-card");
            button.setGraphic(card);
            button.setAccessibleText(name.apply(option));
            Runnable updateSelection = () -> {
                boolean selected = getSelectedValue() != null
                        && Objects.equals(identity.apply(option), identity.apply(getSelectedValue()));
                card.getStyleClass().remove("shale-card-selected");
                if (selected) card.getStyleClass().add("shale-card-selected");
                button.setAccessibleHelp(selected ? "Selected practice area" : "Select practice area");
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
    public static <T> Optional<T> showPicker(Window owner, List<T> options, T currentValue,
            Function<T, Integer> identity, Function<T, String> name, Function<T, String> color) {
        Dialog<T> dialog = new Dialog<>();
        AppDialogs.applySecondaryDialogShell(dialog, "Select Practice Area");
        dialog.initOwner(owner);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.setResultConverter(buttonType -> null);
        PracticeAreaSelector<T> selector = new PracticeAreaSelector<>(options, currentValue, identity, name, color);
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
