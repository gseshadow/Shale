package com.shale.ui.controller;

import com.shale.data.dao.CaseDao.CaseRow;
import com.shale.data.dao.TaskDao.AssignedUserTaskRow;
import com.shale.data.dao.UserDao.UserDetailRow;
import com.shale.data.dao.UserDao.UserProfileUpdateRequest;
import com.shale.data.dao.UserDao.UserRoleRow;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.core.dto.TaskStatusOptionDto;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.dialog.ContactPickerDialog;
import com.shale.ui.component.dialog.TaskDetailDialog;
import com.shale.ui.component.factory.TaskCardFactory;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.controller.support.CaseListFilterSortSupport;
import com.shale.ui.state.AppState;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.UserDetailService;
import com.shale.ui.util.PerfLog;
import com.shale.ui.util.ColorUtil;
import com.shale.ui.util.ReadOnlyTextDisplaySupport;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class UserController {

	private static final Color DEFAULT_USER_COLOR = Color.WHITE;
	private static final String TASK_SORT_RECENT = "Recent";
	private static final String TASK_SORT_DUE_DATE = "Due Date";
	private static final String TASK_SORT_NAME = "Name";
	private static final String TASK_SORT_PRIORITY = "Priority";
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
	@FXML private VBox assignedTasksContainer;
	@FXML private Label assignedTasksEmptyLabel;
	@FXML private TextField assignedTasksSearchField;
	@FXML private ChoiceBox<String> assignedTasksSortChoice;
	@FXML private ScrollPane assignedTasksScroll;

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
	private Consumer<Integer> onOpenUser;
	private CaseTaskService caseTaskService;
	private CaseCardFactory caseCardFactory;
	private TaskCardFactory taskCardFactory;
	private Consumer<UiRuntimeBridge.CaseUpdatedEvent> liveCaseUpdatedHandler;
	private boolean liveSubscribed;
	private UserDetailRow currentUser;
	private List<UserRoleRow> assignedRoles = List.of();
	private List<UserRoleRow> assignableRoles = List.of();
	private List<CaseRow> assignedCases = List.of();
	private List<AssignedUserTaskRow> assignedTasks = List.of();
	private java.util.Map<Long, List<TaskCardFactory.AssignedUserModel>> assignedTaskUsers = java.util.Map.of();
	private long assignedCasesRefreshSequence;
	private long assignedTasksRefreshSequence;
	private boolean editMode;
	private boolean colorEditedInSession;
	private long pageLoadStartNanos;
	private final AtomicBoolean taskDetailDialogInFlight = new AtomicBoolean(false);

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "user-detail-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(int userId,
			UserDetailService userDetailService,
			AppState appState,
			UiRuntimeBridge runtimeBridge,
			Consumer<Integer> onOpenCase,
			Consumer<Integer> onOpenUser,
			CaseTaskService caseTaskService) {
		this.userId = userId;
		this.userDetailService = userDetailService;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.onOpenCase = onOpenCase;
		this.onOpenUser = onOpenUser == null ? id -> {
		} : onOpenUser;
		this.caseTaskService = caseTaskService;
		this.caseCardFactory = new CaseCardFactory(onOpenCase);
		this.taskCardFactory = new TaskCardFactory(
				this::openTask,
				this::onToggleAssignedTaskComplete,
				onOpenCase,
				this::onOpenUserFromTask);
		this.pageLoadStartNanos = PerfLog.start();
		PerfLog.log("NAV", "start", "page=user_view userId=" + userId);
		PerfLog.log("CTRL", "start", "controller=UserController page=user_view userId=" + userId);
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
		initializeAssignedTaskControls();
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
				long daoStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=loadUser page=user_view userId=" + userId + " organizationId=" + shaleClientId);
				UserDetailRow loaded = userDetailService.loadUser(userId, shaleClientId);
				PerfLog.logDone("DAO", "method=loadUser page=user_view userId=" + userId + " organizationId=" + shaleClientId + " rows=" + (loaded == null ? 0 : 1), daoStartNanos);
				Platform.runLater(() -> {
					setBusy(false);
					if (loaded == null) {
						setError("User not found.");
						return;
					}
						currentUser = loaded;
						resetAssignedCaseControls();
						resetAssignedTaskControls();
						renderFromCurrent();
					setEditMode(false);
					clearError();
					refreshRolesAsync();
					refreshAssignedCasesAsync();
					refreshAssignedTasksAsync();
					PerfLog.logDone("NAV", "ready page=user_view userId=" + userId, pageLoadStartNanos);
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
				long assignedRolesStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=loadAssignedRoles page=user_view userId=" + targetUserId + " organizationId=" + shaleClientId);
				List<UserRoleRow> loadedAssigned = userDetailService.loadAssignedRoles(targetUserId, shaleClientId);
				PerfLog.logDone("DAO", "method=loadAssignedRoles page=user_view userId=" + targetUserId + " organizationId=" + shaleClientId + " rows=" + (loadedAssigned == null ? 0 : loadedAssigned.size()), assignedRolesStartNanos);
				long assignableRolesStartNanos = PerfLog.start();
				List<UserRoleRow> loadedAssignable = canManageRoles()
						? userDetailService.loadAssignableRoles(targetUserId, shaleClientId)
						: List.of();
				PerfLog.logDone("DAO", "method=loadAssignableRoles page=user_view userId=" + targetUserId + " organizationId=" + shaleClientId + " rows=" + (loadedAssignable == null ? 0 : loadedAssignable.size()), assignableRolesStartNanos);
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
				long assignedCasesStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=loadAssignedCases page=user_view userId=" + targetUserId);
				List<CaseRow> loaded = userDetailService.loadAssignedCases(targetUserId);
				PerfLog.logDone("DAO", "method=loadAssignedCases page=user_view userId=" + targetUserId + " rows=" + (loaded == null ? 0 : loaded.size()), assignedCasesStartNanos);
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

	private void refreshAssignedTasksAsync() {
		if (userDetailService == null || currentUser == null || appState == null) {
			assignedTasks = List.of();
			assignedTaskUsers = java.util.Map.of();
			renderAssignedTasks();
			return;
		}
		Integer shaleClientId = appState.getShaleClientId();
		if (shaleClientId == null || shaleClientId <= 0) {
			assignedTasks = List.of();
			assignedTaskUsers = java.util.Map.of();
			renderAssignedTasks();
			return;
		}
		final int targetUserId = currentUser.id();
		final int tenantId = shaleClientId;
		final long requestId = ++assignedTasksRefreshSequence;
		dbExec.submit(() -> {
			try {
				long assignedTasksStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=loadAssignedTasks page=user_view userId=" + targetUserId + " organizationId=" + tenantId);
				List<AssignedUserTaskRow> loaded = userDetailService.loadAssignedTasks(tenantId, targetUserId);
				PerfLog.logDone("DAO", "method=loadAssignedTasks page=user_view userId=" + targetUserId + " organizationId=" + tenantId + " rows=" + (loaded == null ? 0 : loaded.size()), assignedTasksStartNanos);
				List<Long> taskIds = (loaded == null ? List.<AssignedUserTaskRow>of() : loaded).stream()
						.map(AssignedUserTaskRow::taskId)
						.toList();
				long assignedTaskUsersStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=loadAssignedUsersForTasks page=user_view userId=" + targetUserId + " organizationId=" + tenantId);
				java.util.Map<Long, List<TaskCardFactory.AssignedUserModel>> usersByTask = caseTaskService == null
						? java.util.Map.of()
						: caseTaskService.loadAssignedUsersForTasks(taskIds, tenantId).stream()
								.collect(java.util.stream.Collectors.groupingBy(
										CaseTaskService.TaskAssignedUsersByTask::taskId,
										java.util.stream.Collectors.mapping(
												row -> new TaskCardFactory.AssignedUserModel(
														row.userId(),
														row.displayName(),
												row.color()),
												java.util.stream.Collectors.toList())));
				PerfLog.logDone("DAO", "method=loadAssignedUsersForTasks page=user_view userId=" + targetUserId + " organizationId=" + tenantId + " rows=" + usersByTask.size(), assignedTaskUsersStartNanos);
				Platform.runLater(() -> {
					if (requestId != assignedTasksRefreshSequence) {
						return;
					}
					assignedTasks = loaded == null ? List.of() : List.copyOf(loaded);
					assignedTaskUsers = usersByTask;
					renderAssignedTasks();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					assignedTasks = List.of();
					assignedTaskUsers = java.util.Map.of();
					renderAssignedTasks();
					setError("Failed to load assigned tasks for this user.");
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
		renderAssignedTasks();
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
		long renderStartNanos = PerfLog.start();
		PerfLog.log("RENDER", "start", "panel=assigned_cases page=user_view userId=" + (currentUser == null ? null : currentUser.id()));
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
		PerfLog.logDone("RENDER", "panel=assigned_cases page=user_view userId=" + (currentUser == null ? null : currentUser.id()) + " childCount=" + assignedCasesContainer.getChildren().size(), renderStartNanos);
	}

	private void initializeAssignedTaskControls() {
		if (assignedTasksSortChoice != null) {
			assignedTasksSortChoice.getItems().setAll(TASK_SORT_RECENT, TASK_SORT_DUE_DATE, TASK_SORT_PRIORITY, TASK_SORT_NAME);
			assignedTasksSortChoice.getSelectionModel().select(TASK_SORT_RECENT);
			assignedTasksSortChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> renderAssignedTasks());
		}
		if (assignedTasksSearchField != null) {
			assignedTasksSearchField.textProperty().addListener((obs, oldV, newV) -> renderAssignedTasks());
		}
	}

	private void resetAssignedTaskControls() {
		if (assignedTasksSearchField != null) {
			assignedTasksSearchField.clear();
		}
		if (assignedTasksSortChoice != null) {
			assignedTasksSortChoice.getSelectionModel().select(TASK_SORT_RECENT);
		}
	}

	private void renderAssignedTasks() {
		if (assignedTasksContainer == null) {
			return;
		}
		long renderStartNanos = PerfLog.start();
		PerfLog.log("RENDER", "start", "panel=assigned_tasks page=user_view userId=" + (currentUser == null ? null : currentUser.id()));
		if (taskCardFactory == null) {
			taskCardFactory = new TaskCardFactory(this::openTask, this::onToggleAssignedTaskComplete, onOpenCase, this::onOpenUserFromTask);
		}
		String query = normalizedAssignedTaskQuery();
		Comparator<AssignedUserTaskRow> comparator = assignedTasksComparator();
		List<Node> taskCards = assignedTasks.stream()
				.filter(task -> matchesAssignedTaskQuery(task, query))
				.sorted(comparator)
				.map(this::createAssignedTaskCard)
				.toList();
		assignedTasksContainer.getChildren().setAll(taskCards);
		boolean empty = taskCards.isEmpty();
		if (assignedTasksEmptyLabel != null) {
			assignedTasksEmptyLabel.setVisible(empty);
			assignedTasksEmptyLabel.setManaged(empty);
			if (!empty) {
				assignedTasksEmptyLabel.toBack();
			} else if (!query.isEmpty()) {
				assignedTasksEmptyLabel.setText("No assigned tasks match your search");
			} else {
				assignedTasksEmptyLabel.setText("No assigned tasks");
			}
		}
		PerfLog.logDone("RENDER", "panel=assigned_tasks page=user_view userId=" + (currentUser == null ? null : currentUser.id()) + " childCount=" + assignedTasksContainer.getChildren().size(), renderStartNanos);
	}

	private Node createAssignedTaskCard(AssignedUserTaskRow row) {
		TaskCardFactory.TaskCardModel model = new TaskCardFactory.TaskCardModel(
				row.taskId(),
				row.caseId(),
				row.caseName(),
				row.caseResponsibleAttorney(),
				row.caseResponsibleAttorneyColor(),
				row.caseNonEngagementLetterSent(),
				row.title(),
				row.description(),
				null,
				row.priorityColorHex(),
				row.dueAt(),
				row.completedAt(),
				assignedTaskUsers.getOrDefault(row.taskId(), List.of()));
		Node card = taskCardFactory.create(model, TaskCardFactory.Variant.COMPACT_FLUID);
		if (card instanceof Region region) {
			region.setMaxWidth(Double.MAX_VALUE);
		}
		return card;
	}

	private String normalizedAssignedTaskQuery() {
		return assignedTasksSearchField == null || assignedTasksSearchField.getText() == null
				? ""
				: assignedTasksSearchField.getText().trim().toLowerCase(Locale.ROOT);
	}

	private Comparator<AssignedUserTaskRow> assignedTasksComparator() {
		String selected = assignedTasksSortChoice == null ? TASK_SORT_RECENT : assignedTasksSortChoice.getValue();
		if (TASK_SORT_NAME.equals(selected)) {
			return Comparator.comparing((AssignedUserTaskRow task) -> safeText(task.title()), String.CASE_INSENSITIVE_ORDER)
					.thenComparingLong(AssignedUserTaskRow::taskId);
		}
		if (TASK_SORT_DUE_DATE.equals(selected)) {
			return Comparator.comparing(
					AssignedUserTaskRow::dueAt,
					Comparator.nullsLast(Comparator.naturalOrder()))
					.thenComparing(AssignedUserTaskRow::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
					.thenComparingLong(AssignedUserTaskRow::taskId);
		}
		if (TASK_SORT_PRIORITY.equals(selected)) {
			return Comparator.comparing(
					AssignedUserTaskRow::prioritySortOrder,
					Comparator.nullsLast(Comparator.naturalOrder()))
					.thenComparing(AssignedUserTaskRow::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
					.thenComparingLong(AssignedUserTaskRow::taskId);
		}
		return Comparator.comparing(AssignedUserTaskRow::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(AssignedUserTaskRow::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparingLong(AssignedUserTaskRow::taskId);
	}

	private boolean matchesAssignedTaskQuery(AssignedUserTaskRow task, String query) {
		if (task == null) {
			return false;
		}
		if (query == null || query.isBlank()) {
			return true;
		}
		return safeText(task.title()).toLowerCase(Locale.ROOT).contains(query)
				|| safeText(task.description()).toLowerCase(Locale.ROOT).contains(query)
				|| safeText(task.caseName()).toLowerCase(Locale.ROOT).contains(query)
				|| safeText(task.priorityName()).toLowerCase(Locale.ROOT).contains(query);
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
				row.responsibleAttorneyColor(),
				row.nonEngagementLetterSent()));
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

	private void onOpenUserFromTask(Integer selectedUserId) {
		if (selectedUserId == null || selectedUserId <= 0) {
			return;
		}
		if (currentUser != null && currentUser.id() == selectedUserId.intValue()) {
			return;
		}
		onOpenUser.accept(selectedUserId);
	}

	private void openTask(Long taskId) {
		showTaskDetailPopup(taskId);
	}

	private void onToggleAssignedTaskComplete(Long taskId) {
		if (taskId == null || taskId <= 0 || caseTaskService == null || appState == null) {
			return;
		}
		Integer shaleClientId = appState.getShaleClientId();
		if (shaleClientId == null || shaleClientId <= 0) {
			showTaskActionError("Unable to update task right now.");
			return;
		}
		boolean currentlyCompleted = findAssignedTaskById(taskId)
				.map(task -> task.completedAt() != null)
				.orElse(false);
		new Thread(() -> {
			try {
				if (currentlyCompleted) {
					caseTaskService.uncompleteTask(taskId, shaleClientId, appState.getUserId());
				} else {
					caseTaskService.completeTask(taskId, shaleClientId, appState.getUserId());
				}
				Platform.runLater(this::refreshAssignedTasksAsync);
			} catch (Exception ex) {
				Platform.runLater(() -> showTaskActionError("Failed to update task completion."));
			}
		}, "user-view-toggle-task-" + taskId).start();
	}

	private Optional<AssignedUserTaskRow> findAssignedTaskById(Long taskId) {
		if (taskId == null || assignedTasks == null) {
			return Optional.empty();
		}
		for (AssignedUserTaskRow task : assignedTasks) {
			if (task.taskId() == taskId.longValue()) {
				return Optional.of(task);
			}
		}
		return Optional.empty();
	}

	private void showTaskDetailPopup(Long taskId) {
		if (taskId == null || taskId <= 0 || caseTaskService == null || appState == null) {
			return;
		}
		Integer shaleClientId = appState.getShaleClientId();
		Integer currentUserId = appState.getUserId();
		if (shaleClientId == null || shaleClientId <= 0 || currentUserId == null || currentUserId <= 0) {
			showTaskActionError("You must be signed in to edit tasks.");
			return;
		}
		if (!taskDetailDialogInFlight.compareAndSet(false, true)) {
			return;
		}

		new Thread(() -> {
			try {
				TaskDetailDto detail = caseTaskService.loadTaskDetail(taskId, shaleClientId);
				List<TaskStatusOptionDto> statuses = caseTaskService.loadActiveTaskStatuses(shaleClientId);
				List<TaskPriorityOptionDto> priorities = caseTaskService.loadActivePriorities(shaleClientId);
				List<CaseTaskService.AssignedTaskUserOption> assignedTeam = detail == null
						? List.of()
						: caseTaskService.loadAssignedUsersForTask(detail.id(), shaleClientId);
					List<TaskDetailDialog.TaskActivityEntry> activityEntries = detail == null
							? List.of()
							: caseTaskService.loadTaskActivity(detail.id(), shaleClientId).stream()
									.map(item -> new TaskDetailDialog.TaskActivityEntry(
											item.title(),
											item.body(),
											item.actorDisplayName(),
											item.occurredAt()))
									.toList();
					List<TaskDetailDialog.TaskNoteEntry> noteEntries = detail == null
							? List.of()
							: caseTaskService.loadTaskNotes(detail.id(), shaleClientId).stream()
									.map(note -> new TaskDetailDialog.TaskNoteEntry(
											note.id(),
											note.userId(),
											note.userDisplayName(),
											note.body(),
											note.createdAt(),
											note.updatedAt(),
											note.userId() == currentUserId))
									.toList();
				Platform.runLater(() -> {
					try {
						if (detail == null) {
							showTaskActionError("Task was not found or may have been deleted.");
							refreshAssignedTasksAsync();
							return;
						}
						TaskDetailDialog.TaskDetailModel model = new TaskDetailDialog.TaskDetailModel(
								detail.id(),
								detail.caseId(),
								detail.caseName(),
								detail.caseResponsibleAttorney(),
								detail.caseResponsibleAttorneyColor(),
				detail.caseNonEngagementLetterSent(),
						detail.title(),
						detail.description(),
						detail.dueAt(),
						detail.statusId(),
						detail.priorityId(),
								detail.createdByDisplayName(),
									assignedTeam.stream()
											.map(member -> new TaskDetailDialog.AssignedTeamMember(
													member.userId(),
													member.displayName(),
													member.color()))
											.toList(),
									activityEntries,
									noteEntries,
									detail.completedAt() != null);
						Optional<TaskDetailDialog.TaskDetailResult> result = TaskDetailDialog.showAndWait(
								"USER_CONTROLLER",
								0L,
								taskDialogOwner(),
								model,
								statuses,
								priorities,
								id -> caseTaskService.loadAssignableUsersForTask(id, shaleClientId),
								id -> caseTaskService.loadAssignedUsersForTask(id, shaleClientId).stream()
										.map(member -> new TaskDetailDialog.AssignedTeamMember(
												member.userId(),
												member.displayName(),
												member.color()))
										.toList(),
								id -> caseTaskService.loadTaskActivity(id, shaleClientId).stream()
										.map(item -> new TaskDetailDialog.TaskActivityEntry(
												item.title(),
												item.body(),
												item.actorDisplayName(),
												item.occurredAt()))
										.toList(),
								id -> caseTaskService.loadTaskNotes(id, shaleClientId).stream()
										.map(note -> new TaskDetailDialog.TaskNoteEntry(
												note.id(),
												note.userId(),
												note.userDisplayName(),
												note.body(),
												note.createdAt(),
												note.updatedAt(),
												note.userId() == currentUserId))
										.toList(),
									new TaskDetailDialog.AssignmentEditor() {
									@Override
									public List<TaskDetailDialog.AssignedTeamMember> addAndReload(int userId) {
										caseTaskService.addTaskAssignment(model.taskId(), shaleClientId, userId, currentUserId);
										return caseTaskService.loadAssignedUsersForTask(model.taskId(), shaleClientId).stream()
												.map(member -> new TaskDetailDialog.AssignedTeamMember(
														member.userId(),
														member.displayName(),
														member.color()))
												.toList();
									}

									@Override
									public List<TaskDetailDialog.AssignedTeamMember> removeAndReload(int userId) {
										caseTaskService.removeTaskAssignment(model.taskId(), shaleClientId, userId, currentUserId);
										return caseTaskService.loadAssignedUsersForTask(model.taskId(), shaleClientId).stream()
												.map(member -> new TaskDetailDialog.AssignedTeamMember(
														member.userId(),
														member.displayName(),
														member.color()))
												.toList();
									}
									},
									new TaskDetailDialog.NotesEditor() {
										@Override
										public List<TaskDetailDialog.TaskNoteEntry> addAndReload(String body) {
											caseTaskService.addTaskNote(model.taskId(), shaleClientId, currentUserId, body);
											return caseTaskService.loadTaskNotes(model.taskId(), shaleClientId).stream()
													.map(note -> new TaskDetailDialog.TaskNoteEntry(
															note.id(),
															note.userId(),
															note.userDisplayName(),
															note.body(),
															note.createdAt(),
															note.updatedAt(),
															note.userId() == currentUserId))
													.toList();
										}

										@Override
										public List<TaskDetailDialog.TaskNoteEntry> editAndReload(long noteId, String body) {
											caseTaskService.updateTaskNote(noteId, shaleClientId, currentUserId, body);
											return caseTaskService.loadTaskNotes(model.taskId(), shaleClientId).stream()
													.map(note -> new TaskDetailDialog.TaskNoteEntry(
															note.id(),
															note.userId(),
															note.userDisplayName(),
															note.body(),
															note.createdAt(),
															note.updatedAt(),
															note.userId() == currentUserId))
													.toList();
										}
									},
									this::onOpenUserFromTask,
									onOpenCase);
						if (result.isEmpty()) {
							return;
						}
						TaskDetailDialog.TaskDetailResult action = result.get();
						if (action.action() == TaskDetailDialog.TaskDetailAction.DELETE) {
							deleteTaskFromDetail(taskId, shaleClientId, currentUserId);
							return;
						}
						TaskDetailDialog.SaveTaskPayload payload = action.payload();
						if (payload == null) {
							return;
						}
						saveTaskFromDetail(taskId, shaleClientId, currentUserId, payload);
					} finally {
						taskDetailDialogInFlight.set(false);
					}
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					taskDetailDialogInFlight.set(false);
					showTaskActionError("Failed to load task details.");
				});
			}
		}, "user-view-task-detail-" + taskId).start();
	}

	private void saveTaskFromDetail(long taskId, int shaleClientId, int currentUserId, TaskDetailDialog.SaveTaskPayload payload) {
		CaseTaskService.UpdateTaskRequest request = new CaseTaskService.UpdateTaskRequest(
				taskId,
				shaleClientId,
				payload.title(),
				payload.description(),
				payload.dueAt(),
				payload.statusId(),
				payload.priorityId(),
				payload.completed(),
				currentUserId);
		new Thread(() -> {
			try {
				caseTaskService.updateTask(request);
				Platform.runLater(this::refreshAssignedTasksAsync);
			} catch (Exception ex) {
				Platform.runLater(() -> showTaskActionError("Failed to save task."));
			}
		}, "user-view-task-save-" + taskId).start();
	}

	private void deleteTaskFromDetail(long taskId, int shaleClientId, int currentUserId) {
		new Thread(() -> {
			try {
				caseTaskService.deleteTask(taskId, shaleClientId, currentUserId);
				Platform.runLater(this::refreshAssignedTasksAsync);
			} catch (Exception ex) {
				Platform.runLater(() -> showTaskActionError("Failed to delete task."));
			}
		}, "user-view-task-delete-" + taskId).start();
	}

	private Window taskDialogOwner() {
		if (assignedTasksContainer != null && assignedTasksContainer.getScene() != null) {
			return assignedTasksContainer.getScene().getWindow();
		}
		return dialogOwner(addRoleButton);
	}

	private void showTaskActionError(String message) {
		AppDialogs.showError(taskDialogOwner(), "Tasks", message);
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
		if (editorNode instanceof TextInputControl textInput) {
			setVisibleManaged(valueNode, false);
			setVisibleManaged(editorNode, true);
			ReadOnlyTextDisplaySupport.apply(textInput, editMode);
			return;
		}
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
