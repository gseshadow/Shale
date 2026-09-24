package com.shale.ui.controller.support;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.controller.support.NewIntakeDatesConfiguration.Selection;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.util.WindowSizingUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

/** Staged, owner-bound editor for the New Intake date-field configuration. */
public final class NewIntakeDatesCustomizationDialog {
    private final Dialog<Void> dialog = new Dialog<>();
    private final VBox rows = new VBox(10);
    private final Label status = new Label("Loading the current configuration…");
    private final ComboBox<EffectiveCaseDateTypeDto> addSelector = ControlStyles.formControl(new ComboBox<>());
    private final Button addButton;
    private final Button saveButton;
    private final Button reloadButton;
    private final List<Selection> selections = new ArrayList<>();
    private List<EffectiveCaseDateTypeDto> availableTypes = List.of();
    private Consumer<List<Selection>> saveHandler = ignored -> {};
    private Runnable reloadHandler = () -> {};

    public NewIntakeDatesCustomizationDialog(Window owner) {
        AppDialogs.applySecondaryDialogShell(dialog, "Customize New Intake Form");
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        Button hiddenClose = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        hiddenClose.setVisible(false);
        hiddenClose.setManaged(false);

        status.setWrapText(true);
        status.getStyleClass().add("shale-metadata-muted");
        addSelector.setPromptText("Select an active Case Date Type");
        addSelector.setMaxWidth(Double.MAX_VALUE);
        addSelector.setConverter(typeConverter());
        HBox.setHgrow(addSelector, Priority.ALWAYS);
        addButton = ActionButtonFactory.semantic("Add field", event -> addSelected(),
                ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
        addButton.setDisable(true);
        addSelector.valueProperty().addListener((obs, oldValue, value) -> addButton.setDisable(value == null));
        HBox addBar = new HBox(10, addSelector, addButton);
        addBar.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMinHeight(260);
        scroll.getStyleClass().add("new-intake-customization-scroll");

        reloadButton = ActionButtonFactory.semantic("Reload configuration", event -> reloadHandler.run(),
                ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
        reloadButton.setVisible(false);
        reloadButton.setManaged(false);
        Button cancelButton = ActionButtonFactory.semantic("Cancel", event -> dialog.close(),
                ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
        saveButton = ActionButtonFactory.semantic("Save", event -> saveHandler.accept(snapshot()),
                ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
        saveButton.setDisable(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, reloadButton, spacer, cancelButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Label guidance = new Label("Choose the fields shown on New Intake, their order, and whether each field is required.");
        guidance.setWrapText(true);
        VBox content = new VBox(12, guidance, status, addBar, scroll, actions);
        content.setPadding(new Insets(16));
        content.getStyleClass().add("new-intake-customization-dialog");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(1040, 680);
        dialog.getDialogPane().setMinSize(760, 480);
        dialog.setOnShown(event -> Platform.runLater(() -> {
            if (dialog.getDialogPane().getScene().getWindow() instanceof Stage stage) {
                stage.setResizable(true);
                WindowSizingUtil.sizeModalStage(stage, owner, 1040, 680, 760, 480);
            }
        }));
    }

    public void show() { dialog.show(); }
    public void close() { dialog.close(); }
    public boolean isShowing() { return dialog.isShowing(); }
    public void setOnSave(Consumer<List<Selection>> handler) { saveHandler = Objects.requireNonNull(handler); }
    public void setOnReload(Runnable handler) { reloadHandler = Objects.requireNonNull(handler); }

    public void showConfiguration(List<Selection> current, List<EffectiveCaseDateTypeDto> types) {
        selections.clear(); selections.addAll(current == null ? List.of() : current);
        availableTypes = types == null ? List.of() : List.copyOf(types);
        status.setText("Changes are staged until Save. Required controls whether intake may be submitted without a value.");
        status.getStyleClass().remove("dialog-error-text"); reloadButton.setVisible(false); reloadButton.setManaged(false);
        saveButton.setDisable(false); render();
    }

    public void showLoadError(String message) {
        selections.clear();
        rows.getChildren().clear();
        status.setText(message);
        if (!status.getStyleClass().contains("dialog-error-text")) status.getStyleClass().add("dialog-error-text");
        reloadButton.setVisible(true);
        reloadButton.setManaged(true);
        saveButton.setDisable(true);
    }

    public void setSaving(boolean saving) {
        dialog.getDialogPane().getContent().setDisable(saving);
        if (!saving) dialog.getDialogPane().getContent().setDisable(false);
    }

    public void showSaveError(String message, boolean stale) {
        setSaving(false);
        status.setText(message);
        if (!status.getStyleClass().contains("dialog-error-text")) status.getStyleClass().add("dialog-error-text");
        reloadButton.setVisible(stale);
        reloadButton.setManaged(stale);
    }

    public List<Selection> snapshot() { return List.copyOf(selections); }
    private void addSelected() {
        EffectiveCaseDateTypeDto selected = addSelector.getValue();
        if (selected == null) return;
        selections.add(new Selection(selected, false));
        render();
    }

    private void render() {
        rows.getChildren().clear();
        refreshAddChoices();
        for (int index = 0; index < selections.size(); index++) rows.getChildren().add(fieldRow(index));
        if (selections.isEmpty()) {
            Label empty = new Label("No date fields are configured. Add an active Case Date Type above.");
            empty.getStyleClass().add("shale-empty-message");
            rows.getChildren().add(empty);
        }
    }

    private VBox fieldRow(int index) {
        Selection selection = selections.get(index);
        Label name = new Label(selection.type().name());
        name.setWrapText(true);
        name.setMinWidth(220);
        name.getStyleClass().add("shale-field-label");

        CheckBox required = ControlStyles.formControl(new CheckBox("Required"));
        required.setSelected(selection.required());
        required.setAccessibleText(selection.type().name() + " required");
        required.selectedProperty().addListener((obs, oldValue, value) -> selections.set(index,
                NewIntakeDatesConfiguration.withRequired(selections.get(index), value)));


        Button up = action("Move up", "Move " + selection.type().name() + " up", () -> move(index, -1));
        Button down = action("Move down", "Move " + selection.type().name() + " down", () -> move(index, 1));
        Button remove = action("Remove field", "Remove " + selection.type().name(), () -> remove(index));
        up.setDisable(index == 0);
        down.setDisable(index == selections.size() - 1);

        HBox ordering = new HBox(8, up, down, remove);
        ordering.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(10, name, required, ordering);
        card.getStyleClass().add("new-intake-customization-row");
        return card;
    }

    private Button action(String text, String accessibleText, Runnable action) {
        Button button = ActionButtonFactory.semantic(text, event -> action.run(),
                ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
        button.setAccessibleText(accessibleText);
        button.setTooltip(new Tooltip(accessibleText));
        return button;
    }

    private void move(int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= selections.size()) return;
        Collections.swap(selections, index, target);
        render();
    }

    private void remove(int index) { selections.remove(index); render(); }

    private void refreshAddChoices() {
        addSelector.getItems().setAll(availableTypes.stream()
                .filter(type -> selections.stream().noneMatch(selection -> selection.type().id() == type.id())).toList());
        addSelector.setValue(null);
    }

    private static StringConverter<EffectiveCaseDateTypeDto> typeConverter() {
        return new StringConverter<>() {
            @Override public String toString(EffectiveCaseDateTypeDto value) { return value == null ? "" : value.name(); }
            @Override public EffectiveCaseDateTypeDto fromString(String value) { return null; }
        };
    }

}
