package com.shale.ui.component.dialog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

public final class TaskDetailDialog {

    private TaskDetailDialog() {
    }

    public static Optional<TaskDetailResult> showAndWait(
            Window owner,
            TaskDetailModel model,
            List<TaskPriorityOptionDto> priorities,
            Function<Long, List<CaseTaskService.AssignableUserOption>> loadAssignableUsersForTask,
            AssignmentAdder assignmentAdder,
            Consumer<Integer> onOpenCase) {
        Stage stage = AppDialogs.createModalStage(owner, "Task Details");

        ResultHolder result = new ResultHolder();

        Label heading = new Label("Task details");
        heading.getStyleClass().add("app-dialog-title");
        Label message = new Label("Update task fields, assigned team, completion, or delete the task.");
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
        Label assignedTeamLabel = new Label("Assigned team");
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
        renderAssignedTeam(assignedTeamList, assignedTeamCardFactory, initialAssignedTeamMembers);
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
                List<AssignedTeamMember> refreshed = assignmentAdder == null
                        ? List.of()
                        : assignmentAdder.addAndReload(user.id());
                renderAssignedTeam(assignedTeamList, assignedTeamCardFactory, refreshed);
            } catch (Exception ex) {
                showError(errorLabel, "Failed to add assigned user. " + rootCauseMessage(ex));
            }
        });
        assignedTeamSection.getChildren().setAll(assignedTeamHeader, assignedTeamList);

        VBox content = new VBox(8,
                createdByLabel,
                new Label("Title"), titleField,
                new Label("Description"), descriptionArea,
                relatedCaseSection,
                new Label("Priority"), priorityCombo,
                new Label("Due date/time"), dueRow,
                assignedTeamSection,
                completedCheck,
                errorLabel);
        content.setPadding(new Insets(8, 2, 4, 2));

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
        VBox root = new VBox(16, windowHeader, heading, message, content, actions);
        root.getStyleClass().add("app-dialog-root");
        root.setPadding(new Insets(22, 24, 22, 24));
        root.setMinWidth(500);

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
            List<AssignedTeamMember> members) {
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
                    new UserCardModel(null, safe(member.displayName()), member.colorCss(), null),
                    UserCardFactory.Variant.MINI);
            card.setMouseTransparent(true);
            assignedTeamList.getChildren().add(card);
        }
    }

    private static Optional<CaseTaskService.AssignableUserOption> showAssignUserPicker(
            Window owner,
            List<CaseTaskService.AssignableUserOption> candidates) {
        Stage stage = AppDialogs.createModalStage(owner, "Add Assigned User");
        Label heading = new Label("Add to assigned team");
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
            boolean completed) {
    }

    public record AssignedTeamMember(
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
    @FunctionalInterface
    public interface AssignmentAdder {
        List<AssignedTeamMember> addAndReload(int userId);
    }

    private static final class PriorityListCell extends javafx.scene.control.ListCell<TaskPriorityOptionDto> {
        @Override
        protected void updateItem(TaskPriorityOptionDto item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.name());
        }
    }

}
