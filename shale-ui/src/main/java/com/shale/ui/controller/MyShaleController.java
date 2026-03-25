package com.shale.ui.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.CaseDao.CaseSort;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.dialog.TaskDetailDialog;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.component.factory.TaskCardFactory;
import com.shale.ui.controller.support.CaseListUiSupport;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class MyShaleController {

	private static final String SORT_NAME = "Name";
	private static final String SORT_INTAKE = "Date of Intake";
	private static final String SORT_SOL = "Statute of Limitations Date";
	private static final String MY_TASKS_SORT_DUE_ASC = "Due Date (Soonest)";
	private static final String MY_TASKS_SORT_DUE_DESC = "Due Date (Latest)";

	@FXML
	private TextField myCasesSearchField;
	@FXML
	private ChoiceBox<String> myCasesSortChoice;
	@FXML
	private MenuButton myCasesStatusFilterMenuButton;
	@FXML
	private ScrollPane myCasesScroll;
	@FXML
	private FlowPane myCasesFlow;
	@FXML
	private ChoiceBox<String> myTasksSortChoice;
	@FXML
	private ScrollPane myTasksScroll;
	@FXML
	private VBox myTasksList;
	@FXML
	private Label myTasksEmptyLabel;

	private CaseDao caseDao;
	private CaseTaskService caseTaskService;
	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private Consumer<Integer> onOpenCase;
	private CaseCardFactory caseCardFactory;
	private TaskCardFactory taskCardFactory;
	private Consumer<UiRuntimeBridge.CaseUpdatedEvent> liveCaseUpdatedHandler;
	private boolean liveSubscribed;

	private int currentPage = 0;
	private final int pageSize = 100;
	private boolean loading = false;
	private boolean hasMore = true;
	private int loadGeneration = 0;

	private final List<CaseCardVm> loaded = new ArrayList<>();
	private List<CaseTaskListItemDto> myTasks = List.of();
	private final Set<Integer> selectedStatusIds = CaseListUiSupport.defaultSelectedStatuses();

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "my-cases-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(
			AppState appState,
			UiRuntimeBridge runtimeBridge,
			CaseDao caseDao,
			CaseTaskService caseTaskService,
			Consumer<Integer> onOpenCase) {
		this.caseDao = caseDao;
		this.caseTaskService = caseTaskService;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.onOpenCase = onOpenCase;
		this.caseCardFactory = new CaseCardFactory(onOpenCase);
		this.taskCardFactory = new TaskCardFactory(this::openTask, this::onToggleMyTaskComplete, onOpenCase, id -> {
		});
	}

	@FXML
	private void initialize() {
		if (myCasesSortChoice != null) {
			myCasesSortChoice.getItems().setAll(SORT_NAME, SORT_INTAKE, SORT_SOL);
			myCasesSortChoice.getSelectionModel().select(SORT_NAME);
			myCasesSortChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> loadFirstPage());
		}

		if (myCasesSearchField != null) {
			myCasesSearchField.textProperty().addListener((obs, oldV, newV) -> rerender());
		}
		if (myTasksSortChoice != null) {
			myTasksSortChoice.getItems().setAll(MY_TASKS_SORT_DUE_ASC, MY_TASKS_SORT_DUE_DESC);
			myTasksSortChoice.getSelectionModel().select(MY_TASKS_SORT_DUE_ASC);
			myTasksSortChoice.getSelectionModel().selectedItemProperty()
					.addListener((obs, oldV, newV) -> refreshMyTasks());
		}

		CaseListUiSupport.initializeStatusFilterMenu(myCasesStatusFilterMenuButton, selectedStatusIds, this::rerender);

		Platform.runLater(() -> {
			wireInfiniteScroll();
			loadFirstPage();
			refreshMyTasks();
		});

		if (myCasesFlow != null) {
			myCasesFlow.sceneProperty().addListener((obs, oldScene, newScene) -> {
				System.out.println("[DEBUG LIVE][MY_CASES] scene changed old=" + (oldScene != null) + " new=" + (newScene != null));
				if (newScene == null) {
					unsubscribeLiveCaseUpdates();
				} else {
					subscribeLiveCaseUpdates();
				}
			});
		}

		subscribeLiveCaseUpdates();
	}

	private void subscribeLiveCaseUpdates() {
		if (runtimeBridge == null) {
			System.out.println("[DEBUG LIVE][MY_CASES] subscribe skipped: runtimeBridge is null");
			return;
		}
		if (liveSubscribed) {
			System.out.println("[DEBUG LIVE][MY_CASES] subscribe skipped: already subscribed");
			return;
		}

		liveCaseUpdatedHandler = this::handleLiveCaseUpdatedEvent;
		runtimeBridge.subscribeCaseUpdated(liveCaseUpdatedHandler);
		liveSubscribed = true;
		System.out.println("[DEBUG LIVE][MY_CASES] subscribed to case updates");
	}

	private void unsubscribeLiveCaseUpdates() {
		if (!liveSubscribed || runtimeBridge == null || liveCaseUpdatedHandler == null) {
			return;
		}
		runtimeBridge.unsubscribeCaseUpdated(liveCaseUpdatedHandler);
		liveSubscribed = false;
		System.out.println("[DEBUG LIVE][MY_CASES] unsubscribed from case updates");
	}

	private void handleLiveCaseUpdatedEvent(UiRuntimeBridge.CaseUpdatedEvent event) {
		String mine = runtimeBridge == null ? "" : runtimeBridge.getClientInstanceId();
		System.out.println("[DEBUG LIVE][MY_CASES] event received caseId=" + event.caseId()
				+ " updatedBy=" + event.updatedByUserId()
				+ " mineInstance=" + mine
				+ " eventInstance=" + event.clientInstanceId()
				+ " patchLen=" + (event.rawPatchJson() == null ? 0 : event.rawPatchJson().length()));

		if (!mine.isBlank() && mine.equals(event.clientInstanceId())) {
			System.out.println("[DEBUG LIVE][MY_CASES] event ignored: own echo");
			return;
		}

		System.out.println("[DEBUG LIVE][MY_CASES] event accepted -> scheduling targeted refresh");
		refreshCaseIncremental(event.caseId());
	}


	private void refreshCaseIncremental(long caseId) {
		if (caseDao == null || appState == null || appState.getUserId() == null || appState.getUserId() <= 0) {
			System.out.println("[DEBUG LIVE][MY_CASES] targeted refresh skipped: missing dependencies");
			return;
		}

		final int userId = appState.getUserId();
		final int generationAtSubmit = loadGeneration;
		dbExec.submit(() -> {
			try {
				CaseDao.CaseRow row = caseDao.getMyCaseRow(userId, caseId);
				Platform.runLater(() -> {
					if (generationAtSubmit != loadGeneration) {
						System.out.println("[DEBUG LIVE][MY_CASES] targeted refresh ignored due to generation mismatch");
						return;
					}

					boolean changed;
					if (row == null) {
						changed = removeLoadedCase(caseId);
						System.out.println("[DEBUG LIVE][MY_CASES] targeted refresh row missing -> removed=" + changed + " caseId=" + caseId);
					} else {
						changed = upsertLoadedCase(toVm(row));
						System.out.println("[DEBUG LIVE][MY_CASES] targeted refresh upsert changed=" + changed + " caseId=" + caseId);
					}

					if (changed) {
						rerender();
					}
				});
			} catch (Exception ex) {
				System.out.println("[DEBUG LIVE][MY_CASES] targeted refresh failed caseId=" + caseId + " message=" + ex.getMessage());
				runOnFx(this::loadFirstPage);
			}
		});
	}

	private boolean removeLoadedCase(long caseId) {
		for (int i = 0; i < loaded.size(); i++) {
			if (loaded.get(i).id == caseId) {
				loaded.remove(i);
				return true;
			}
		}
		return false;
	}

	private boolean upsertLoadedCase(CaseCardVm vm) {
		for (int i = 0; i < loaded.size(); i++) {
			CaseCardVm existing = loaded.get(i);
			if (existing.id == vm.id) {
				if (existing.sameContent(vm)) {
					return false;
				}
				loaded.set(i, vm);
				return true;
			}
		}
		loaded.add(vm);
		return true;
	}

	private CaseCardVm toVm(CaseDao.CaseRow r) {
		return new CaseCardVm(
			r.id(),
			safe(r.name()),
			r.intakeDate(),
			r.statuteOfLimitationsDate(),
			r.primaryStatusId(),
			safe(r.responsibleAttorneyName()),
			safe(r.responsibleAttorneyColor())
		);
	}

	private void wireInfiniteScroll() {
		if (myCasesScroll == null)
			return;
		myCasesScroll.vvalueProperty().addListener((obs, oldV, newV) -> {
			if (newV != null && newV.doubleValue() >= 0.95 && !isSearchActive()) {
				loadNextPage();
			}
		});
	}

	private void loadFirstPage() {
		loadGeneration++;
		System.out.println("[DEBUG LIVE][MY_CASES] loadFirstPage generation=" + loadGeneration + " sort=" + (myCasesSortChoice == null ? "<null>" : myCasesSortChoice.getValue()) + " query='" + normalizedSearchQuery() + "' selectedStatuses=" + selectedStatusIds.size());
		currentPage = 0;
		loading = false;
		hasMore = true;
		loaded.clear();
		if (myCasesFlow != null) {
			myCasesFlow.getChildren().clear();
		}
		loadNextPage();
	}

	private void loadNextPage() {
		if (loading || !hasMore || caseDao == null || appState == null || appState.getUserId() == null || appState.getUserId() <= 0) {
			return;
		}

		loading = true;
		final int pageToLoad = currentPage;
		final int generationAtSubmit = loadGeneration;
		final int userId = appState.getUserId();

		dbExec.submit(() -> {
			try {
				var page = caseDao.findMyCasesPage(userId, pageToLoad, pageSize, selectedSort(), false);
				List<CaseCardVm> newItems = page.items().stream()
						.map(this::toVm)
						.toList();

				Platform.runLater(() -> {
					if (generationAtSubmit != loadGeneration) {
						loading = false;
						return;
					}
					for (CaseCardVm vm : newItems) {
						upsertLoadedCase(vm);
					}
					System.out.println("[DEBUG LIVE][MY_CASES] page loaded page=" + pageToLoad + " items=" + newItems.size() + " total=" + page.total() + " loadedUnique=" + loaded.size());
					currentPage++;
					hasMore = loaded.size() < page.total();
					loading = false;
					rerender();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					if (generationAtSubmit == loadGeneration) {
						loading = false;
						System.out.println("[DEBUG LIVE][MY_CASES] load failed generation=" + generationAtSubmit + " message=" + ex.getMessage());
						ex.printStackTrace();
					}
				});
			}
		});
	}

	private void rerender() {
		if (myCasesFlow == null) {
			return;
		}

		String q = normalizedSearchQuery();
		Comparator<CaseCardVm> comp = comparatorFor(myCasesSortChoice == null ? SORT_NAME : myCasesSortChoice.getValue());

		List<CaseCardVm> filtered = loaded.stream()
				.filter(vm -> matchesQuery(vm, q) && matchesSelectedStatus(vm))
				.sorted(comp)
				.toList();

		if (!q.isEmpty() && filtered.size() < pageSize && hasMore && !loading) {
			loadNextPage();
		}

		List<CaseCardVm> view = q.isEmpty() ? filtered : filtered.stream().limit(pageSize).toList();
		myCasesFlow.getChildren().setAll(view.stream().map(this::buildCaseCard).toList());
	}

	private CaseSort selectedSort() {
		String value = myCasesSortChoice == null ? SORT_NAME : myCasesSortChoice.getValue();
		if (SORT_NAME.equals(value)) {
			return CaseSort.CASE_NAME_ASC;
		}
		if (SORT_SOL.equals(value)) {
			return CaseSort.STATUTE_SOONEST;
		}
		return CaseSort.INTAKE_NEWEST;
	}

	private Comparator<CaseCardVm> comparatorFor(String sortOption) {
		if (SORT_NAME.equals(sortOption)) {
			return Comparator.comparing((CaseCardVm v) -> v.name, this::nullsLastString);
		}
		if (SORT_SOL.equals(sortOption)) {
			return Comparator.comparing((CaseCardVm v) -> v.solDate, this::nullsLastDate);
		}
		if (SORT_INTAKE.equals(sortOption)) {
			return Comparator.comparing((CaseCardVm v) -> v.intakeDate, this::nullsLastDate).reversed();
		}
		return Comparator.comparing((CaseCardVm v) -> v.name, this::nullsLastString);
	}

	private boolean matchesSelectedStatus(CaseCardVm vm) {
		return vm.primaryStatusId == null || selectedStatusIds.contains(vm.primaryStatusId);
	}

	private boolean isSearchActive() {
		return !normalizedSearchQuery().isEmpty();
	}

	private String normalizedSearchQuery() {
		if (myCasesSearchField == null)
			return "";
		return safe(myCasesSearchField.getText()).trim().toLowerCase(Locale.ROOT);
	}

	private static boolean matchesQuery(CaseCardVm vm, String query) {
		if (query.isEmpty())
			return true;
		return vm.name.toLowerCase(Locale.ROOT).contains(query)
				|| vm.responsibleAttorney.toLowerCase(Locale.ROOT).contains(query);
	}

	private int nullsLastDate(LocalDate a, LocalDate b) {
		if (a == null && b == null)
			return 0;
		if (a == null)
			return 1;
		if (b == null)
			return -1;
		return a.compareTo(b);
	}

	private int nullsLastString(String a, String b) {
		if (a == null && b == null)
			return 0;
		if (a == null)
			return 1;
		if (b == null)
			return -1;
		return a.compareToIgnoreCase(b);
	}

	private Node buildCaseCard(CaseCardVm vm) {
		return caseCardFactory.create(new CaseCardModel(
				vm.id,
				vm.name,
				vm.intakeDate,
				vm.solDate,
				vm.responsibleAttorney,
				vm.responsibleAttorneyColor));
	}

	private void refreshMyTasks() {
		if (caseTaskService == null || appState == null) {
			return;
		}
		Integer shaleClientId = appState.getShaleClientId();
		Integer userId = appState.getUserId();
		if (shaleClientId == null || shaleClientId <= 0 || userId == null || userId <= 0) {
			myTasks = List.of();
			renderMyTasks();
			return;
		}

		CaseTaskService.MyTasksSortOption sortOption = selectedMyTaskSort();
		dbExec.submit(() -> {
			try {
				List<CaseTaskListItemDto> tasks = caseTaskService.loadMyTasks(shaleClientId, userId, sortOption);
				runOnFx(() -> {
					myTasks = tasks == null ? List.of() : tasks;
					renderMyTasks();
				});
			} catch (Exception ex) {
				System.err.println("My tasks load failed: " + ex.getMessage());
				ex.printStackTrace();
				runOnFx(() -> showTaskActionError("Failed to load your tasks."));
			}
		});
	}

	private void renderMyTasks() {
		if (myTasksList == null || myTasksEmptyLabel == null || myTasksScroll == null) {
			return;
		}
		myTasksList.getChildren().clear();
		if (myTasks == null || myTasks.isEmpty()) {
			setVisibleManaged(myTasksEmptyLabel, true);
			setVisibleManaged(myTasksScroll, false);
			myTasksEmptyLabel.setText("No tasks assigned to you.");
			return;
		}
		for (CaseTaskListItemDto task : myTasks) {
			TaskCardFactory.TaskCardModel model = new TaskCardFactory.TaskCardModel(
					task.id(),
					task.caseId(),
					task.caseName(),
					task.title(),
					task.description(),
					task.dueAt(),
					task.completedAt(),
					task.assignedUserId(),
					task.assignedUserDisplayName(),
					task.assignedUserColor());
			myTasksList.getChildren().add(taskCardFactory.create(model, TaskCardFactory.Variant.COMPACT));
		}
		setVisibleManaged(myTasksEmptyLabel, false);
		setVisibleManaged(myTasksScroll, true);
	}

	private CaseTaskService.MyTasksSortOption selectedMyTaskSort() {
		if (MY_TASKS_SORT_DUE_DESC.equals(myTasksSortChoice == null ? null : myTasksSortChoice.getValue())) {
			return CaseTaskService.MyTasksSortOption.DUE_DATE_DESC;
		}
		return CaseTaskService.MyTasksSortOption.DUE_DATE_ASC;
	}

	private void openTask(Long taskId) {
		showTaskDetailPopup(taskId);
	}

	private void onToggleMyTaskComplete(Long taskId) {
		if (taskId == null || taskId <= 0 || caseTaskService == null || appState == null) {
			return;
		}
		Integer shaleClientId = appState.getShaleClientId();
		if (shaleClientId == null || shaleClientId <= 0) {
			showTaskActionError("Unable to update task right now.");
			return;
		}
		boolean currentlyCompleted = findMyTaskById(taskId)
				.map(task -> task.completedAt() != null)
				.orElse(false);

		new Thread(() -> {
			try {
				if (currentlyCompleted) {
					caseTaskService.uncompleteTask(taskId, shaleClientId);
				} else {
					caseTaskService.completeTask(taskId, shaleClientId);
				}
				runOnFx(this::refreshMyTasks);
			} catch (Exception ex) {
				runOnFx(() -> showTaskActionError("Failed to update task completion. " + rootCauseMessage(ex)));
			}
		}, "my-shale-toggle-task-" + taskId).start();
	}

	private Optional<CaseTaskListItemDto> findMyTaskById(Long taskId) {
		if (taskId == null || myTasks == null) {
			return Optional.empty();
		}
		for (CaseTaskListItemDto task : myTasks) {
			if (task.id() == taskId.longValue()) {
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

		new Thread(() -> {
			try {
				TaskDetailDto detail = caseTaskService.loadTaskDetail(taskId, shaleClientId);
				List<TaskPriorityOptionDto> priorities = caseTaskService.loadActivePriorities(shaleClientId);
				List<CaseTaskService.AssignableUserOption> users = caseTaskService.loadAssignableUsers(shaleClientId);

				runOnFx(() -> {
					if (detail == null) {
						showTaskActionError("Task was not found or may have been deleted.");
						refreshMyTasks();
						return;
					}
					TaskDetailDialog.TaskDetailModel model = new TaskDetailDialog.TaskDetailModel(
							detail.id(),
							detail.caseId(),
							detail.caseName(),
							detail.title(),
							detail.description(),
							detail.dueAt(),
							detail.priorityId(),
							detail.assignedUserId(),
							detail.completedAt() != null);
					Optional<TaskDetailDialog.TaskDetailResult> result =
							TaskDetailDialog.showAndWait(taskDialogOwner(), model, priorities, users, onOpenCase);
					if (result.isEmpty()) {
						return;
					}
					TaskDetailDialog.TaskDetailResult action = result.get();
					if (action.action() == TaskDetailDialog.TaskDetailAction.DELETE) {
						deleteTaskFromDetail(taskId, shaleClientId);
						return;
					}
					TaskDetailDialog.SaveTaskPayload payload = action.payload();
					if (payload == null) {
						return;
					}
					saveTaskFromDetail(taskId, shaleClientId, currentUserId, payload);
				});
			} catch (Exception ex) {
				runOnFx(() -> showTaskActionError("Failed to load task details. " + rootCauseMessage(ex)));
			}
		}, "my-shale-task-detail-" + taskId).start();
	}

	private void saveTaskFromDetail(
			long taskId,
			int shaleClientId,
			int currentUserId,
			TaskDetailDialog.SaveTaskPayload payload) {
		CaseTaskService.UpdateTaskRequest request = new CaseTaskService.UpdateTaskRequest(
				taskId,
				shaleClientId,
				payload.title(),
				payload.description(),
				payload.dueAt(),
				payload.priorityId(),
				payload.assigneeUserId(),
				payload.completed(),
				currentUserId);
		new Thread(() -> {
			try {
				caseTaskService.updateTask(request);
				runOnFx(this::refreshMyTasks);
			} catch (Exception ex) {
				runOnFx(() -> showTaskActionError("Failed to save task. " + rootCauseMessage(ex)));
			}
		}, "my-shale-task-save-" + taskId).start();
	}

	private void deleteTaskFromDetail(long taskId, int shaleClientId) {
		new Thread(() -> {
			try {
				caseTaskService.deleteTask(taskId, shaleClientId);
				runOnFx(this::refreshMyTasks);
			} catch (Exception ex) {
				runOnFx(() -> showTaskActionError("Failed to delete task. " + rootCauseMessage(ex)));
			}
		}, "my-shale-task-delete-" + taskId).start();
	}

	private void showTaskActionError(String message) {
		AppDialogs.showError(taskDialogOwner(), "Tasks", message);
	}

	private String rootCauseMessage(Throwable throwable) {
		if (throwable == null) {
			return "";
		}
		Throwable current = throwable;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		String message = current.getMessage();
		return (message == null || message.isBlank()) ? "" : "Details: " + message;
	}

	private Window taskDialogOwner() {
		if (myTasksList != null && myTasksList.getScene() != null) {
			return myTasksList.getScene().getWindow();
		}
		return null;
	}

	private static void setVisibleManaged(Node node, boolean visible) {
		if (node == null) {
			return;
		}
		node.setVisible(visible);
		node.setManaged(visible);
	}

	private static void runOnFx(Runnable runnable) {
		if (Platform.isFxApplicationThread()) {
			runnable.run();
		} else {
			Platform.runLater(runnable);
		}
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	private static final class CaseCardVm {
		final long id;
		final String name;
		final LocalDate intakeDate;
		final LocalDate solDate;
		final Integer primaryStatusId;
		final String responsibleAttorney;
		final String responsibleAttorneyColor;

		CaseCardVm(long id, String name, LocalDate intakeDate, LocalDate solDate, Integer primaryStatusId,
				String responsibleAttorney, String responsibleAttorneyColor) {
			this.id = id;
			this.name = Objects.requireNonNullElse(name, "");
			this.intakeDate = intakeDate;
			this.solDate = solDate;
			this.primaryStatusId = primaryStatusId;
			this.responsibleAttorney = Objects.requireNonNullElse(responsibleAttorney, "");
			this.responsibleAttorneyColor = Objects.requireNonNullElse(responsibleAttorneyColor, "");
		}

		boolean sameContent(CaseCardVm other) {
			if (other == null) {
				return false;
			}
			return id == other.id
					&& Objects.equals(name, other.name)
					&& Objects.equals(intakeDate, other.intakeDate)
					&& Objects.equals(solDate, other.solDate)
					&& Objects.equals(primaryStatusId, other.primaryStatusId)
					&& Objects.equals(responsibleAttorney, other.responsibleAttorney)
					&& Objects.equals(responsibleAttorneyColor, other.responsibleAttorneyColor);
		}
	}
}
