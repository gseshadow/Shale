package com.shale.ui.component.dialog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.services.CaseTaskService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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

public final class NewTaskDialog {

    private static final int MEDIUM_PRIORITY_ID = 2;

    private NewTaskDialog() {
    }

    public static Optional<CreateTaskInput> showAndWait(
            Window owner,
            List<TaskPriorityOptionDto> availablePriorities,
            List<CaseTaskService.AssignableUserOption> availableAssignees) {
        Stage stage = AppDialogs.createModalStage(owner, "New Task");

        ResultHolder result = new ResultHolder();

        Label heading = new Label("Create task");
        heading.getStyleClass().add("app-dialog-title");

        Label message = new Label("Title is required. Description and due date are optional.");
        message.getStyleClass().add("app-dialog-message");

        Label titleLabel = new Label("Title");
        TextField titleField = new TextField();
        titleField.setPromptText("Task title");

        Label descriptionLabel = new Label("Description");
        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("Optional");
        descriptionArea.setPrefRowCount(4);
        descriptionArea.setWrapText(true);

        Label dueLabel = new Label("Due date/time");
        DatePicker dueDatePicker = new DatePicker();
        dueDatePicker.setPromptText("Optional");
        TextField dueTimeField = new TextField();
        dueTimeField.setPromptText("HH:mm (optional)");
        dueTimeField.setPrefColumnCount(8);
        HBox dueRow = new HBox(8, dueDatePicker, dueTimeField);

        Label priorityLabel = new Label("Priority");
        ComboBox<TaskPriorityOptionDto> priorityComboBox = new ComboBox<>();
        priorityComboBox.setMaxWidth(Double.MAX_VALUE);
        priorityComboBox.setPromptText("Select priority");
        List<TaskPriorityOptionDto> safePriorities = availablePriorities == null ? List.of() : availablePriorities;
        priorityComboBox.getItems().setAll(safePriorities);
        priorityComboBox.setCellFactory(cb -> new PriorityListCell());
        priorityComboBox.setButtonCell(new PriorityListCell());
        selectDefaultPriority(priorityComboBox, safePriorities);

        Label assigneeLabel = new Label("Assignee");
        ComboBox<AssigneeChoice> assigneeComboBox = new ComboBox<>();
        assigneeComboBox.setMaxWidth(Double.MAX_VALUE);
        assigneeComboBox.setPromptText("Unassigned");
        UserCardFactory userCardFactory = new UserCardFactory(id -> {
        });
        assigneeComboBox.setCellFactory(cb -> new AssigneeChoiceListCell(userCardFactory));
        assigneeComboBox.setButtonCell(new AssigneeChoiceButtonCell());
        List<CaseTaskService.AssignableUserOption> safeAssignees = availableAssignees == null ? List.of() : availableAssignees;
        assigneeComboBox.getItems().add(AssigneeChoice.unassigned());
        for (CaseTaskService.AssignableUserOption assignee : safeAssignees) {
            if (assignee == null || assignee.id() <= 0) {
                continue;
            }
            assigneeComboBox.getItems().add(new AssigneeChoice(assignee.id(), assignee.displayName(), assignee.color()));
        }
        assigneeComboBox.setValue(AssigneeChoice.unassigned());

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #b42318;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        VBox content = new VBox(8,
                titleLabel,
                titleField,
                descriptionLabel,
                descriptionArea,
                priorityLabel,
                priorityComboBox,
                assigneeLabel,
                assigneeComboBox,
                dueLabel,
                dueRow,
                errorLabel);
        content.setPadding(new Insets(6, 2, 2, 2));

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());

        Button createButton = new Button("Create Task");
        createButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
        createButton.setDefaultButton(true);
        createButton.setOnAction(e -> {
            String title = titleField.getText() == null ? "" : titleField.getText().trim();
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

            Integer selectedPriorityId = Optional.ofNullable(priorityComboBox.getValue())
                    .map(TaskPriorityOptionDto::id)
                    .orElse(null);
            Integer selectedAssigneeId = Optional.ofNullable(assigneeComboBox.getValue())
                    .map(AssigneeChoice::userId)
                    .orElse(null);
            result.value = new CreateTaskInput(
                    title,
                    descriptionArea.getText(),
                    dueAt,
                    selectedPriorityId,
                    selectedAssigneeId);
            stage.close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, spacer, cancelButton, createButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox windowHeader = AppDialogs.createSecondaryWindowHeader(stage, "New Task", stage::close);
        VBox root = new VBox(16, windowHeader, heading, message, content, actions);
        root.getStyleClass().add("app-dialog-root");
        root.setPadding(new Insets(22, 24, 22, 24));
        root.setMinWidth(460);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(
                NewTaskDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
        return Optional.ofNullable(result.value);
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

    private static void selectDefaultPriority(ComboBox<TaskPriorityOptionDto> priorityComboBox,
            List<TaskPriorityOptionDto> priorities) {
        if (priorities == null || priorities.isEmpty()) {
            return;
        }
        TaskPriorityOptionDto preferred = priorities.stream()
                .filter(p -> p.id() == MEDIUM_PRIORITY_ID)
                .findFirst()
                .orElse(priorities.get(0));
        priorityComboBox.setValue(preferred);
    }

    public record CreateTaskInput(
            String title,
            String description,
            LocalDateTime dueAt,
            Integer priorityId,
            Integer assigneeUserId) {
    }

    private static final class ResultHolder {
        private CreateTaskInput value;
    }

    private static final class PriorityListCell extends javafx.scene.control.ListCell<TaskPriorityOptionDto> {
        @Override
        protected void updateItem(TaskPriorityOptionDto item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.name());
        }
    }

    private record AssigneeChoice(Integer userId, String displayName, String colorCss) {
        static AssigneeChoice unassigned() {
            return new AssigneeChoice(null, "Unassigned", null);
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
