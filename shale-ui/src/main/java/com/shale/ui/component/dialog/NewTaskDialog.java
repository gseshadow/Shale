package com.shale.ui.component.dialog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class NewTaskDialog {

    private NewTaskDialog() {
    }

    public static Optional<CreateTaskInput> showAndWait(Window owner) {
        Stage stage = AppDialogs.createModalStage(owner, "New Task");

        ResultHolder result = new ResultHolder();

        Label heading = new Label("Create task");
        heading.getStyleClass().add("app-dialog-title");

        Label message = new Label("Title is required. Description and due date are optional.");
        message.getStyleClass().add("app-dialog-message");

        Label titleLabel = new Label("Title");
        TextField titleField = new TextField();
        titleField.setPromptText("Task title");

        Label descriptionLabel = new Label("Description");
        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("Optional");
        descriptionArea.setPrefRowCount(4);
        descriptionArea.setWrapText(true);

        Label dueLabel = new Label("Due date/time");
        DatePicker dueDatePicker = new DatePicker();
        dueDatePicker.setPromptText("Optional");
        TextField dueTimeField = new TextField();
        dueTimeField.setPromptText("HH:mm (optional)");
        dueTimeField.setPrefColumnCount(8);
        HBox dueRow = new HBox(8, dueDatePicker, dueTimeField);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #b42318;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        VBox content = new VBox(8,
                titleLabel,
                titleField,
                descriptionLabel,
                descriptionArea,
                dueLabel,
                dueRow,
                errorLabel);

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());

        Button createButton = new Button("Create Task");
        createButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
        createButton.setDefaultButton(true);
        createButton.setOnAction(e -> {
            String title = titleField.getText() == null ? "" : titleField.getText().trim();
            if (title.isBlank()) {
                showError(errorLabel, "Title is required.");
                return;
            }

            LocalDateTime dueAt;
            try {
                dueAt = parseDueAt(dueDatePicker.getValue(), dueTimeField.getText());
            } catch (DateTimeParseException ex) {
                showError(errorLabel, "Invalid time. Use HH:mm (example: 14:30).");
                return;
            }

            result.value = new CreateTaskInput(title, descriptionArea.getText(), dueAt);
            stage.close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, spacer, cancelButton, createButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(16, heading, message, content, actions);
        root.getStyleClass().add("app-dialog-root");
        root.setPadding(new Insets(18));
        root.setMinWidth(460);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(
                NewTaskDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
        return Optional.ofNullable(result.value);
    }

    private static LocalDateTime parseDueAt(LocalDate dueDate, String dueTimeRaw) {
        if (dueDate == null) {
            return null;
        }
        String trimmedTime = dueTimeRaw == null ? "" : dueTimeRaw.trim();
        if (trimmedTime.isBlank()) {
            return dueDate.atStartOfDay();
        }
        return LocalDateTime.of(dueDate, LocalTime.parse(trimmedTime));
    }

    private static void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    public record CreateTaskInput(String title, String description, LocalDateTime dueAt) {
    }

    private static final class ResultHolder {
        private CreateTaskInput value;
    }
}
