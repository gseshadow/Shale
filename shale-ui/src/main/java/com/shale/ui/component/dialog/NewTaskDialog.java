package com.shale.ui.component.dialog;

import com.shale.ui.component.EnhancedTextArea;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.util.WindowSizingUtil;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
        ControlStyles.formControl(titleField);
        titleField.setPromptText("Task title");

        Label descriptionLabel = new Label("Description");
        EnhancedTextArea descriptionArea = new EnhancedTextArea();
        descriptionArea.setPromptText("Optional");
        descriptionArea.setPrefRowCount(4);
        descriptionArea.setEditorTitle("Task Description");

        Label dueLabel = new Label("Due date/time");
        DatePicker dueDatePicker = new DatePicker();
        ControlStyles.formControl(dueDatePicker);
        dueDatePicker.setPromptText("Optional");
        TextField dueTimeField = new TextField();
        ControlStyles.formControl(dueTimeField);
        dueTimeField.setPromptText("HH:mm (optional)");
        dueTimeField.setPrefColumnCount(8);
        HBox dueRow = new HBox(8, dueDatePicker, dueTimeField);

        Label priorityLabel = new Label("Priority");
        ComboBox<TaskPriorityOptionDto> priorityComboBox = new ComboBox<>();
        ControlStyles.formControl(priorityComboBox);
        priorityComboBox.setMaxWidth(Double.MAX_VALUE);
        priorityComboBox.setPromptText("Select priority");
        List<TaskPriorityOptionDto> safePriorities = availablePriorities == null ? List.of() : availablePriorities;
        priorityComboBox.getItems().setAll(safePriorities);
        priorityComboBox.setCellFactory(cb -> new PriorityListCell());
        priorityComboBox.setButtonCell(new PriorityListCell());
        selectDefaultPriority(priorityComboBox, safePriorities);

        List<CaseTaskService.AssignableUserOption> safeAssignees = availableAssignees == null ? List.of() : availableAssignees;
        VBox assignedSection = new VBox(6);
        Label assignedLabel = new Label("Assigned");
        assignedLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: rgba(17,37,66,0.62);");
        Button addAssignedButton = new Button("Add");
        ControlStyles.apply(addAssignedButton, ControlStyles.Purpose.SECONDARY);
        Region assignedSpacer = new Region();
        HBox.setHgrow(assignedSpacer, Priority.ALWAYS);
        HBox assignedHeader = new HBox(8, assignedLabel, assignedSpacer, addAssignedButton);
        assignedHeader.setAlignment(Pos.CENTER_LEFT);
        VBox assignedList = new VBox(6);
        ScrollPane assignedScrollPane = new ScrollPane(assignedList);
        assignedScrollPane.setFitToWidth(true);
        assignedScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        assignedScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        assignedScrollPane.setMinHeight(96);
        assignedScrollPane.setPrefHeight(168);
        assignedScrollPane.setMaxHeight(220);
        assignedScrollPane.getStyleClass().add("transparent-scroll");
        java.util.LinkedHashMap<Integer, CaseTaskService.AssignableUserOption> selectedAssignedUsers = new java.util.LinkedHashMap<>();
        UserCardFactory assignedUserFactory = new UserCardFactory(id -> {
        });
        @SuppressWarnings("unchecked")
        Consumer<Integer>[] removeAssignedRef = new Consumer[1];
        removeAssignedRef[0] = userId -> {
            selectedAssignedUsers.remove(userId);
            renderAssignedUsers(assignedList, assignedUserFactory, selectedAssignedUsers.values().stream().toList(), removeAssignedRef[0]);
        };
        renderAssignedUsers(assignedList, assignedUserFactory, List.of(), removeAssignedRef[0]);
        addAssignedButton.setOnAction(e -> {
            List<CaseTaskService.AssignableUserOption> candidates = safeAssignees.stream()
                    .filter(user -> user != null && user.id() > 0 && !selectedAssignedUsers.containsKey(user.id()))
                    .toList();
            Optional<CaseTaskService.AssignableUserOption> selected = showAssignUserPicker(stage, candidates);
            if (selected.isEmpty()) {
                return;
            }
            CaseTaskService.AssignableUserOption user = selected.get();
            selectedAssignedUsers.put(user.id(), user);
            renderAssignedUsers(assignedList, assignedUserFactory, selectedAssignedUsers.values().stream().toList(), removeAssignedRef[0]);
        });
        assignedSection.getChildren().setAll(assignedHeader, assignedScrollPane);

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
                assignedSection,
                dueLabel,
                dueRow,
                errorLabel);
        content.setPadding(new Insets(6, 24, 2, 24));
        content.setMinHeight(Region.USE_PREF_SIZE);

        ScrollPane contentScrollPane = new ScrollPane(content);
        contentScrollPane.setFitToWidth(true);
        contentScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contentScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        contentScrollPane.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(contentScrollPane, Priority.ALWAYS);

        Button cancelButton = new Button("Cancel");
        ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY);
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());

        Button createButton = new Button("Create Task");
        ControlStyles.apply(createButton, ControlStyles.Purpose.PRIMARY);
        createButton.setDefaultButton(true);
        createButton.setOnAction(e -> {
            String title = titleField.getText() == null ? "" : titleField.getText().trim();
            if (title.isBlank()) {
                ControlStyles.setInvalid(titleField, true);
                showError(errorLabel, "Title is required.");
                return;
            }

            LocalDateTime dueAt;
            try {
                dueAt = parseDueAt(dueDatePicker.getValue(), dueTimeField.getText());
            } catch (DateTimeParseException ex) {
                ControlStyles.setInvalid(dueTimeField, true);
                showError(errorLabel, "Invalid time. Use HH:mm (example: 14:30).");
                return;
            }

            Integer selectedPriorityId = Optional.ofNullable(priorityComboBox.getValue())
                    .map(TaskPriorityOptionDto::id)
                    .orElse(null);
            result.value = new CreateTaskInput(
                    title,
                    descriptionArea.getText(),
                    dueAt,
                    selectedPriorityId,
                    selectedAssignedUsers.values().stream().map(CaseTaskService.AssignableUserOption::id).toList());
            stage.close();
        });
        titleField.textProperty().addListener((obs, oldText, newText) ->
                ControlStyles.setInvalid(titleField, newText == null || newText.trim().isBlank()));
        dueTimeField.textProperty().addListener((obs, oldText, newText) ->
                ControlStyles.setInvalid(dueTimeField, !isValidDueTime(dueDatePicker.getValue(), newText)));
        dueDatePicker.valueProperty().addListener((obs, oldDate, newDate) ->
                ControlStyles.setInvalid(dueTimeField, !isValidDueTime(newDate, dueTimeField.getText())));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, spacer, cancelButton, createButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().addAll("app-dialog-action-bar", "new-task-dialog-action-bar");
        actions.setMaxWidth(Double.MAX_VALUE);

        VBox headerContent = new VBox(8, heading, message);
        headerContent.setPadding(new Insets(22, 24, 0, 24));
        VBox body = new VBox(14, headerContent, contentScrollPane, actions);
        body.setPadding(Insets.EMPTY);
        VBox.setVgrow(contentScrollPane, Priority.ALWAYS);
        VBox root = AppDialogs.createSecondaryWindowShell(stage, "New Task", stage::close, body);
        VBox.setVgrow(body, Priority.ALWAYS);
        double dialogWidth = WindowSizingUtil.cappedModalWidth(owner, 560);
        double dialogHeight = WindowSizingUtil.cappedModalHeight(owner, 680);
        double minWidth = Math.min(460, dialogWidth);
        double minHeight = Math.min(420, dialogHeight);
        root.setMinWidth(minWidth);
        root.setPrefWidth(dialogWidth);
        root.setMinHeight(minHeight);
        root.setPrefHeight(dialogHeight);
        stage.setResizable(true);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(
                NewTaskDialog.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        WindowSizingUtil.sizeModalStage(stage, owner, dialogWidth, dialogHeight, minWidth, minHeight);
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

    private static boolean isValidDueTime(LocalDate dueDate, String dueTimeRaw) {
        try {
            parseDueAt(dueDate, dueTimeRaw);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
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
            List<Integer> assignedUserIds) {
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

    private static void renderAssignedUsers(
            VBox assignedList,
            UserCardFactory cardFactory,
            List<CaseTaskService.AssignableUserOption> users,
            Consumer<Integer> onRemove) {
        assignedList.getChildren().clear();
        if (users == null || users.isEmpty()) {
            Label emptyLabel = new Label("No users assigned");
            emptyLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.70);");
            assignedList.getChildren().add(emptyLabel);
            return;
        }
        for (CaseTaskService.AssignableUserOption user : users) {
            if (user == null || user.id() <= 0) {
                continue;
            }
            var card = cardFactory.create(
                    new UserCardModel(user.id(), user.displayName(), user.color(), null),
                    UserCardFactory.Variant.MINI);
            card.setMouseTransparent(true);
            Button removeButton = new Button("Remove");
            ControlStyles.apply(removeButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
            int userId = user.id();
            removeButton.setOnAction(e -> onRemove.accept(userId));
            HBox row = new HBox(8, card, removeButton);
            row.setAlignment(Pos.CENTER_LEFT);
            assignedList.getChildren().add(row);
        }
    }

    private static Optional<CaseTaskService.AssignableUserOption> showAssignUserPicker(
            Window owner,
            List<CaseTaskService.AssignableUserOption> candidates) {
        return AssignedUserPickerDialog.show(owner, candidates, NewTaskDialog.class);
    }
}
