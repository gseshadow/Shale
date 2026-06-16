package com.shale.ui.controller;

import com.shale.core.dto.CaseStatusDto;
import com.shale.core.dto.PracticeAreaDto;
import com.shale.core.service.CaseServicePort;
import com.shale.data.dao.UserDao;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.notification.NotificationPreferenceKey;
import com.shale.ui.notification.NotificationPreferences;
import com.shale.ui.notification.NotificationPreferencesService;
import com.shale.ui.state.AppState;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.paint.Color;
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
	@FXML
	private TableView<PracticeAreaViewRow> practiceAreasTable;
	@FXML
	private TableColumn<PracticeAreaViewRow, String> practiceAreaNameColumn;
	@FXML
	private TableColumn<PracticeAreaViewRow, String> practiceAreaColorColumn;
	@FXML
	private TableColumn<PracticeAreaViewRow, String> practiceAreaActiveColumn;
	@FXML
	private TableColumn<PracticeAreaViewRow, String> practiceAreaSystemKeyColumn;
	@FXML
	private Label practiceAreaSettingsStatusLabel;

	private NotificationPreferencesService notificationPreferencesService;
	private AppState appState;
	private CaseServicePort caseService;
	private UserDao userDao;
	private Runnable onOpenAuditLog;
	private boolean fxmlReady;

	@FXML
	private void initialize() {
		fxmlReady = true;
		configureCaseStatusesTable();
		configurePracticeAreasTable();
		updateAdminControlsVisibility();
		if (notificationPreferencesService != null) {
			loadFromPreferences();
		}
		if (caseService != null) {
			loadCaseStatuses();
			loadPracticeAreas();
		}
	}

	public void init(NotificationPreferencesService notificationPreferencesService, AppState appState, Runnable onOpenAuditLog, CaseServicePort caseService, UserDao userDao) {
		this.notificationPreferencesService = Objects.requireNonNull(notificationPreferencesService, "notificationPreferencesService");
		this.appState = Objects.requireNonNull(appState, "appState");
		this.onOpenAuditLog = Objects.requireNonNull(onOpenAuditLog, "onOpenAuditLog");
		this.caseService = Objects.requireNonNull(caseService, "caseService");
		this.userDao = Objects.requireNonNull(userDao, "userDao");
		if (fxmlReady) {
			loadFromPreferences();
			updateAdminControlsVisibility();
			loadCaseStatuses();
			loadPracticeAreas();
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
	private void onAddPracticeArea() {
		showPracticeAreaDialog(null).ifPresent(input -> {
			caseService.createPracticeArea(new CaseServicePort.PracticeAreaCommand(
					null, requireTenantId(), input.name(), input.color(), input.active(), input.systemKey()));
			loadPracticeAreas();
			setPracticeAreaMessage("Practice area added.");
		});
	}

	@FXML
	private void onEditPracticeArea() {
		PracticeAreaViewRow selected = selectedPracticeAreaRow();
		if (selected == null) return;
		showPracticeAreaDialog(selected.practiceArea()).ifPresent(input -> {
			caseService.updatePracticeArea(new CaseServicePort.PracticeAreaCommand(
					selected.id(), requireTenantId(), input.name(), input.color(), input.active(), input.systemKey()));
			loadPracticeAreas();
			setPracticeAreaMessage("Practice area updated.");
		});
	}

	@FXML
	private void onRemovePracticeArea() {
		PracticeAreaViewRow selected = selectedPracticeAreaRow();
		if (selected == null) return;
		try {
			caseService.deactivatePracticeArea(requireTenantId(), selected.id());
			loadPracticeAreas();
			setPracticeAreaMessage("Practice area removed from new selections. Existing cases keep their value.");
		} catch (RuntimeException ex) {
			AppDialogs.showError(practiceAreasTable.getScene().getWindow(), "Practice Areas", rootMessage(ex));
		}
	}

	private void configurePracticeAreasTable() {
		if (practiceAreasTable == null) return;
		practiceAreaNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
		practiceAreaColorColumn.setCellValueFactory(new PropertyValueFactory<>("color"));
		practiceAreaActiveColumn.setCellValueFactory(new PropertyValueFactory<>("activeState"));
		practiceAreaSystemKeyColumn.setCellValueFactory(new PropertyValueFactory<>("systemKey"));
	}

	private void loadPracticeAreas() {
		if (caseService == null || practiceAreasTable == null) return;
		try {
			List<PracticeAreaViewRow> rows = new ArrayList<>();
			for (PracticeAreaDto area : caseService.listPracticeAreas(requireTenantId(), true)) rows.add(new PracticeAreaViewRow(area));
			practiceAreasTable.getItems().setAll(rows);
			setPracticeAreaMessage(rows.isEmpty() ? "No practice areas are configured for this tenant." : "");
		} catch (RuntimeException ex) {
			setPracticeAreaMessage("Failed to load practice areas. " + rootMessage(ex));
		}
	}

	private Optional<PracticeAreaInput> showPracticeAreaDialog(PracticeAreaDto existing) {
		Dialog<PracticeAreaInput> dialog = new Dialog<>();
		String dialogTitle = existing == null ? "Add Practice Area" : "Edit Practice Area";
		dialog.setTitle(dialogTitle);
		AppDialogs.applySecondaryDialogShell(dialog, dialogTitle);
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		TextField name = new TextField(existing == null ? "" : existing.name());
		CheckBox active = new CheckBox("Active");
		active.setSelected(existing == null || existing.active());
		ColorPicker colorPicker = new ColorPicker(dbColorToFx(existing == null ? null : existing.color()));
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("Name"), 0, 0); grid.add(name, 1, 0);
		grid.add(new Label("Color"), 0, 1); grid.add(colorPicker, 1, 1);
		grid.add(active, 1, 2);
		if (existing != null && !safe(existing.systemKey()).isBlank()) {
			grid.add(new Label("System Key"), 0, 3);
			grid.add(new Label(existing.systemKey()), 1, 3);
		}
		dialog.getDialogPane().setContent(grid);
		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) return null;
			String trimmedName = name.getText() == null ? "" : name.getText().trim();
			if (trimmedName.isBlank()) throw new IllegalArgumentException("Name is required.");
			return new PracticeAreaInput(trimmedName, fxColorToDb(colorPicker.getValue()), active.isSelected(), practiceAreaSystemKeyForSave(existing));
		});
		try { return dialog.showAndWait(); }
		catch (RuntimeException ex) { AppDialogs.showError(dialog.getOwner(), "Practice Areas", rootMessage(ex)); return Optional.empty(); }
	}

	private PracticeAreaViewRow selectedPracticeAreaRow() {
		PracticeAreaViewRow selected = practiceAreasTable == null ? null : practiceAreasTable.getSelectionModel().getSelectedItem();
		if (selected == null) setPracticeAreaMessage("Select a practice area first.");
		return selected;
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
		String dialogTitle = existing == null ? "Add Status" : "Edit Status";
		dialog.setTitle(dialogTitle);
		AppDialogs.applySecondaryDialogShell(dialog, dialogTitle);
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		TextField name = new TextField(existing == null ? "" : existing.name());
		CheckBox closed = new CheckBox("Closed status");
		closed.setSelected(existing != null && existing.closed());
		ColorPicker colorPicker = new ColorPicker(dbColorToFx(existing == null ? null : existing.color()));
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("Name"), 0, 0); grid.add(name, 1, 0);
		grid.add(closed, 1, 1);
		grid.add(new Label("Color"), 0, 2); grid.add(colorPicker, 1, 2);
		if (existing != null && !safe(existing.lifecycleKey()).isBlank()) {
			grid.add(new Label("Lifecycle Key"), 0, 3);
			grid.add(new Label(existing.lifecycleKey()), 1, 3);
		}
		if (existing != null && !safe(existing.systemKey()).isBlank()) {
			grid.add(new Label("System Key"), 0, 4);
			grid.add(new Label(existing.systemKey()), 1, 4);
		}
		dialog.getDialogPane().setContent(grid);
		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) return null;
			String trimmedName = name.getText() == null ? "" : name.getText().trim();
			if (trimmedName.isBlank()) throw new IllegalArgumentException("Name is required.");
			return new CaseStatusInput(
					trimmedName,
					closed.isSelected(),
					sortOrderForSave(existing),
					fxColorToDb(colorPicker.getValue()),
					lifecycleKeyForSave(existing),
					systemKeyForSave(existing));
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

	private static final Color DEFAULT_STATUS_COLOR = Color.rgb(108, 117, 125);


	static Color dbColorToFx(String value) {
		String normalized = value == null ? "" : value.trim();
		if (normalized.matches("(?i)^0x[0-9a-f]{8}$")) {
			try {
				int red = Integer.parseInt(normalized.substring(2, 4), 16);
				int green = Integer.parseInt(normalized.substring(4, 6), 16);
				int blue = Integer.parseInt(normalized.substring(6, 8), 16);
				int alpha = Integer.parseInt(normalized.substring(8, 10), 16);
				return Color.rgb(red, green, blue, alpha / 255.0);
			} catch (RuntimeException ignored) {
				return DEFAULT_STATUS_COLOR;
			}
		}
		return DEFAULT_STATUS_COLOR;
	}

	static String fxColorToDb(Color color) {
		Color safeColor = color == null ? DEFAULT_STATUS_COLOR : color;
		return String.format("0x%02X%02X%02X%02X",
				toColorByte(safeColor.getRed()),
				toColorByte(safeColor.getGreen()),
				toColorByte(safeColor.getBlue()),
				toColorByte(safeColor.getOpacity()));
	}

	private static int toColorByte(double value) {
		return Math.max(0, Math.min(255, (int) Math.round(value * 255.0)));
	}

	static Integer sortOrderForSave(CaseStatusDto existing) {
		return existing == null ? null : existing.sortOrder();
	}

	static String lifecycleKeyForSave(CaseStatusDto existing) {
		return existing == null ? null : existing.lifecycleKey();
	}

	static String practiceAreaSystemKeyForSave(PracticeAreaDto existing) {
		return existing == null ? null : existing.systemKey();
	}

	static String systemKeyForSave(CaseStatusDto existing) {
		return existing == null ? null : existing.systemKey();
	}

	private void setCaseStatusMessage(String message) { if (caseStatusSettingsStatusLabel != null) caseStatusSettingsStatusLabel.setText(message == null ? "" : message); }
	@FXML
	private void onAddUser() {
		if (!isAdminUser()) {
			AppDialogs.showError(null, "Add User", "Only admin users can create users.");
			return;
		}
		showAddUserDialog().ifPresent(request -> {
			try {
				userDao.createUser(request);
				AppDialogs.showInfo(null, "Add User", "User added.");
			} catch (RuntimeException ex) {
				AppDialogs.showError(null, "Add User", rootMessage(ex));
			}
		});
	}

	private Optional<UserDao.UserCreateRequest> showAddUserDialog() {
		Dialog<UserDao.UserCreateRequest> dialog = new Dialog<>();
		dialog.setTitle("Add User");
		AppDialogs.applySecondaryDialogShell(dialog, "Add User");
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		TextField firstName = new TextField();
		TextField lastName = new TextField();
		TextField email = new TextField();
		PasswordField password = new PasswordField();
		TextField initials = new TextField();
		ColorPicker colorPicker = new ColorPicker(DEFAULT_STATUS_COLOR);
		CheckBox attorney = new CheckBox("Attorney");
		CheckBox admin = new CheckBox("Admin");
		TextField defaultOrganization = new TextField();
		defaultOrganization.setPromptText("Optional numeric organization id");
		TextField organizationId = new TextField();
		organizationId.setPromptText("Optional numeric organization id");

		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("First Name"), 0, 0); grid.add(firstName, 1, 0);
		grid.add(new Label("Last Name"), 0, 1); grid.add(lastName, 1, 1);
		grid.add(new Label("Email"), 0, 2); grid.add(email, 1, 2);
		grid.add(new Label("Temporary Password"), 0, 3); grid.add(password, 1, 3);
		grid.add(new Label("Initials"), 0, 4); grid.add(initials, 1, 4);
		grid.add(new Label("Color"), 0, 5); grid.add(colorPicker, 1, 5);
		grid.add(attorney, 1, 6);
		grid.add(admin, 1, 7);
		grid.add(new Label("Default Organization"), 0, 8); grid.add(defaultOrganization, 1, 8);
		grid.add(new Label("Organization"), 0, 9); grid.add(organizationId, 1, 9);
		dialog.getDialogPane().setContent(grid);
		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) return null;
			return new UserDao.UserCreateRequest(
					trim(firstName.getText()),
					trim(lastName.getText()),
					trim(email.getText()),
					password.getText(),
					fxColorToDb(colorPicker.getValue()),
					trim(initials.getText()),
					attorney.isSelected(),
					admin.isSelected(),
					parseOptionalInt(defaultOrganization.getText(), "Default organization"),
					parseOptionalInt(organizationId.getText(), "Organization"));
		});
		try { return dialog.showAndWait(); }
		catch (RuntimeException ex) { AppDialogs.showError(dialog.getOwner(), "Add User", rootMessage(ex)); return Optional.empty(); }
	}

	private static String trim(String value) { return value == null ? "" : value.trim(); }

	private static Integer parseOptionalInt(String value, String label) {
		String trimmed = trim(value);
		if (trimmed.isBlank()) return null;
		try { return Integer.valueOf(trimmed); }
		catch (NumberFormatException ex) { throw new IllegalArgumentException(label + " must be a number."); }
	}

	private void setPracticeAreaMessage(String message) { if (practiceAreaSettingsStatusLabel != null) practiceAreaSettingsStatusLabel.setText(message == null ? "" : message); }
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

	public static final class PracticeAreaViewRow {
		private final PracticeAreaDto practiceArea;
		PracticeAreaViewRow(PracticeAreaDto practiceArea) { this.practiceArea = practiceArea; }
		public int getId() { return practiceArea.id(); }
		public int id() { return practiceArea.id(); }
		public String getName() { return safe(practiceArea.name()); }
		public String getColor() { return safe(practiceArea.color()); }
		public String getActiveState() { return practiceArea.active() && !practiceArea.deleted() ? "Active" : "Inactive"; }
		public String getSystemKey() { return safe(practiceArea.systemKey()); }
		PracticeAreaDto practiceArea() { return practiceArea; }
	}

	private record PracticeAreaInput(String name, String color, boolean active, String systemKey) {}

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

	@FXML
	private VBox userAdministrationSection;

	private void updateAdminControlsVisibility() {
		boolean visible = isAdminUser();
		if (auditSection != null) {
			auditSection.setVisible(visible);
			auditSection.setManaged(visible);
		}
		if (userAdministrationSection != null) {
			userAdministrationSection.setVisible(visible);
			userAdministrationSection.setManaged(visible);
		}
	}
}
