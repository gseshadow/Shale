package com.shale.ui.controller;

import com.shale.core.dto.CaseStatusDto;
import com.shale.core.service.CaseServicePort;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.notification.NotificationPreferenceKey;
import com.shale.ui.notification.NotificationPreferences;
import com.shale.ui.notification.NotificationPreferencesService;
import com.shale.ui.state.AppState;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;

public final class SettingsController {
	@FXML
	private CheckBox taskAssignedToMeCheck;
	@FXML
	private CheckBox taskOverdueCheck;
	@FXML
	private CheckBox taskDueTodayCheck;
	@FXML
	private CheckBox taskDueTomorrowCheck;
	@FXML
	private CheckBox appUpdatesCheck;
	@FXML
	private CheckBox connectivityCheck;
	@FXML
	private CheckBox taskOverdueBannerCheck;
	@FXML
	private CheckBox taskDueTodayBannerCheck;
	@FXML
	private CheckBox appUpdatesBannerCheck;
	@FXML
	private CheckBox connectivityBannerCheck;
	@FXML
	private Label notificationSettingsStatusLabel;
	@FXML
	private VBox auditSection;
	@FXML
	private TableView<CaseStatusViewRow> caseStatusesTable;
	@FXML
	private TableColumn<CaseStatusViewRow, String> statusNameColumn;
	@FXML
	private TableColumn<CaseStatusViewRow, String> statusClosedColumn;
	@FXML
	private TableColumn<CaseStatusViewRow, String> statusLifecycleKeyColumn;
	@FXML
	private TableColumn<CaseStatusViewRow, String> statusSystemKeyColumn;
	@FXML
	private TableColumn<CaseStatusViewRow, Integer> statusSortOrderColumn;
	@FXML
	private Label caseStatusSettingsStatusLabel;

	private NotificationPreferencesService notificationPreferencesService;
	private AppState appState;
	private CaseServicePort caseService;
	private Runnable onOpenAuditLog;
	private boolean fxmlReady;

	@FXML
	private void initialize() {
		fxmlReady = true;
		configureCaseStatusesTable();
		updateAuditVisibility();
		if (notificationPreferencesService != null) {
			loadFromPreferences();
		}
		if (caseService != null) {
			loadCaseStatuses();
		}
	}

	public void init(NotificationPreferencesService notificationPreferencesService, AppState appState, Runnable onOpenAuditLog, CaseServicePort caseService) {
		this.notificationPreferencesService = Objects.requireNonNull(notificationPreferencesService, "notificationPreferencesService");
		this.appState = Objects.requireNonNull(appState, "appState");
		this.onOpenAuditLog = Objects.requireNonNull(onOpenAuditLog, "onOpenAuditLog");
		this.caseService = Objects.requireNonNull(caseService, "caseService");
		if (fxmlReady) {
			loadFromPreferences();
			updateAuditVisibility();
			loadCaseStatuses();
		}
	}

	@FXML
	private void onApplyNotificationPreferences() {
		if (notificationPreferencesService == null) {
			return;
		}
		NotificationPreferences preferences = notificationPreferencesService.getForCurrentUser();
		Map<NotificationPreferenceKey, Boolean> selected = selectedValues();
		for (Map.Entry<NotificationPreferenceKey, Boolean> entry : selected.entrySet()) {
			preferences = preferences.withEnabled(entry.getKey(), entry.getValue());
		}
		notificationPreferencesService.setForCurrentUser(preferences);
		if (notificationSettingsStatusLabel != null) {
			notificationSettingsStatusLabel.setText("Notification settings applied for this session.");
		}
	}

	@FXML
	private void onResetNotificationPreferences() {
		loadFromPreferences();
		if (notificationSettingsStatusLabel != null) {
			notificationSettingsStatusLabel.setText("Notification settings reset to saved values.");
		}
	}

	@FXML
	private void onViewAuditLog() {
		if (!isAdminUser() || onOpenAuditLog == null) {
			return;
		}
		onOpenAuditLog.run();
	}


	@FXML
	private void onAddCaseStatus() {
		showCaseStatusDialog(null).ifPresent(input -> {
			caseService.createCaseStatus(new CaseServicePort.CaseStatusCommand(
					null,
					requireTenantId(),
					input.name(),
					input.closed(),
					input.sortOrder(),
					input.color(),
					input.lifecycleKey(),
					input.systemKey()));
			loadCaseStatuses();
			setCaseStatusMessage("Case status added.");
		});
	}

	@FXML
	private void onEditCaseStatus() {
		CaseStatusViewRow selected = selectedStatusRow();
		if (selected == null) return;
		showCaseStatusDialog(selected.status()).ifPresent(input -> {
			caseService.updateCaseStatus(new CaseServicePort.CaseStatusCommand(
					selected.id(),
					requireTenantId(),
					input.name(),
					input.closed(),
					input.sortOrder(),
					input.color(),
					input.lifecycleKey(),
					input.systemKey()));
			loadCaseStatuses();
			setCaseStatusMessage("Case status updated.");
		});
	}

	@FXML
	private void onMoveCaseStatusUp() { moveSelectedStatus(-1); }

	@FXML
	private void onMoveCaseStatusDown() { moveSelectedStatus(1); }

	private void configureCaseStatusesTable() {
		if (caseStatusesTable == null) return;
		statusNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
		statusClosedColumn.setCellValueFactory(new PropertyValueFactory<>("closedState"));
		statusSortOrderColumn.setCellValueFactory(new PropertyValueFactory<>("sortOrder"));
		statusLifecycleKeyColumn.setCellValueFactory(new PropertyValueFactory<>("lifecycleKey"));
		statusSystemKeyColumn.setCellValueFactory(new PropertyValueFactory<>("systemKey"));
	}

	private void loadCaseStatuses() {
		if (caseService == null || caseStatusesTable == null) return;
		try {
			List<CaseStatusViewRow> rows = new ArrayList<>();
			for (CaseStatusDto status : caseService.listCaseStatuses(requireTenantId(), true)) rows.add(new CaseStatusViewRow(status));
			caseStatusesTable.getItems().setAll(rows);
			setCaseStatusMessage(rows.isEmpty() ? "No case statuses are configured for this tenant." : "");
		} catch (RuntimeException ex) {
			setCaseStatusMessage("Failed to load case statuses. " + rootMessage(ex));
		}
	}

	private void moveSelectedStatus(int delta) {
		CaseStatusViewRow selected = selectedStatusRow();
		if (selected == null) return;
		int index = caseStatusesTable.getItems().indexOf(selected);
		int otherIndex = index + delta;
		if (otherIndex < 0 || otherIndex >= caseStatusesTable.getItems().size()) return;
		CaseStatusViewRow other = caseStatusesTable.getItems().get(otherIndex);
		try {
			caseService.reorderCaseStatuses(requireTenantId(), selected.id(), other.id());
			loadCaseStatuses();
			caseStatusesTable.getSelectionModel().select(otherIndex);
		} catch (RuntimeException ex) {
			AppDialogs.showError(caseStatusesTable.getScene().getWindow(), "Case Statuses", rootMessage(ex));
		}
	}

	private Optional<CaseStatusInput> showCaseStatusDialog(CaseStatusDto existing) {
		Dialog<CaseStatusInput> dialog = new Dialog<>();
		dialog.setTitle(existing == null ? "Add Status" : "Edit Status");
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		TextField name = new TextField(existing == null ? "" : existing.name());
		CheckBox closed = new CheckBox("Closed status");
		closed.setSelected(existing != null && existing.closed());
		TextField sortOrder = new TextField(existing == null || existing.sortOrder() == null ? "" : String.valueOf(existing.sortOrder()));
		TextField color = new TextField(existing == null ? "" : safe(existing.color()));
		TextField lifecycleKey = new TextField(existing == null ? "" : safe(existing.lifecycleKey()));
		TextField systemKey = new TextField(existing == null ? "" : safe(existing.systemKey()));
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("Name"), 0, 0); grid.add(name, 1, 0);
		grid.add(closed, 1, 1);
		grid.add(new Label("Sort Order"), 0, 2); grid.add(sortOrder, 1, 2);
		grid.add(new Label("Color"), 0, 3); grid.add(color, 1, 3);
		grid.add(new Label("Lifecycle Key"), 0, 4); grid.add(lifecycleKey, 1, 4);
		grid.add(new Label("System Key"), 0, 5); grid.add(systemKey, 1, 5);
		dialog.getDialogPane().setContent(grid);
		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) return null;
			String trimmedName = name.getText() == null ? "" : name.getText().trim();
			if (trimmedName.isBlank()) throw new IllegalArgumentException("Name is required.");
			Integer sort = null;
			String sortText = sortOrder.getText() == null ? "" : sortOrder.getText().trim();
			if (!sortText.isBlank()) sort = Integer.parseInt(sortText);
			return new CaseStatusInput(
					trimmedName,
					closed.isSelected(),
					sort,
					color.getText(),
					lifecycleKey.getText(),
					systemKey.getText());
		});
		try { return dialog.showAndWait(); }
		catch (RuntimeException ex) { AppDialogs.showError(dialog.getOwner(), "Case Statuses", rootMessage(ex)); return Optional.empty(); }
	}

	private CaseStatusViewRow selectedStatusRow() {
		CaseStatusViewRow selected = caseStatusesTable == null ? null : caseStatusesTable.getSelectionModel().getSelectedItem();
		if (selected == null) setCaseStatusMessage("Select a case status first.");
		return selected;
	}

	private int requireTenantId() {
		Integer id = appState == null ? null : appState.getShaleClientId();
		if (id == null || id <= 0) throw new IllegalStateException("No tenant is selected.");
		return id;
	}

	private void setCaseStatusMessage(String message) { if (caseStatusSettingsStatusLabel != null) caseStatusSettingsStatusLabel.setText(message == null ? "" : message); }
	private static String safe(String value) { return value == null ? "" : value; }
	private static String rootMessage(Throwable ex) { Throwable t = ex; while (t.getCause() != null) t = t.getCause(); return t.getMessage() == null ? t.toString() : t.getMessage(); }

	public static final class CaseStatusViewRow {
		private final CaseStatusDto status;
		CaseStatusViewRow(CaseStatusDto status) { this.status = status; }
		public int getId() { return status.id(); }
		public int id() { return status.id(); }
		public String getName() { return safe(status.name()); }
		public String getClosedState() { return status.closed() ? "Closed" : "Open"; }
		public Integer getSortOrder() { return status.sortOrder(); }
		public String getLifecycleKey() { return safe(status.lifecycleKey()); }
		public String getSystemKey() { return safe(status.systemKey()); }
		CaseStatusDto status() { return status; }
	}

	private record CaseStatusInput(
			String name,
			boolean closed,
			Integer sortOrder,
			String color,
			String lifecycleKey,
			String systemKey) {}

	private void loadFromPreferences() {
		if (notificationPreferencesService == null) {
			return;
		}
		NotificationPreferences preferences = notificationPreferencesService.getForCurrentUser();
		setChecked(taskAssignedToMeCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_ASSIGNED_TO_ME));
		setChecked(taskOverdueCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_OVERDUE));
		setChecked(taskDueTodayCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_TODAY));
		setChecked(taskDueTomorrowCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_TOMORROW));
		setChecked(appUpdatesCheck, preferences.isEnabled(NotificationPreferenceKey.APP_UPDATES));
		setChecked(connectivityCheck, preferences.isEnabled(NotificationPreferenceKey.CONNECTIVITY_STATUS));
		setChecked(taskOverdueBannerCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_OVERDUE_BANNER));
		setChecked(taskDueTodayBannerCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_TODAY_BANNER));
		setChecked(appUpdatesBannerCheck, preferences.isEnabled(NotificationPreferenceKey.APP_UPDATES_BANNER));
		setChecked(connectivityBannerCheck, preferences.isEnabled(NotificationPreferenceKey.CONNECTIVITY_BANNER));
		if (notificationSettingsStatusLabel != null) {
			notificationSettingsStatusLabel.setText("");
		}
	}

	private Map<NotificationPreferenceKey, Boolean> selectedValues() {
		Map<NotificationPreferenceKey, Boolean> values = new EnumMap<>(NotificationPreferenceKey.class);
		values.put(NotificationPreferenceKey.TASK_ASSIGNED_TO_ME, isChecked(taskAssignedToMeCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_OVERDUE, isChecked(taskOverdueCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_TODAY, isChecked(taskDueTodayCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_TOMORROW, isChecked(taskDueTomorrowCheck));
		values.put(NotificationPreferenceKey.APP_UPDATES, isChecked(appUpdatesCheck));
		values.put(NotificationPreferenceKey.CONNECTIVITY_STATUS, isChecked(connectivityCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_OVERDUE_BANNER, isChecked(taskOverdueBannerCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_TODAY_BANNER, isChecked(taskDueTodayBannerCheck));
		values.put(NotificationPreferenceKey.APP_UPDATES_BANNER, isChecked(appUpdatesBannerCheck));
		values.put(NotificationPreferenceKey.CONNECTIVITY_BANNER, isChecked(connectivityBannerCheck));
		return values;
	}

	private static boolean isChecked(CheckBox checkBox) {
		return checkBox != null && checkBox.isSelected();
	}

	private static void setChecked(CheckBox checkBox, boolean selected) {
		if (checkBox != null) {
			checkBox.setSelected(selected);
		}
	}

	private boolean isAdminUser() {
		return appState != null && appState.isAdmin();
	}

	private void updateAuditVisibility() {
		if (auditSection != null) {
			boolean visible = isAdminUser();
			auditSection.setVisible(visible);
			auditSection.setManaged(visible);
		}
	}
}
