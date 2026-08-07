package com.shale.ui.component.dialog;

import com.shale.core.dto.CaseDateDto;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.ui.component.ColorCodedComboBox;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class CaseDateOccurrenceDialog {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");
    private CaseDateOccurrenceDialog() {}

    public record Input(int caseDateTypeId, LocalDateTime startsAt, LocalDateTime endsAt, boolean allDay, String notes) {}

    public static void show(Window owner, String title, List<EffectiveCaseDateTypeDto> selectableTypes, CaseDateDto existing,
            Function<Input, ? extends CompletionStage<String>> onSave, Runnable onReload) {
        Stage stage = AppDialogs.createModalStage(owner, title);
        AtomicBoolean submitting = new AtomicBoolean(false);
        List<EffectiveCaseDateTypeDto> safeTypes = selectableTypes == null ? List.of() : List.copyOf(selectableTypes);
        ColorCodedComboBox<TypeChoice> typeBox = new ColorCodedComboBox<>(TypeChoice::name, TypeChoice::color, TypeChoice::secondaryText);
        ControlStyles.formControl(typeBox);
        typeBox.setPromptText("Choose a date type");
        List<TypeChoice> choices = safeTypes.stream().map(TypeChoice::effective).toList();
        typeBox.getItems().setAll(choices);
        TypeChoice historical = null;
        if (existing != null) {
            Optional<TypeChoice> match = choices.stream().filter(c -> c.id() == existing.caseDateTypeId()).findFirst();
            if (match.isPresent()) typeBox.setValue(match.get());
            else {
                historical = TypeChoice.historical(existing);
                typeBox.setButtonCell(typeBox.createColorCodedCell());
                typeBox.setValue(historical);
            }
        } else if (!choices.isEmpty()) typeBox.setValue(choices.get(0));
        DatePicker startDate = new DatePicker(existing == null || existing.startsAt() == null ? LocalDate.now() : existing.startsAt().toLocalDate());
        DatePicker endDate = new DatePicker(existing == null || existing.endsAt() == null ? null : existing.endsAt().toLocalDate());
        TextField startTime = new TextField(existing == null || existing.startsAt() == null || existing.allDay() ? "" : existing.startsAt().toLocalTime().format(TIME_FORMAT));
        TextField endTime = new TextField(existing == null || existing.endsAt() == null || existing.allDay() ? "" : existing.endsAt().toLocalTime().format(TIME_FORMAT));
        CheckBox allDay = new CheckBox("All day"); allDay.setSelected(existing == null || existing.allDay());
        TextArea notes = new TextArea(existing == null ? "" : safe(existing.notes())); notes.setPrefRowCount(4); notes.setWrapText(true);
        ControlStyles.formControl(startDate); ControlStyles.formControl(endDate); ControlStyles.formControl(startTime); ControlStyles.formControl(endTime); ControlStyles.formControl(notes);
        Label error = new Label(); error.getStyleClass().add("form-validation-message"); error.setWrapText(true); error.setVisible(false); error.setManaged(false);
        Button save = ActionButtonFactory.semantic("Save", e -> {}, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
        Button cancel = ActionButtonFactory.semantic("Cancel", e -> { if (!submitting.get()) stage.close(); }, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
        Button reload = ActionButtonFactory.semantic("Reload", e -> { if (onReload != null) onReload.run(); stage.close(); }, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
        reload.setVisible(false); reload.setManaged(false);
        Runnable syncTimeControls = () -> {
            TypeChoice selected = typeBox.getValue(); boolean supports = selected == null || selected.supportsTime();
            if (!supports) allDay.setSelected(true);
            allDay.setDisable(!supports); boolean timed = supports && !allDay.isSelected();
            startTime.setDisable(!timed); endTime.setDisable(!timed);
        };
        typeBox.valueProperty().addListener((o,a,b) -> syncTimeControls.run()); allDay.selectedProperty().addListener((o,a,b) -> syncTimeControls.run()); syncTimeControls.run();
        save.setOnAction(e -> {
            Optional<Input> input = read(typeBox.getValue(), startDate, startTime, endDate, endTime, allDay, notes, error);
            if (input.isEmpty() || submitting.getAndSet(true)) return;
            save.setDisable(true); cancel.setDisable(true); typeBox.setDisable(true);
            CompletionStage<String> result;
            try { result = onSave == null ? CompletableFuture.completedFuture("Save is unavailable.") : onSave.apply(input.get()); }
            catch (RuntimeException ex) { result = CompletableFuture.completedFuture("Unable to save this case date."); }
            if (result == null) result = CompletableFuture.completedFuture("Save is unavailable.");
            result.whenComplete((message, failure) -> javafx.application.Platform.runLater(() -> {
                String displayed = failure == null ? message : "Unable to save this case date.";
                submitting.set(false); save.setDisable(false); cancel.setDisable(false); typeBox.setDisable(false);
                if (displayed == null || displayed.isBlank()) stage.close();
                else { showError(error, displayed); if (displayed.toLowerCase().contains("changed")) { reload.setVisible(true); reload.setManaged(true); } }
            }));
        });
        stage.getScene();
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(8);
        addRow(grid,0,"Date type",typeBox); addRow(grid,1,"Start date",startDate); addRow(grid,2,"Start time",startTime); addRow(grid,3,"End date",endDate); addRow(grid,4,"End time",endTime); addRow(grid,5,"",allDay); addRow(grid,6,"Notes",notes);
        HBox footer = new HBox(8, reload, cancel, save); footer.setAlignment(Pos.CENTER_RIGHT);
        VBox body = new VBox(12, grid, error, footer); body.setPadding(new Insets(16));
        Scene scene = new Scene(AppDialogs.createSecondaryWindowShell(stage, title, () -> { if (!submitting.get()) stage.close(); }, body));
        scene.getStylesheets().add(Objects.requireNonNull(CaseDateOccurrenceDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene); stage.showAndWait();
    }
    private static void addRow(GridPane g,int r,String label,Node n){ Label l=new Label(label); g.add(l,0,r); g.add(n,1,r); GridPane.setHgrow(n, Priority.ALWAYS); }
    private static Optional<Input> read(TypeChoice t, DatePicker sd, TextField st, DatePicker ed, TextField et, CheckBox ad, TextArea notes, Label error){
        if(t==null){showError(error,"Choose a date type.");return Optional.empty();} if(sd.getValue()==null){showError(error,"Start date is required.");return Optional.empty();}
        boolean allDay=ad.isSelected(); LocalDateTime start; LocalDateTime end=null;
        try { start = allDay ? sd.getValue().atStartOfDay() : LocalDateTime.of(sd.getValue(), parseTime(st.getText(), "Start time", error)); } catch (IllegalArgumentException ex){return Optional.empty();}
        if(ed.getValue()!=null || !blank(et.getText())) { if(ed.getValue()==null){showError(error,"End date is required when an end time is entered.");return Optional.empty();} try { end = allDay ? ed.getValue().atStartOfDay() : LocalDateTime.of(ed.getValue(), parseTime(et.getText(), "End time", error)); } catch (IllegalArgumentException ex){return Optional.empty();} }
        if(end!=null && end.isBefore(start)){showError(error,"End must not be before start.");return Optional.empty();}
        error.setVisible(false); error.setManaged(false); return Optional.of(new Input(t.id(), start, end, allDay, notes.getText()));
    }
    private static LocalTime parseTime(String value, String label, Label error){ if(blank(value)){showError(error,label+" is required for timed dates."); throw new IllegalArgumentException();} try{return LocalTime.parse(value.trim(), TIME_FORMAT);} catch(DateTimeParseException ex){showError(error,label+" must be a valid time such as 9:30."); throw new IllegalArgumentException();}}
    private static void showError(Label l,String m){ l.setText(m); l.setVisible(true); l.setManaged(true); }
    private static boolean blank(String s){ return s==null||s.isBlank(); } private static String safe(String s){return s==null?"":s;}
    private record TypeChoice(int id, String name, String color, boolean supportsTime, String secondaryText) { static TypeChoice effective(EffectiveCaseDateTypeDto d){return new TypeChoice(d.id(), d.name(), d.color(), d.supportsTime(), d.supportsTime()?"Timed or all-day":"All-day only");} static TypeChoice historical(CaseDateDto d){return new TypeChoice(d.caseDateTypeId(), d.typeName(), d.color(), d.supportsTime(), "Historical/inactive — retained unless replaced");} }
}
