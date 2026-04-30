package com.shale.ui.component.dialog;

import com.shale.core.model.CalendarEventType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class NewCalendarEventDialog {
    private static final List<String> TIME_OPTIONS = buildTimeOptions();
    private static final List<Integer> DURATION_OPTIONS_MINUTES = buildDurationMinutes();
    private static final int DEFAULT_DURATION_MINUTES = 60;

    private NewCalendarEventDialog() {}

    public static Optional<CreateCalendarEventInput> showAndWait(Window owner, List<CalendarEventType> eventTypes, LocalDate defaultDate) {
        Stage stage = AppDialogs.createModalStage(owner, "New Event");
        ResultHolder holder = new ResultHolder();
        DialogParts p = DialogParts.build(eventTypes, new CreateCalendarEventInput("", 0, defaultDate == null ? LocalDate.now() : defaultDate, true, null, DEFAULT_DURATION_MINUTES, ""));

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(e -> {
            Optional<CreateCalendarEventInput> input = p.readInput().get();
            if (input.isEmpty()) return;
            holder.value = input.get();
            stage.close();
        });

        showStage(stage, "New Event", "Create event", p.content, null, cancelButton, saveButton);
        return Optional.ofNullable(holder.value);
    }

    public static void showEditDialog(Window owner, List<CalendarEventType> eventTypes, CreateCalendarEventInput initial, Function<CreateCalendarEventInput, String> onSave, Supplier<String> onDelete) {
        showEditDialog(owner, eventTypes, initial, onSave, onDelete, null, null);
    }

    public static void showEditDialog(Window owner, List<CalendarEventType> eventTypes, CreateCalendarEventInput initial, Function<CreateCalendarEventInput, String> onSave, Supplier<String> onDelete, Node relatedCaseNode, Node relatedTaskNode) {
        Stage stage = AppDialogs.createModalStage(owner, "Edit Event");
        DialogParts p = DialogParts.build(eventTypes, initial, relatedCaseNode, relatedTaskNode);

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
            if (err == null || err.isBlank()) stage.close(); else showError(p.errorLabel, err);
        });

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(e -> {
            Optional<CreateCalendarEventInput> input = p.readInput().get();
            if (input.isEmpty()) return;
            String err = onSave == null ? "Save is unavailable." : onSave.apply(input.get());
            if (err == null || err.isBlank()) stage.close(); else showError(p.errorLabel, err);
        });

        showStage(stage, "Edit Event", "Edit event", p.content, deleteButton, cancelButton, saveButton);
    }

    private static void showStage(Stage stage, String shellTitle, String headingText, VBox content, Button leftAction, Button cancelButton, Button saveButton) {
        Label heading = new Label(headingText);
        heading.getStyleClass().add("app-dialog-title");
        Label message = new Label("Title, type, and date are required.");
        message.getStyleClass().add("app-dialog-message");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = leftAction == null ? new HBox(10, spacer, cancelButton, saveButton) : new HBox(10, leftAction, spacer, cancelButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        ScrollPane formScroll = new ScrollPane(content);
        formScroll.setFitToWidth(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        formScroll.setMinViewportHeight(300);
        formScroll.setPrefViewportHeight(380);
        formScroll.getStyleClass().add("calendar-day-scroll");

        VBox body = new VBox(16, heading, message, formScroll, actions);
        VBox.setVgrow(formScroll, Priority.ALWAYS);
        body.setPadding(new Insets(22, 24, 22, 24));
        VBox root = AppDialogs.createSecondaryWindowShell(stage, shellTitle, stage::close, body);
        root.setMinWidth(460);
        root.setPrefHeight(640);
        root.setMaxHeight(680);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(NewCalendarEventDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    public record CreateCalendarEventInput(String title, int calendarEventTypeId, LocalDate date, boolean allDay, LocalTime startTime, int durationMinutes, String description) {}

    private static final class ResultHolder { private CreateCalendarEventInput value; }

    private record DialogParts(VBox content, Label errorLabel, Supplier<Optional<CreateCalendarEventInput>> readInput) {
        static DialogParts build(List<CalendarEventType> eventTypes, CreateCalendarEventInput initial) {
            return build(eventTypes, initial, null, null);
        }
        static DialogParts build(List<CalendarEventType> eventTypes, CreateCalendarEventInput initial, Node relatedCaseNode, Node relatedTaskNode) {
            Label titleLabel = new Label("Title");
            TextField titleField = new TextField(initial == null ? "" : initial.title());
            Label eventTypeLabel = new Label("Type");
            ComboBox<CalendarEventType> eventTypeComboBox = new ComboBox<>();
            eventTypeComboBox.setMaxWidth(Double.MAX_VALUE);
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
            ComboBox<String> startTimeCombo = new ComboBox<>();
            startTimeCombo.getItems().setAll(TIME_OPTIONS);
            startTimeCombo.setMaxWidth(Double.MAX_VALUE);
            startTimeCombo.setPromptText("Select start");

            Label amPmLabel = new Label("AM/PM");
            ComboBox<String> amPmCombo = new ComboBox<>();
            amPmCombo.getItems().setAll("AM", "PM");
            amPmCombo.setMaxWidth(Double.MAX_VALUE);

            HBox startRow = new HBox(8);
            VBox startTimeCol = new VBox(4, startLabel, startTimeCombo);
            VBox amPmCol = new VBox(4, amPmLabel, amPmCombo);
            startTimeCol.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(startTimeCol, Priority.ALWAYS);
            amPmCol.setMinWidth(100);
            startRow.getChildren().addAll(startTimeCol, amPmCol);

            Label durationLabel = new Label("Duration");
            ComboBox<Integer> durationCombo = new ComboBox<>();
            durationCombo.getItems().setAll(DURATION_OPTIONS_MINUTES);
            durationCombo.setCellFactory(cb -> new ListCell<>() { protected void updateItem(Integer item, boolean empty){ super.updateItem(item, empty); setText(empty||item==null?null:formatDuration(item)); }});
            durationCombo.setButtonCell(new ListCell<>() { protected void updateItem(Integer item, boolean empty){ super.updateItem(item, empty); setText(empty||item==null?null:formatDuration(item)); }});
            durationCombo.setMaxWidth(Double.MAX_VALUE);
            VBox durationSection = new VBox(4, durationLabel, durationCombo);

            if (initial != null && !initial.allDay() && initial.startTime() != null) {
                String[] t = toTwelveHour(initial.startTime());
                startTimeCombo.setValue(t[0]);
                amPmCombo.setValue(t[1]);
                durationCombo.setValue(normalizeDurationSelection(initial.durationMinutes()));
            } else {
                startTimeCombo.setValue("9:00");
                amPmCombo.setValue("AM");
                durationCombo.setValue(DEFAULT_DURATION_MINUTES);
            }

            Label descriptionLabel = new Label("Description");
            TextArea descriptionArea = new TextArea(initial == null ? "" : initial.description());
            descriptionArea.setPrefRowCount(4);
            descriptionArea.setWrapText(true);

            Runnable refresh = () -> {
                boolean timed = !allDayCheckBox.isSelected();
                startRow.setDisable(!timed); durationSection.setDisable(!timed);
                startRow.setManaged(timed); durationSection.setManaged(timed);
                startRow.setVisible(timed); durationSection.setVisible(timed);
            };
            allDayCheckBox.selectedProperty().addListener((obs,o,n)->refresh.run());
            refresh.run();

            Label errorLabel = new Label(); errorLabel.setStyle("-fx-text-fill: #b42318;"); errorLabel.setVisible(false); errorLabel.setManaged(false);
            VBox content = new VBox(8);
            if (relatedCaseNode != null || relatedTaskNode != null) {
                Label relatedLabel = new Label("Related");
                relatedLabel.getStyleClass().add("calendar-all-day-meta");
                content.getChildren().add(relatedLabel);
                if (relatedCaseNode != null) content.getChildren().add(relatedCaseNode);
                if (relatedTaskNode != null) content.getChildren().add(relatedTaskNode);
            }
            content.getChildren().addAll(titleLabel,titleField,eventTypeLabel,eventTypeComboBox,dateLabel,datePicker,allDayCheckBox,startRow,durationSection,descriptionLabel,descriptionArea,errorLabel);
            content.setPadding(new Insets(6,2,2,2));

            Supplier<Optional<CreateCalendarEventInput>> readInput = () -> {
                String title = titleField.getText() == null ? "" : titleField.getText().trim();
                if (title.isBlank()) { showError(errorLabel, "Title is required."); return Optional.empty(); }
                CalendarEventType t = eventTypeComboBox.getValue();
                if (t == null || t.calendarEventTypeId() <= 0) { showError(errorLabel, "Event type is required."); return Optional.empty(); }
                LocalDate d = datePicker.getValue();
                if (d == null) { showError(errorLabel, "Date is required."); return Optional.empty(); }
                LocalTime startTime = null;
                int durationMinutes = DEFAULT_DURATION_MINUTES;
                if (!allDayCheckBox.isSelected()) {
                    if (startTimeCombo.getValue() == null || amPmCombo.getValue() == null) { showError(errorLabel, "Start time and AM/PM are required for timed events."); return Optional.empty(); }
                    if (durationCombo.getValue() == null || durationCombo.getValue() <= 0) { showError(errorLabel, "Duration is required for timed events."); return Optional.empty(); }
                    startTime = fromTwelveHour(startTimeCombo.getValue(), amPmCombo.getValue());
                    durationMinutes = durationCombo.getValue();
                }
                return Optional.of(new CreateCalendarEventInput(title, t.calendarEventTypeId(), d, allDayCheckBox.isSelected(), startTime, durationMinutes, descriptionArea.getText()));
            };
            return new DialogParts(content, errorLabel, readInput);
        }
    }

    private static List<String> buildTimeOptions() {
        List<String> out = new ArrayList<>();
        for (int hour = 0; hour < 12; hour++) {
            int displayHour = hour == 0 ? 12 : hour;
            out.add(displayHour + ":00");
            out.add(displayHour + ":30");
        }
        return out;
    }

    private static List<Integer> buildDurationMinutes() {
        List<Integer> out = new ArrayList<>();
        for (int minutes = 30; minutes <= 8 * 60; minutes += 30) out.add(minutes);
        return out;
    }

    private static Integer normalizeDurationSelection(int rawMinutes) {
        int candidate = rawMinutes <= 0 ? DEFAULT_DURATION_MINUTES : rawMinutes;
        int rounded = ((candidate + 29) / 30) * 30;
        if (rounded < 30) rounded = 30;
        if (rounded > 8 * 60) rounded = 8 * 60;
        return rounded;
    }

    private static String formatDuration(int minutes) {
        Duration d = Duration.ofMinutes(minutes);
        long h = d.toHours();
        long m = d.minusHours(h).toMinutes();
        if (h > 0 && m > 0) return h + " hr " + m + " min";
        if (h > 0) return h + (h == 1 ? " hour" : " hours");
        return m + " min";
    }

    private static String[] toTwelveHour(LocalTime time) {
        int hour24 = time.getHour();
        String ampm = hour24 < 12 ? "AM" : "PM";
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        String minute = time.getMinute() >= 30 ? "30" : "00";
        return new String[]{hour12 + ":" + minute, ampm};
    }

    private static LocalTime fromTwelveHour(String time, String ampm) {
        String[] parts = time.split(":");
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        if ("AM".equals(ampm)) {
            if (h == 12) h = 0;
        } else if (h != 12) {
            h += 12;
        }
        return LocalTime.of(h, m);
    }

    private static final class CalendarTypeCell extends ListCell<CalendarEventType> {
        @Override protected void updateItem(CalendarEventType item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? null : item.name()); }
    }
}
