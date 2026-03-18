package com.shale.ui.controller;

import com.shale.data.dao.UserDao;
import com.shale.data.dao.UserDao.UserDetailRow;
import com.shale.data.dao.UserDao.UserProfileUpdateRequest;
import com.shale.data.dao.UserDao.UserRoleRow;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.dialog.ContactPickerDialog;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
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
	@FXML private Button addRoleButton;
	@FXML private FlowPane rolesFlow;
	@FXML private Label rolesEmptyLabel;

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
	private List<UserRoleRow> assignedRoles = List.of();
	private List<UserRoleRow> assignableRoles = List.of();
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
		if (addRoleButton != null) {
			addRoleButton.setOnAction(e -> onAddRole());
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
					refreshRolesAsync();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setBusy(false);
					setError("Failed to load user details.");
				});
			}
		});
	}

	private void refreshRolesAsync() {
		if (userDao == null || currentUser == null) {
			assignedRoles = List.of();
			assignableRoles = List.of();
			renderRoles();
			return;
		}

		final int targetUserId = currentUser.id();
		final int shaleClientId = currentUser.shaleClientId();
		dbExec.submit(() -> {
			try {
				List<UserRoleRow> loadedAssigned = userDao.listAssignedRoles(targetUserId, shaleClientId);
				List<UserRoleRow> loadedAssignable = canManageRoles()
						? userDao.listAssignableRoles(targetUserId, shaleClientId)
						: List.of();
				Platform.runLater(() -> {
					assignedRoles = loadedAssigned == null ? List.of() : List.copyOf(loadedAssigned);
					assignableRoles = loadedAssignable == null ? List.of() : List.copyOf(loadedAssignable);
					renderRoles();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					assignedRoles = List.of();
					assignableRoles = List.of();
					renderRoles();
					setError("Failed to load roles for this user.");
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

	private void onAddRole() {
		if (!canManageRoles()) {
			setError("Only admin users can manage roles.");
			return;
		}
		if (currentUser == null || userDao == null) {
			setError("User details are unavailable.");
			return;
		}
		if (assignableRoles.isEmpty()) {
			AppDialogs.showInfo(dialogOwner(addRoleButton), "Roles", "No additional roles are available for this user.");
			return;
		}

		ContactPickerDialog<UserRoleRow> picker = new ContactPickerDialog<>(
				dialogOwner(addRoleButton),
				"Add Role",
				assignableRoles,
				role -> role == null ? "" : fallback(role.roleName()),
				null);

		var selected = picker.showAndWait();
		if (selected.isEmpty()) {
			return;
		}

		UserRoleRow chosen = selected.get();
		setBusy(true);
		dbExec.submit(() -> {
			try {
				boolean added = userDao.addRoleToUser(currentUser.id(), chosen.roleId(), currentUser.shaleClientId());
				Platform.runLater(() -> {
					setBusy(false);
					if (!added) {
						setError("Role could not be added. It may already be assigned.");
						refreshRolesAsync();
						return;
					}
					clearError();
					refreshRolesAsync();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setBusy(false);
					setError("Failed to add role to this user.");
				});
			}
		});
	}

	private void onRemoveRole(UserRoleRow role) {
		if (!canManageRoles()) {
			setError("Only admin users can manage roles.");
			return;
		}
		if (currentUser == null || role == null || userDao == null) {
			setError("Role details are unavailable.");
			return;
		}
		boolean confirmed = AppDialogs.showConfirmation(
				dialogOwner(addRoleButton),
				"Remove Role",
				"Remove this role from the user?",
				fallback(role.roleName()),
				"Remove Role",
				AppDialogs.DialogActionKind.DANGER);
		if (!confirmed) {
			return;
		}

		setBusy(true);
		dbExec.submit(() -> {
			try {
				boolean removed = userDao.removeRoleFromUser(currentUser.id(), role.roleId(), currentUser.shaleClientId());
				Platform.runLater(() -> {
					setBusy(false);
					if (!removed) {
						setError("Role could not be removed from this user.");
						refreshRolesAsync();
						return;
					}
					clearError();
					refreshRolesAsync();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setBusy(false);
					setError("Failed to remove role from this user.");
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
		renderRoles();
	}

	private void renderRoles() {
		if (rolesFlow == null) {
			return;
		}
		List<Node> cards = assignedRoles.stream()
				.map(this::createRoleNode)
				.toList();
		rolesFlow.getChildren().setAll(cards);

		boolean empty = cards.isEmpty();
		if (rolesEmptyLabel != null) {
			rolesEmptyLabel.setVisible(empty);
			rolesEmptyLabel.setManaged(empty);
		}
		setVisibleManaged(addRoleButton, canManageRoles());
	}

	private Node createRoleNode(UserRoleRow role) {
		Label roleLabel = new Label(fallback(role == null ? null : role.roleName()));
		roleLabel.setStyle("-fx-font-weight: 600;");

		HBox row;
		if (canManageRoles()) {
			Button removeButton = new Button("Remove");
			removeButton.getStyleClass().add("button-secondary");
			removeButton.setOnAction(e -> onRemoveRole(role));
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			row = new HBox(8, roleLabel, spacer, removeButton);
		} else {
			row = new HBox(roleLabel);
		}
		VBox container = new VBox(row);
		container.getStyleClass().add("secondary-panel");
		container.setPrefWidth(280);
		return container;
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
		setVisibleManaged(addRoleButton, canManageRoles());
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
		if (addRoleButton != null) {
			addRoleButton.setDisable(busy);
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

	private boolean canManageRoles() {
		return currentUser != null && isAdminViewer();
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
			return isViewingSelf()
					? "Admin access: you can edit this profile and manage roles."
					: "Admin access: you can edit this user and manage roles.";
		}
		if (isViewingSelf()) {
			return "You can edit your basic profile fields. Roles are read only.";
		}
		return "Read only: only admins or the user themselves can edit this profile.";
	}

	private Window dialogOwner(Button button) {
		if (button != null && button.getScene() != null) {
			return button.getScene().getWindow();
		}
		return null;
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
