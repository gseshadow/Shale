package com.shale.ui.component.dialog;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class CasePickerDialog {
    private CasePickerDialog() {}

    static Optional<NewCalendarEventDialog.CaseOption> show(Window owner, List<NewCalendarEventDialog.CaseOption> options) {
        Stage stage = AppDialogs.createModalStage(owner, "Select Case");
        TextField search = new TextField();
        search.setPromptText("Search cases...");
        ListView<NewCalendarEventDialog.CaseOption> list = new ListView<>();
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(NewCalendarEventDialog.CaseOption item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? null : item.displayName()); }
        });
        List<NewCalendarEventDialog.CaseOption> safe = options == null ? List.of() : options;
        list.getItems().setAll(safe);
        search.textProperty().addListener((obs, o, n) -> {
            String q = n == null ? "" : n.trim().toLowerCase();
            list.getItems().setAll(safe.stream().filter(c -> q.isBlank() || (c != null && c.displayName() != null && c.displayName().toLowerCase().contains(q))).toList());
        });
        Button select = new Button("Select");
        Button cancel = new Button("Cancel");
        final NewCalendarEventDialog.CaseOption[] picked = new NewCalendarEventDialog.CaseOption[1];
        select.setOnAction(e -> { picked[0] = list.getSelectionModel().getSelectedItem(); stage.close(); });
        cancel.setOnAction(e -> stage.close());
        list.setOnMouseClicked(e -> { if (e.getClickCount() >= 2) { picked[0] = list.getSelectionModel().getSelectedItem(); stage.close(); } });
        VBox root = new VBox(8, new Label("Case"), search, list, new javafx.scene.layout.HBox(8, cancel, select));
        VBox.setVgrow(list, Priority.ALWAYS);
        root.setPadding(new Insets(16));
        Scene scene = new Scene(root, 420, 520);
        scene.getStylesheets().add(Objects.requireNonNull(CasePickerDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
        return Optional.ofNullable(picked[0]);
    }
}
