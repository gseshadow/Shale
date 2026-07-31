package com.shale.ui.component.dialog;

import com.shale.core.model.CalendarEventType;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.util.PerfLog;
import com.shale.ui.util.ControlStyles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class NewCalendarEventDialog {
    private static final List<String> TIME_OPTIONS = buildTimeOptions();
    private static final List<Integer> DURATION_OPTIONS_MINUTES = buildDurationMinutes();
    private static final int DEFAULT_DURATION_MINUTES = 60;

    private NewCalendarEventDialog() {}

    public static Optional<CreateCalendarEventInput> showAndWait(Window owner, List<CalendarEventType> eventTypes, LocalDate defaultDate) {
        return showAndWait(owner, eventTypes, defaultDate, List.of(), List.of());
    }
    public static Optional<CreateCalendarEventInput> showAndWait(Window owner, List<CalendarEventType> eventTypes, LocalDate defaultDate, List<CaseOption> caseOptions, List<AssignedUserOption> assignedUserOptions) {
        return showAndWait(owner, eventTypes, defaultDate, caseOptions, assignedUserOptions, null);
    }

    public static Optional<CreateCalendarEventInput> showAndWait(Window owner, List<CalendarEventType> eventTypes, LocalDate defaultDate, List<CaseOption> caseOptions, List<AssignedUserOption> assignedUserOptions, CaseOption initialSelectedCase) {
        Stage stage = AppDialogs.createModalStage(owner, "New Event");
        ResultHolder holder = new ResultHolder();
        DialogParts p = DialogParts.build(eventTypes, new CreateCalendarEventInput("", 0, defaultDate == null ? LocalDate.now() : defaultDate, false, null, DEFAULT_DURATION_MINUTES, "", initialSelectedCase == null ? null : initialSelectedCase.caseId(), null), null, null, () -> caseOptions, () -> assignedUserOptions, id -> {}, initialSelectedCase, true);

        Button cancelButton = new Button("Cancel");
        ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY);
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());

        Button saveButton = new Button("Save");
        ControlStyles.apply(saveButton, ControlStyles.Purpose.PRIMARY);
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
        showEditDialog(owner, eventTypes, initial, onSave, onDelete, relatedCaseNode, relatedTaskNode, List.of(), List.of());
    }
    public static void showEditDialog(Window owner, List<CalendarEventType> eventTypes, CreateCalendarEventInput initial, Function<CreateCalendarEventInput, String> onSave, Supplier<String> onDelete, Node relatedCaseNode, Node relatedTaskNode, List<CaseOption> caseOptions, List<AssignedUserOption> assignedUserOptions) {
        Stage stage = AppDialogs.createModalStage(owner, "Edit Event");
        DialogParts p = DialogParts.build(eventTypes, initial, relatedCaseNode, relatedTaskNode, () -> caseOptions, () -> assignedUserOptions, id -> {}, null, true);

        Button cancelButton = new Button("Cancel");
        ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY);
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());

        Button deleteButton = new Button("Delete");
        ControlStyles.apply(deleteButton, ControlStyles.Purpose.DANGER);
        deleteButton.setOnAction(e -> {
            boolean confirmed = AppDialogs.showConfirmation(stage.getOwner(), "Delete Event", "Delete event", "Delete this event?", "Delete", AppDialogs.DialogActionKind.DANGER);
            if (!confirmed) return;
            String err = onDelete == null ? "Delete is unavailable." : onDelete.get();
            if (err == null || err.isBlank()) stage.close(); else showError(p.errorLabel, err);
        });

        Button saveButton = new Button("Save");
        ControlStyles.apply(saveButton, ControlStyles.Purpose.PRIMARY);
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(e -> {
            Optional<CreateCalendarEventInput> input = p.readInput().get();
            if (input.isEmpty()) return;
            String err = onSave == null ? "Save is unavailable." : onSave.apply(input.get());
            if (err == null || err.isBlank()) stage.close(); else showError(p.errorLabel, err);
        });

        showStage(stage, "Edit Event", "Edit event", p.content, deleteButton, cancelButton, saveButton);
    }

    public static EditDialogHandle showEditDialogAsyncShell(Window owner) {
        long stageCreateStart = PerfLog.start();
        PerfLog.log("DIALOG", "start", "calendar edit-event stage create");
        Stage stage = AppDialogs.createModalStage(owner, "Edit Event");
        PerfLog.logDone("DIALOG", "calendar edit-event stage create", stageCreateStart);
        Label loadingLabel = new Label("Loading event…");
        loadingLabel.getStyleClass().add("app-dialog-message");
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        VBox content = new VBox(8, loadingLabel, errorLabel);
        content.setPadding(new Insets(6,2,2,2));

        Button cancelButton = new Button("Cancel");
        ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY);
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());

        Button deleteButton = new Button("Delete");
        ControlStyles.apply(deleteButton, ControlStyles.Purpose.DANGER);
        deleteButton.setDisable(true);

        Button saveButton = new Button("Save");
        ControlStyles.apply(saveButton, ControlStyles.Purpose.PRIMARY);
        saveButton.setDefaultButton(true);
        saveButton.setDisable(true);

        long showStart = PerfLog.start();
        showStageNonBlocking(stage, "Edit Event", "Edit event", content, deleteButton, cancelButton, saveButton);
        PerfLog.logDone("DIALOG", "calendar edit-event show", showStart);
        Platform.runLater(() -> PerfLog.log("DIALOG", "tick", "calendar edit-event first-runLater-after-show"));
        return new EditDialogHandle(stage, content, errorLabel, loadingLabel, saveButton, deleteButton, cancelButton);
    }

    public static final class EditDialogHandle {
        private final Stage stage;
        private final VBox content;
        private final Label errorLabel;
        private final Label loadingLabel;
        private final Button saveButton;
        private final Button deleteButton;
        private final Button cancelButton;

        private EditDialogHandle(Stage stage, VBox content, Label errorLabel, Label loadingLabel, Button saveButton, Button deleteButton, Button cancelButton) {
            this.stage = stage;
            this.content = content;
            this.errorLabel = errorLabel;
            this.loadingLabel = loadingLabel;
            this.saveButton = saveButton;
            this.deleteButton = deleteButton;
            this.cancelButton = cancelButton;
        }

        public boolean isShowing() { return stage.isShowing(); }

        public void showLoadError(String message) { showError(errorLabel, message); }

        public void populate(List<CalendarEventType> eventTypes, CreateCalendarEventInput initial, Function<CreateCalendarEventInput, String> onSave, Supplier<String> onDelete, Node relatedCaseNode, Node relatedTaskNode, Supplier<List<CaseOption>> caseOptionsSupplier, Supplier<List<AssignedUserOption>> assignedUserOptionsSupplier, Consumer<Integer> onOpenCase, CaseOption initialSelectedCase) {
            DialogParts p = DialogParts.build(eventTypes, initial, relatedCaseNode, relatedTaskNode, caseOptionsSupplier, assignedUserOptionsSupplier, onOpenCase, initialSelectedCase, true);
            content.getChildren().setAll(p.content());
            deleteButton.setDisable(false);
            saveButton.setDisable(false);
            deleteButton.setOnAction(e -> {
                boolean confirmed = AppDialogs.showConfirmation(stage.getOwner(), "Delete Event", "Delete event", "Delete this event?", "Delete", AppDialogs.DialogActionKind.DANGER);
                if (!confirmed) return;
                String err = onDelete == null ? "Delete is unavailable." : onDelete.get();
                if (err == null || err.isBlank()) stage.close(); else showError(p.errorLabel, err);
            });
            saveButton.setOnAction(e -> {
                Optional<CreateCalendarEventInput> input = p.readInput().get();
                if (input.isEmpty()) return;
                String err = onSave == null ? "Save is unavailable." : onSave.apply(input.get());
                if (err == null || err.isBlank()) stage.close(); else showError(p.errorLabel, err);
            });
            loadingLabel.setVisible(false);
            loadingLabel.setManaged(false);
        }
    }


    public static CreateDialogHandle showCreateDialogAsyncShell(Window owner, LocalDate defaultDate, Function<CreateCalendarEventInput, String> onSave, Supplier<List<CaseOption>> caseOptionsSupplier, Supplier<List<AssignedUserOption>> assignedUserOptionsSupplier) {
        long stageCreateStart = PerfLog.start();
        PerfLog.log("DIALOG", "start", "calendar new-event stage create");
        Stage stage = AppDialogs.createModalStage(owner, "New Event");
        PerfLog.logDone("DIALOG", "calendar new-event stage create", stageCreateStart);
        CreateCalendarEventInput initial = new CreateCalendarEventInput("", 0, defaultDate == null ? LocalDate.now() : defaultDate, false, null, DEFAULT_DURATION_MINUTES, "", null, defaultAssignedUserId(assignedUserOptionsSupplier));
        DialogParts p = DialogParts.build(List.of(), initial, null, null, caseOptionsSupplier, assignedUserOptionsSupplier, id -> {}, null, false);
        Button cancelButton = new Button("Cancel");
        ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY);
        cancelButton.setOnAction(e -> stage.close());
        Button saveButton = new Button("Save");
        ControlStyles.apply(saveButton, ControlStyles.Purpose.PRIMARY);
        saveButton.setDisable(true);
        saveButton.setOnAction(e -> { Optional<CreateCalendarEventInput> input = p.readInput().get(); if (input.isEmpty()) return; String err = onSave == null ? "Save is unavailable." : onSave.apply(input.get()); if (err == null || err.isBlank()) stage.close(); else showError(p.errorLabel(), err); });
        long showStart = PerfLog.start();
        showStageNonBlocking(stage, "New Event", "Create event", p.content(), null, cancelButton, saveButton);
        PerfLog.logDone("DIALOG", "calendar new-event show", showStart);
        Platform.runLater(() -> PerfLog.log("DIALOG", "tick", "calendar new-event first-runLater-after-show"));
        return new CreateDialogHandle(stage, p.content(), p.errorLabel(), saveButton, defaultDate, onSave, caseOptionsSupplier, assignedUserOptionsSupplier);
    }

    public static final class CreateDialogHandle {
        private final Stage stage; private final VBox content; private final Label errorLabel; private final Button saveButton;
        private final LocalDate defaultDate;
        private final Function<CreateCalendarEventInput, String> onSave;
        private final Supplier<List<CaseOption>> caseOptionsSupplier;
        private final Supplier<List<AssignedUserOption>> assignedUserOptionsSupplier;
        private CreateDialogHandle(Stage stage, VBox content, Label errorLabel, Button saveButton, LocalDate defaultDate, Function<CreateCalendarEventInput, String> onSave, Supplier<List<CaseOption>> caseOptionsSupplier, Supplier<List<AssignedUserOption>> assignedUserOptionsSupplier){ this.stage=stage; this.content=content; this.errorLabel=errorLabel; this.saveButton=saveButton; this.defaultDate=defaultDate; this.onSave=onSave; this.caseOptionsSupplier=caseOptionsSupplier; this.assignedUserOptionsSupplier=assignedUserOptionsSupplier; }
        public void populateEventTypes(List<CalendarEventType> eventTypes){
            if(!stage.isShowing()) return;
            List<CalendarEventType> safeTypes = eventTypes == null ? List.of() : eventTypes;
            CreateCalendarEventInput initial = new CreateCalendarEventInput("", resolveDefaultTypeId(safeTypes), defaultDate == null ? LocalDate.now() : defaultDate, false, null, DEFAULT_DURATION_MINUTES, "", null, defaultAssignedUserId(assignedUserOptionsSupplier));
            DialogParts updated = DialogParts.build(safeTypes, initial, null, null, caseOptionsSupplier, assignedUserOptionsSupplier, id -> {}, null, true);
            content.getChildren().setAll(updated.content());
            saveButton.setDisable(safeTypes.isEmpty());
            saveButton.setOnAction(e -> { Optional<CreateCalendarEventInput> input = updated.readInput().get(); if (input.isEmpty()) return; String err = onSave == null ? "Save is unavailable." : onSave.apply(input.get()); if (err == null || err.isBlank()) stage.close(); else showError(updated.errorLabel(), err); });
        }
        public void showLoadError(String message){ showError(errorLabel, message); }
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
        formScroll.setMinViewportHeight(360);
        formScroll.setPrefViewportHeight(500);
        formScroll.getStyleClass().add("calendar-day-scroll");

        VBox body = new VBox(12, heading, message, formScroll, actions);
        VBox.setVgrow(formScroll, Priority.ALWAYS);
        body.setPadding(new Insets(20, 24, 16, 24));
        VBox root = AppDialogs.createSecondaryWindowShell(stage, shellTitle, stage::close, body);
        root.setMinWidth(680);
        root.setPrefWidth(760);
        root.setPrefHeight(Region.USE_COMPUTED_SIZE);
        root.setMaxHeight(Region.USE_COMPUTED_SIZE);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(NewCalendarEventDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static void showStageNonBlocking(Stage stage, String shellTitle, String headingText, VBox content, Button leftAction, Button cancelButton, Button saveButton) {
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
        formScroll.setMinViewportHeight(360);
        formScroll.setPrefViewportHeight(500);
        formScroll.getStyleClass().add("calendar-day-scroll");
        VBox body = new VBox(12, heading, message, formScroll, actions);
        VBox.setVgrow(formScroll, Priority.ALWAYS);
        body.setPadding(new Insets(20, 24, 16, 24));
        VBox root = AppDialogs.createSecondaryWindowShell(stage, shellTitle, stage::close, body);
        root.setMinWidth(680);
        root.setPrefWidth(760);
        root.setPrefHeight(Region.USE_COMPUTED_SIZE);
        root.setMaxHeight(Region.USE_COMPUTED_SIZE);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(NewCalendarEventDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private static void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    public record CreateCalendarEventInput(String title, int calendarEventTypeId, LocalDate date, boolean allDay, LocalTime startTime, int durationMinutes, String description, Integer caseId, Integer assignedToUserId) {}
    public record CaseOption(Integer caseId, String displayName, String responsibleAttorney, String responsibleAttorneyColor, Boolean nonEngagementLetterSent) {}
    public record AssignedUserOption(Integer userId, String displayName, String color) {}

    private static final class ResultHolder { private CreateCalendarEventInput value; }

    private record DialogParts(VBox content, Label errorLabel, Supplier<Optional<CreateCalendarEventInput>> readInput) {
        static DialogParts build(List<CalendarEventType> eventTypes, CreateCalendarEventInput initial) {
            return build(eventTypes, initial, null, null, () -> List.of(), () -> List.of(), id -> {}, null, true);
        }
        static DialogParts build(List<CalendarEventType> eventTypes, CreateCalendarEventInput initial, Node relatedCaseNode, Node relatedTaskNode, Supplier<List<CaseOption>> caseOptionsSupplier, Supplier<List<AssignedUserOption>> assignedUserOptionsSupplier, Consumer<Integer> onOpenCase, CaseOption initialSelectedCase, boolean typesReady) {
            Label titleLabel = new Label("Title");
            TextField titleField = new TextField(initial == null ? "" : initial.title());
            ControlStyles.formControl(titleField);
            Label eventTypeLabel = new Label("Type");
            ComboBox<CalendarEventType> eventTypeComboBox = new ComboBox<>();
            ControlStyles.formControl(eventTypeComboBox);
            eventTypeComboBox.setMaxWidth(Double.MAX_VALUE);
            List<CalendarEventType> safeTypes = eventTypes == null ? List.of() : eventTypes;
            eventTypeComboBox.getItems().setAll(safeTypes);
            eventTypeComboBox.setCellFactory(cb -> new CalendarTypeCell());
            eventTypeComboBox.setButtonCell(new CalendarTypeCell());
            if (!typesReady) { eventTypeComboBox.setDisable(true); eventTypeComboBox.setPromptText("Loading types..."); }
            if (initial != null) safeTypes.stream().filter(t -> t.calendarEventTypeId() == initial.calendarEventTypeId()).findFirst().ifPresent(eventTypeComboBox::setValue);
            if (typesReady && eventTypeComboBox.getValue() == null && !safeTypes.isEmpty()) eventTypeComboBox.setValue(safeTypes.getFirst());

            Label dateLabel = new Label("Date");
            DatePicker datePicker = new DatePicker(initial == null || initial.date() == null ? LocalDate.now() : initial.date());
            ControlStyles.formControl(datePicker);
            CheckBox allDayCheckBox = new CheckBox("All day");
            allDayCheckBox.setSelected(initial == null || initial.allDay());

            Label startLabel = new Label("Start time");
            ComboBox<String> startTimeCombo = new ComboBox<>();
            ControlStyles.formControl(startTimeCombo);
            startTimeCombo.getItems().setAll(TIME_OPTIONS);
            startTimeCombo.setMaxWidth(Double.MAX_VALUE);
            startTimeCombo.setPromptText("Select start");

            Label amPmLabel = new Label("AM/PM");
            ComboBox<String> amPmCombo = new ComboBox<>();
            ControlStyles.formControl(amPmCombo);
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
            ControlStyles.formControl(durationCombo);
            durationCombo.getItems().setAll(DURATION_OPTIONS_MINUTES);
            durationCombo.setCellFactory(cb -> new ListCell<>() { protected void updateItem(Integer item, boolean empty){ super.updateItem(item, empty); setText(empty||item==null?null:formatDuration(item)); }});
            durationCombo.setButtonCell(new ListCell<>() { protected void updateItem(Integer item, boolean empty){ super.updateItem(item, empty); setText(empty||item==null?null:formatDuration(item)); }});
            durationCombo.setMaxWidth(Double.MAX_VALUE);
            VBox durationSection = new VBox(4, durationLabel, durationCombo);
            durationSection.setMinWidth(130);

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
            ControlStyles.formControl(descriptionArea);
            descriptionArea.setPrefRowCount(4);
            descriptionArea.setWrapText(true);
            VBox selectedCaseHost = new VBox();
            selectedCaseHost.setAlignment(Pos.CENTER_LEFT);
            selectedCaseHost.setFillWidth(true);
            selectedCaseHost.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(selectedCaseHost, Priority.ALWAYS);
            final CaseOption[] selectedCase = new CaseOption[]{initialSelectedCase};
            final boolean[] hasCaseAssignment = new boolean[]{initial != null && initial.caseId() != null};
            CaseCardFactory caseCardFactory = new CaseCardFactory(onOpenCase == null ? id -> {} : onOpenCase);
            Runnable renderCase = () -> {
                selectedCaseHost.getChildren().clear();
                if (selectedCase[0] != null) selectedCaseHost.getChildren().add(createRelatedCasePreview(caseCardFactory, selectedCase[0]));
            };
            Button addCaseButton = new Button();
            ControlStyles.apply(addCaseButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
            Button clearCaseButton = new Button("Clear");
            ControlStyles.apply(clearCaseButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
            Runnable refreshCaseControls = () -> {
                boolean hasAssigned = selectedCase[0] != null || hasCaseAssignment[0];
                addCaseButton.setText(hasAssigned ? "Change Case" : "Add Case");
                clearCaseButton.setVisible(hasAssigned);
                clearCaseButton.setManaged(hasAssigned);
            };
            if (selectedCase[0] == null && initial != null && initial.caseId() != null) {
                renderCase.run();
                Integer selectedCaseId = initial.caseId();
                long caseResolveStart = System.nanoTime();
                CompletableFuture
                        .supplyAsync(() -> {
                            try {
                                return safeList(caseOptionsSupplier == null ? List.<CaseOption>of() : caseOptionsSupplier.get()).stream()
                                        .filter(v -> v != null && Objects.equals(v.caseId(), selectedCaseId))
                                        .findFirst()
                                        .orElse(null);
                            } catch (RuntimeException ex) {
                                return null;
                            }
                        })
                        .thenAccept(resolved -> Platform.runLater(() -> {
                            if (resolved != null) selectedCase[0] = resolved;
                            else { hasCaseAssignment[0] = false; }
                            renderCase.run();
                            refreshCaseControls.run();
                            long elapsedMs = (System.nanoTime() - caseResolveStart) / 1_000_000;
                            PerfLog.log("DIALOG", "related-case-resolve", "caseId=" + selectedCaseId + " elapsedMs=" + elapsedMs + " resolved=" + (resolved != null));
                        }));
            }
            renderCase.run();
            addCaseButton.setOnAction(e -> {
                List<CaseOption> sortedCases = (caseOptionsSupplier == null ? List.<CaseOption>of() : safeList(caseOptionsSupplier.get())).stream()
                        .sorted(Comparator.comparing(c -> safe(c.displayName()).toLowerCase()))
                        .toList();
                CasePickerDialog.show(addCaseButton.getScene().getWindow(), sortedCases).ifPresent(v -> { selectedCase[0] = v; hasCaseAssignment[0] = true; renderCase.run(); refreshCaseControls.run(); });
            });
            clearCaseButton.setOnAction(e -> { selectedCase[0] = null; hasCaseAssignment[0] = false; renderCase.run(); refreshCaseControls.run(); });
            HBox caseActionsRow = new HBox(8, addCaseButton, clearCaseButton);
            caseActionsRow.setAlignment(Pos.CENTER_LEFT);
            refreshCaseControls.run();

            Label selectedUserLabel = new Label("Shared Calendar");
            StackPane selectedUserHost = new StackPane(selectedUserLabel);
            selectedUserHost.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(selectedUserHost, Priority.ALWAYS);
            final AssignedUserOption[] selectedUser = new AssignedUserOption[1];
            final boolean[] hasUserAssignment = new boolean[]{initial != null && initial.assignedToUserId() != null};
            UserCardFactory userCardFactory = new UserCardFactory(id -> {});
            Runnable renderUser = () -> {
                selectedUserHost.getChildren().clear();
                if (selectedUser[0] == null) selectedUserHost.getChildren().add(selectedUserLabel);
                else selectedUserHost.getChildren().add(userCardFactory.create(new UserCardModel(selectedUser[0].userId(), selectedUser[0].displayName(), selectedUser[0].color(), null), UserCardFactory.Variant.MINI));
            };
            if (initial != null && initial.assignedToUserId() != null) {
                selectedUserLabel.setText("Loading...");
                renderUser.run();
                try {
                    AssignedUserOption resolved = safeList(assignedUserOptionsSupplier == null ? List.<AssignedUserOption>of() : assignedUserOptionsSupplier.get()).stream()
                            .filter(v -> v != null && Objects.equals(v.userId(), initial.assignedToUserId()))
                            .findFirst()
                            .orElse(null);
                    if (resolved != null) {
                        selectedUser[0] = resolved;
                    } else {
                        selectedUserLabel.setText("User unavailable");
                    }
                } catch (RuntimeException ex) {
                    selectedUserLabel.setText("User unavailable");
                }
            }
            renderUser.run();
            Button assignUserButton = new Button();
            ControlStyles.apply(assignUserButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
            Button clearAssignedButton = new Button("Clear");
            ControlStyles.apply(clearAssignedButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
            Runnable refreshUserControls = () -> {
                boolean hasAssigned = selectedUser[0] != null || hasUserAssignment[0];
                assignUserButton.setText(hasAssigned ? "Change Calendar" : "Choose Calendar");
                clearAssignedButton.setVisible(hasAssigned);
                clearAssignedButton.setManaged(hasAssigned);
            };
            assignUserButton.setOnAction(e -> {
                List<AssignedUserOption> sortedUsers = (assignedUserOptionsSupplier == null ? List.<AssignedUserOption>of() : safeList(assignedUserOptionsSupplier.get())).stream()
                        .sorted(Comparator.comparing(u -> safe(u.displayName()).toLowerCase()))
                        .toList();
                List<com.shale.ui.services.CaseTaskService.AssignableUserOption> candidates = sortedUsers.stream().map(u -> new com.shale.ui.services.CaseTaskService.AssignableUserOption(u.userId(), u.displayName(), u.color())).toList();
                AssignedUserPickerDialog.show(assignUserButton.getScene().getWindow(), candidates, NewCalendarEventDialog.class).ifPresent(v -> {
                    selectedUser[0] = new AssignedUserOption(v.id(), v.displayName(), v.color());
                    hasUserAssignment[0] = true;
                    renderUser.run();
                    refreshUserControls.run();
                });
            });
            clearAssignedButton.setOnAction(e -> { selectedUser[0] = null; hasUserAssignment[0] = false; renderUser.run(); refreshUserControls.run(); });
            HBox assignedActionsRow = new HBox(8, assignUserButton, clearAssignedButton);
            assignedActionsRow.setAlignment(Pos.CENTER_LEFT);
            refreshUserControls.run();
            HBox titleTypeRow = new HBox(8, new VBox(4, titleLabel, titleField), new VBox(4, eventTypeLabel, eventTypeComboBox));
            HBox.setHgrow(titleTypeRow.getChildren().getFirst(), Priority.ALWAYS);
            HBox typeDateRow = new HBox(8, new VBox(4, dateLabel, datePicker), allDayCheckBox);
            typeDateRow.setAlignment(Pos.BOTTOM_LEFT);
            HBox timeRow = new HBox(8, startTimeCol, amPmCol, durationSection);
            HBox.setHgrow(startTimeCol, Priority.ALWAYS);
            VBox caseSection = new VBox(6, caseActionsRow, selectedCaseHost);
            Label assignedSectionLabel = new Label("Calendar");
            assignedSectionLabel.getStyleClass().add("calendar-all-day-meta");
            VBox assignedSection = new VBox(6, assignedSectionLabel, assignedActionsRow, selectedUserHost);
            caseSection.setMaxWidth(Double.MAX_VALUE);
            assignedSection.setMaxWidth(Double.MAX_VALUE);
            HBox peopleSection = new HBox(12, caseSection, assignedSection);
            HBox.setHgrow(caseSection, Priority.ALWAYS);
            HBox.setHgrow(assignedSection, Priority.ALWAYS);

            Runnable refresh = () -> {
                boolean timed = !allDayCheckBox.isSelected();
                timeRow.setDisable(!timed);
                timeRow.setManaged(timed);
                timeRow.setVisible(timed);
            };
            allDayCheckBox.selectedProperty().addListener((obs,o,n)->refresh.run());
            refresh.run();

            Label errorLabel = new Label(); errorLabel.getStyleClass().add("error"); errorLabel.setVisible(false); errorLabel.setManaged(false);
            VBox content = new VBox(8);
            if (relatedCaseNode != null || relatedTaskNode != null) {
                Label relatedLabel = new Label("Related");
                relatedLabel.getStyleClass().add("calendar-all-day-meta");
                content.getChildren().add(relatedLabel);
                if (relatedCaseNode != null) content.getChildren().add(relatedCaseNode);
                if (relatedTaskNode != null) content.getChildren().add(relatedTaskNode);
            }
            content.getChildren().addAll(titleTypeRow,typeDateRow,timeRow,descriptionLabel,descriptionArea,peopleSection,errorLabel);
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
                Integer caseId = selectedCase[0] == null ? null : selectedCase[0].caseId();
                Integer assignedToUserId = selectedUser[0] == null ? null : selectedUser[0].userId();
                return Optional.of(new CreateCalendarEventInput(title, t.calendarEventTypeId(), d, allDayCheckBox.isSelected(), startTime, durationMinutes, descriptionArea.getText(), caseId, assignedToUserId));
            };
            return new DialogParts(content, errorLabel, readInput);
        }
    }


    private static Node createRelatedCasePreview(CaseCardFactory caseCardFactory, CaseOption selectedCase) {
        Node casePreview = caseCardFactory.create(new CaseCardFactory.CaseCardModel(selectedCase.caseId(), selectedCase.displayName(), null, null, selectedCase.responsibleAttorney(), selectedCase.responsibleAttorneyColor(), selectedCase.nonEngagementLetterSent()), CaseCardFactory.Variant.MINI);
        if (casePreview instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return casePreview;
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
    private static String safe(String value) { return value == null ? "" : value; }

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

    private static <T> List<T> safeList(List<T> values) { return values == null ? List.of() : values; }
    private static Integer defaultAssignedUserId(Supplier<List<AssignedUserOption>> supplier) {
        try {
            List<AssignedUserOption> options = supplier == null ? List.of() : safeList(supplier.get());
            if (options.isEmpty()) return null;
            AssignedUserOption first = options.getFirst();
            return first == null ? null : first.userId();
        } catch (RuntimeException ex) {
            return null;
        }
    }
    static int resolveDefaultTypeId(List<CalendarEventType> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) return 0;
        if (eventTypes.size() == 1) return eventTypes.getFirst().calendarEventTypeId();
        Integer meetingByKey = eventTypes.stream()
                .filter(t -> t != null && "MEETING".equalsIgnoreCase(safe(t.systemKey())))
                .map(CalendarEventType::calendarEventTypeId)
                .findFirst()
                .orElse(null);
        if (meetingByKey != null) return meetingByKey;
        Integer meetingByName = eventTypes.stream()
                .filter(t -> t != null && "MEETING".equalsIgnoreCase(safe(t.name())))
                .map(CalendarEventType::calendarEventTypeId)
                .findFirst()
                .orElse(null);
        if (meetingByName != null) return meetingByName;
        return eventTypes.getFirst().calendarEventTypeId();
    }

    private static final class CalendarTypeCell extends ListCell<CalendarEventType> {
        @Override protected void updateItem(CalendarEventType item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? null : item.name()); }
    }
}
