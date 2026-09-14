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
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.scene.AccessibleRole;
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
            Consumer<Integer> onOpenCase,
            Function<Input, ? extends CompletionStage<String>> onSave,
            Supplier<? extends CompletionStage<String>> onRemove, Runnable onReload) {
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
        Button remove = existing == null ? null : ActionButtonFactory.semantic("Remove", e -> {}, ControlStyles.Purpose.DANGER, ControlStyles.Size.STANDARD);
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
        AtomicBoolean dirty = new AtomicBoolean(false);
        typeBox.valueProperty().addListener((o,a,b) -> dirty.set(true)); occurrenceTitle.textProperty().addListener((o,a,b) -> dirty.set(true));
        startDate.valueProperty().addListener((o,a,b) -> dirty.set(true)); endDate.valueProperty().addListener((o,a,b) -> dirty.set(true));
        allDay.selectedProperty().addListener((o,a,b) -> dirty.set(true)); notes.textProperty().addListener((o,a,b) -> dirty.set(true));
        timing.startTimeControl().getEditor().textProperty().addListener((o,a,b) -> dirty.set(true));
        timing.hoursControl().valueProperty().addListener((o,a,b) -> dirty.set(true)); timing.minutesControl().valueProperty().addListener((o,a,b) -> dirty.set(true));
        save.setOnAction(e -> {
            Optional<Input> input = read(typeBox.getValue(), occurrenceTitle, startDate, timing, endDate, allDay, notes, error, existing);
            if (input.isEmpty() || submitting.getAndSet(true)) return;
            setMutationControlsDisabled(true, save, remove, cancel, reload, typeBox);
            CompletionStage<String> result;
            try { result = onSave == null ? CompletableFuture.completedFuture("Save is unavailable.") : onSave.apply(input.get()); }
            catch (RuntimeException ex) { result = CompletableFuture.completedFuture("Unable to save this case date."); }
            if (result == null) result = CompletableFuture.completedFuture("Save is unavailable.");
            result.whenComplete((message, failure) -> javafx.application.Platform.runLater(() -> {
                String displayed = failure == null ? message : "Unable to save this case date.";
                submitting.set(false); setMutationControlsDisabled(false, save, remove, cancel, reload, typeBox);
                if (displayed == null || displayed.isBlank()) stage.close();
                else { showError(error, displayed); if (displayed.toLowerCase().contains("changed")) { reload.setVisible(true); reload.setManaged(true); } }
            }));
        });
        if (remove != null) remove.setOnAction(e -> {
            if (!AppDialogs.showConfirmation(stage, "Remove Date", "Remove this case date?",
                    "This takes the Case Date off active Calendar and Case views. It can be restored later.",
                    "Remove", AppDialogs.DialogActionKind.DANGER) || submitting.getAndSet(true)) return;
            setMutationControlsDisabled(true, save, remove, cancel, reload, typeBox);
            CompletionStage<String> result;
            try { result = onRemove == null ? CompletableFuture.completedFuture("Remove is unavailable.") : onRemove.get(); }
            catch (RuntimeException ex) { result = CompletableFuture.completedFuture("Unable to remove this case date."); }
            if (result == null) result = CompletableFuture.completedFuture("Remove is unavailable.");
            result.whenComplete((message, failure) -> javafx.application.Platform.runLater(() -> {
                String displayed = failure == null ? message : "Unable to remove this case date.";
                submitting.set(false); setMutationControlsDisabled(false, save, remove, cancel, reload, typeBox);
                if (displayed == null || displayed.isBlank()) stage.close();
                else { showError(error, displayed); if (displayed.toLowerCase().contains("changed")) { reload.setVisible(true); reload.setManaged(true); } }
            }));
        });
        stage.getScene();
        GridPane grid = createEditorGrid(typeBox,occurrenceTitle,startDate,timing,endDate,allDay,notes);
        HBox footer = new HBox(8); if (remove != null) footer.getChildren().add(remove); footer.getChildren().addAll(reload, cancel, save); footer.setAlignment(Pos.CENTER_RIGHT);
        CaseNavigationGate navigation = new CaseNavigationGate(associatedCase.id(), dirty::get, submitting::get,
                () -> AppDialogs.showConfirmation(stage, "Discard Changes?", "Discard unsaved changes?",
                        "Navigating to the Case will discard changes in this Case Date.", "Discard Changes",
                        AppDialogs.DialogActionKind.DANGER), stage::close, onOpenCase);
        VBox caseSection = createCaseSection(associatedCase, navigation::activate);
        VBox body = new VBox(12, caseSection, grid, error, footer); body.setPadding(new Insets(16));
        Scene scene = new Scene(AppDialogs.createSecondaryWindowShell(stage, title, () -> { if (!submitting.get()) stage.close(); }, body));
        scene.getStylesheets().add(Objects.requireNonNull(CaseDateOccurrenceDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene); stage.showAndWait();
    }
    private static void setMutationControlsDisabled(boolean disabled, Button save, Button remove, Button cancel,
            Button reload, Node editor) {
        save.setDisable(disabled); if (remove != null) remove.setDisable(disabled); cancel.setDisable(disabled);
        reload.setDisable(disabled); editor.setDisable(disabled);
    }
    static VBox createCaseSection(CaseCardModel associatedCase, Consumer<Integer> onOpenCase) {
        if (associatedCase == null || associatedCase.id() <= 0) throw new IllegalArgumentException("Associated Case is required.");
        Label label = new Label("Case");
        label.setId("case-date-associated-case-label");
        label.getStyleClass().add("section-title");
        Node card = new CaseCardFactory(onOpenCase).create(associatedCase, CaseCardFactory.Variant.MINI);
        card.setId("case-date-associated-case-card");
        card.setAccessibleText("Open associated Case");
        card.setAccessibleRole(AccessibleRole.BUTTON);
        card.setFocusTraversable(true);
        label.setLabelFor(card);
        VBox section = new VBox(6, label, card);
        section.setFillWidth(true);
        return section;
    }
    static final class CaseNavigationGate {
        private final int caseId; private final BooleanSupplier dirty; private final BooleanSupplier saving;
        private final Supplier<Boolean> confirmDiscard; private final Runnable close; private final Consumer<Integer> navigate;
        private final AtomicBoolean activated = new AtomicBoolean(false);
        CaseNavigationGate(long caseId, BooleanSupplier dirty, BooleanSupplier saving, Supplier<Boolean> confirmDiscard,
                Runnable close, Consumer<Integer> navigate) {
            this.caseId = Math.toIntExact(caseId); this.dirty = Objects.requireNonNull(dirty); this.saving = Objects.requireNonNull(saving);
            this.confirmDiscard = Objects.requireNonNull(confirmDiscard); this.close = Objects.requireNonNull(close);
            this.navigate = navigate == null ? id -> {} : navigate;
        }
        void activate(int requestedCaseId) {
            if (requestedCaseId != caseId || saving.getAsBoolean() || !activated.compareAndSet(false, true)) return;
            if (dirty.getAsBoolean() && !Boolean.TRUE.equals(confirmDiscard.get())) { activated.set(false); return; }
            close.run(); navigate.accept(caseId);
        }
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
