package com.shale.ui.component.dialog;

import com.shale.core.dto.CaseDateDto;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.ui.component.ColorCodedComboBox;
import com.shale.ui.component.TimeDurationInput;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private CaseDateOccurrenceDialog() {}

    public record Input(int caseDateTypeId, String title, LocalDateTime startsAt, LocalDateTime endsAt, boolean allDay, String notes) {
        public Input { title=normalizeTitle(title); if(title!=null&&title.length()>255) throw new IllegalArgumentException("Title must be 255 characters or fewer."); }
    }

    public static void show(Window owner, String title, List<EffectiveCaseDateTypeDto> selectableTypes, CaseDateDto existing,
            CaseCardModel associatedCase,
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
        TextField occurrenceTitle = createTitleField(existing);
        DatePicker startDate = new DatePicker(existing == null || existing.startsAt() == null ? LocalDate.now() : existing.startsAt().toLocalDate());
        DatePicker endDate = new DatePicker(existing == null || existing.endsAt() == null ? null : existing.endsAt().toLocalDate());
        TimeDurationInput timing = new TimeDurationInput();
        if (existing != null && !existing.allDay()) {
            TimeDurationInput.TimedValue value = TimeDurationInput.fromTimestamps(existing.startsAt(), existing.endsAt());
            timing.setTimedValue(value.startTime(), value.durationMinutes());
        }
        CheckBox allDay = new CheckBox("All day"); allDay.setSelected(existing == null || existing.allDay());
        TextArea notes = new TextArea(existing == null ? "" : safe(existing.notes())); notes.setPrefRowCount(4); notes.setWrapText(true);
        ControlStyles.formControl(occurrenceTitle); ControlStyles.formControl(startDate); ControlStyles.formControl(endDate); ControlStyles.formControl(notes);
        Label error = new Label(); error.getStyleClass().add("form-validation-message"); error.setWrapText(true); error.setVisible(false); error.setManaged(false);
        Button save = ActionButtonFactory.semantic("Save", e -> {}, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
        Button cancel = ActionButtonFactory.semantic("Cancel", e -> { if (!submitting.get()) stage.close(); }, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
        Button reload = ActionButtonFactory.semantic("Reload", e -> { if (onReload != null) onReload.run(); stage.close(); }, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
        reload.setVisible(false); reload.setManaged(false);
        boolean[] forcingAllDay = {false}; boolean[] allDayBeforeForce = {allDay.isSelected()};
        Runnable syncTimeControls = () -> {
            TypeChoice selected = typeBox.getValue(); boolean supports = selected == null || selected.supportsTime();
            if (!supports && !forcingAllDay[0]) { allDayBeforeForce[0]=allDay.isSelected(); forcingAllDay[0]=true; allDay.setSelected(true); }
            else if (supports && forcingAllDay[0]) { forcingAllDay[0]=false; allDay.setSelected(allDayBeforeForce[0]); }
            allDay.setDisable(!supports); boolean timed = supports && !allDay.isSelected();
            timing.setTimedControlsDisabled(!timed);
        };
        typeBox.valueProperty().addListener((o,a,b) -> syncTimeControls.run()); allDay.selectedProperty().addListener((o,a,b) -> syncTimeControls.run()); syncTimeControls.run();
        save.setOnAction(e -> {
            Optional<Input> input = read(typeBox.getValue(), occurrenceTitle, startDate, timing, endDate, allDay, notes, error, existing);
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
        GridPane grid = createEditorGrid(typeBox,occurrenceTitle,startDate,timing,endDate,allDay,notes);
        HBox footer = new HBox(8, reload, cancel, save); footer.setAlignment(Pos.CENTER_RIGHT);
        VBox caseSection = createCaseSection(associatedCase);
        VBox body = new VBox(12, caseSection, grid, error, footer); body.setPadding(new Insets(16));
        Scene scene = new Scene(AppDialogs.createSecondaryWindowShell(stage, title, () -> { if (!submitting.get()) stage.close(); }, body));
        scene.getStylesheets().add(Objects.requireNonNull(CaseDateOccurrenceDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene); stage.showAndWait();
    }
    static VBox createCaseSection(CaseCardModel associatedCase) {
        if (associatedCase == null || associatedCase.id() <= 0) throw new IllegalArgumentException("Associated Case is required.");
        Label label = new Label("Case");
        label.setId("case-date-associated-case-label");
        label.getStyleClass().add("section-title");
        Node card = new CaseCardFactory(id -> {}).create(associatedCase, CaseCardFactory.Variant.MINI);
        card.setId("case-date-associated-case-card");
        card.setAccessibleText("Associated Case");
        card.setFocusTraversable(false);
        card.setMouseTransparent(true);
        label.setLabelFor(card);
        VBox section = new VBox(6, label, card);
        section.setFillWidth(true);
        return section;
    }
    private static Label addRow(GridPane g,int r,String label,Node n){ Label l=new Label(label); l.setLabelFor(n); g.add(l,0,r); g.add(n,1,r); GridPane.setHgrow(n, Priority.ALWAYS); return l; }
    static TextField createTitleField(CaseDateDto existing){ TextField field=new TextField(existing==null?"":safe(existing.title())); field.setId("case-date-occurrence-title"); field.setAccessibleText("Case date occurrence title"); field.setPromptText("Optional occurrence title"); return field; }
    static GridPane createEditorGrid(Node type,TextField title,Node startDate,Node timing,Node endDate,Node allDay,Node notes){ GridPane grid=new GridPane(); grid.setHgap(10); grid.setVgap(8); addRow(grid,0,"Date type",type); Label titleLabel=addRow(grid,1,"Title",title); titleLabel.setId("case-date-occurrence-title-label"); addRow(grid,2,"Start date",startDate); addRow(grid,3,"Time and duration",timing); addRow(grid,4,"End date",endDate); addRow(grid,5,"",allDay); addRow(grid,6,"Notes",notes); return grid; }
    private static Optional<Input> read(TypeChoice t, TextField title, DatePicker sd, TimeDurationInput timing, DatePicker ed, CheckBox ad, TextArea notes, Label error, CaseDateDto existing){
        if(title.getText()!=null && title.getText().trim().length()>255){showError(error,"Title must be 255 characters or fewer.");return Optional.empty();}
        if(t==null){showError(error,"Choose a date type.");return Optional.empty();} if(sd.getValue()==null){showError(error,"Start date is required.");return Optional.empty();}
        if(ed.getValue()!=null && ed.getValue().isBefore(sd.getValue())){showError(error,"End date must not be before Start date.");return Optional.empty();}
        boolean allDay=ad.isSelected(); LocalDateTime start; LocalDateTime end;
        try {
            if (allDay) { start=sd.getValue().atStartOfDay(); end=ed.getValue()==null?null:ed.getValue().atStartOfDay(); }
            else { var time=timing.commitTime(); int duration=timing.durationMinutes(); start=LocalDateTime.of(sd.getValue(),time); end=TimeDurationInput.calculateEnd(sd.getValue(),ed.getValue(),time,duration); }
        } catch (IllegalArgumentException ex){showError(error,ex.getMessage());return Optional.empty();}
        if(end!=null && end.isBefore(start)){showError(error,"End must not be before start.");return Optional.empty();}
        if (unchangedTimestamps(existing, sd.getValue(), ed.getValue(), allDay, timing, start)) {
            start=existing.startsAt(); end=existing.endsAt();
        }
        error.setVisible(false); error.setManaged(false); return Optional.of(new Input(t.id(), normalizeTitle(title.getText()), start, end, allDay, notes.getText()));
    }
    private static boolean unchangedTimestamps(CaseDateDto existing, LocalDate startDate, LocalDate endDate,
            boolean allDay, TimeDurationInput timing, LocalDateTime parsedStart) {
        if (existing==null || existing.startsAt()==null || existing.allDay()!=allDay
                || !existing.startsAt().toLocalDate().equals(startDate)
                || !Objects.equals(existing.endsAt()==null?null:existing.endsAt().toLocalDate(),endDate)) return false;
        if (allDay) return true;
        TimeDurationInput.TimedValue original=TimeDurationInput.fromTimestamps(existing.startsAt(),existing.endsAt());
        return parsedStart.toLocalTime().equals(original.startTime())
                && timing.durationMinutes()==original.durationMinutes();
    }
    private static void showError(Label l,String m){ l.setText(m); l.setVisible(true); l.setManaged(true); }
    static String normalizeTitle(String value){ if(value==null)return null; String normalized=value.trim(); return normalized.isEmpty()?null:normalized; }
    private static boolean blank(String s){ return s==null||s.isBlank(); } private static String safe(String s){return s==null?"":s;}
    private record TypeChoice(int id, String name, String color, boolean supportsTime, String secondaryText) { static TypeChoice effective(EffectiveCaseDateTypeDto d){return new TypeChoice(d.id(), d.name(), d.color(), d.supportsTime(), d.supportsTime()?"Timed or all-day":"All-day only");} static TypeChoice historical(CaseDateDto d){return new TypeChoice(d.caseDateTypeId(), d.typeName(), d.color(), d.supportsTime(), "Historical/inactive — retained unless replaced");} }
}
