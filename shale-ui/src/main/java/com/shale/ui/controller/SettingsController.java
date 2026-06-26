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
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.MetadataChipFactory;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Button;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

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
	private VBox caseStatusAdministrationSection;
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
	private HBox caseStatusActionRow;
	@FXML
	private Label caseStatusSettingsStatusLabel;
	@FXML
	private VBox practiceAreaAdministrationSection;
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
	private HBox practiceAreaActionRow;
	@FXML
	private Label practiceAreaSettingsStatusLabel;
	@FXML
	private TableView<UserManagementViewRow> userManagementTable;
	@FXML
	private TableColumn<UserManagementViewRow, String> userNameColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userEmailColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userInitialsColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userAttorneyColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userAdminColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userStatusColumn;
	@FXML
	private CheckBox showInactiveUsersCheck;
	@FXML
	private Button deactivateUserButton;
	@FXML
	private Button reactivateUserButton;
	@FXML
	private Button resetPasswordButton;
	@FXML
	private Label userManagementStatusLabel;

	private NotificationPreferencesService notificationPreferencesService;
	private AppState appState;
	private CaseServicePort caseService;
	private UserDao userDao;
	private Runnable onOpenAuditLog;
	private boolean fxmlReady;

	@FXML
	private void initialize() {
		fxmlReady = true;
		configureLookupActionRows();
		configureCaseStatusesTable();
		configurePracticeAreasTable();
		configureUserManagementTable();
		updateAdminControlsVisibility();
		if (notificationPreferencesService != null) {
			loadFromPreferences();
		}
		if (caseService != null && isAdminUser()) {
			loadCaseStatuses();
			loadPracticeAreas();
		}
		if (userDao != null && isAdminUser()) {
			loadManagedUsers();
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
			if (isAdminUser()) {
				loadCaseStatuses();
				loadPracticeAreas();
				loadManagedUsers();
			}
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

	private void configureLookupActionRows() {
		if (caseStatusActionRow != null) {
			caseStatusActionRow.getChildren().setAll(
					ActionButtonFactory.primary("Add Status", event -> onAddCaseStatus()),
					ActionButtonFactory.neutral("Edit Status", event -> onEditCaseStatus()),
					ActionButtonFactory.neutral("Move Up", event -> onMoveCaseStatusUp()),
					ActionButtonFactory.neutral("Move Down", event -> onMoveCaseStatusDown()),
					caseStatusSettingsStatusLabel);
		}
		if (practiceAreaActionRow != null) {
			practiceAreaActionRow.getChildren().setAll(
					ActionButtonFactory.primary("Add Practice Area", event -> onAddPracticeArea()),
					ActionButtonFactory.neutral("Edit Practice Area", event -> onEditPracticeArea()),
					ActionButtonFactory.neutral("Remove Practice Area", event -> onRemovePracticeArea()),
					practiceAreaSettingsStatusLabel);
		}
	}

	@FXML
	private void onAddPracticeArea() {
		if (!requireAdminLookupManagement("Practice Areas")) return;
		showPracticeAreaDialog(null).ifPresent(input -> {
			caseService.createPracticeArea(new CaseServicePort.PracticeAreaCommand(
					null, requireTenantId(), input.name(), input.color(), input.active(), input.systemKey()));
			loadPracticeAreas();
			setPracticeAreaMessage("Practice area added.");
		});
	}

	@FXML
	private void onEditPracticeArea() {
		if (!requireAdminLookupManagement("Practice Areas")) return;
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
		if (!requireAdminLookupManagement("Practice Areas")) return;
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
		practiceAreaColorColumn.setCellFactory(column -> metadataChipCell());
		practiceAreaActiveColumn.setCellFactory(column -> metadataChipCell());
		practiceAreaSystemKeyColumn.setCellFactory(column -> metadataChipCell());
	}

	private void loadPracticeAreas() {
		if (caseService == null || practiceAreasTable == null) return;
		if (!requireAdminLookupManagement("Practice Areas")) {
			practiceAreasTable.getItems().clear();
			return;
		}
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
		if (!requireAdminLookupManagement("Case Statuses")) return;
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
		if (!requireAdminLookupManagement("Case Statuses")) return;
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
	private void onMoveCaseStatusUp() { if (requireAdminLookupManagement("Case Statuses")) moveSelectedStatus(-1); }

	@FXML
	private void onMoveCaseStatusDown() { if (requireAdminLookupManagement("Case Statuses")) moveSelectedStatus(1); }

	private void configureCaseStatusesTable() {
		if (caseStatusesTable == null) return;
		statusNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
		statusClosedColumn.setCellValueFactory(new PropertyValueFactory<>("closedState"));
		statusSortOrderColumn.setCellValueFactory(new PropertyValueFactory<>("sortOrder"));
		statusLifecycleKeyColumn.setCellValueFactory(new PropertyValueFactory<>("lifecycleKey"));
		statusSystemKeyColumn.setCellValueFactory(new PropertyValueFactory<>("systemKey"));
		statusClosedColumn.setCellFactory(column -> metadataChipCell());
		statusSortOrderColumn.setCellFactory(column -> metadataChipCell());
		statusLifecycleKeyColumn.setCellFactory(column -> metadataChipCell());
		statusSystemKeyColumn.setCellFactory(column -> metadataChipCell());
	}

	private static <S, T> TableCell<S, T> metadataChipCell() {
		return new TableCell<>() {
			@Override
			protected void updateItem(T item, boolean empty) {
				super.updateItem(item, empty);
				if (empty) {
					setText(null);
					setGraphic(null);
					return;
				}
				String text = item == null ? null : String.valueOf(item);
				setText(null);
				setGraphic(MetadataChipFactory.compact(text, text));
			}
		};
	}

	private void loadCaseStatuses() {
		if (caseService == null || caseStatusesTable == null) return;
		if (!requireAdminLookupManagement("Case Statuses")) {
			caseStatusesTable.getItems().clear();
			return;
		}
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
		if (!requireAdminLookupManagement("Case Statuses")) return;
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

	private boolean requireAdminLookupManagement(String sectionName) {
		if (isAdminUser()) {
			return true;
		}
		String message = "Only admin users can manage " + sectionName.toLowerCase() + ".";
		if ("Case Statuses".equals(sectionName)) {
			setCaseStatusMessage(message);
		} else if ("Practice Areas".equals(sectionName)) {
			setPracticeAreaMessage(message);
		}
		return false;
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
				loadManagedUsers();
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
		Label emailValidation = new Label("");
		emailValidation.getStyleClass().add("dialog-error-text");
		email.focusedProperty().addListener((obs, oldValue, focused) -> {
			if (!focused) validateAddUserEmail(email, emailValidation);
		});
		PasswordField password = new PasswordField();
		TextField initials = new TextField();
		ColorPicker colorPicker = new ColorPicker(DEFAULT_STATUS_COLOR);
		CheckBox attorney = new CheckBox("Attorney");
		CheckBox admin = new CheckBox("Admin");
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("First Name"), 0, 0); grid.add(firstName, 1, 0);
		grid.add(new Label("Last Name"), 0, 1); grid.add(lastName, 1, 1);
		grid.add(new Label("Email"), 0, 2); grid.add(email, 1, 2);
		grid.add(emailValidation, 1, 3);
		grid.add(new Label("Temporary Password"), 0, 4); grid.add(password, 1, 4);
		grid.add(new Label("Initials"), 0, 5); grid.add(initials, 1, 5);
		grid.add(new Label("Color"), 0, 6); grid.add(colorPicker, 1, 6);
		grid.add(attorney, 1, 7);
		grid.add(admin, 1, 8);
		dialog.getDialogPane().setContent(grid);
		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) return null;
			String duplicateMessage = validateAddUserEmail(email, emailValidation);
			if (!duplicateMessage.isBlank()) throw new IllegalArgumentException(duplicateMessage);
			return new UserDao.UserCreateRequest(
					trim(firstName.getText()),
					trim(lastName.getText()),
					trim(email.getText()),
					password.getText(),
					fxColorToDb(colorPicker.getValue()),
					trim(initials.getText()),
					attorney.isSelected(),
					admin.isSelected());
		});
		try { return dialog.showAndWait(); }
		catch (RuntimeException ex) { AppDialogs.showError(dialog.getOwner(), "Add User", rootMessage(ex)); return Optional.empty(); }
	}


	private String validateAddUserEmail(TextField emailField, Label emailValidation) {
		String normalized = UserDao.normalizeEmail(trim(emailField == null ? null : emailField.getText()));
		if (normalized.isBlank() || userDao == null) {
			if (emailValidation != null) emailValidation.setText("");
			return "";
		}
		try {
			UserDao.ExistingEmailRow existing = userDao.findExistingEmailForCurrentTenant(normalized);
			String message = existing == null ? "" : UserDao.duplicateEmailMessage(existing.deleted());
			if (emailValidation != null) emailValidation.setText(message);
			return message;
		} catch (RuntimeException ex) {
			String message = rootMessage(ex);
			if (emailValidation != null) emailValidation.setText(message);
			return message;
		}
	}

	@FXML
	private void onToggleInactiveUsers() { loadManagedUsers(); }

	@FXML
	private void onDeactivateUser() {
		UserManagementViewRow selected = selectedManagedUser();
		if (selected == null) return;
		boolean confirmed = AppDialogs.showConfirmation(null, "Deactivate User", "Deactivate this user?", "This will disable their access while preserving historical records.", "Deactivate", AppDialogs.DialogActionKind.DANGER);
		if (!confirmed) return;
		try {
			userDao.deactivateUser(selected.id());
			loadManagedUsers();
			setUserManagementMessage("User deactivated.");
		} catch (RuntimeException ex) {
			AppDialogs.showError(null, "Deactivate User", rootMessage(ex));
		}
	}

	@FXML
	private void onReactivateUser() {
		UserManagementViewRow selected = selectedManagedUser();
		if (selected == null) return;
		try {
			userDao.reactivateUser(selected.id());
			loadManagedUsers();
			setUserManagementMessage("User reactivated.");
		} catch (RuntimeException ex) {
			AppDialogs.showError(null, "Reactivate User", rootMessage(ex));
		}
	}

	@FXML
	private void onResetUserPassword() {
		UserManagementViewRow selected = selectedManagedUser();
		if (selected == null) return;
		Dialog<String> dialog = new Dialog<>();
		dialog.setTitle("Reset Password");
		AppDialogs.applySecondaryDialogShell(dialog, "Reset Password");
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		PasswordField password = new PasswordField();
		PasswordField confirm = new PasswordField();
		Label validation = new Label("");
		validation.getStyleClass().add("dialog-error-text");
		password.textProperty().addListener((obs, oldValue, newValue) -> validation.setText(""));
		confirm.textProperty().addListener((obs, oldValue, newValue) -> validation.setText(""));
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("New Password"), 0, 0); grid.add(password, 1, 0);
		grid.add(new Label("Confirm Password"), 0, 1); grid.add(confirm, 1, 1);
		grid.add(validation, 1, 2);
		dialog.getDialogPane().setContent(grid);
		Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
		okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
			String message = resetPasswordValidationMessage(password.getText(), confirm.getText());
			if (!message.isBlank()) {
				validation.setText(message);
				event.consume();
			}
		});
		dialog.setResultConverter(button -> button == ButtonType.OK ? password.getText() : null);
		try {
			dialog.showAndWait().ifPresent(newPassword -> {
				boolean confirmed = AppDialogs.showConfirmation(null, "Reset Password", "Reset password for " + selected.name() + "?", "Password access will change immediately.", "Reset", AppDialogs.DialogActionKind.PRIMARY);
				if (!confirmed) return;
				userDao.resetPassword(selected.id(), newPassword);
				AppDialogs.showInfo(null, "Reset Password", "Password successfully updated.");
			});
		} catch (RuntimeException ex) {
			AppDialogs.showError(null, "Reset Password", rootMessage(ex));
		}
	}

	static String resetPasswordValidationMessage(String password, String confirmPassword) {
		if (password == null || password.isBlank()) return "Password is required.";
		if (confirmPassword == null || confirmPassword.isBlank()) return "Confirm password is required.";
		if (!Objects.equals(password, confirmPassword)) return "Passwords do not match.";
		return "";
	}

	private void configureUserManagementTable() {
		if (userManagementTable == null) return;
		userNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
		userEmailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
		userInitialsColumn.setCellValueFactory(new PropertyValueFactory<>("initials"));
		userAttorneyColumn.setCellValueFactory(new PropertyValueFactory<>("attorneyState"));
		userAdminColumn.setCellValueFactory(new PropertyValueFactory<>("adminState"));
		userStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
		userManagementTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> updateUserActionButtons(newRow));
	}

	private void loadManagedUsers() {
		if (userDao == null || userManagementTable == null || !isAdminUser()) return;
		try {
			boolean includeInactive = showInactiveUsersCheck != null && showInactiveUsersCheck.isSelected();
			List<UserManagementViewRow> rows = new ArrayList<>();
			for (UserDao.UserManagementRow row : userDao.listUsersForManagement(includeInactive)) rows.add(new UserManagementViewRow(row));
			userManagementTable.getItems().setAll(rows);
			updateUserActionButtons(userManagementTable.getSelectionModel().getSelectedItem());
			setUserManagementMessage(rows.isEmpty() ? "No users found for this tenant." : "");
		} catch (RuntimeException ex) {
			setUserManagementMessage("Failed to load users. " + rootMessage(ex));
		}
	}

	private UserManagementViewRow selectedManagedUser() {
		UserManagementViewRow selected = userManagementTable == null ? null : userManagementTable.getSelectionModel().getSelectedItem();
		if (selected == null) setUserManagementMessage("Select a user first.");
		return selected;
	}

	private void updateUserActionButtons(UserManagementViewRow selected) {
		boolean has = selected != null;
		if (deactivateUserButton != null) deactivateUserButton.setDisable(!has || selected.deleted());
		if (reactivateUserButton != null) reactivateUserButton.setDisable(!has || !selected.deleted());
		if (resetPasswordButton != null) resetPasswordButton.setDisable(!has || selected.deleted());
	}

	private void setUserManagementMessage(String message) { if (userManagementStatusLabel != null) userManagementStatusLabel.setText(message == null ? "" : message); }

	private static String trim(String value) { return value == null ? "" : value.trim(); }

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


	public static final class UserManagementViewRow {
		private final UserDao.UserManagementRow row;
		UserManagementViewRow(UserDao.UserManagementRow row) { this.row = row; }
		public int getId() { return row.id(); }
		public int id() { return row.id(); }
		public String getName() { return safe(row.name()); }
		public String name() { return getName(); }
		public String getEmail() { return safe(row.email()); }
		public String getInitials() { return safe(row.initials()); }
		public String getAttorneyState() { return row.attorney() ? "Yes" : "No"; }
		public String getAdminState() { return row.admin() ? "Yes" : "No"; }
		public String getStatus() { return row.deleted() ? "Inactive" : "Active"; }
		public boolean deleted() { return row.deleted(); }
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
		if (caseStatusAdministrationSection != null) {
			caseStatusAdministrationSection.setVisible(visible);
			caseStatusAdministrationSection.setManaged(visible);
		}
		if (practiceAreaAdministrationSection != null) {
			practiceAreaAdministrationSection.setVisible(visible);
			practiceAreaAdministrationSection.setManaged(visible);
		}
		if (userAdministrationSection != null) {
			userAdministrationSection.setVisible(visible);
			userAdministrationSection.setManaged(visible);
		}
		if (visible) {
			loadCaseStatuses();
			loadPracticeAreas();
			loadManagedUsers();
		}
	}
}
