package com.shale.ui.component.dialog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.util.UtcDateTimeDisplayFormatter;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
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
            Window owner,
            TaskDetailModel model,
            List<TaskPriorityOptionDto> priorities,
            Function<Long, List<CaseTaskService.AssignableUserOption>> loadAssignableUsersForTask,
            AssignmentEditor assignmentEditor,
            NotesEditor notesEditor,
            Consumer<Integer> onOpenCase) {
        Stage stage = AppDialogs.createModalStage(owner, "Task Details");

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

        ComboBox<TaskPriorityOptionDto> priorityCombo = new ComboBox<>();
        priorityCombo.setMaxWidth(Double.MAX_VALUE);
        List<TaskPriorityOptionDto> safePriorities = priorities == null ? List.of() : priorities;
        priorityCombo.getItems().setAll(safePriorities);
        priorityCombo.setCellFactory(cb -> new PriorityListCell());
        priorityCombo.setButtonCell(new PriorityListCell());
        selectPriority(priorityCombo, safePriorities, model.priorityId());

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
            CaseCardFactory caseCardFactory = new CaseCardFactory(onOpenCase);
            var caseCard = caseCardFactory.create(
                    new CaseCardModel(
                            model.caseId(),
                            relatedCaseName,
                            null,
                            null,
                            model.caseResponsibleAttorney(),
                            model.caseResponsibleAttorneyColor()),
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

        UserCardFactory assignedTeamCardFactory = new UserCardFactory(id -> {
        });
        VBox assignedTeamList = new VBox(6);
        List<AssignedTeamMember> initialAssignedTeamMembers = model.assignedTeamMembers() == null
                ? List.of()
                : model.assignedTeamMembers();
        @SuppressWarnings("unchecked")
        Consumer<Integer>[] removeAssignedUserRef = new Consumer[1];
        removeAssignedUserRef[0] = userId -> {
            try {
                List<AssignedTeamMember> refreshed = assignmentEditor == null
                        ? List.of()
                        : assignmentEditor.removeAndReload(userId);
                renderAssignedTeam(assignedTeamList, assignedTeamCardFactory, refreshed, removeAssignedUserRef[0]);
            } catch (Exception ex) {
                showError(errorLabel, "Failed to remove assigned user. " + rootCauseMessage(ex));
            }
        };
        renderAssignedTeam(assignedTeamList, assignedTeamCardFactory, initialAssignedTeamMembers, removeAssignedUserRef[0]);
        addAssignedUserButton.setOnAction(e -> {
            List<CaseTaskService.AssignableUserOption> candidates = loadAssignableUsersForTask == null
                    ? List.of()
                    : loadAssignableUsersForTask.apply(model.taskId());
            Optional<CaseTaskService.AssignableUserOption> selected = showAssignUserPicker(stage, candidates);
            if (selected.isEmpty()) {
                return;
            }
            CaseTaskService.AssignableUserOption user = selected.get();
            try {
                List<AssignedTeamMember> refreshed = assignmentEditor == null
                        ? List.of()
                        : assignmentEditor.addAndReload(user.id());
                renderAssignedTeam(assignedTeamList, assignedTeamCardFactory, refreshed, removeAssignedUserRef[0]);
            } catch (Exception ex) {
                showError(errorLabel, "Failed to add assigned user. " + rootCauseMessage(ex));
            }
        });
        assignedTeamSection.getChildren().setAll(assignedTeamHeader, assignedTeamList);

        VBox formContent = new VBox(8,
                createdByLabel,
                new Label("Title"), titleField,
                new Label("Description"), descriptionArea,
                relatedCaseSection,
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
        renderActivityItems(activityList, model.activityEntries());
        ScrollPane activityScrollPane = new ScrollPane(activityList);
        activityScrollPane.setFitToWidth(true);
        activityScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        activityScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        activityScrollPane.setPrefViewportHeight(420);
        VBox.setVgrow(activityScrollPane, Priority.ALWAYS);

        VBox activityPanel = new VBox(6, activityLabel, activityScrollPane);
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
        List<TaskNoteEntry> noteEntries = model.noteEntries() == null ? List.of() : model.noteEntries();
        renderNoteEntries(notesList, noteEntries, notesEditor, notesErrorLabel);
        ScrollPane notesScrollPane = new ScrollPane(notesList);
        notesScrollPane.setFitToWidth(true);
        notesScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        notesScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        notesScrollPane.setPrefViewportHeight(420);
        VBox.setVgrow(notesScrollPane, Priority.ALWAYS);
        addNoteButton.setOnAction(e -> {
            String body = safe(noteComposer.getText()).trim();
            if (body.isBlank()) {
                showError(notesErrorLabel, "Note text is required.");
                return;
            }
            try {
                List<TaskNoteEntry> refreshed = notesEditor == null ? noteEntries : notesEditor.addAndReload(body);
                renderNoteEntries(notesList, refreshed, notesEditor, notesErrorLabel);
                noteComposer.clear();
                notesErrorLabel.setManaged(false);
                notesErrorLabel.setVisible(false);
            } catch (Exception ex) {
                showError(notesErrorLabel, "Failed to add note. " + rootCauseMessage(ex));
            }
        });
        notesPanel.getChildren().setAll(notesLabel, noteComposer, addNoteButton, notesErrorLabel, notesScrollPane);
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
        cancelButton.setOnAction(e -> stage.close());

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(e -> {
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
            result.value = TaskDetailResult.save(new SaveTaskPayload(
                    title,
                    descriptionArea.getText(),
                    dueAt,
                    priorityId,
                    completedCheck.isSelected()));
            stage.close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, deleteButton, spacer, cancelButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox windowHeader = AppDialogs.createSecondaryWindowHeader(stage, "Task Details", stage::close);
        VBox root = new VBox(16, windowHeader, heading, message, contentColumns, actions);
        root.getStyleClass().add("app-dialog-root");
        root.setPadding(new Insets(22, 24, 22, 24));
        root.setMinWidth(860);
        root.setPrefWidth(980);
        root.setMinHeight(620);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(
                TaskDetailDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
        return Optional.ofNullable(result.value);
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
            card.setMouseTransparent(true);
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
            Label notesErrorLabel) {
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
                        String updatedText = safe(editArea.getText()).trim();
                        if (updatedText.isBlank()) {
                            showError(notesErrorLabel, "Note text is required.");
                            return;
                        }
                        try {
                            List<TaskNoteEntry> refreshed = notesEditor == null
                                    ? safeEntries
                                    : notesEditor.editAndReload(entry.id(), updatedText);
                            renderNoteEntries(notesList, refreshed, notesEditor, notesErrorLabel);
                            notesErrorLabel.setManaged(false);
                            notesErrorLabel.setVisible(false);
                        } catch (Exception ex) {
                            showError(notesErrorLabel, "Failed to update note. " + rootCauseMessage(ex));
                        }
                    });
                    cancelButton.setOnAction(cancelEvent -> renderNoteEntries(notesList, safeEntries, notesEditor, notesErrorLabel));
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
        Stage stage = AppDialogs.createModalStage(owner, "Add Assigned User");
        Label heading = new Label("Add to assigned");
        heading.getStyleClass().add("app-dialog-title");

        VBox list = new VBox(8);
        UserCardFactory cardFactory = new UserCardFactory(id -> {
        });
        List<CaseTaskService.AssignableUserOption> safeCandidates = candidates == null ? List.of() : candidates;
        ResultHolderAssignable holder = new ResultHolderAssignable();
        if (safeCandidates.isEmpty()) {
            Label emptyLabel = new Label("No additional users available");
            emptyLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.70);");
            list.getChildren().add(emptyLabel);
        } else {
            for (CaseTaskService.AssignableUserOption user : safeCandidates) {
                if (user == null || user.id() <= 0) {
                    continue;
                }
                var card = cardFactory.create(
                        new UserCardModel(user.id(), safe(user.displayName()), user.color(), null),
                        UserCardFactory.Variant.MINI);
                Button cardButton = new Button();
                cardButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
                cardButton.setMaxWidth(Double.MAX_VALUE);
                cardButton.setGraphic(card);
                cardButton.setOnAction(e -> {
                    holder.value = user;
                    stage.close();
                });
                list.getChildren().add(cardButton);
            }
        }
        Button closeButton = new Button("Close");
        closeButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        closeButton.setOnAction(e -> stage.close());

        VBox root = new VBox(12, heading, list, closeButton);
        root.getStyleClass().add("app-dialog-root");
        root.setPadding(new Insets(18));
        root.setMinWidth(380);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(
                TaskDetailDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
        return Optional.ofNullable(holder.value);
    }

    public record TaskDetailModel(
            long taskId,
            long caseId,
            String caseName,
            String caseResponsibleAttorney,
            String caseResponsibleAttorneyColor,
            String title,
            String description,
            LocalDateTime dueAt,
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
            Integer priorityId,
            boolean completed) {
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
    private static final class ResultHolderAssignable {
        private CaseTaskService.AssignableUserOption value;
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

    private static final DateTimeFormatter TASK_ACTIVITY_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

}
