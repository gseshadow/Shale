package com.shale.ui.component.dialog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.core.dto.TaskStatusOptionDto;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.util.UtcDateTimeDisplayFormatter;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class TaskDetailDialog {

    private TaskDetailDialog() {
    }

    public static Optional<TaskDetailResult> showAndWait(
            String timingContext,
            long clickReceivedAtNanos,
            Window owner,
            TaskDetailModel model,
            List<TaskStatusOptionDto> statuses,
            List<TaskPriorityOptionDto> priorities,
            Function<Long, CoreTaskHydration> loadCoreTaskData,
            Function<Long, List<CaseTaskService.AssignableUserOption>> loadAssignableUsersForTask,
            Function<Long, List<AssignedTeamMember>> loadAssignedTeamMembers,
            Function<Long, List<TaskActivityEntry>> loadActivityEntries,
            Function<Long, List<TaskNoteEntry>> loadNoteEntries,
            AssignmentEditor assignmentEditor,
            NotesEditor notesEditor,
            Consumer<Integer> onOpenUser,
            Consumer<Integer> onOpenCase) {
        long dialogCreateStartedAt = System.nanoTime();
        Stage stage = AppDialogs.createModalStage(owner, "Task Details");
        Consumer<Integer> closeAndOpenUser = userId -> {
            stage.close();
            if (onOpenUser != null) {
                onOpenUser.accept(userId);
            }
        };
        Consumer<Integer> closeAndOpenCase = caseId -> {
            stage.close();
            if (onOpenCase != null) {
                onOpenCase.accept(caseId);
            }
        };

        ResultHolder result = new ResultHolder();

        Label heading = new Label("Task details");
        heading.getStyleClass().add("app-dialog-title");
        Label message = new Label("Update task fields, assigned users, completion, or delete the task.");
        message.getStyleClass().add("app-dialog-message");
        Label createdByLabel = new Label("Created by: " + displayCreatedBy(model.createdByDisplayName()));
        createdByLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.75);");

        TextField titleField = new TextField(safe(model.title()));
        TextArea descriptionArea = new TextArea(safe(model.description()));
        descriptionArea.setPrefRowCount(4);
        descriptionArea.setWrapText(true);

        DatePicker dueDatePicker = new DatePicker(model.dueAt() == null ? null : model.dueAt().toLocalDate());
        TextField dueTimeField = new TextField(model.dueAt() == null ? "" : model.dueAt().toLocalTime().toString());
        dueTimeField.setPromptText("HH:mm (optional)");
        dueTimeField.setPrefColumnCount(8);
        HBox dueRow = new HBox(8, dueDatePicker, dueTimeField);

        ComboBox<TaskStatusOptionDto> statusCombo = new ComboBox<>();
        statusCombo.setMaxWidth(Double.MAX_VALUE);
        statusCombo.getStyleClass().add("app-toolbar-select");
        List<TaskStatusOptionDto> safeStatuses = statuses == null ? List.of() : statuses;
        statusCombo.getItems().setAll(safeStatuses);
        statusCombo.setCellFactory(cb -> new StatusListCell(true));
        statusCombo.setButtonCell(new StatusListCell(false));
        selectStatus(statusCombo, safeStatuses, model.statusId());
        applyColoredToolbarSelect(statusCombo, Optional.ofNullable(statusCombo.getValue()).map(TaskStatusOptionDto::colorHex).orElse(null));
        statusCombo.valueProperty().addListener((obs, oldValue, newValue) ->
                applyColoredToolbarSelect(statusCombo, newValue == null ? null : newValue.colorHex()));

        ComboBox<TaskPriorityOptionDto> priorityCombo = new ComboBox<>();
        priorityCombo.setMaxWidth(Double.MAX_VALUE);
        priorityCombo.getStyleClass().add("app-toolbar-select");
        List<TaskPriorityOptionDto> safePriorities = priorities == null ? List.of() : priorities;
        priorityCombo.getItems().setAll(safePriorities);
        priorityCombo.setCellFactory(cb -> new PriorityListCell(true));
        priorityCombo.setButtonCell(new PriorityListCell(false));
        selectPriority(priorityCombo, safePriorities, model.priorityId());
        applyColoredToolbarSelect(priorityCombo, Optional.ofNullable(priorityCombo.getValue()).map(TaskPriorityOptionDto::colorHex).orElse(null));
        priorityCombo.valueProperty().addListener((obs, oldValue, newValue) ->
                applyColoredToolbarSelect(priorityCombo, newValue == null ? null : newValue.colorHex()));
        Label coreLoadingLabel = loadingLabel("Loading task details…");
        boolean needsCoreHydration = safeStatuses.isEmpty() || safePriorities.isEmpty() || loadCoreTaskData != null;
        setVisibleManaged(coreLoadingLabel, needsCoreHydration);

        CheckBox completedCheck = new CheckBox("Completed");
        completedCheck.setSelected(model.completed());

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #b42318;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        VBox relatedCaseSection = new VBox(4);
        String relatedCaseName = safe(model.caseName()).trim();
        if (model.caseId() > 0 && !relatedCaseName.isBlank()) {
            Label relatedCaseLabel = new Label("Case:");
            relatedCaseLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: rgba(17,37,66,0.62);");
            CaseCardFactory caseCardFactory = new CaseCardFactory(closeAndOpenCase);
            var caseCard = caseCardFactory.create(
                    new CaseCardModel(
                            model.caseId(),
                            relatedCaseName,
                            null,
                            null,
                            model.caseResponsibleAttorney(),
                            model.caseResponsibleAttorneyColor(),
                            model.caseNonEngagementLetterSent()),
                    CaseCardFactory.Variant.MINI);
            relatedCaseSection.getChildren().setAll(relatedCaseLabel, caseCard);
        } else {
            relatedCaseSection.setManaged(false);
            relatedCaseSection.setVisible(false);
        }

        VBox assignedTeamSection = new VBox(6);
        Label assignedTeamLabel = new Label("Assigned");
        assignedTeamLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: rgba(17,37,66,0.62);");
        Button addAssignedUserButton = new Button("Add Assignee");
        addAssignedUserButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        addAssignedUserButton.setFocusTraversable(false);
        Region assignedHeaderSpacer = new Region();
        HBox.setHgrow(assignedHeaderSpacer, Priority.ALWAYS);
        HBox assignedTeamHeader = new HBox(8, assignedTeamLabel, assignedHeaderSpacer, addAssignedUserButton);
        assignedTeamHeader.setAlignment(Pos.CENTER_LEFT);

        UserCardFactory assignedTeamCardFactory = new UserCardFactory(closeAndOpenUser);
        VBox assignedTeamList = new VBox(6);
        Label assignedLoadingLabel = loadingLabel("Loading assigned users…");
        setVisibleManaged(assignedLoadingLabel, false);
        ScrollPane assignedTeamScrollPane = new ScrollPane(assignedTeamList);
        assignedTeamScrollPane.setFitToWidth(true);
        assignedTeamScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        assignedTeamScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        assignedTeamScrollPane.setMinHeight(96);
        assignedTeamScrollPane.setPrefHeight(168);
        assignedTeamScrollPane.setMaxHeight(220);
        assignedTeamScrollPane.getStyleClass().add("transparent-scroll");
        List<AssignedTeamMember> initialAssignedTeamMembers = model.assignedTeamMembers() == null
                ? List.of()
                : model.assignedTeamMembers();
        @SuppressWarnings("unchecked")
        Consumer<Integer>[] removeAssignedUserRef = new Consumer[1];
        BusyMutationState busyMutationState = new BusyMutationState();
        BusyMutationUi busyMutationUi = new BusyMutationUi(busyMutationState);
        busyMutationUi.register(addAssignedUserButton);
        busyMutationUi.register(assignedTeamScrollPane);
        removeAssignedUserRef[0] = userId -> {
            if (busyMutationState.isBusy()) {
                return;
            }
            runMutationAsync(
                    busyMutationState,
                    busyMutationUi::refresh,
                    () -> assignmentEditor == null ? List.<AssignedTeamMember>of() : assignmentEditor.removeAndReload(userId),
                    refreshed -> renderAssignedTeam(assignedTeamList, assignedTeamCardFactory, refreshed, removeAssignedUserRef[0]),
                    ex -> showError(errorLabel, "Failed to remove assigned user. " + rootCauseMessage(ex)));
        };
        renderAssignedTeam(assignedTeamList, assignedTeamCardFactory, initialAssignedTeamMembers, removeAssignedUserRef[0]);
        addAssignedUserButton.setOnAction(e -> {
            if (busyMutationState.isBusy()) {
                return;
            }
            List<CaseTaskService.AssignableUserOption> candidates = loadAssignableUsersForTask == null
                    ? List.of()
                    : loadAssignableUsersForTask.apply(model.taskId());
            Optional<CaseTaskService.AssignableUserOption> selected = showAssignUserPicker(stage, candidates);
            if (selected.isEmpty()) {
                return;
            }
            CaseTaskService.AssignableUserOption user = selected.get();
            runMutationAsync(
                    busyMutationState,
                    busyMutationUi::refresh,
                    () -> assignmentEditor == null ? List.<AssignedTeamMember>of() : assignmentEditor.addAndReload(user.id()),
                    refreshed -> renderAssignedTeam(assignedTeamList, assignedTeamCardFactory, refreshed, removeAssignedUserRef[0]),
                    ex -> showError(errorLabel, "Failed to add assigned user. " + rootCauseMessage(ex)));
        });
        assignedTeamSection.getChildren().setAll(assignedTeamHeader, assignedLoadingLabel, assignedTeamScrollPane);

        VBox formContent = new VBox(8,
                createdByLabel,
                coreLoadingLabel,
                relatedCaseSection,
                new Label("Title"), titleField,
                new Label("Description"), descriptionArea,
                new Label("Status"), statusCombo,
                new Label("Priority"), priorityCombo,
                new Label("Due date/time"), dueRow,
                assignedTeamSection,
                completedCheck,
                errorLabel);
        formContent.setPadding(new Insets(8, 2, 4, 2));
        HBox.setHgrow(formContent, Priority.ALWAYS);

        VBox historyPanel = new VBox(8);
        Label historyLabel = new Label("History");
        historyLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: rgba(17,37,66,0.62);");
        TextArea noteComposer = new TextArea();
        noteComposer.setPromptText("Add note...");
        noteComposer.setPrefRowCount(3);
        noteComposer.setWrapText(true);
        Button addNoteButton = new Button("Add Note");
        addNoteButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        Label uncommittedNoteWarningLabel = new Label("You have an unadded note. Click Add Note or clear the text to continue.");
        uncommittedNoteWarningLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #b42318;");
        uncommittedNoteWarningLabel.setWrapText(true);
        uncommittedNoteWarningLabel.setVisible(false);
        uncommittedNoteWarningLabel.setManaged(false);
        Label notesErrorLabel = new Label();
        notesErrorLabel.setStyle("-fx-text-fill: #b42318;");
        notesErrorLabel.setVisible(false);
        notesErrorLabel.setManaged(false);
        VBox historyList = new VBox(8);
        historyList.setPadding(new Insets(6, 10, 8, 10));
        Label historyLoadingLabel = loadingLabel("Loading history…");
        setVisibleManaged(historyLoadingLabel, false);
        final boolean[] loadingActivityState = new boolean[] { false };
        final boolean[] loadingNotesState = new boolean[] { false };
        List<TaskNoteEntry> noteEntries = model.noteEntries() == null ? List.of() : model.noteEntries();
        List<TaskActivityEntry> activityEntries = model.activityEntries() == null ? List.of() : model.activityEntries();
        AtomicReference<List<TaskNoteEntry>> noteEntriesState = new AtomicReference<>(noteEntries);
        AtomicReference<List<TaskActivityEntry>> activityEntriesState = new AtomicReference<>(activityEntries);
        renderUnifiedHistoryFeed(
                historyList,
                activityEntriesState.get(),
                noteEntriesState.get(),
                notesEditor,
                notesErrorLabel,
                busyMutationState,
                busyMutationUi);
        ScrollPane historyScrollPane = new ScrollPane(historyList);
        historyScrollPane.setFitToWidth(true);
        historyScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        historyScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        historyScrollPane.setPrefViewportHeight(420);
        VBox.setVgrow(historyScrollPane, Priority.ALWAYS);
        busyMutationUi.register(addNoteButton);
        busyMutationUi.register(noteComposer);
        busyMutationUi.register(historyScrollPane);
        addNoteButton.setOnAction(e -> {
            if (busyMutationState.isBusy()) {
                return;
            }
            String body = safe(noteComposer.getText()).trim();
            if (body.isBlank()) {
                showError(notesErrorLabel, "Note text is required.");
                return;
            }
            runMutationAsync(
                    busyMutationState,
                    busyMutationUi::refresh,
                    () -> notesEditor == null ? noteEntriesState.get() : notesEditor.addAndReload(body),
                    refreshed -> {
                        noteEntriesState.set(refreshed == null ? List.of() : refreshed);
                        renderUnifiedHistoryFeed(
                                historyList,
                                activityEntriesState.get(),
                                noteEntriesState.get(),
                                notesEditor,
                                notesErrorLabel,
                                busyMutationState,
                                busyMutationUi);
                        noteComposer.clear();
                        notesErrorLabel.setManaged(false);
                        notesErrorLabel.setVisible(false);
                    },
                    ex -> showError(notesErrorLabel, "Failed to add note. " + rootCauseMessage(ex)));
        });
        historyPanel.getChildren().setAll(
                historyLabel,
                noteComposer,
                addNoteButton,
                uncommittedNoteWarningLabel,
                notesErrorLabel,
                historyLoadingLabel,
                historyScrollPane);
        historyPanel.setPrefWidth(320);
        historyPanel.setMinWidth(280);
        historyPanel.setMaxWidth(360);
        historyPanel.setPadding(new Insets(8, 2, 4, 8));
        VBox.setVgrow(historyPanel, Priority.ALWAYS);

        VBox rightRail = new VBox(8, historyPanel);
        rightRail.setPrefWidth(340);
        rightRail.setMinWidth(300);
        rightRail.setMaxWidth(380);
        VBox.setVgrow(historyPanel, Priority.ALWAYS);

        HBox contentColumns = new HBox(12, formContent, rightRail);
        HBox.setHgrow(formContent, Priority.ALWAYS);
        contentColumns.setAlignment(Pos.TOP_LEFT);

        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        deleteButton.setOnAction(e -> {
            boolean confirmed = AppDialogs.showConfirmation(
                    stage,
                    "Delete Task",
                    "Delete this task?",
                    "This task will be removed from the case task list.",
                    "Delete Task",
                    AppDialogs.DialogActionKind.DANGER);
            if (!confirmed) {
                return;
            }
            result.value = TaskDetailResult.delete();
            stage.close();
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> {
            if (hasUncommittedNoteText(noteComposer)) {
                boolean discardUncommitted = AppDialogs.showConfirmation(
                        stage,
                        "Discard Unadded Note?",
                        "You have note text that has not been added.",
                        "Click Add Note first to keep this text, or continue to discard it.",
                        "Discard Note Text",
                        AppDialogs.DialogActionKind.DANGER);
                if (!discardUncommitted) {
                    return;
                }
            }
            stage.close();
        });

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
        saveButton.setDefaultButton(true);
        final boolean[] saveBlockedByCoreState = new boolean[] { safeStatuses.isEmpty() || safePriorities.isEmpty() };
        updateSaveCancelAvailability(
                saveButton,
                cancelButton,
                noteComposer,
                uncommittedNoteWarningLabel,
                saveBlockedByCoreState[0]);
        noteComposer.textProperty().addListener((obs, oldText, newText) -> updateSaveCancelAvailability(
                saveButton,
                cancelButton,
                noteComposer,
                uncommittedNoteWarningLabel,
                saveBlockedByCoreState[0]));
        saveButton.setOnAction(e -> {
            if (hasUncommittedNoteText(noteComposer)) {
                boolean discardUncommitted = AppDialogs.showConfirmation(
                        stage,
                        "Save Without Adding Note?",
                        "You have note text that has not been added.",
                        "Click Add Note first to keep this text, or continue to save without it.",
                        "Save Without Note",
                        AppDialogs.DialogActionKind.DANGER);
                if (!discardUncommitted) {
                    return;
                }
            }
            String title = safe(titleField.getText()).trim();
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
            Integer priorityId = Optional.ofNullable(priorityCombo.getValue()).map(TaskPriorityOptionDto::id).orElse(null);
            if (priorityId == null) {
                showError(errorLabel, "Priority is required.");
                return;
            }
            Integer statusId = Optional.ofNullable(statusCombo.getValue()).map(TaskStatusOptionDto::id).orElse(null);
            if (statusId == null) {
                showError(errorLabel, "Status is required.");
                return;
            }
            result.value = TaskDetailResult.save(new SaveTaskPayload(
                    title,
                    descriptionArea.getText(),
                    dueAt,
                    statusId,
                    priorityId,
                    completedCheck.isSelected()));
            stage.close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, deleteButton, spacer, cancelButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox body = new VBox(16, heading, message, contentColumns, actions);
        body.setPadding(new Insets(22, 24, 22, 24));
        VBox root = AppDialogs.createSecondaryWindowShell(stage, "Task Details", stage::close, body);
        root.setMinWidth(860);
        root.setPrefWidth(980);
        root.setMinHeight(620);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(
                TaskDetailDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        String context = safe(timingContext).isBlank() ? "UNKNOWN" : timingContext;
        System.out.println("[TASK_DETAIL_TIMING][" + context + "] dialog_creation_ms=" + elapsedMillis(dialogCreateStartedAt)
                + " taskId=" + model.taskId());
        System.out.println("[TASK_DETAIL_TIMING][" + context + "] fxml_load_ms=0 taskId=" + model.taskId() + " reason=programmatic-dialog");
        stage.setOnShown(e -> {
            AtomicLong coreLoadVersion = new AtomicLong();
            AtomicLong assignedLoadVersion = new AtomicLong();
            AtomicLong activityLoadVersion = new AtomicLong();
            AtomicLong notesLoadVersion = new AtomicLong();
            System.out.println("[TASK_DETAIL_TIMING][" + context + "] initial_show_ms=" + elapsedMillis(dialogCreateStartedAt)
                    + " taskId=" + model.taskId());
            if (clickReceivedAtNanos > 0L) {
                System.out.println("[TASK_DETAIL_TIMING][" + context + "] total_time_to_visible_ms="
                        + elapsedMillis(clickReceivedAtNanos) + " taskId=" + model.taskId());
            }
            System.out.println("[TASK_DETAIL_TIMING][" + context + "] background_load_start_all taskId=" + model.taskId());
            if (loadCoreTaskData != null) {
                loadSectionAsync(
                        context,
                        "core",
                        stage,
                        coreLoadVersion,
                        model.taskId(),
                        () -> List.of(loadCoreTaskData.apply(model.taskId())),
                        entries -> {
                            CoreTaskHydration core = entries.isEmpty() ? null : entries.get(0);
                            if (core == null || core.detail() == null) {
                                return;
                            }
                            var detail = core.detail();
                            titleField.setText(safe(detail.title()));
                            descriptionArea.setText(safe(detail.description()));
                            dueDatePicker.setValue(detail.dueAt() == null ? null : detail.dueAt().toLocalDate());
                            dueTimeField.setText(detail.dueAt() == null ? "" : detail.dueAt().toLocalTime().toString());
                            completedCheck.setSelected(detail.completedAt() != null);
                            createdByLabel.setText("Created by: " + displayCreatedBy(detail.createdByDisplayName()));
                            List<TaskStatusOptionDto> hydratedStatuses = core.statuses() == null ? List.of() : core.statuses();
                            statusCombo.getItems().setAll(hydratedStatuses);
                            selectStatus(statusCombo, hydratedStatuses, detail.statusId());
                            applyColoredToolbarSelect(statusCombo, Optional.ofNullable(statusCombo.getValue()).map(TaskStatusOptionDto::colorHex).orElse(null));
                            List<TaskPriorityOptionDto> hydratedPriorities = core.priorities() == null ? List.of() : core.priorities();
                            priorityCombo.getItems().setAll(hydratedPriorities);
                            selectPriority(priorityCombo, hydratedPriorities, detail.priorityId());
                            applyColoredToolbarSelect(priorityCombo, Optional.ofNullable(priorityCombo.getValue()).map(TaskPriorityOptionDto::colorHex).orElse(null));
                            saveBlockedByCoreState[0] = hydratedStatuses.isEmpty() || hydratedPriorities.isEmpty();
                            updateSaveCancelAvailability(
                                    saveButton,
                                    cancelButton,
                                    noteComposer,
                                    uncommittedNoteWarningLabel,
                                    saveBlockedByCoreState[0]);
                            setVisibleManaged(coreLoadingLabel, false);
                        },
                        ex -> {
                            showError(errorLabel, "Failed to load task details. " + rootCauseMessage(ex));
                            setVisibleManaged(coreLoadingLabel, false);
                        });
            } else {
                setVisibleManaged(coreLoadingLabel, false);
            }
            loadSectionAsync(
                    context,
                    "assigned",
                    stage,
                    assignedLoadVersion,
                    model.taskId(),
                    () -> loadAssignedTeamMembers == null ? List.of() : loadAssignedTeamMembers.apply(model.taskId()),
                    members -> {
                        setVisibleManaged(assignedLoadingLabel, false);
                        renderAssignedTeam(assignedTeamList, assignedTeamCardFactory, members, removeAssignedUserRef[0]);
                        addAssignedUserButton.setDisable(false);
                    },
                    ex -> {
                        setVisibleManaged(assignedLoadingLabel, false);
                        addAssignedUserButton.setDisable(false);
                        showError(errorLabel, "Failed to load assigned users. " + rootCauseMessage(ex));
                    });
            loadSectionAsync(
                    context,
                    "activity",
                    stage,
                    activityLoadVersion,
                    model.taskId(),
                    () -> loadActivityEntries == null ? List.of() : loadActivityEntries.apply(model.taskId()),
                    entries -> {
                        activityEntriesState.set(entries == null ? List.of() : entries);
                        loadingActivityState[0] = false;
                        setVisibleManaged(historyLoadingLabel, loadingActivityState[0] || loadingNotesState[0]);
                        renderUnifiedHistoryFeed(
                                historyList,
                                activityEntriesState.get(),
                                noteEntriesState.get(),
                                notesEditor,
                                notesErrorLabel,
                                busyMutationState,
                                busyMutationUi);
                    },
                    ex -> {
                        loadingActivityState[0] = false;
                        setVisibleManaged(historyLoadingLabel, loadingActivityState[0] || loadingNotesState[0]);
                        showError(errorLabel, "Failed to load activity. " + rootCauseMessage(ex));
                    });
            loadSectionAsync(
                    context,
                    "notes",
                    stage,
                    notesLoadVersion,
                    model.taskId(),
                    () -> loadNoteEntries == null ? List.of() : loadNoteEntries.apply(model.taskId()),
                    entries -> {
                        noteEntriesState.set(entries == null ? List.of() : entries);
                        loadingNotesState[0] = false;
                        setVisibleManaged(historyLoadingLabel, loadingActivityState[0] || loadingNotesState[0]);
                        renderUnifiedHistoryFeed(
                                historyList,
                                activityEntriesState.get(),
                                noteEntriesState.get(),
                                notesEditor,
                                notesErrorLabel,
                                busyMutationState,
                                busyMutationUi);
                        noteComposer.setDisable(false);
                        addNoteButton.setDisable(false);
                    },
                    ex -> {
                        loadingNotesState[0] = false;
                        setVisibleManaged(historyLoadingLabel, loadingActivityState[0] || loadingNotesState[0]);
                        noteComposer.setDisable(false);
                        addNoteButton.setDisable(false);
                        showError(notesErrorLabel, "Failed to load notes. " + rootCauseMessage(ex));
                    });
        });
        if (initialAssignedTeamMembers.isEmpty()) {
            setVisibleManaged(assignedLoadingLabel, true);
        }
        if ((model.activityEntries() == null || model.activityEntries().isEmpty())) {
            loadingActivityState[0] = true;
        }
        if (noteEntries.isEmpty()) {
            loadingNotesState[0] = true;
            noteComposer.setDisable(true);
            addNoteButton.setDisable(true);
        }
        setVisibleManaged(historyLoadingLabel, loadingActivityState[0] || loadingNotesState[0]);
        if (initialAssignedTeamMembers.isEmpty()) {
            addAssignedUserButton.setDisable(true);
        }
        stage.showAndWait();
        return Optional.ofNullable(result.value);
    }

    private static <T> void loadSectionAsync(
            String context,
            String sectionName,
            Stage stage,
            AtomicLong sectionVersion,
            long taskId,
            Callable<List<T>> loader,
            Consumer<List<T>> onSuccess,
            Consumer<Throwable> onError) {
        System.out.println("[TASK_DETAIL_TIMING][" + context + "] background_load_start section=" + sectionName + " taskId=" + taskId);
        long requestVersion = sectionVersion == null ? 0L : sectionVersion.incrementAndGet();
        long startedAt = System.nanoTime();
        new Thread(() -> {
            try {
                List<T> value = loader == null ? List.of() : loader.call();
                Platform.runLater(() -> {
                    if (stage != null && !stage.isShowing()) {
                        return;
                    }
                    if (sectionVersion != null && sectionVersion.get() != requestVersion) {
                        return;
                    }
                    if (onSuccess != null) {
                        onSuccess.accept(value == null ? List.of() : value);
                    }
                    System.out.println("[TASK_DETAIL_TIMING][" + context + "] background_load_end section=" + sectionName
                            + " duration_ms=" + elapsedMillis(startedAt) + " taskId=" + taskId);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (stage != null && !stage.isShowing()) {
                        return;
                    }
                    if (sectionVersion != null && sectionVersion.get() != requestVersion) {
                        return;
                    }
                    if (onError != null) {
                        onError.accept(ex);
                    }
                    System.out.println("[TASK_DETAIL_TIMING][" + context + "] background_load_end section=" + sectionName
                            + " duration_ms=" + elapsedMillis(startedAt) + " taskId=" + taskId + " status=error");
                });
            }
        }, "task-detail-dialog-load-" + sectionName + "-" + taskId).start();
    }

    private static Label loadingLabel(String text) {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(16, 16);
        Label label = new Label(text, spinner);
        label.setGraphicTextGap(8);
        label.setStyle("-fx-text-fill: rgba(17,37,66,0.72); -fx-font-size: 12px;");
        return label;
    }

    private static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private static void setVisibleManaged(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static <T> void runMutationAsync(
            BusyMutationState busyState,
            Runnable onBusyChanged,
            java.util.concurrent.Callable<T> worker,
            Consumer<T> onSuccess,
            Consumer<Throwable> onError) {
        busyState.increment();
        if (onBusyChanged != null) {
            onBusyChanged.run();
        }
        new Thread(() -> {
            try {
                T value = worker == null ? null : worker.call();
                Platform.runLater(() -> {
                    try {
                        if (onSuccess != null) {
                            onSuccess.accept(value);
                        }
                    } finally {
                        busyState.decrement();
                        if (onBusyChanged != null) {
                            onBusyChanged.run();
                        }
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    try {
                        if (onError != null) {
                            onError.accept(ex);
                        }
                    } finally {
                        busyState.decrement();
                        if (onBusyChanged != null) {
                            onBusyChanged.run();
                        }
                    }
                });
            }
        }, "task-detail-dialog-mutation").start();
    }

    private static final class BusyMutationState {
        private int inFlight;

        void increment() {
            inFlight++;
        }

        void decrement() {
            if (inFlight > 0) {
                inFlight--;
            }
        }

        boolean isBusy() {
            return inFlight > 0;
        }
    }

    private static final class BusyMutationUi {
        private final BusyMutationState busyState;
        private final java.util.List<Node> nodes = new java.util.ArrayList<>();

        BusyMutationUi(BusyMutationState busyState) {
            this.busyState = busyState;
        }

        void register(Node node) {
            if (node != null) {
                nodes.add(node);
            }
        }

        void refresh() {
            boolean busy = busyState.isBusy();
            for (Node node : nodes) {
                node.setDisable(busy);
            }
        }
    }

    private static void selectPriority(
            ComboBox<TaskPriorityOptionDto> priorityCombo,
            List<TaskPriorityOptionDto> priorities,
            Integer currentPriorityId) {
        if (priorities == null || priorities.isEmpty()) {
            return;
        }
        TaskPriorityOptionDto selected = null;
        if (currentPriorityId != null) {
            for (TaskPriorityOptionDto priority : priorities) {
                if (priority != null && priority.id() == currentPriorityId) {
                    selected = priority;
                    break;
                }
            }
        }
        if (selected == null) {
            selected = priorities.get(0);
        }
        priorityCombo.setValue(selected);
    }

    private static void selectStatus(
            ComboBox<TaskStatusOptionDto> statusCombo,
            List<TaskStatusOptionDto> statuses,
            Integer currentStatusId) {
        if (statuses == null || statuses.isEmpty()) {
            return;
        }
        TaskStatusOptionDto selected = null;
        if (currentStatusId != null) {
            for (TaskStatusOptionDto status : statuses) {
                if (status != null && status.id() == currentStatusId) {
                    selected = status;
                    break;
                }
            }
        }
        if (selected == null) {
            selected = statuses.get(0);
        }
        statusCombo.setValue(selected);
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

    private static boolean hasUncommittedNoteText(TextArea noteComposer) {
        return !safe(noteComposer == null ? null : noteComposer.getText()).trim().isBlank();
    }

    private static void updateSaveCancelAvailability(
            Button saveButton,
            Button cancelButton,
            TextArea noteComposer,
            Label uncommittedNoteWarningLabel,
            boolean saveBlockedByCoreState) {
        boolean hasUncommittedText = hasUncommittedNoteText(noteComposer);
        if (saveButton != null) {
            saveButton.setDisable(saveBlockedByCoreState || hasUncommittedText);
        }
        if (cancelButton != null) {
            cancelButton.setDisable(hasUncommittedText);
        }
        setVisibleManaged(uncommittedNoteWarningLabel, hasUncommittedText);
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static String displayCreatedBy(String name) {
        String trimmed = safe(name).trim();
        return trimmed.isBlank() ? "—" : trimmed;
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        String message = null;
        while (cursor != null) {
            if (cursor.getMessage() != null && !cursor.getMessage().isBlank()) {
                message = cursor.getMessage().trim();
            }
            cursor = cursor.getCause();
        }
        return (message == null || message.isBlank()) ? "Unexpected error." : message;
    }

    private static void renderAssignedTeam(
            VBox assignedTeamList,
            UserCardFactory cardFactory,
            List<AssignedTeamMember> members,
            Consumer<Integer> onRemove) {
        assignedTeamList.getChildren().clear();
        List<AssignedTeamMember> safeMembers = members == null ? List.of() : members;
        if (safeMembers.isEmpty()) {
            Label emptyLabel = new Label("No users assigned");
            emptyLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.70);");
            assignedTeamList.getChildren().add(emptyLabel);
            return;
        }
        for (AssignedTeamMember member : safeMembers) {
            if (member == null) {
                continue;
            }
            var card = cardFactory.create(
                    new UserCardModel(member.userId(), safe(member.displayName()), member.colorCss(), null),
                    UserCardFactory.Variant.MINI);
            Button removeButton = new Button("Remove");
            removeButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
            removeButton.setFocusTraversable(false);
            removeButton.setOnAction(e -> onRemove.accept(member.userId()));
            HBox row = new HBox(8, card, removeButton);
            row.setAlignment(Pos.CENTER_LEFT);
            assignedTeamList.getChildren().add(row);
        }
    }

    private static void renderUnifiedHistoryFeed(
            VBox historyList,
            List<TaskActivityEntry> activityEntries,
            List<TaskNoteEntry> noteEntries,
            NotesEditor notesEditor,
            Label notesErrorLabel,
            BusyMutationState busyMutationState,
            BusyMutationUi busyMutationUi) {
        historyList.getChildren().clear();
        List<TaskActivityEntry> safeActivities = activityEntries == null ? List.of() : activityEntries;
        List<TaskNoteEntry> safeNotes = noteEntries == null ? List.of() : noteEntries;
        List<HistoryFeedItem> items = mergeHistoryItems(safeActivities, safeNotes);
        if (items.isEmpty()) {
            Label empty = new Label("No history yet.");
            empty.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.70);");
            historyList.getChildren().add(empty);
            return;
        }

        Runnable rerender = () -> renderUnifiedHistoryFeed(
                historyList,
                activityEntries,
                noteEntries,
                notesEditor,
                notesErrorLabel,
                busyMutationState,
                busyMutationUi);
        for (HistoryFeedItem item : items) {
            if (item.type() == HistoryFeedItemType.NOTE && item.note() != null) {
                historyList.getChildren().add(createNoteCard(
                        historyList,
                        item.note(),
                        safeNotes,
                        safeActivities,
                        notesEditor,
                        notesErrorLabel,
                        busyMutationState,
                        busyMutationUi,
                        rerender));
                continue;
            }
            if (item.type() == HistoryFeedItemType.ACTIVITY && item.activity() != null) {
                historyList.getChildren().add(createActivityRow(item.activity()));
            }
        }
    }

    private static VBox createActivityRow(TaskActivityEntry entry) {
        String body = safe(entry.body()).trim();
        String title = safe(entry.title()).trim();
        String message = body.isBlank() ? title : body;
        if (message.isBlank()) {
            message = "Activity event";
        }
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.72);");
        VBox content = new VBox(1, messageLabel);

        String actor = safe(entry.actorDisplayName()).trim();
        if (actor.isBlank()) {
            actor = "System";
        }
        Label metaLabel = new Label(actor + " · " + formatDateTime(entry.occurredAt()));
        metaLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(17,37,66,0.56);");
        content.getChildren().add(metaLabel);

        VBox row = new VBox(content);
        row.setPadding(new Insets(3, 6, 3, 6));
        return row;
    }

    private static VBox createNoteCard(
            VBox historyList,
            TaskNoteEntry entry,
            List<TaskNoteEntry> safeNotes,
            List<TaskActivityEntry> safeActivities,
            NotesEditor notesEditor,
            Label notesErrorLabel,
            BusyMutationState busyMutationState,
            BusyMutationUi busyMutationUi,
            Runnable rerender) {
        Label authorLabel = new Label((safe(entry.userDisplayName()).trim().isBlank() ? "Unknown user" : entry.userDisplayName())
                + " · " + formatDateTime(entry.createdAt()));
        authorLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(17,37,66,0.70);");

        String updated = "";
        if (entry.updatedAt() != null && !entry.updatedAt().equals(entry.createdAt())) {
            updated = " (edited " + formatDateTime(entry.updatedAt()) + ")";
        }
        if (!updated.isBlank()) {
            authorLabel.setText(authorLabel.getText() + updated);
        }

        Label bodyLabel = new Label(safe(entry.body()));
        bodyLabel.setWrapText(true);

        VBox cardContent = new VBox(6, authorLabel, bodyLabel);
        if (entry.editable()) {
            Button editButton = new Button("Edit");
            editButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
            editButton.getStyleClass().add("app-dialog-button-compact");
            editButton.setOnAction(e -> {
                TextArea editArea = new TextArea(safe(entry.body()));
                editArea.setWrapText(true);
                editArea.setPrefRowCount(3);
                Button saveButton = new Button("Save");
                saveButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
                Button cancelButton = new Button("Cancel");
                cancelButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
                HBox actions = new HBox(6, saveButton, cancelButton);
                VBox editContent = new VBox(6, authorLabel, editArea, actions);
                VBox card = (VBox) ((Button) e.getSource()).getParent().getParent();
                card.getChildren().setAll(editContent);
                saveButton.setOnAction(saveEvent -> {
                    if (busyMutationState != null && busyMutationState.isBusy()) {
                        return;
                    }
                    String updatedText = safe(editArea.getText()).trim();
                    if (updatedText.isBlank()) {
                        showError(notesErrorLabel, "Note text is required.");
                        return;
                    }
                    runMutationAsync(
                            busyMutationState,
                            busyMutationUi == null ? null : busyMutationUi::refresh,
                            () -> notesEditor == null ? safeNotes : notesEditor.editAndReload(entry.id(), updatedText),
                            refreshed -> {
                                renderUnifiedHistoryFeed(
                                        historyList,
                                        safeActivities,
                                        refreshed,
                                        notesEditor,
                                        notesErrorLabel,
                                        busyMutationState,
                                        busyMutationUi);
                                notesErrorLabel.setManaged(false);
                                notesErrorLabel.setVisible(false);
                            },
                            ex -> showError(notesErrorLabel, "Failed to update note. " + rootCauseMessage(ex)));
                });
                cancelButton.setOnAction(cancelEvent -> rerender.run());
            });
            HBox actionRow = new HBox(6, editButton);
            actionRow.setAlignment(Pos.CENTER_RIGHT);
            actionRow.setMaxWidth(Double.MAX_VALUE);
            cardContent.getChildren().add(actionRow);
        }

        VBox card = new VBox(cardContent);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.getStyleClass().add("secondary-panel");
        card.setStyle("-fx-background-color: rgba(52, 110, 201, 0.22);");
        return card;
    }

    private static List<HistoryFeedItem> mergeHistoryItems(List<TaskActivityEntry> activityEntries, List<TaskNoteEntry> noteEntries) {
        List<HistoryFeedItem> combined = new ArrayList<>();
        for (TaskActivityEntry activity : activityEntries) {
            if (activity == null) {
                continue;
            }
            combined.add(new HistoryFeedItem(HistoryFeedItemType.ACTIVITY, activity.occurredAt(), activity, null));
        }
        for (TaskNoteEntry note : noteEntries) {
            if (note == null) {
                continue;
            }
            combined.add(new HistoryFeedItem(HistoryFeedItemType.NOTE, note.createdAt(), null, note));
        }
        combined.sort(Comparator.comparing(HistoryFeedItem::occurredAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed());
        return combined;
    }

    private enum HistoryFeedItemType {
        NOTE,
        ACTIVITY
    }

    private record HistoryFeedItem(
            HistoryFeedItemType type,
            LocalDateTime occurredAt,
            TaskActivityEntry activity,
            TaskNoteEntry note) {
    }

    private static void applyColoredToolbarSelect(ComboBox<?> comboBox, String colorHex) {
        if (comboBox == null) {
            return;
        }
        RgbColor baseColor = parseHexColor(colorHex);
        if (baseColor == null) {
            comboBox.setStyle("");
            return;
        }
        RgbColor top = blend(baseColor, new RgbColor(255, 255, 255), 0.18);
        RgbColor bottom = blend(baseColor, new RgbColor(0, 0, 0), 0.12);
        RgbColor border = blend(baseColor, new RgbColor(255, 255, 255), 0.30);
        String textColor = contrastTextColor(baseColor);
        comboBox.setStyle(
                "-app-toolbar-select-bg-top: " + toCssRgba(top, 0.95) + ";"
                        + "-app-toolbar-select-bg-bottom: " + toCssRgba(bottom, 0.98) + ";"
                        + "-app-toolbar-select-border: " + toCssRgba(border, 0.88) + ";"
                        + "-app-toolbar-select-text: " + textColor + ";");
    }

    private static RgbColor parseHexColor(String rawHex) {
        String normalized = safe(rawHex).trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.regionMatches(true, 0, "0x", 0, 2)) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() == 8) {
            normalized = normalized.substring(0, 6);
        }
        if (normalized.length() != 6 || !normalized.matches("[0-9a-fA-F]{6}")) {
            return null;
        }
        try {
            int red = Integer.parseInt(normalized.substring(0, 2), 16);
            int green = Integer.parseInt(normalized.substring(2, 4), 16);
            int blue = Integer.parseInt(normalized.substring(4, 6), 16);
            return new RgbColor(red, green, blue);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String contrastTextColor(RgbColor color) {
        double red = color.red() / 255.0;
        double green = color.green() / 255.0;
        double blue = color.blue() / 255.0;
        double luminance = (0.2126 * red) + (0.7152 * green) + (0.0722 * blue);
        return luminance >= 0.58 ? "#112542" : "#f9fbff";
    }

    private static RgbColor blend(RgbColor source, RgbColor target, double ratio) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        int red = (int) Math.round((source.red() * (1 - clamped)) + (target.red() * clamped));
        int green = (int) Math.round((source.green() * (1 - clamped)) + (target.green() * clamped));
        int blue = (int) Math.round((source.blue() * (1 - clamped)) + (target.blue() * clamped));
        return new RgbColor(red, green, blue);
    }

    private static String toCssRgba(RgbColor color, double alpha) {
        double clampedAlpha = Math.max(0.0, Math.min(1.0, alpha));
        return "rgba(" + color.red() + ", " + color.green() + ", " + color.blue() + ", " + clampedAlpha + ")";
    }

    private static String colorBarStyle(String colorHex) {
        RgbColor parsed = parseHexColor(colorHex);
        if (parsed == null) {
            return "-fx-background-color: rgba(63, 90, 132, 0.70); -fx-background-radius: 2;";
        }
        return "-fx-background-color: " + toCssRgba(parsed, 0.95) + "; -fx-background-radius: 2;";
    }

    private record RgbColor(int red, int green, int blue) {
    }

    private static String contrastTextColor(RgbColor color) {
        double red = color.red() / 255.0;
        double green = color.green() / 255.0;
        double blue = color.blue() / 255.0;
        double luminance = (0.2126 * red) + (0.7152 * green) + (0.0722 * blue);
        return luminance >= 0.58 ? "#112542" : "#f9fbff";
    }

    private static RgbColor blend(RgbColor source, RgbColor target, double ratio) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        int red = (int) Math.round((source.red() * (1 - clamped)) + (target.red() * clamped));
        int green = (int) Math.round((source.green() * (1 - clamped)) + (target.green() * clamped));
        int blue = (int) Math.round((source.blue() * (1 - clamped)) + (target.blue() * clamped));
        return new RgbColor(red, green, blue);
    }

    private static String toCssRgba(RgbColor color, double alpha) {
        double clampedAlpha = Math.max(0.0, Math.min(1.0, alpha));
        return "rgba(" + color.red() + ", " + color.green() + ", " + color.blue() + ", " + clampedAlpha + ")";
    }

    private record RgbColor(int red, int green, int blue) {
    }

    private static String formatDateTime(LocalDateTime value) {
        return UtcDateTimeDisplayFormatter.formatUtcToLocal(value, TASK_ACTIVITY_TIMESTAMP_FORMAT);
    }

    private static Optional<CaseTaskService.AssignableUserOption> showAssignUserPicker(
            Window owner,
            List<CaseTaskService.AssignableUserOption> candidates) {
        return AssignedUserPickerDialog.show(owner, candidates, TaskDetailDialog.class);
    }

    public record TaskDetailModel(
            long taskId,
            long caseId,
            String caseName,
            String caseResponsibleAttorney,
            String caseResponsibleAttorneyColor,
            Boolean caseNonEngagementLetterSent,
            String title,
            String description,
            LocalDateTime dueAt,
            Integer statusId,
            Integer priorityId,
            String createdByDisplayName,
            List<AssignedTeamMember> assignedTeamMembers,
            List<TaskActivityEntry> activityEntries,
            List<TaskNoteEntry> noteEntries,
            boolean completed) {
    }

    public record TaskActivityEntry(
            String title,
            String body,
            String actorDisplayName,
            LocalDateTime occurredAt) {
    }
    public record TaskNoteEntry(
            long id,
            int userId,
            String userDisplayName,
            String body,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean editable) {
    }

    public record AssignedTeamMember(
            int userId,
            String displayName,
            String colorCss) {
    }

    public record SaveTaskPayload(
            String title,
            String description,
            LocalDateTime dueAt,
            Integer statusId,
            Integer priorityId,
            boolean completed) {
    }

    public record CoreTaskHydration(
            com.shale.core.dto.TaskDetailDto detail,
            List<TaskStatusOptionDto> statuses,
            List<TaskPriorityOptionDto> priorities) {
    }

    public record TaskDetailResult(TaskDetailAction action, SaveTaskPayload payload) {
        static TaskDetailResult delete() {
            return new TaskDetailResult(TaskDetailAction.DELETE, null);
        }

        static TaskDetailResult save(SaveTaskPayload payload) {
            return new TaskDetailResult(TaskDetailAction.SAVE, payload);
        }
    }

    public enum TaskDetailAction {
        SAVE, DELETE
    }

    private static final class ResultHolder {
        private TaskDetailResult value;
    }

    public interface AssignmentEditor {
        List<AssignedTeamMember> addAndReload(int userId);
        List<AssignedTeamMember> removeAndReload(int userId);
    }
    public interface NotesEditor {
        List<TaskNoteEntry> addAndReload(String body);
        List<TaskNoteEntry> editAndReload(long noteId, String body);
    }

    private static final class PriorityListCell extends javafx.scene.control.ListCell<TaskPriorityOptionDto> {
        private final boolean showColorBar;
        private final Region colorBar = new Region();

        private PriorityListCell(boolean showColorBar) {
            this.showColorBar = showColorBar;
            colorBar.setMinWidth(4);
            colorBar.setPrefWidth(4);
            colorBar.setMaxWidth(4);
            colorBar.setMinHeight(14);
            colorBar.setPrefHeight(14);
        }

        @Override
        protected void updateItem(TaskPriorityOptionDto item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(item.name());
            if (showColorBar) {
                colorBar.setStyle(colorBarStyle(item.colorHex()));
                setGraphic(colorBar);
                setGraphicTextGap(8);
            } else {
                setGraphic(null);
            }
        }
    }

    private static final class StatusListCell extends javafx.scene.control.ListCell<TaskStatusOptionDto> {
        private final boolean showColorBar;
        private final Region colorBar = new Region();

        private StatusListCell(boolean showColorBar) {
            this.showColorBar = showColorBar;
            colorBar.setMinWidth(4);
            colorBar.setPrefWidth(4);
            colorBar.setMaxWidth(4);
            colorBar.setMinHeight(14);
            colorBar.setPrefHeight(14);
        }

        @Override
        protected void updateItem(TaskStatusOptionDto item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(item.name());
            if (showColorBar) {
                colorBar.setStyle(colorBarStyle(item.colorHex()));
                setGraphic(colorBar);
                setGraphicTextGap(8);
            } else {
                setGraphic(null);
            }
        }
    }

    private static final DateTimeFormatter TASK_ACTIVITY_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

}
