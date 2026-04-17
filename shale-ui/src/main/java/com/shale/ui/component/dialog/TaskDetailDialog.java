package com.shale.ui.component.dialog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
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
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
        List<TaskStatusOptionDto> safeStatuses = statuses == null ? List.of() : statuses;
        statusCombo.getItems().setAll(safeStatuses);
        statusCombo.setCellFactory(cb -> new StatusListCell());
        statusCombo.setButtonCell(new StatusListCell());
        selectStatus(statusCombo, safeStatuses, model.statusId());

        ComboBox<TaskPriorityOptionDto> priorityCombo = new ComboBox<>();
        priorityCombo.setMaxWidth(Double.MAX_VALUE);
        List<TaskPriorityOptionDto> safePriorities = priorities == null ? List.of() : priorities;
        priorityCombo.getItems().setAll(safePriorities);
        priorityCombo.setCellFactory(cb -> new PriorityListCell());
        priorityCombo.setButtonCell(new PriorityListCell());
        selectPriority(priorityCombo, safePriorities, model.priorityId());
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
        Button addAssignedUserButton = new Button("Add");
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
                new Label("Title"), titleField,
                new Label("Description"), descriptionArea,
                relatedCaseSection,
                new Label("Status"), statusCombo,
                new Label("Priority"), priorityCombo,
                new Label("Due date/time"), dueRow,
                assignedTeamSection,
                completedCheck,
                errorLabel);
        formContent.setPadding(new Insets(8, 2, 4, 2));
        HBox.setHgrow(formContent, Priority.ALWAYS);

        Label activityLabel = new Label("Activity");
        activityLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: rgba(17,37,66,0.62);");
        VBox activityList = new VBox(8);
        Label activityLoadingLabel = loadingLabel("Loading activity…");
        setVisibleManaged(activityLoadingLabel, false);
        renderActivityItems(activityList, model.activityEntries());
        ScrollPane activityScrollPane = new ScrollPane(activityList);
        activityScrollPane.setFitToWidth(true);
        activityScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        activityScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        activityScrollPane.setPrefViewportHeight(420);
        VBox.setVgrow(activityScrollPane, Priority.ALWAYS);

        VBox activityPanel = new VBox(6, activityLabel, activityLoadingLabel, activityScrollPane);
        activityPanel.setPrefWidth(320);
        activityPanel.setMinWidth(280);
        activityPanel.setMaxWidth(360);
        activityPanel.setPadding(new Insets(8, 2, 4, 8));
        VBox.setVgrow(activityPanel, Priority.ALWAYS);

        VBox notesPanel = new VBox(8);
        Label notesLabel = new Label("Notes");
        notesLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: rgba(17,37,66,0.62);");
        TextArea noteComposer = new TextArea();
        noteComposer.setPromptText("Add note...");
        noteComposer.setPrefRowCount(3);
        noteComposer.setWrapText(true);
        Button addNoteButton = new Button("Add Note");
        addNoteButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        Label notesErrorLabel = new Label();
        notesErrorLabel.setStyle("-fx-text-fill: #b42318;");
        notesErrorLabel.setVisible(false);
        notesErrorLabel.setManaged(false);
        VBox notesList = new VBox(8);
        Label notesLoadingLabel = loadingLabel("Loading notes…");
        setVisibleManaged(notesLoadingLabel, false);
        List<TaskNoteEntry> noteEntries = model.noteEntries() == null ? List.of() : model.noteEntries();
        renderNoteEntries(notesList, noteEntries, notesEditor, notesErrorLabel, busyMutationState, busyMutationUi);
        ScrollPane notesScrollPane = new ScrollPane(notesList);
        notesScrollPane.setFitToWidth(true);
        notesScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        notesScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        notesScrollPane.setPrefViewportHeight(420);
        VBox.setVgrow(notesScrollPane, Priority.ALWAYS);
        busyMutationUi.register(addNoteButton);
        busyMutationUi.register(noteComposer);
        busyMutationUi.register(notesScrollPane);
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
                    () -> notesEditor == null ? noteEntries : notesEditor.addAndReload(body),
                    refreshed -> {
                        renderNoteEntries(notesList, refreshed, notesEditor, notesErrorLabel, busyMutationState, busyMutationUi);
                        noteComposer.clear();
                        notesErrorLabel.setManaged(false);
                        notesErrorLabel.setVisible(false);
                    },
                    ex -> showError(notesErrorLabel, "Failed to add note. " + rootCauseMessage(ex)));
        });
        notesPanel.getChildren().setAll(notesLabel, noteComposer, addNoteButton, notesErrorLabel, notesLoadingLabel, notesScrollPane);
        notesPanel.setPrefWidth(320);
        notesPanel.setMinWidth(280);
        notesPanel.setMaxWidth(360);
        notesPanel.setPadding(new Insets(8, 2, 4, 8));
        VBox.setVgrow(notesPanel, Priority.ALWAYS);

        ToggleGroup rightRailToggle = new ToggleGroup();
        ToggleButton activityToggle = new ToggleButton("Activity");
        activityToggle.setToggleGroup(rightRailToggle);
        ToggleButton notesToggle = new ToggleButton("Notes");
        notesToggle.setToggleGroup(rightRailToggle);
        notesToggle.setSelected(true);
        HBox rightRailTabs = new HBox(6, activityToggle, notesToggle);

        StackPane rightRailBody = new StackPane(activityPanel, notesPanel);
        activityPanel.setVisible(false);
        activityPanel.setManaged(false);
        rightRailToggle.selectedToggleProperty().addListener((obs, oldToggle, selectedToggle) -> {
            boolean showNotes = selectedToggle == notesToggle;
            notesPanel.setVisible(showNotes);
            notesPanel.setManaged(showNotes);
            activityPanel.setVisible(!showNotes);
            activityPanel.setManaged(!showNotes);
        });

        VBox rightRail = new VBox(8, rightRailTabs, rightRailBody);
        rightRail.setPrefWidth(340);
        rightRail.setMinWidth(300);
        rightRail.setMaxWidth(380);
        VBox.setVgrow(rightRailBody, Priority.ALWAYS);

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
        updateSaveCancelAvailability(saveButton, cancelButton, noteComposer, saveBlockedByCoreState[0]);
        noteComposer.textProperty().addListener((obs, oldText, newText) -> updateSaveCancelAvailability(
                saveButton,
                cancelButton,
                noteComposer,
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
                            List<TaskPriorityOptionDto> hydratedPriorities = core.priorities() == null ? List.of() : core.priorities();
                            priorityCombo.getItems().setAll(hydratedPriorities);
                            selectPriority(priorityCombo, hydratedPriorities, detail.priorityId());
                            saveBlockedByCoreState[0] = hydratedStatuses.isEmpty() || hydratedPriorities.isEmpty();
                            updateSaveCancelAvailability(saveButton, cancelButton, noteComposer, saveBlockedByCoreState[0]);
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
                        setVisibleManaged(activityLoadingLabel, false);
                        renderActivityItems(activityList, entries);
                    },
                    ex -> {
                        setVisibleManaged(activityLoadingLabel, false);
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
                        setVisibleManaged(notesLoadingLabel, false);
                        renderNoteEntries(notesList, entries, notesEditor, notesErrorLabel, busyMutationState, busyMutationUi);
                        noteComposer.setDisable(false);
                        addNoteButton.setDisable(false);
                    },
                    ex -> {
                        setVisibleManaged(notesLoadingLabel, false);
                        noteComposer.setDisable(false);
                        addNoteButton.setDisable(false);
                        showError(notesErrorLabel, "Failed to load notes. " + rootCauseMessage(ex));
                    });
        });
        if (initialAssignedTeamMembers.isEmpty()) {
            setVisibleManaged(assignedLoadingLabel, true);
        }
        if ((model.activityEntries() == null || model.activityEntries().isEmpty())) {
            setVisibleManaged(activityLoadingLabel, true);
        }
        if (noteEntries.isEmpty()) {
            setVisibleManaged(notesLoadingLabel, true);
            noteComposer.setDisable(true);
            addNoteButton.setDisable(true);
        }
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
            boolean saveBlockedByCoreState) {
        boolean hasUncommittedText = hasUncommittedNoteText(noteComposer);
        if (saveButton != null) {
            saveButton.setDisable(saveBlockedByCoreState || hasUncommittedText);
        }
        if (cancelButton != null) {
            cancelButton.setDisable(hasUncommittedText);
        }
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

    private static void renderActivityItems(VBox activityList, List<TaskActivityEntry> entries) {
        activityList.getChildren().clear();
        List<TaskActivityEntry> safeEntries = entries == null ? List.of() : entries;
        if (safeEntries.isEmpty()) {
            Label emptyLabel = new Label("No activity yet.");
            emptyLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.70);");
            activityList.getChildren().add(emptyLabel);
            return;
        }

        for (TaskActivityEntry entry : safeEntries) {
            if (entry == null) {
                continue;
            }
            Label titleLabel = new Label(safe(entry.title()).trim().isBlank() ? "Activity event" : safe(entry.title()).trim());
            titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;");
            titleLabel.setWrapText(true);

            VBox cardContent = new VBox(4, titleLabel);
            String body = safe(entry.body()).trim();
            if (!body.isBlank()) {
                Label bodyLabel = new Label(body);
                bodyLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.88);");
                bodyLabel.setWrapText(true);
                cardContent.getChildren().add(bodyLabel);
            }

            String actor = safe(entry.actorDisplayName()).trim();
            if (actor.isBlank()) {
                actor = "System";
            }
            String metaText = actor + " · " + formatDateTime(entry.occurredAt());
            Label metaLabel = new Label(metaText);
            metaLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(17,37,66,0.70);");
            cardContent.getChildren().add(metaLabel);

            VBox card = new VBox(cardContent);
            card.setPadding(new Insets(10, 12, 10, 12));
            card.getStyleClass().add("secondary-panel");
            activityList.getChildren().add(card);
        }
    }

    private static void renderNoteEntries(
            VBox notesList,
            List<TaskNoteEntry> entries,
            NotesEditor notesEditor,
            Label notesErrorLabel,
            BusyMutationState busyMutationState,
            BusyMutationUi busyMutationUi) {
        notesList.getChildren().clear();
        List<TaskNoteEntry> safeEntries = entries == null ? List.of() : entries;
        if (safeEntries.isEmpty()) {
            Label empty = new Label("No notes yet.");
            empty.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.70);");
            notesList.getChildren().add(empty);
            return;
        }

        for (TaskNoteEntry entry : safeEntries) {
            if (entry == null) {
                continue;
            }
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
                                () -> notesEditor == null ? safeEntries : notesEditor.editAndReload(entry.id(), updatedText),
                                refreshed -> {
                                    renderNoteEntries(notesList, refreshed, notesEditor, notesErrorLabel, busyMutationState, busyMutationUi);
                                    notesErrorLabel.setManaged(false);
                                    notesErrorLabel.setVisible(false);
                                },
                                ex -> showError(notesErrorLabel, "Failed to update note. " + rootCauseMessage(ex)));
                    });
                    cancelButton.setOnAction(cancelEvent -> renderNoteEntries(notesList, safeEntries, notesEditor, notesErrorLabel, busyMutationState, busyMutationUi));
                });
                HBox actionRow = new HBox(6, editButton);
                cardContent.getChildren().add(actionRow);
            }

            VBox card = new VBox(cardContent);
            card.setPadding(new Insets(10, 12, 10, 12));
            card.getStyleClass().add("secondary-panel");
            notesList.getChildren().add(card);
        }
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
        @Override
        protected void updateItem(TaskPriorityOptionDto item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.name());
        }
    }

    private static final class StatusListCell extends javafx.scene.control.ListCell<TaskStatusOptionDto> {
        @Override
        protected void updateItem(TaskStatusOptionDto item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.name());
        }
    }

    private static final DateTimeFormatter TASK_ACTIVITY_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

}
