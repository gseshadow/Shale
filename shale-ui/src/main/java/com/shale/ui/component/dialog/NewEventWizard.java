package com.shale.ui.component.dialog;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.model.CalendarEventType;
import com.shale.ui.component.ColorCodedComboBox;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/** The single-window, non-persisting Calendar creation form for both authoritative domains. */
public final class NewEventWizard {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final int TITLE_LIMIT = 255;
    private NewEventWizard() {}

    public enum SourceKind { GENERAL_EVENT, CASE_EVENT }
    public record TypeChoice(SourceKind sourceKind, int authoritativeTypeId, String name, String color,
                             boolean supportsTime, int sortOrder) {
        public String groupLabel() { return sourceKind == SourceKind.GENERAL_EVENT ? "General Event" : "Case Event"; }
        @Override public String toString() { return name; }
    }
    public record CaseDateInput(int caseDateTypeId, long caseId, String title, LocalDateTime startsAt,
                                LocalDateTime endsAt, boolean allDay, String notes) {}
    public record GeneralEventInput(int calendarEventTypeId, String title, LocalDateTime startsAt,
                                    LocalDateTime endsAt, boolean allDay, String notes) {}
    public record SaveRequest(SourceKind sourceKind, GeneralEventInput general,
                              CaseDateInput caseDate) {}

    public static List<TypeChoice> choices(List<CalendarEventType> general, List<EffectiveCaseDateTypeDto> cases) {
        List<TypeChoice> out = new ArrayList<>();
        if (general != null) general.stream().filter(CalendarEventType::active).forEach(t -> out.add(
                new TypeChoice(SourceKind.GENERAL_EVENT, t.calendarEventTypeId(), t.name(), t.colorHex(), true, t.sortOrder())));
        if (cases != null) cases.stream().filter(t -> t.active() && !t.deleted()).forEach(t -> out.add(
                new TypeChoice(SourceKind.CASE_EVENT, t.id(), t.name(), t.color(), t.supportsTime(), t.sortOrder())));
        out.sort(Comparator.comparing(TypeChoice::sourceKind).thenComparingInt(TypeChoice::sortOrder)
                .thenComparing(t -> safe(t.name()).toLowerCase(Locale.ROOT)).thenComparingInt(TypeChoice::authoritativeTypeId));
        return List.copyOf(out);
    }

    public static Handle show(Window owner, int tenantId, LocalDate defaultDate,
            Supplier<List<NewCalendarEventDialog.CaseOption>> caseLoader,
            Supplier<List<NewCalendarEventDialog.AssignedUserOption>> ignoredUserLoader,
            Function<SaveRequest, ? extends CompletionStage<String>> saver, Executor executor) {
        return new Handle(owner, tenantId, defaultDate, caseLoader, saver, executor == null ? Runnable::run : executor);
    }

    public static final class Handle {
        private final Stage stage;
        private final int tenantId;
        private final Executor executor;
        private final Supplier<List<NewCalendarEventDialog.CaseOption>> caseLoader;
        private final Function<SaveRequest, ? extends CompletionStage<String>> saver;
        private final AtomicBoolean submitting = new AtomicBoolean();
        private int typeGeneration;
        private int caseGeneration;
        private final List<TypeChoice> loadedTypes = new ArrayList<>();
        private final List<NewCalendarEventDialog.CaseOption> loadedCases = new ArrayList<>();
        private NewCalendarEventDialog.CaseOption selectedCase;

        private final TextField title = new TextField();
        private final VBox caseField = new VBox(7);
        private final VBox caseDisplay = new VBox(7);
        private final TextField caseSearch = new TextField();
        private final ListView<NewCalendarEventDialog.CaseOption> caseList = new ListView<>();
        private final ColorCodedComboBox<TypeChoice> type = new ColorCodedComboBox<>(TypeChoice::name, TypeChoice::color, TypeChoice::groupLabel);
        private final DatePicker startDate;
        private final DatePicker endDate = new DatePicker();
        private final TextField startTime = new TextField("9:00");
        private final ComboBox<Integer> duration = new ComboBox<>();
        private final CheckBox allDay = new CheckBox();
        private final TextArea notes = new TextArea();
        private final Label error = new Label();
        private final Button save;
        private final Button cancel;

        private Handle(Window owner, int tenantId, LocalDate initialDate,
                Supplier<List<NewCalendarEventDialog.CaseOption>> caseLoader,
                Function<SaveRequest, ? extends CompletionStage<String>> saver, Executor executor) {
            this.tenantId = tenantId; this.caseLoader = caseLoader; this.saver = saver; this.executor = executor;
            startDate = new DatePicker(initialDate == null ? LocalDate.now() : initialDate);
            stage = AppDialogs.createModalStage(owner, "New Event");
            save = ActionButtonFactory.semantic("Save", e -> submit(), ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
            cancel = ActionButtonFactory.semantic("Cancel", e -> { if (!submitting.get()) close(); }, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
            save.setDefaultButton(true); cancel.setCancelButton(true); cancel.setAccessibleText("Cancel new event");
            configureControls();
            GridPane fields = new GridPane(); fields.setHgap(16); fields.setVgap(10);
            ColumnConstraints labels = new ColumnConstraints(125); labels.setMinWidth(125);
            ColumnConstraints controls = new ColumnConstraints(); controls.setHgrow(Priority.ALWAYS);
            fields.getColumnConstraints().setAll(labels, controls);
            int row = 0;
            add(fields,row++,"Title",title); add(fields,row++,"Assign to Case",caseField); add(fields,row++,"Type",type);
            add(fields,row++,"Start Date",startDate); add(fields,row++,"End Date",endDate); add(fields,row++,"Start Time",startTime);
            add(fields,row++,"Duration",duration); add(fields,row++,"All Day",allDay); add(fields,row,"Notes",notes);
            error.getStyleClass().add("form-validation-message"); error.setWrapText(true); hide(error);
            Region spacer = new Region(); HBox.setHgrow(spacer,Priority.ALWAYS);
            HBox actions = new HBox(8,spacer,save,cancel); actions.setAlignment(Pos.CENTER_RIGHT);
            VBox body = new VBox(14,fields,error,actions); body.setPadding(new Insets(20,24,18,24));
            Scene scene = new Scene(AppDialogs.createSecondaryWindowShell(stage,"New Event",this::close,body),720,680);
            scene.getStylesheets().add(Objects.requireNonNull(NewEventWizard.class.getResource("/css/app.css")).toExternalForm());
            scene.setOnKeyPressed(e -> { if (e.getCode()==KeyCode.ESCAPE && !submitting.get()) { close(); e.consume(); } });
            stage.setScene(scene); stage.setMinWidth(620); stage.setMinHeight(600); stage.show(); Platform.runLater(title::requestFocus);
        }

        public boolean isShowing(){ return stage.isShowing(); }
        public int tenantId(){ return tenantId; }
        public int beginTypeLoad(){ return ++typeGeneration; }
        public void close(){ typeGeneration++; caseGeneration++; stage.close(); }
        public void populateTypes(int resultTenantId,List<CalendarEventType> general,List<EffectiveCaseDateTypeDto> cases,int requestGeneration){
            if(!acceptType(resultTenantId,requestGeneration)) return; loadedTypes.clear(); loadedTypes.addAll(choices(general,cases)); refreshTypes();
        }
        public void showTypeLoadError(int resultTenantId,int requestGeneration,String message){ if(acceptType(resultTenantId,requestGeneration)) showError(message); }
        private boolean acceptType(int resultTenantId,int requestGeneration){ return stage.isShowing()&&tenantId==resultTenantId&&typeGeneration==requestGeneration; }
        private boolean acceptCase(int resultTenantId,int requestGeneration){ return stage.isShowing()&&tenantId==resultTenantId&&caseGeneration==requestGeneration; }

        private void configureControls(){
            title.setPromptText("Event title"); title.setAccessibleText("Title");
            type.setPromptText("Search and select a type"); type.setAccessibleText("Type"); type.setEditable(true);
            type.getEditor().textProperty().addListener((o,a,b)-> { if(type.isShowing()) filterTypes(b); });
            type.valueProperty().addListener((o,a,b)-> updateTimeAuthority());
            duration.getItems().setAll(15,30,45,60,90,120,180,240); duration.setValue(60);
            allDay.setAccessibleText("All Day"); notes.setPrefRowCount(4); notes.setWrapText(true);
            for(Control c:List.of(title,type,startDate,endDate,startTime,duration,allDay,notes,caseSearch)) ControlStyles.formControl(c);
            allDay.selectedProperty().addListener((o,a,b)->updateTimedControls());
            CaseCardFactory cards = new CaseCardFactory(id -> {});
            caseSearch.setPromptText("Search cases"); caseSearch.setAccessibleText("Search cases");
            caseSearch.textProperty().addListener((o,a,b)->refreshCaseFilter());
            caseList.setAccessibleText("Case search results"); caseList.setPrefHeight(190);
            caseList.setCellFactory(v->new ListCell<>() { @Override protected void updateItem(NewCalendarEventDialog.CaseOption x,boolean empty){
                super.updateItem(x,empty); setText(null); setGraphic(empty||x==null?null:cards.create(new CaseCardFactory.CaseCardModel(
                        x.caseId(),x.displayName(),null,null,x.responsibleAttorney(),x.responsibleAttorneyColor(),x.nonEngagementLetterSent()),CaseCardFactory.Variant.MINI)); }});
            caseList.setOnMouseClicked(e->{ if(e.getClickCount()==2) chooseCase(); });
            caseList.setOnKeyPressed(e->{ if(e.getCode()==KeyCode.ENTER){chooseCase();e.consume();} });
            renderCaseField(false);
        }

        private void renderCaseField(boolean choosing){
            caseDisplay.getChildren().clear();
            if(selectedCase==null){
                Button assign=ActionButtonFactory.semantic("Assign to Case",e->openCaseSelector(),ControlStyles.Purpose.SECONDARY,ControlStyles.Size.STANDARD);
                assign.setAccessibleText("Assign event to a Case"); caseDisplay.getChildren().add(assign);
            } else {
                CaseCardFactory cards=new CaseCardFactory(id->{});
                Node card=cards.create(new CaseCardFactory.CaseCardModel(selectedCase.caseId(),selectedCase.displayName(),null,null,
                        selectedCase.responsibleAttorney(),selectedCase.responsibleAttorneyColor(),selectedCase.nonEngagementLetterSent()),CaseCardFactory.Variant.MINI);
                Button change=ActionButtonFactory.semantic("Change",e->openCaseSelector(),ControlStyles.Purpose.SECONDARY,ControlStyles.Size.SMALL);
                Button remove=ActionButtonFactory.semantic("Remove",e->{selectedCase=null;renderCaseField(false);refreshTypes();},ControlStyles.Purpose.GHOST,ControlStyles.Size.SMALL);
                caseDisplay.getChildren().addAll(card,new HBox(8,change,remove));
            }
            caseField.getChildren().setAll(caseDisplay);
            if(choosing) caseField.getChildren().addAll(caseSearch,caseList);
        }
        private void openCaseSelector(){ renderCaseField(true); caseSearch.clear(); caseList.setPlaceholder(new Label("Loading cases…")); int request=++caseGeneration;
            CompletableFuture.supplyAsync(()->caseLoader==null?List.<NewCalendarEventDialog.CaseOption>of():caseLoader.get(),executor)
                    .whenComplete((rows,failure)->Platform.runLater(()->{ if(!acceptCase(tenantId,request)||!caseField.getChildren().contains(caseList))return;
                        if(failure!=null){caseList.setPlaceholder(new Label("Unable to load cases. Try again."));return;}
                        loadedCases.clear();loadedCases.addAll(rows==null?List.of():rows);refreshCaseFilter(); })); }
        private void chooseCase(){ NewCalendarEventDialog.CaseOption chosen=caseList.getSelectionModel().getSelectedItem(); if(chosen==null)return;
            selectedCase=chosen; renderCaseField(false); refreshTypes(); }
        private void refreshCaseFilter(){ String q=safe(caseSearch.getText()).strip().toLowerCase(Locale.ROOT); caseList.setItems(FXCollections.observableArrayList(
                loadedCases.stream().filter(c->q.isEmpty()||safe(c.displayName()).toLowerCase(Locale.ROOT).contains(q)||safe(c.responsibleAttorney()).toLowerCase(Locale.ROOT).contains(q)).toList()));
            caseList.setPlaceholder(new Label(loadedCases.isEmpty()?"No active cases are available.":"No cases match this search.")); }
        private void refreshTypes(){ TypeChoice old=type.getValue(); SourceKind authority=selectedCase==null?SourceKind.GENERAL_EVENT:SourceKind.CASE_EVENT;
            List<TypeChoice> available=loadedTypes.stream().filter(t->t.sourceKind()==authority).toList(); type.getItems().setAll(available);
            if(old==null||old.sourceKind()!=authority||available.stream().noneMatch(t->t.sourceKind()==old.sourceKind()&&t.authoritativeTypeId()==old.authoritativeTypeId())) type.setValue(null);
            updateTimeAuthority(); }
        private void filterTypes(String query){ SourceKind authority=selectedCase==null?SourceKind.GENERAL_EVENT:SourceKind.CASE_EVENT; String q=safe(query).strip().toLowerCase(Locale.ROOT);
            type.setItems(FXCollections.observableArrayList(loadedTypes.stream().filter(t->t.sourceKind()==authority&&(q.isEmpty()||safe(t.name()).toLowerCase(Locale.ROOT).contains(q))).toList())); }
        private void updateTimeAuthority(){ TypeChoice t=type.getValue(); boolean forced=t!=null&&!t.supportsTime(); allDay.setDisable(forced); if(forced)allDay.setSelected(true); updateTimedControls(); }
        private void updateTimedControls(){ boolean disabled=allDay.isSelected()||(type.getValue()!=null&&!type.getValue().supportsTime()); startTime.setDisable(disabled);duration.setDisable(disabled); }

        private void submit(){ if(submitting.get())return; Optional<SaveRequest> request=read();if(request.isEmpty()||!submitting.compareAndSet(false,true))return;
            setBusy(true); CompletionStage<String> result; try{result=saver==null?CompletableFuture.completedFuture("Save is unavailable."):saver.apply(request.get());}
            catch(RuntimeException ex){result=CompletableFuture.completedFuture("Unable to save this event.");} if(result==null)result=CompletableFuture.completedFuture("Save is unavailable.");
            result.whenComplete((message,failure)->Platform.runLater(()->{if(!stage.isShowing())return;submitting.set(false);setBusy(false);String shown=failure==null?message:"Unable to save this event.";if(shown==null||shown.isBlank())close();else showError(shown);})); }
        private Optional<SaveRequest> read(){
            String normalized=safe(title.getText()).strip(); if(normalized.isBlank()){showError("Title is required.");return Optional.empty();}
            if(normalized.length()>TITLE_LIMIT){showError("Title must be 255 characters or fewer.");return Optional.empty();}
            TypeChoice selected=type.getValue(); if(selected==null){showError("Type is required.");return Optional.empty();}
            if(startDate.getValue()==null){showError("Start Date is required.");return Optional.empty();}
            if(endDate.getValue()!=null&&endDate.getValue().isBefore(startDate.getValue())){showError("End Date must not be before Start Date.");return Optional.empty();}
            boolean ad=allDay.isSelected()||!selected.supportsTime(); LocalTime time=null;
            if(!ad)try{time=parse(startTime.getText());}catch(IllegalArgumentException ex){showError(ex.getMessage());return Optional.empty();}
            Integer minutes=duration.getValue();if(!ad&&(minutes==null||minutes<=0)){showError("Duration is required for a timed event.");return Optional.empty();}
            LocalDateTime starts=ad?startDate.getValue().atStartOfDay():startDate.getValue().atTime(time);
            LocalDateTime ends=ad?(endDate.getValue()==null?null:endDate.getValue().atStartOfDay()):(endDate.getValue()==null?startDate.getValue():endDate.getValue()).atTime(time).plusMinutes(minutes);
            if(selectedCase==null){ if(selected.sourceKind()!=SourceKind.GENERAL_EVENT){showError("Select a General Event type.");return Optional.empty();}
                return Optional.of(new SaveRequest(SourceKind.GENERAL_EVENT,new GeneralEventInput(selected.authoritativeTypeId(),normalized,starts,ends,ad,notes.getText()),null)); }
            if(selected.sourceKind()!=SourceKind.CASE_EVENT){showError("Select a Case Event type.");return Optional.empty();}
            return Optional.of(new SaveRequest(SourceKind.CASE_EVENT,null,new CaseDateInput(selected.authoritativeTypeId(),selectedCase.caseId(),normalized,starts,ends,ad,notes.getText())));
        }
        private void setBusy(boolean busy){save.setDisable(busy);cancel.setDisable(busy);title.setDisable(busy);type.setDisable(busy);caseField.setDisable(busy);startDate.setDisable(busy);endDate.setDisable(busy);allDay.setDisable(busy||(type.getValue()!=null&&!type.getValue().supportsTime()));updateTimedControls();notes.setDisable(busy);}
        private void showError(String message){error.setText(message);error.setVisible(true);error.setManaged(true);}
        private static void add(GridPane grid,int row,String text,Node node){Label label=new Label(text);label.setLabelFor(node);label.setMinWidth(Region.USE_PREF_SIZE);grid.add(label,0,row);grid.add(node,1,row);GridPane.setHgrow(node,Priority.ALWAYS);}
        private static LocalTime parse(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Start Time is required for a timed event.");try{return LocalTime.parse(value.strip(),TIME);}catch(DateTimeParseException ex){throw new IllegalArgumentException("Start Time must be valid, such as 9:30.");}}
        private static void hide(Node n){n.setVisible(false);n.setManaged(false);}
    }
    private static String safe(String value){return value==null?"":value;}
}
