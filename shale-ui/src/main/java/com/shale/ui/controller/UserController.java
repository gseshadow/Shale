package com.shale.ui.controller;

import com.shale.data.dao.CaseDao.CaseRow;
import com.shale.data.dao.UserDao.UserDetailRow;
import com.shale.data.dao.UserDao.UserProfileUpdateRequest;
import com.shale.data.dao.UserDao.UserRoleRow;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.dialog.ContactPickerDialog;
import com.shale.ui.controller.support.CaseListFilterSortSupport;
import com.shale.ui.state.AppState;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.UserDetailService;
import com.shale.ui.util.ColorUtil;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class UserController {

	private static final Color DEFAULT_USER_COLOR = Color.WHITE;
	private static final String COLOR_SWATCH_BASE_STYLE = "-fx-min-width: 22px; -fx-pref-width: 22px; -fx-max-width: 22px; "
			+ "-fx-min-height: 22px; -fx-pref-height: 22px; -fx-max-height: 22px; "
			+ "-fx-background-radius: 11px; -fx-border-radius: 11px; "
			+ "-fx-border-color: rgba(0,0,0,0.18); -fx-border-width: 1px;";

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
	@FXML private VBox assignedCasesContainer;
	@FXML private Label assignedCasesEmptyLabel;
	@FXML private TextField assignedCasesSearchField;
	@FXML private ChoiceBox<String> assignedCasesSortChoice;

	@FXML private Label displayNameValue;
	@FXML private Label firstNameValue;
	@FXML private TextField firstNameEditor;
	@FXML private Label lastNameValue;
	@FXML private TextField lastNameEditor;
	@FXML private Label emailValue;
	@FXML private TextField emailEditor;
	@FXML private Label phoneValue;
	@FXML private TextField phoneEditor;
	@FXML private Label initialsValue;
	@FXML private TextField initialsEditor;
	@FXML private HBox colorValueContainer;
	@FXML private Region colorPreview;
	@FXML private Label colorValue;
	@FXML private HBox colorEditorContainer;
	@FXML private ColorPicker colorEditor;
	@FXML private Label colorEditorValue;

	private Integer userId;
	private UserDetailService userDetailService;
	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private Consumer<Integer> onOpenCase;
	private CaseCardFactory caseCardFactory;
	private Consumer<UiRuntimeBridge.CaseUpdatedEvent> liveCaseUpdatedHandler;
	private boolean liveSubscribed;
	private UserDetailRow currentUser;
	private List<UserRoleRow> assignedRoles = List.of();
	private List<UserRoleRow> assignableRoles = List.of();
	private List<CaseRow> assignedCases = List.of();
	private long assignedCasesRefreshSequence;
	private boolean editMode;
	private boolean colorEditedInSession;

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "user-detail-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(int userId,
			UserDetailService userDetailService,
			AppState appState,
			UiRuntimeBridge runtimeBridge,
			Consumer<Integer> onOpenCase) {
		this.userId = userId;
		this.userDetailService = userDetailService;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.onOpenCase = onOpenCase;
		this.caseCardFactory = new CaseCardFactory(onOpenCase);
		System.out.println("[TRACE ASSIGNED_CASES][UserController.init] selectedUserId=" + userId);
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
		CaseListFilterSortSupport.initializeControls(assignedCasesSearchField, assignedCasesSortChoice, this::renderAssignedCases);
		configureColorEditor();

		setEditMode(false);
		wireLiveRefreshLifecycle();
		Platform.runLater(this::loadUser);
	}

	private void wireLiveRefreshLifecycle() {
		if (assignedCasesContainer == null) {
			return;
		}
		assignedCasesContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
			if (newScene == null) {
				unsubscribeLiveCaseUpdates();
			} else {
				subscribeLiveCaseUpdates();
			}
		});
		subscribeLiveCaseUpdates();
	}

	private void subscribeLiveCaseUpdates() {
		if (runtimeBridge == null || liveSubscribed) {
			return;
		}
		liveCaseUpdatedHandler = this::handleLiveCaseUpdatedEvent;
		runtimeBridge.subscribeCaseUpdated(liveCaseUpdatedHandler);
		liveSubscribed = true;
	}

	private void unsubscribeLiveCaseUpdates() {
		if (!liveSubscribed || runtimeBridge == null || liveCaseUpdatedHandler == null) {
			return;
		}
		runtimeBridge.unsubscribeCaseUpdated(liveCaseUpdatedHandler);
		liveSubscribed = false;
	}

	private void handleLiveCaseUpdatedEvent(UiRuntimeBridge.CaseUpdatedEvent event) {
		if (event == null || currentUser == null) {
			return;
		}
		String mine = runtimeBridge == null ? "" : runtimeBridge.getClientInstanceId();
		if (!mine.isBlank() && mine.equals(event.clientInstanceId())) {
			return;
		}
		Integer currentShaleClientId = appState == null ? null : appState.getShaleClientId();
		if (currentShaleClientId == null || currentShaleClientId <= 0 || event.shaleClientId() != currentShaleClientId) {
			return;
		}
		refreshAssignedCasesAsync();
	}

	private void loadUser() {
		if (userDetailService == null || userId == null || userId <= 0) {
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
				UserDetailRow loaded = userDetailService.loadUser(userId, shaleClientId);
				Platform.runLater(() -> {
					setBusy(false);
					if (loaded == null) {
						setError("User not found.");
						return;
					}
						currentUser = loaded;
						resetAssignedCaseControls();
						renderFromCurrent();
					setEditMode(false);
					clearError();
					refreshRolesAsync();
					refreshAssignedCasesAsync();
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
		if (userDetailService == null || currentUser == null) {
			assignedRoles = List.of();
			assignableRoles = List.of();
			renderRoles();
			return;
		}

		final int targetUserId = currentUser.id();
		final int shaleClientId = currentUser.shaleClientId();
		dbExec.submit(() -> {
			try {
				List<UserRoleRow> loadedAssigned = userDetailService.loadAssignedRoles(targetUserId, shaleClientId);
				List<UserRoleRow> loadedAssignable = canManageRoles()
						? userDetailService.loadAssignableRoles(targetUserId, shaleClientId)
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

	private void refreshAssignedCasesAsync() {
		if (userDetailService == null || currentUser == null) {
			assignedCases = List.of();
			System.out.println("[TRACE ASSIGNED_CASES][UserController.refreshAssignedCasesAsync] "
					+ "userDetailServiceOrCurrentUserMissing=true");
			renderAssignedCases();
			return;
		}
		final int targetUserId = currentUser.id();
		final long requestId = ++assignedCasesRefreshSequence;
		final String targetUserName = safeText(currentUser.displayName());
		final String targetUserEmail = safeText(currentUser.email());
		System.out.println("[TRACE ASSIGNED_CASES][UserController.refreshAssignedCasesAsync] "
				+ "requestId=" + requestId
				+ " selectedUserId=" + targetUserId
				+ " selectedUserName=\"" + targetUserName + "\""
				+ " selectedUserEmail=\"" + targetUserEmail + "\"");
		dbExec.submit(() -> {
			try {
				List<CaseRow> loaded = userDetailService.loadAssignedCases(targetUserId);
				Platform.runLater(() -> {
					if (requestId != assignedCasesRefreshSequence) {
						System.out.println("[TRACE ASSIGNED_CASES][UserController.refreshAssignedCasesAsync] "
								+ "requestId=" + requestId
								+ " selectedUserId=" + targetUserId
								+ " staleResultDiscard=true");
						return;
					}
					assignedCases = loaded == null ? List.of() : List.copyOf(loaded);
					System.out.println("[TRACE ASSIGNED_CASES][UserController.refreshAssignedCasesAsync] "
							+ "requestId=" + requestId
							+ " selectedUserId=" + targetUserId
							+ " controllerRowsReceived=" + assignedCases.size()
							+ " asyncRefreshReplacement=true"
							+ " staleResultDiscard=false");
					renderAssignedCases();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					System.err.println("[TRACE ASSIGNED_CASES][UserController.refreshAssignedCasesAsync] "
							+ "requestId=" + requestId
							+ " selectedUserId=" + targetUserId
							+ " controllerException=" + ex.getMessage());
					ex.printStackTrace(System.err);
					assignedCases = List.of();
					renderAssignedCases();
					setError("Failed to load assigned cases for this user.");
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
		if (currentUser == null || userDetailService == null) {
			setError("User details are unavailable.");
			return;
		}

		UserProfileUpdateRequest request = new UserProfileUpdateRequest(
				currentUser.id(),
				currentUser.shaleClientId(),
				safeText(firstNameEditor == null ? null : firstNameEditor.getText()),
				safeText(lastNameEditor == null ? null : lastNameEditor.getText()),
				safeText(emailEditor == null ? null : emailEditor.getText()),
				safeText(phoneEditor == null ? null : phoneEditor.getText()),
				safeText(initialsEditor == null ? null : initialsEditor.getText()),
				selectedStoredColor());

		setBusy(true);
		dbExec.submit(() -> {
			try {
				boolean updated = userDetailService.updateBasicProfile(request);
				if (!updated) {
					Platform.runLater(() -> {
						setBusy(false);
						setError("User profile could not be saved.");
					});
					return;
				}

				UserDetailRow reloaded = userDetailService.loadUser(currentUser.id(), currentUser.shaleClientId());
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
		if (currentUser == null || userDetailService == null) {
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
				boolean added = userDetailService.addRoleToUser(currentUser.id(), chosen.roleId(), currentUser.shaleClientId());
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
		if (currentUser == null || role == null || userDetailService == null) {
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
				boolean removed = userDetailService.removeRoleFromUser(currentUser.id(), role.roleId(), currentUser.shaleClientId());
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
		setLabel(initialsValue, currentUser.initials());
		renderColorValue(currentUser.color());
		writeEditorsFromCurrent();
		refreshActionVisibility();
		renderRoles();
		renderAssignedCases();
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

	private void renderAssignedCases() {
		if (assignedCasesContainer == null) {
			return;
		}
		if (caseCardFactory == null) {
			caseCardFactory = new CaseCardFactory(onOpenCase);
		}
		String query = normalizedAssignedCaseQuery();
		Comparator<CaseRow> comparator = assignedCasesComparator();
		boolean hasTextSearchFilter = !query.isEmpty();
		boolean hasStatusFilterAfterDao = false;
		boolean sortCurrentPageOnly = true;
		boolean paginationTruncationAfterDao = false;
		boolean clientSideAttorneyOrNameFilter = hasTextSearchFilter;
		System.out.println("[TRACE ASSIGNED_CASES][UserController.renderAssignedCases] "
				+ "selectedUserId=" + (currentUser == null ? null : currentUser.id())
				+ " renderInputRowCount=" + assignedCases.size()
				+ " textSearchFilterApplied=" + hasTextSearchFilter
				+ " statusFilterApplied=" + hasStatusFilterAfterDao
				+ " sortOnlyCurrentPageLogicApplied=" + sortCurrentPageOnly
				+ " paginationTruncationApplied=" + paginationTruncationAfterDao
				+ " clientSideFilterByResponsibleAttorneyNameOrIdApplied=" + clientSideAttorneyOrNameFilter);
		List<Node> cards = assignedCases.stream()
				.filter(row -> CaseListFilterSortSupport.matchesQuery(query, row.name(), row.responsibleAttorneyName()))
				.sorted(comparator)
				.map(this::createAssignedCaseCard)
				.toList();
		System.out.println("[TRACE ASSIGNED_CASES][UserController.renderAssignedCases] "
				+ "selectedUserId=" + (currentUser == null ? null : currentUser.id())
				+ " rawAssignedCases=" + assignedCases.size()
				+ " query=\"" + query + "\""
				+ " renderedCardCount=" + cards.size());
		assignedCasesContainer.getChildren().setAll(cards);
		System.out.println("[TRACE ASSIGNED_CASES][UserController.renderAssignedCases] "
				+ "selectedUserId=" + (currentUser == null ? null : currentUser.id())
				+ " finalCardCountDisplayed=" + assignedCasesContainer.getChildren().size());
		boolean empty = cards.isEmpty();
		if (assignedCasesEmptyLabel != null) {
			assignedCasesEmptyLabel.setVisible(empty);
			assignedCasesEmptyLabel.setManaged(empty);
			if (!empty) {
				assignedCasesEmptyLabel.toBack();
			} else if (!query.isEmpty()) {
				assignedCasesEmptyLabel.setText("No assigned cases match your search");
			} else {
				assignedCasesEmptyLabel.setText("No assigned cases");
			}
		}
	}

	private String normalizedAssignedCaseQuery() {
		return CaseListFilterSortSupport.normalizedQuery(assignedCasesSearchField);
	}

	private Comparator<CaseRow> assignedCasesComparator() {
		return CaseListFilterSortSupport.comparator(
				assignedCasesSortChoice,
				CaseRow::name,
				CaseRow::intakeDate,
				CaseRow::statuteOfLimitationsDate);
	}

	private void resetAssignedCaseControls() {
		CaseListFilterSortSupport.resetControls(assignedCasesSearchField, assignedCasesSortChoice);
	}

	private Node createAssignedCaseCard(CaseRow row) {
		Node card = caseCardFactory.create(new CaseCardModel(
				Math.toIntExact(row.id()),
				row.name(),
				row.intakeDate(),
				row.statuteOfLimitationsDate(),
				row.responsibleAttorneyName(),
				row.responsibleAttorneyColor()));
		if (card instanceof Region region) {
			region.setMaxWidth(Double.MAX_VALUE);
			region.setPrefWidth(340);
		}
		return card;
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
		if (initialsEditor != null) {
			initialsEditor.setText(safeText(currentUser.initials()));
		}
		setEditorColor(currentUser.color());
	}

	private void setEditMode(boolean enabled) {
		this.editMode = enabled && canEditCurrentUser();
		refreshActionVisibility();

		toggleEditableField(firstNameValue, firstNameEditor, this.editMode);
		toggleEditableField(lastNameValue, lastNameEditor, this.editMode);
		toggleEditableField(emailValue, emailEditor, this.editMode);
		toggleEditableField(phoneValue, phoneEditor, this.editMode);
		toggleEditableField(initialsValue, initialsEditor, this.editMode);
		toggleEditableField(colorValueContainer, colorEditorContainer, this.editMode);
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
		if (initialsEditor != null) {
			initialsEditor.setDisable(busy);
		}
		if (colorEditor != null) {
			colorEditor.setDisable(busy);
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

	private void configureColorEditor() {
		if (colorPreview != null) {
			colorPreview.setStyle(COLOR_SWATCH_BASE_STYLE + " -fx-background-color: white;");
		}
		if (colorEditor != null) {
			colorEditor.setValue(DEFAULT_USER_COLOR);
			colorEditor.setOnAction(e -> {
				colorEditedInSession = true;
				updateColorEditorValueLabel(colorEditor.getValue());
			});
		}
		updateColorEditorValueLabel(colorEditor == null ? DEFAULT_USER_COLOR : colorEditor.getValue());
	}

	private void renderColorValue(String storedColor) {
		if (colorValue != null) {
			colorValue.setText(ColorUtil.toDisplayValue(storedColor));
		}
		updateColorPreview(colorPreview, ColorUtil.toFxColor(storedColor));
	}

	private void setEditorColor(String storedColor) {
		colorEditedInSession = false;
		Color fxColor = ColorUtil.toFxColor(storedColor);
		if (colorEditor != null) {
			colorEditor.setValue(fxColor);
		}
		updateColorEditorValueLabel(fxColor);
	}

	private void updateColorEditorValueLabel(Color color) {
		if (colorEditorValue != null) {
			colorEditorValue.setText(color == null ? "—" : "#" + ColorUtil.toStoredColor(color));
		}
	}

	private String selectedStoredColor() {
		if (!colorEditedInSession && currentUser != null) {
			return currentUser.color();
		}
		Color selected = colorEditor == null ? DEFAULT_USER_COLOR : colorEditor.getValue();
		return ColorUtil.toStoredColor(selected == null ? DEFAULT_USER_COLOR : selected);
	}

	private static void updateColorPreview(Region preview, Color color) {
		if (preview == null) {
			return;
		}
		Color resolved = color == null ? DEFAULT_USER_COLOR : color;
		preview.setStyle(COLOR_SWATCH_BASE_STYLE + " -fx-background-color: " + ColorUtil.toCssBackgroundColor(ColorUtil.toStoredColor(resolved)) + ";");
	}

	private static void toggleEditableField(Node valueNode, Node editorNode, boolean editMode) {
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

	private static String safeText(String value) {
		return value == null ? "" : value.trim();
	}

	private static String fallback(String value) {
		return value == null || value.isBlank() ? "—" : value.trim();
	}
}
