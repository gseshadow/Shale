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
import java.util.function.Function;
import java.util.function.Supplier;

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

    public static void showEditDialog(
            Window owner,
            List<CalendarEventType> eventTypes,
            CreateCalendarEventInput initial,
            Function<CreateCalendarEventInput, String> onSave,
            Supplier<String> onDelete) {
        Stage stage = AppDialogs.createModalStage(owner, "Edit Event");
        Label heading = new Label("Edit event");
        heading.getStyleClass().add("app-dialog-title");
        Label message = new Label("Title, type, and date are required.");
        message.getStyleClass().add("app-dialog-message");
        DialogParts p = DialogParts.build(eventTypes, initial);

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());

        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        deleteButton.setOnAction(e -> {
            boolean confirmed = AppDialogs.showConfirmation(stage.getOwner(), "Delete Event", "Delete event", "Delete this event?", "Delete", AppDialogs.DialogActionKind.DANGER);
            if (!confirmed) return;
            String err = onDelete == null ? "Delete is unavailable." : onDelete.get();
            if (err == null || err.isBlank()) stage.close();
            else showError(p.errorLabel, err);
        });

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(e -> {
            Optional<CreateCalendarEventInput> input = p.readInput().get();
            if (input.isEmpty()) return;
            String err = onSave == null ? "Save is unavailable." : onSave.apply(input.get());
            if (err == null || err.isBlank()) stage.close();
            else showError(p.errorLabel, err);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, deleteButton, spacer, cancelButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        VBox body = new VBox(16, heading, message, p.content, actions);
        body.setPadding(new Insets(22, 24, 22, 24));
        VBox root = AppDialogs.createSecondaryWindowShell(stage, "Edit Event", stage::close, body);
        root.setMinWidth(460);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(NewCalendarEventDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
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

    private record DialogParts(VBox content, Label errorLabel, Supplier<Optional<CreateCalendarEventInput>> readInput) {
        static DialogParts build(List<CalendarEventType> eventTypes, CreateCalendarEventInput initial) {
            Label titleLabel = new Label("Title");
            TextField titleField = new TextField(initial == null ? "" : initial.title());
            titleField.setPromptText("Event title");
            Label eventTypeLabel = new Label("Type");
            ComboBox<CalendarEventType> eventTypeComboBox = new ComboBox<>();
            eventTypeComboBox.setMaxWidth(Double.MAX_VALUE);
            eventTypeComboBox.setPromptText("Select type");
            List<CalendarEventType> safeTypes = eventTypes == null ? List.of() : eventTypes;
            eventTypeComboBox.getItems().setAll(safeTypes);
            eventTypeComboBox.setCellFactory(cb -> new CalendarTypeCell());
            eventTypeComboBox.setButtonCell(new CalendarTypeCell());
            if (initial != null) safeTypes.stream().filter(t -> t.calendarEventTypeId() == initial.calendarEventTypeId()).findFirst().ifPresent(eventTypeComboBox::setValue);
            Label dateLabel = new Label("Date");
            DatePicker datePicker = new DatePicker(initial == null || initial.date() == null ? LocalDate.now() : initial.date());
            CheckBox allDayCheckBox = new CheckBox("All day");
            allDayCheckBox.setSelected(initial == null || initial.allDay());
            Label startLabel = new Label("Start time");
            TextField startTimeField = new TextField(initial != null && initial.startTime() != null ? initial.startTime().toString() : "");
            startTimeField.setPromptText("HH:mm");
            Label endLabel = new Label("End time");
            TextField endTimeField = new TextField(initial != null && initial.endTime() != null ? initial.endTime().toString() : "");
            endTimeField.setPromptText("Optional HH:mm");
            Label descriptionLabel = new Label("Description");
            TextArea descriptionArea = new TextArea(initial == null ? "" : initial.description());
            descriptionArea.setPromptText("Optional");
            descriptionArea.setPrefRowCount(4);
            descriptionArea.setWrapText(true);
            Runnable refreshTimeState = () -> {
                boolean timed = !allDayCheckBox.isSelected();
                startLabel.setDisable(!timed); startTimeField.setDisable(!timed); endLabel.setDisable(!timed); endTimeField.setDisable(!timed);
            };
            allDayCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> refreshTimeState.run());
            refreshTimeState.run();
            Label errorLabel = new Label();
            errorLabel.setStyle("-fx-text-fill: #b42318;");
            errorLabel.setVisible(false); errorLabel.setManaged(false);
            VBox content = new VBox(8, titleLabel,titleField,eventTypeLabel,eventTypeComboBox,dateLabel,datePicker,allDayCheckBox,startLabel,startTimeField,endLabel,endTimeField,descriptionLabel,descriptionArea,errorLabel);
            content.setPadding(new Insets(6, 2, 2, 2));
            Supplier<Optional<CreateCalendarEventInput>> readInput = () -> {
                String title = titleField.getText() == null ? "" : titleField.getText().trim();
                if (title.isBlank()) { showError(errorLabel, "Title is required."); return Optional.empty(); }
                CalendarEventType selectedType = eventTypeComboBox.getValue();
                if (selectedType == null || selectedType.calendarEventTypeId() <= 0) { showError(errorLabel, "Event type is required."); return Optional.empty(); }
                LocalDate selectedDate = datePicker.getValue();
                if (selectedDate == null) { showError(errorLabel, "Date is required."); return Optional.empty(); }
                LocalTime startTime = null; LocalTime endTime = null;
                if (!allDayCheckBox.isSelected()) {
                    try { startTime = parseTime(startTimeField.getText(), false); endTime = parseTime(endTimeField.getText(), true); }
                    catch (DateTimeParseException ex) { showError(errorLabel, "Invalid time. Use HH:mm (example: 14:30)."); return Optional.empty(); }
                    if (startTime == null) { showError(errorLabel, "Start time is required for timed events."); return Optional.empty(); }
                    if (endTime != null && !endTime.isAfter(startTime)) { showError(errorLabel, "End time must be after start time."); return Optional.empty(); }
                }
                return Optional.of(new CreateCalendarEventInput(title, selectedType.calendarEventTypeId(), selectedDate, allDayCheckBox.isSelected(), startTime, endTime, descriptionArea.getText()));
            };
            return new DialogParts(content, errorLabel, readInput);
        }
    }

    private static final class CalendarTypeCell extends javafx.scene.control.ListCell<CalendarEventType> {
        @Override
        protected void updateItem(CalendarEventType item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.name());
        }
    }
}
