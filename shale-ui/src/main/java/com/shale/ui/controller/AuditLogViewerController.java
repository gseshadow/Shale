package com.shale.ui.controller;

import com.shale.data.dao.AuditLogDao;
import com.shale.data.dao.UserDao;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.state.AppState;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AuditLogViewerController {

    @FXML
    private TextField userIdFilterField;
    @FXML
    private TextField objectTypeIdFilterField;
    @FXML
    private TextField objectIdFilterField;
    @FXML
    private TextField fieldNameFilterField;
    @FXML
    private TextField startDateFilterField;
    @FXML
    private TextField endDateFilterField;
    @FXML
    private Label statusLabel;
    @FXML
    private TableView<AuditLogDao.AuditLogEntryRow> auditTable;
    @FXML
    private TableColumn<AuditLogDao.AuditLogEntryRow, LocalDateTime> entryDateColumn;
    @FXML
    private TableColumn<AuditLogDao.AuditLogEntryRow, String> userIdColumn;
    @FXML
    private TableColumn<AuditLogDao.AuditLogEntryRow, String> objectTypeIdColumn;
    @FXML
    private TableColumn<AuditLogDao.AuditLogEntryRow, Long> objectIdColumn;
    @FXML
    private TableColumn<AuditLogDao.AuditLogEntryRow, String> fieldNameColumn;
    @FXML
    private TableColumn<AuditLogDao.AuditLogEntryRow, String> fieldCodeColumn;
    @FXML
    private TableColumn<AuditLogDao.AuditLogEntryRow, String> stringValueColumn;
    @FXML
    private TableColumn<AuditLogDao.AuditLogEntryRow, LocalDate> dateValueColumn;
    @FXML
    private TableColumn<AuditLogDao.AuditLogEntryRow, Boolean> booleanValueColumn;
    @FXML
    private TableColumn<AuditLogDao.AuditLogEntryRow, Integer> intValueColumn;

    private AppState appState;
    private AuditLogDao auditLogDao;
    private UserDao userDao;
    private final Map<Integer, String> userDisplayNamesById = new HashMap<>();
    private boolean fxmlReady;
    private boolean initialLoadPending;
    private static final Map<Integer, String> OBJECT_TYPE_LABELS = Map.of(
            1, "Case",
            2, "Contact",
            3, "Task",
            4, "CaseUpdate",
            5, "TaskUpdate",
            6, "CaseTimeline",
            7, "TaskTimeline");

    @FXML
    private void initialize() {
        fxmlReady = true;
        configureColumns();
        configureTableReadability();
        configureFilterFieldActions();
        runInitialLoadIfReady();
    }

    public void init(AppState appState, AuditLogDao auditLogDao, UserDao userDao) {
        this.appState = Objects.requireNonNull(appState, "appState");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
        this.userDao = Objects.requireNonNull(userDao, "userDao");
        initialLoadPending = true;
        runInitialLoadIfReady();
    }

    private void runInitialLoadIfReady() {
        if (!fxmlReady || !initialLoadPending) {
            return;
        }
        initialLoadPending = false;
        if (!this.appState.isAdmin()) {
            auditTable.setItems(FXCollections.emptyObservableList());
            setStatus("Only admin users can view audit logs.");
            return;
        }
        loadAuditRows();
    }

    @FXML
    private void onApplyFilters() {
        if (!isAdminUser()) {
            return;
        }
        loadAuditRows();
    }

    @FXML
    private void onClearFilters() {
        if (userIdFilterField != null) {
            userIdFilterField.clear();
        }
        if (objectTypeIdFilterField != null) {
            objectTypeIdFilterField.clear();
        }
        if (objectIdFilterField != null) {
            objectIdFilterField.clear();
        }
        if (fieldNameFilterField != null) {
            fieldNameFilterField.clear();
        }
        if (startDateFilterField != null) {
            startDateFilterField.clear();
        }
        if (endDateFilterField != null) {
            endDateFilterField.clear();
        }
        if (!isAdminUser()) {
            return;
        }
        loadAuditRows();
    }

    private void configureColumns() {
        entryDateColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().entryDate()));
        userIdColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(formatUserDisplay(cell.getValue().userId())));
        objectTypeIdColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(formatObjectTypeDisplay(cell.getValue().objectTypeId())));
        objectIdColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().objectId()));
        fieldNameColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().fieldName()));
        fieldCodeColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().fieldCode()));
        stringValueColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().stringValue()));
        dateValueColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().dateValue()));
        booleanValueColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().booleanValue()));
        intValueColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().intValue()));
    }

    private void configureTableReadability() {
        if (auditTable == null) {
            return;
        }
        auditTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        auditTable.setPlaceholder(new Label("No audit records found."));
    }

    private void configureFilterFieldActions() {
        bindEnterToReload(userIdFilterField);
        bindEnterToReload(objectTypeIdFilterField);
        bindEnterToReload(objectIdFilterField);
        bindEnterToReload(fieldNameFilterField);
        bindEnterToReload(startDateFilterField);
        bindEnterToReload(endDateFilterField);
    }

    private void bindEnterToReload(TextField field) {
        if (field == null) {
            return;
        }
        field.setOnAction(event -> onApplyFilters());
    }

    private boolean isAdminUser() {
        if (appState == null || appState.isAdmin()) {
            return true;
        }
        AppDialogs.showError(dialogOwner(), "Audit Log", "Only admin users can view audit logs.");
        return false;
    }

    private void loadAuditRows() {
        Integer shaleClientId = appState == null ? null : appState.getShaleClientId();
        if (shaleClientId == null || shaleClientId <= 0) {
            auditTable.setItems(FXCollections.emptyObservableList());
            setStatus("No audit records found.");
            return;
        }
        loadUserDisplayNames(shaleClientId);
        Integer userId = parseOptionalInt(userIdFilterField, "UserId");
        if (userId == null && userIdFilterField != null && hasText(userIdFilterField.getText())) {
            return;
        }
        Integer objectTypeId = parseOptionalInt(objectTypeIdFilterField, "ObjectTypeId");
        if (objectTypeId == null && objectTypeIdFilterField != null && hasText(objectTypeIdFilterField.getText())) {
            return;
        }
        Long objectId = parseOptionalLong(objectIdFilterField, "ObjectId");
        if (objectId == null && objectIdFilterField != null && hasText(objectIdFilterField.getText())) {
            return;
        }
        LocalDate startDate = parseOptionalDate(startDateFilterField, "StartDate");
        if (startDate == null && startDateFilterField != null && hasText(startDateFilterField.getText())) {
            return;
        }
        LocalDate endDate = parseOptionalDate(endDateFilterField, "EndDate");
        if (endDate == null && endDateFilterField != null && hasText(endDateFilterField.getText())) {
            return;
        }
        String fieldName = fieldNameFilterField == null ? null : trimToNull(fieldNameFilterField.getText());

        List<AuditLogDao.AuditLogEntryRow> rows = auditLogDao.listAuditLogEntries(
                shaleClientId,
                userId,
                objectId,
                fieldName,
                objectTypeId,
                startDate,
                endDate);
        auditTable.setItems(FXCollections.observableArrayList(rows));
        if (rows.isEmpty()) {
            setStatus("No audit records found.");
        } else {
            setStatus(rows.size() + " audit entries");
        }
    }

    private void loadUserDisplayNames(int shaleClientId) {
        if (userDao == null || !userDisplayNamesById.isEmpty()) {
            return;
        }
        try {
            List<UserDao.DirectoryUserRow> users = userDao.listUsersForTenant(shaleClientId);
            for (UserDao.DirectoryUserRow user : users) {
                if (user == null || user.id() <= 0) {
                    continue;
                }
                String display = trimToNull(user.displayName());
                if (display != null) {
                    userDisplayNamesById.put(user.id(), display);
                }
            }
        } catch (RuntimeException ex) {
            // keep raw ids if lookup fails
        }
    }

    private String formatUserDisplay(Integer userId) {
        if (userId == null || userId <= 0) {
            return "";
        }
        String displayName = userDisplayNamesById.get(userId);
        if (displayName == null || displayName.isBlank()) {
            return Integer.toString(userId);
        }
        return userId + " (" + displayName + ")";
    }

    private String formatObjectTypeDisplay(Integer objectTypeId) {
        if (objectTypeId == null || objectTypeId <= 0) {
            return "";
        }
        String label = OBJECT_TYPE_LABELS.get(objectTypeId);
        if (label == null || label.isBlank()) {
            return Integer.toString(objectTypeId);
        }
        return objectTypeId + " (" + label + ")";
    }

    private Integer parseOptionalInt(TextField field, String label) {
        String value = field == null ? null : trimToNull(field.getText());
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            AppDialogs.showError(dialogOwner(), "Audit Log", label + " must be a whole number.");
            return null;
        }
    }

    private Long parseOptionalLong(TextField field, String label) {
        String value = field == null ? null : trimToNull(field.getText());
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            AppDialogs.showError(dialogOwner(), "Audit Log", label + " must be a whole number.");
            return null;
        }
    }

    private LocalDate parseOptionalDate(TextField field, String label) {
        String value = field == null ? null : trimToNull(field.getText());
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            AppDialogs.showError(dialogOwner(), "Audit Log", label + " must use YYYY-MM-DD.");
            return null;
        }
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message == null ? "" : message);
        }
    }

    private Window dialogOwner() {
        if (auditTable == null || auditTable.getScene() == null) {
            return null;
        }
        return auditTable.getScene().getWindow();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
