package com.shale.ui.component.dialog;

import com.shale.core.model.CalendarEventType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class NewCalendarEventDialog {
    private NewCalendarEventDialog() {
    }

    public static Optional<CreateCalendarEventInput> showAndWait(
            Window owner,
            List<CalendarEventType> eventTypes,
            LocalDate defaultDate) {
        Stage stage = AppDialogs.createModalStage(owner, "New Event");
        ResultHolder holder = new ResultHolder();

        Label heading = new Label("Create event");
        heading.getStyleClass().add("app-dialog-title");

        Label message = new Label("Title, type, and date are required.");
        message.getStyleClass().add("app-dialog-message");

        Label titleLabel = new Label("Title");
        TextField titleField = new TextField();
        titleField.setPromptText("Event title");

        Label eventTypeLabel = new Label("Type");
        ComboBox<CalendarEventType> eventTypeComboBox = new ComboBox<>();
        eventTypeComboBox.setMaxWidth(Double.MAX_VALUE);
        eventTypeComboBox.setPromptText("Select type");
        List<CalendarEventType> safeTypes = eventTypes == null ? List.of() : eventTypes;
        eventTypeComboBox.getItems().setAll(safeTypes);
        eventTypeComboBox.setCellFactory(cb -> new CalendarTypeCell());
        eventTypeComboBox.setButtonCell(new CalendarTypeCell());

        Label dateLabel = new Label("Date");
        DatePicker datePicker = new DatePicker(defaultDate == null ? LocalDate.now() : defaultDate);

        CheckBox allDayCheckBox = new CheckBox("All day");
        allDayCheckBox.setSelected(true);

        Label startLabel = new Label("Start time");
        TextField startTimeField = new TextField();
        startTimeField.setPromptText("HH:mm");

        Label endLabel = new Label("End time");
        TextField endTimeField = new TextField();
        endTimeField.setPromptText("Optional HH:mm");

        Label descriptionLabel = new Label("Description");
        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("Optional");
        descriptionArea.setPrefRowCount(4);
        descriptionArea.setWrapText(true);

        Runnable refreshTimeState = () -> {
            boolean timed = !allDayCheckBox.isSelected();
            startLabel.setDisable(!timed);
            startTimeField.setDisable(!timed);
            endLabel.setDisable(!timed);
            endTimeField.setDisable(!timed);
        };
        allDayCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> refreshTimeState.run());
        refreshTimeState.run();

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #b42318;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(e -> {
            String title = titleField.getText() == null ? "" : titleField.getText().trim();
            if (title.isBlank()) {
                showError(errorLabel, "Title is required.");
                return;
            }
            CalendarEventType selectedType = eventTypeComboBox.getValue();
            if (selectedType == null || selectedType.calendarEventTypeId() <= 0) {
                showError(errorLabel, "Event type is required.");
                return;
            }
            LocalDate selectedDate = datePicker.getValue();
            if (selectedDate == null) {
                showError(errorLabel, "Date is required.");
                return;
            }

            LocalTime startTime = null;
            LocalTime endTime = null;
            if (!allDayCheckBox.isSelected()) {
                try {
                    startTime = parseTime(startTimeField.getText(), false);
                    endTime = parseTime(endTimeField.getText(), true);
                } catch (DateTimeParseException ex) {
                    showError(errorLabel, "Invalid time. Use HH:mm (example: 14:30).");
                    return;
                }
                if (startTime == null) {
                    showError(errorLabel, "Start time is required for timed events.");
                    return;
                }
                if (endTime != null && !endTime.isAfter(startTime)) {
                    showError(errorLabel, "End time must be after start time.");
                    return;
                }
            }

            holder.value = new CreateCalendarEventInput(
                    title,
                    selectedType.calendarEventTypeId(),
                    selectedDate,
                    allDayCheckBox.isSelected(),
                    startTime,
                    endTime,
                    descriptionArea.getText());
            stage.close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, spacer, cancelButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(8,
                titleLabel,
                titleField,
                eventTypeLabel,
                eventTypeComboBox,
                dateLabel,
                datePicker,
                allDayCheckBox,
                startLabel,
                startTimeField,
                endLabel,
                endTimeField,
                descriptionLabel,
                descriptionArea,
                errorLabel);
        content.setPadding(new Insets(6, 2, 2, 2));

        VBox body = new VBox(16, heading, message, content, actions);
        body.setPadding(new Insets(22, 24, 22, 24));
        VBox root = AppDialogs.createSecondaryWindowShell(stage, "New Event", stage::close, body);
        root.setMinWidth(460);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(
                NewCalendarEventDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
        return Optional.ofNullable(holder.value);
    }

    private static LocalTime parseTime(String raw, boolean optional) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            return optional ? null : null;
        }
        return LocalTime.parse(value);
    }

    private static void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    public record CreateCalendarEventInput(
            String title,
            int calendarEventTypeId,
            LocalDate date,
            boolean allDay,
            LocalTime startTime,
            LocalTime endTime,
            String description) {
    }

    private static final class ResultHolder {
        private CreateCalendarEventInput value;
    }

    private static final class CalendarTypeCell extends javafx.scene.control.ListCell<CalendarEventType> {
        @Override
        protected void updateItem(CalendarEventType item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.name());
        }
    }
}
