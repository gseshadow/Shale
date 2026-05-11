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
        Label heading = new Label("Select case");
        heading.getStyleClass().add("app-dialog-title");
        Label message = new Label("Search and select a case.");
        message.getStyleClass().add("app-dialog-message");
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, Priority.ALWAYS);
        javafx.scene.layout.HBox actions = new javafx.scene.layout.HBox(8, spacer, cancel, select);
        VBox content = new VBox(8, search, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox body = new VBox(12, heading, message, scroll, actions);
        body.setPadding(new Insets(18));
        VBox root = AppDialogs.createSecondaryWindowShell(stage, "Select Case", stage::close, body);
        Scene scene = new Scene(root, 460, 580);
        scene.getStylesheets().add(Objects.requireNonNull(CasePickerDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
        return Optional.ofNullable(picked[0]);
    }
}
