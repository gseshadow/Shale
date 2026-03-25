package com.shale.ui.component.dialog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

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
import javafx.scene.control.ContentDisplay;
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
            List<CaseTaskService.AssignableUserOption> assignees,
            Consumer<Integer> onOpenCase) {
        Stage stage = AppDialogs.createModalStage(owner, "Task Details");

        ResultHolder result = new ResultHolder();

        Label heading = new Label("Task details");
        heading.getStyleClass().add("app-dialog-title");
        Label message = new Label("Update task fields, assignee, completion, or delete the task.");
        message.getStyleClass().add("app-dialog-message");

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

        ComboBox<AssigneeChoice> assigneeCombo = new ComboBox<>();
        assigneeCombo.setMaxWidth(Double.MAX_VALUE);
        UserCardFactory userCardFactory = new UserCardFactory(id -> {
        });
        assigneeCombo.setCellFactory(cb -> new AssigneeChoiceListCell(userCardFactory));
        assigneeCombo.setButtonCell(new AssigneeChoiceButtonCell());
        assigneeCombo.getItems().add(AssigneeChoice.unassigned());
        List<CaseTaskService.AssignableUserOption> safeAssignees = assignees == null ? List.of() : assignees;
        for (CaseTaskService.AssignableUserOption assignee : safeAssignees) {
            if (assignee == null || assignee.id() <= 0) {
                continue;
            }
            assigneeCombo.getItems().add(new AssigneeChoice(assignee.id(), assignee.displayName(), assignee.color()));
        }
        selectAssignee(assigneeCombo, model.assignedUserId());

        CheckBox completedCheck = new CheckBox("Completed");
        completedCheck.setSelected(model.completed());

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #b42318;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        VBox relatedCaseSection = new VBox(4);
        String relatedCaseName = safe(model.caseName()).trim();
        if (model.caseId() > 0 && !relatedCaseName.isBlank()) {
            Label relatedCaseLabel = new Label("Related case");
            CaseCardFactory caseCardFactory = new CaseCardFactory(onOpenCase);
            var caseCard = caseCardFactory.create(
                    new CaseCardModel(model.caseId(), relatedCaseName, null, null, null, null),
                    CaseCardFactory.Variant.MINI);
            relatedCaseSection.getChildren().setAll(relatedCaseLabel, caseCard);
        } else {
            relatedCaseSection.setManaged(false);
            relatedCaseSection.setVisible(false);
        }

        VBox content = new VBox(8,
                new Label("Title"), titleField,
                new Label("Description"), descriptionArea,
                relatedCaseSection,
                new Label("Priority"), priorityCombo,
                new Label("Assignee"), assigneeCombo,
                new Label("Due date/time"), dueRow,
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
            Integer assigneeUserId = Optional.ofNullable(assigneeCombo.getValue()).map(AssigneeChoice::userId).orElse(null);
            result.value = TaskDetailResult.save(new SaveTaskPayload(
                    title,
                    descriptionArea.getText(),
                    dueAt,
                    priorityId,
                    assigneeUserId,
                    completedCheck.isSelected()));
            stage.close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, deleteButton, spacer, cancelButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(16, heading, message, content, actions);
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

    private static void selectAssignee(ComboBox<AssigneeChoice> assigneeCombo, Integer assignedUserId) {
        if (assignedUserId == null || assignedUserId <= 0) {
            assigneeCombo.setValue(AssigneeChoice.unassigned());
            return;
        }
        for (AssigneeChoice option : assigneeCombo.getItems()) {
            if (option != null && option.userId() != null && option.userId() == assignedUserId) {
                assigneeCombo.setValue(option);
                return;
            }
        }
        assigneeCombo.setValue(AssigneeChoice.unassigned());
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

    public record TaskDetailModel(
            long taskId,
            long caseId,
            String caseName,
            String title,
            String description,
            LocalDateTime dueAt,
            Integer priorityId,
            Integer assignedUserId,
            boolean completed) {
    }

    public record SaveTaskPayload(
            String title,
            String description,
            LocalDateTime dueAt,
            Integer priorityId,
            Integer assigneeUserId,
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

    private record AssigneeChoice(Integer userId, String displayName, String colorCss) {
        static AssigneeChoice unassigned() {
            return new AssigneeChoice(null, "Unassigned", null);
        }
    }

    private static final class PriorityListCell extends javafx.scene.control.ListCell<TaskPriorityOptionDto> {
        @Override
        protected void updateItem(TaskPriorityOptionDto item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.name());
        }
    }

    private static final class AssigneeChoiceListCell extends javafx.scene.control.ListCell<AssigneeChoice> {
        private final UserCardFactory userCardFactory;

        private AssigneeChoiceListCell(UserCardFactory userCardFactory) {
            this.userCardFactory = userCardFactory;
        }

        @Override
        protected void updateItem(AssigneeChoice item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            if (item.userId() == null) {
                setText("Unassigned");
                setGraphic(null);
                return;
            }
            String text = item.displayName();
            if (text == null || text.isBlank()) {
                text = "User #" + item.userId();
            }
            var card = userCardFactory.create(
                    new UserCardModel(item.userId(), text, item.colorCss(), null),
                    UserCardFactory.Variant.MINI);
            card.setMouseTransparent(true);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setText(null);
            setGraphic(card);
        }
    }

    private static final class AssigneeChoiceButtonCell extends javafx.scene.control.ListCell<AssigneeChoice> {
        @Override
        protected void updateItem(AssigneeChoice item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            if (item.userId() == null) {
                setText("Unassigned");
                setGraphic(null);
                return;
            }
            String text = item.displayName();
            if (text == null || text.isBlank()) {
                text = "User #" + item.userId();
            }
            setText(text);
            setGraphic(null);
        }
    }
}
