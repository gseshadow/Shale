package com.shale.ui.component.dialog;

import com.shale.ui.util.ControlStyles;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class CasePickerDialog {
    private CasePickerDialog() {}

    static Handle showAsync(Window owner,
                            Supplier<List<NewCalendarEventDialog.CaseOption>> loader,
                            Executor executor,
                            Consumer<NewCalendarEventDialog.CaseOption> onSelected) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(executor, "executor");
        Stage stage = AppDialogs.createModalStage(owner, "Select Case");
        TextField search = ControlStyles.formControl(new TextField());
        search.setPromptText("Search cases...");

        ListView<NewCalendarEventDialog.CaseOption> list = new ListView<>();
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(NewCalendarEventDialog.CaseOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        var options = FXCollections.<NewCalendarEventDialog.CaseOption>observableArrayList();
        FilteredList<NewCalendarEventDialog.CaseOption> filtered = new FilteredList<>(options);
        list.setItems(filtered);

        Button retry = ControlStyles.apply(new Button("Retry"), ControlStyles.Purpose.SECONDARY);
        Label state = new Label("Loading cases…");
        state.getStyleClass().add("app-dialog-message");
        VBox stateBox = new VBox(10, state, retry);
        retry.setVisible(false);
        retry.setManaged(false);

        Button select = ControlStyles.apply(new Button("Select"), ControlStyles.Purpose.PRIMARY);
        Button cancel = ControlStyles.apply(new Button("Cancel"), ControlStyles.Purpose.SECONDARY);
        select.setDefaultButton(true);
        select.setDisable(true);
        cancel.setCancelButton(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, spacer, cancel, select);

        Label heading = new Label("Select case");
        heading.getStyleClass().add("app-dialog-title");
        Label message = new Label("Search and select a case.");
        message.getStyleClass().add("app-dialog-message");
        VBox content = new VBox(8, search, stateBox, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox body = new VBox(12, heading, message, content, actions);
        body.setPadding(new Insets(18));
        VBox root = AppDialogs.createSecondaryWindowShell(stage, "Select Case", stage::close, body);
        Scene scene = new Scene(root, 460, 580);
        scene.getStylesheets().add(Objects.requireNonNull(CasePickerDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);

        AtomicInteger generation = new AtomicInteger();
        AtomicBoolean disposed = new AtomicBoolean();
        AtomicBoolean loading = new AtomicBoolean();
        Runnable filter = () -> {
            String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(option -> query.isBlank() || option != null
                    && option.displayName() != null && option.displayName().toLowerCase(Locale.ROOT).contains(query));
        };
        search.textProperty().addListener((obs, oldText, newText) -> filter.run());
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> select.setDisable(newItem == null));

        Consumer<NewCalendarEventDialog.CaseOption> accept = option -> {
            if (option == null) return;
            if (onSelected != null) onSelected.accept(option);
            stage.close();
        };
        select.setOnAction(event -> accept.accept(list.getSelectionModel().getSelectedItem()));
        cancel.setOnAction(event -> stage.close());
        list.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 2) accept.accept(list.getSelectionModel().getSelectedItem());
        });
        list.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && list.getSelectionModel().getSelectedItem() != null) {
                accept.accept(list.getSelectionModel().getSelectedItem());
                event.consume();
            }
        });

        Runnable[] load = new Runnable[1];
        load[0] = () -> {
            if (disposed.get() || !loading.compareAndSet(false, true)) return;
            int requestedGeneration = generation.incrementAndGet();
            state.setText("Loading cases…");
            state.getStyleClass().remove("error");
            stateBox.setVisible(true);
            stateBox.setManaged(true);
            retry.setVisible(false);
            retry.setManaged(false);
            list.setVisible(false);
            list.setManaged(false);
            select.setDisable(true);
            executor.execute(() -> {
                try {
                    List<NewCalendarEventDialog.CaseOption> result = loader.get();
                    Platform.runLater(() -> {
                        loading.set(false);
                        if (disposed.get() || requestedGeneration != generation.get() || !stage.isShowing()) return;
                        options.setAll(result == null ? List.of() : result);
                        filter.run();
                        if (filtered.isEmpty()) {
                            state.setText("No selectable cases found.");
                            stateBox.setVisible(true);
                            stateBox.setManaged(true);
                        } else {
                            stateBox.setVisible(false);
                            stateBox.setManaged(false);
                            list.setVisible(true);
                            list.setManaged(true);
                        }
                        Platform.runLater(search::requestFocus);
                    });
                } catch (RuntimeException failure) {
                    Platform.runLater(() -> {
                        loading.set(false);
                        if (disposed.get() || requestedGeneration != generation.get() || !stage.isShowing()) return;
                        state.setText("Cases could not be loaded. Please try again.");
                        if (!state.getStyleClass().contains("error")) state.getStyleClass().add("error");
                        retry.setVisible(true);
                        retry.setManaged(true);
                    });
                }
            });
        };
        retry.setOnAction(event -> load[0].run());
        stage.setOnHidden(event -> {
            disposed.set(true);
            generation.incrementAndGet();
        });
        stage.show();
        Platform.runLater(search::requestFocus);
        load[0].run();
        return new Handle(stage, search, list, state, select, cancel, retry, generation, disposed);
    }

    record Handle(Stage stage, TextField search, ListView<NewCalendarEventDialog.CaseOption> list,
                  Label state, Button select, Button cancel, Button retry,
                  AtomicInteger generation, AtomicBoolean disposed) {}
}
