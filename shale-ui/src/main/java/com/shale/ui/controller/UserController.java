package com.shale.ui.controller;

import com.shale.data.dao.UserDao;
import com.shale.data.dao.UserDao.UserDetailRow;
import com.shale.data.dao.UserDao.UserProfileUpdateRequest;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UserController {

	@FXML private Label userTitleLabel;
	@FXML private Label userSubtitleLabel;
	@FXML private Label userPermissionLabel;
	@FXML private Label errorLabel;
	@FXML private Button editButton;
	@FXML private Button saveButton;
	@FXML private Button cancelButton;

	@FXML private Label displayNameValue;
	@FXML private Label firstNameValue;
	@FXML private TextField firstNameEditor;
	@FXML private Label lastNameValue;
	@FXML private TextField lastNameEditor;
	@FXML private Label emailValue;
	@FXML private TextField emailEditor;
	@FXML private Label phoneValue;
	@FXML private TextField phoneEditor;
	@FXML private Label isAdminValue;
	@FXML private Label isAttorneyValue;
	@FXML private Label initialsValue;
	@FXML private Label defaultOrganizationValue;
	@FXML private Label organizationValue;

	private Integer userId;
	private UserDao userDao;
	private AppState appState;
	private UserDetailRow currentUser;
	private boolean editMode;

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "user-detail-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(int userId, UserDao userDao, AppState appState) {
		this.userId = userId;
		this.userDao = userDao;
		this.appState = appState;
	}

	@FXML
	private void initialize() {
		if (editButton != null) {
			editButton.setOnAction(e -> onEdit());
		}
		if (saveButton != null) {
			saveButton.setOnAction(e -> onSave());
		}
		if (cancelButton != null) {
			cancelButton.setOnAction(e -> onCancel());
		}

		setEditMode(false);
		Platform.runLater(this::loadUser);
	}

	private void loadUser() {
		if (userDao == null || userId == null || userId <= 0) {
			setError("User view is not configured.");
			return;
		}
		Integer shaleClientId = appState == null ? null : appState.getShaleClientId();
		if (shaleClientId == null || shaleClientId <= 0) {
			setError("No tenant is selected.");
			return;
		}

		setBusy(true);
		dbExec.submit(() -> {
			try {
				UserDetailRow loaded = userDao.findById(userId, shaleClientId);
				Platform.runLater(() -> {
					setBusy(false);
					if (loaded == null) {
						setError("User not found.");
						return;
					}
					currentUser = loaded;
					renderFromCurrent();
					setEditMode(false);
					clearError();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setBusy(false);
					setError("Failed to load user details.");
				});
			}
		});
	}

	private void onEdit() {
		if (!canEditCurrentUser()) {
			setError("You do not have permission to edit this user.");
			return;
		}
		if (currentUser == null) {
			setError("User details are unavailable.");
			return;
		}
		writeEditorsFromCurrent();
		setEditMode(true);
		clearError();
	}

	private void onCancel() {
		if (currentUser != null) {
			writeEditorsFromCurrent();
			renderFromCurrent();
		}
		setEditMode(false);
		clearError();
	}

	private void onSave() {
		if (!canEditCurrentUser()) {
			setError("You do not have permission to save changes for this user.");
			return;
		}
		if (currentUser == null || userDao == null) {
			setError("User details are unavailable.");
			return;
		}

		UserProfileUpdateRequest request = new UserProfileUpdateRequest(
				currentUser.id(),
				currentUser.shaleClientId(),
				safeText(firstNameEditor == null ? null : firstNameEditor.getText()),
				safeText(lastNameEditor == null ? null : lastNameEditor.getText()),
				safeText(emailEditor == null ? null : emailEditor.getText()),
				safeText(phoneEditor == null ? null : phoneEditor.getText()));

		setBusy(true);
		dbExec.submit(() -> {
			try {
				boolean updated = userDao.updateBasicProfile(request);
				if (!updated) {
					Platform.runLater(() -> {
						setBusy(false);
						setError("User profile could not be saved.");
					});
					return;
				}

				UserDetailRow reloaded = userDao.findById(currentUser.id(), currentUser.shaleClientId());
				Platform.runLater(() -> {
					setBusy(false);
					if (reloaded == null) {
						setError("User could not be reloaded after save.");
						return;
					}
					currentUser = reloaded;
					if (isViewingSelf() && appState != null) {
						appState.setUserEmail(reloaded.email());
					}
					renderFromCurrent();
					setEditMode(false);
					clearError();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setBusy(false);
					setError("Failed to save user profile.");
				});
			}
		});
	}

	private void renderFromCurrent() {
		if (currentUser == null) {
			return;
		}

		String displayName = displayName(currentUser);
		if (userTitleLabel != null) {
			userTitleLabel.setText(displayName);
		}
		if (userSubtitleLabel != null) {
			userSubtitleLabel.setText("User #" + currentUser.id());
		}
		if (userPermissionLabel != null) {
			userPermissionLabel.setText(permissionMessage());
		}

		setLabel(displayNameValue, displayName);
		setLabel(firstNameValue, currentUser.firstName());
		setLabel(lastNameValue, currentUser.lastName());
		setLabel(emailValue, currentUser.email());
		setLabel(phoneValue, currentUser.phone());
		setLabel(isAdminValue, yesNo(currentUser.admin()));
		setLabel(isAttorneyValue, yesNo(currentUser.attorney()));
		setLabel(initialsValue, currentUser.initials());
		setLabel(defaultOrganizationValue, nullableId(currentUser.defaultOrganizationId()));
		setLabel(organizationValue, nullableId(currentUser.organizationId()));
		writeEditorsFromCurrent();
		refreshActionVisibility();
	}

	private void writeEditorsFromCurrent() {
		if (currentUser == null) {
			return;
		}
		if (firstNameEditor != null) {
			firstNameEditor.setText(safeText(currentUser.firstName()));
		}
		if (lastNameEditor != null) {
			lastNameEditor.setText(safeText(currentUser.lastName()));
		}
		if (emailEditor != null) {
			emailEditor.setText(safeText(currentUser.email()));
		}
		if (phoneEditor != null) {
			phoneEditor.setText(safeText(currentUser.phone()));
		}
	}

	private void setEditMode(boolean enabled) {
		this.editMode = enabled && canEditCurrentUser();
		refreshActionVisibility();

		toggleEditableField(firstNameValue, firstNameEditor, this.editMode);
		toggleEditableField(lastNameValue, lastNameEditor, this.editMode);
		toggleEditableField(emailValue, emailEditor, this.editMode);
		toggleEditableField(phoneValue, phoneEditor, this.editMode);
	}

	private void refreshActionVisibility() {
		boolean canEdit = canEditCurrentUser();
		setVisibleManaged(editButton, canEdit && !editMode);
		setVisibleManaged(saveButton, canEdit && editMode);
		setVisibleManaged(cancelButton, canEdit && editMode);
	}

	private void setBusy(boolean busy) {
		if (editButton != null) {
			editButton.setDisable(busy);
		}
		if (saveButton != null) {
			saveButton.setDisable(busy);
		}
		if (cancelButton != null) {
			cancelButton.setDisable(busy);
		}
		if (firstNameEditor != null) {
			firstNameEditor.setDisable(busy);
		}
		if (lastNameEditor != null) {
			lastNameEditor.setDisable(busy);
		}
		if (emailEditor != null) {
			emailEditor.setDisable(busy);
		}
		if (phoneEditor != null) {
			phoneEditor.setDisable(busy);
		}
	}

	private boolean canEditCurrentUser() {
		return currentUser != null && (isAdminViewer() || isViewingSelf());
	}

	private boolean isAdminViewer() {
		return appState != null && appState.isAdmin();
	}

	private boolean isViewingSelf() {
		return appState != null
				&& appState.getUserId() != null
				&& currentUser != null
				&& appState.getUserId().intValue() == currentUser.id();
	}

	private String permissionMessage() {
		if (currentUser == null) {
			return "Read only";
		}
		if (isAdminViewer()) {
			return isViewingSelf() ? "Admin access: you can edit your profile." : "Admin access: you can edit this user.";
		}
		if (isViewingSelf()) {
			return "You can edit your basic profile fields.";
		}
		return "Read only: only admins or the user themselves can edit this profile.";
	}

	private static void toggleEditableField(Label valueNode, Node editorNode, boolean editMode) {
		setVisibleManaged(valueNode, !editMode);
		setVisibleManaged(editorNode, editMode);
	}

	private static void setVisibleManaged(Node node, boolean visible) {
		if (node == null) {
			return;
		}
		node.setVisible(visible);
		node.setManaged(visible);
	}

	private void setError(String message) {
		if (errorLabel == null) {
			return;
		}
		errorLabel.setText(message == null ? "" : message);
		setVisibleManaged(errorLabel, message != null && !message.isBlank());
	}

	private void clearError() {
		setError("");
	}

	private static void setLabel(Label label, String value) {
		if (label != null) {
			label.setText(fallback(value));
		}
	}

	private static String displayName(UserDetailRow user) {
		if (user == null) {
			return "User";
		}
		String name = fallback(user.displayName());
		if (!"—".equals(name)) {
			return name;
		}
		String email = fallback(user.email());
		return "—".equals(email) ? "User #" + user.id() : email;
	}

	private static String nullableId(Integer value) {
		return value == null ? "—" : Integer.toString(value);
	}

	private static String yesNo(boolean value) {
		return value ? "Yes" : "No";
	}

	private static String safeText(String value) {
		return value == null ? "" : value.trim();
	}

	private static String fallback(String value) {
		return value == null || value.isBlank() ? "—" : value.trim();
	}
}
