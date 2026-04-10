package com.shale.ui.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
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
import com.shale.ui.util.PerfLog;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
	private Button myTasksShowCompletedButton;
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
	private Consumer<Integer> onOpenUser;
	private CaseCardFactory caseCardFactory;
	private TaskCardFactory taskCardFactory;
	private Consumer<UiRuntimeBridge.CaseUpdatedEvent> liveCaseUpdatedHandler;
	private boolean liveSubscribed;
	private final AtomicBoolean taskDetailDialogInFlight = new AtomicBoolean(false);

	private int currentPage = 0;
	private final int pageSize = 100;
	private boolean loading = false;
	private boolean hasMore = true;
	private int loadGeneration = 0;

	private final List<CaseCardVm> loaded = new ArrayList<>();
	private List<CaseTaskListItemDto> myTasks = List.of();
	private java.util.Map<Long, List<TaskCardFactory.AssignedUserModel>> myTaskAssignedUsers = java.util.Map.of();
	private boolean showCompletedMyTasks;
	private final Set<Integer> selectedStatusIds = new LinkedHashSet<>();
	private List<CaseListUiSupport.StatusFilterOption> statusFilterOptions = List.of();

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
			Consumer<Integer> onOpenCase,
			Consumer<Integer> onOpenUser) {
		this.caseDao = caseDao;
		this.caseTaskService = caseTaskService;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		PerfLog.log("CTRL", "start", "controller=MyShaleController page=my_shale userId=" + (appState == null ? null : appState.getUserId()));
		this.onOpenCase = onOpenCase;
		this.onOpenUser = onOpenUser;
		this.caseCardFactory = new CaseCardFactory(onOpenCase);
		this.taskCardFactory = new TaskCardFactory(
				this::openTask,
				this::onToggleMyTaskComplete,
				onOpenCase,
				onOpenUser == null ? id -> {
				} : onOpenUser);
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
		if (myTasksShowCompletedButton != null) {
			myTasksShowCompletedButton.setOnAction(e -> {
				showCompletedMyTasks = !showCompletedMyTasks;
				updateMyTasksCompletionToggleLabel();
				refreshMyTasks();
			});
			updateMyTasksCompletionToggleLabel();
		}

		reloadStatusFilterOptionsAndThen(this::rerender);

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

	private void reloadStatusFilterOptionsAndThen(Runnable onLoaded) {
		Integer tenantId = appState == null ? null : appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0 || caseDao == null) {
			statusFilterOptions = List.of();
			selectedStatusIds.clear();
			CaseListUiSupport.initializeStatusFilterMenu(myCasesStatusFilterMenuButton, selectedStatusIds, statusFilterOptions, onLoaded);
			return;
		}

		dbExec.submit(() -> {
			List<CaseDao.StatusRow> statuses = caseDao.listStatusesForTenant(tenantId);
			List<CaseListUiSupport.StatusFilterOption> options = statuses == null
					? List.of()
					: statuses.stream()
							.filter(Objects::nonNull)
							.map(status -> new CaseListUiSupport.StatusFilterOption(
									status.id(),
									safe(status.name()).isBlank() ? ("Status #" + status.id()) : safe(status.name()),
									CaseDao.isTerminalStatus(status)))
							.toList();

			Platform.runLater(() -> {
				Set<Integer> statusIds = options.stream()
						.map(CaseListUiSupport.StatusFilterOption::id)
						.collect(java.util.stream.Collectors.toSet());
				selectedStatusIds.removeIf(id -> !statusIds.contains(id));
				if (selectedStatusIds.isEmpty()) {
					selectedStatusIds.addAll(CaseListUiSupport.defaultSelectedStatuses(options));
				}
				statusFilterOptions = options;
				CaseListUiSupport.initializeStatusFilterMenu(myCasesStatusFilterMenuButton, selectedStatusIds, statusFilterOptions, onLoaded);
			});
		});
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
		PerfLog.log("PAGE", "start", "page=my_shale userId=" + (appState == null ? null : appState.getUserId()));
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
				long daoStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=findMyCasesPage page=my_shale userId=" + userId + " pageIndex=" + pageToLoad);
				var page = caseDao.findMyCasesPage(userId, pageToLoad, pageSize, selectedSort(), false);
				PerfLog.logDone("DAO", "method=findMyCasesPage page=my_shale userId=" + userId + " pageIndex=" + pageToLoad + " rows=" + (page == null || page.items() == null ? 0 : page.items().size()), daoStartNanos);
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
		long renderStartNanos = PerfLog.start();
		PerfLog.log("RENDER", "start", "panel=my_cases page=my_shale userId=" + (appState == null ? null : appState.getUserId()));

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
		PerfLog.logDone("RENDER", "panel=my_cases page=my_shale userId=" + (appState == null ? null : appState.getUserId()) + " childCount=" + myCasesFlow.getChildren().size(), renderStartNanos);
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
			myTaskAssignedUsers = java.util.Map.of();
			renderMyTasks();
			return;
		}

		CaseTaskService.MyTasksSortOption sortOption = selectedMyTaskSort();
		final boolean includeCompleted = showCompletedMyTasks;
		dbExec.submit(() -> {
			try {
				long loadStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=loadMyTasks page=my_shale userId=" + userId);
				List<CaseTaskListItemDto> tasks = caseTaskService.loadMyTasks(
						shaleClientId,
						userId,
						sortOption,
						includeCompleted);
				List<Long> taskIds = (tasks == null ? List.<CaseTaskListItemDto>of() : tasks).stream()
						.map(CaseTaskListItemDto::id)
						.toList();
				PerfLog.logDone("DAO", "method=loadMyTasks page=my_shale userId=" + userId + " rows=" + (tasks == null ? 0 : tasks.size()), loadStartNanos);
				long usersLoadStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=loadAssignedUsersForTasks page=my_shale userId=" + userId);
				java.util.Map<Long, List<TaskCardFactory.AssignedUserModel>> assignedByTask = caseTaskService
						.loadAssignedUsersForTasks(taskIds, shaleClientId)
						.stream()
						.collect(java.util.stream.Collectors.groupingBy(
								CaseTaskService.TaskAssignedUsersByTask::taskId,
								java.util.stream.Collectors.mapping(
										row -> new TaskCardFactory.AssignedUserModel(
												row.userId(),
												row.displayName(),
												row.color()),
										java.util.stream.Collectors.toList())));
				PerfLog.logDone("DAO", "method=loadAssignedUsersForTasks page=my_shale userId=" + userId + " rows=" + assignedByTask.size(), usersLoadStartNanos);
				runOnFx(() -> {
					myTasks = tasks == null ? List.of() : tasks;
					myTaskAssignedUsers = assignedByTask;
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
		long renderStartNanos = PerfLog.start();
		PerfLog.log("RENDER", "start", "panel=my_tasks page=my_shale userId=" + (appState == null ? null : appState.getUserId()));
		myTasksList.getChildren().clear();
		if (myTasks == null || myTasks.isEmpty()) {
			setVisibleManaged(myTasksEmptyLabel, true);
			setVisibleManaged(myTasksScroll, false);
			myTasksEmptyLabel.setText(showCompletedMyTasks
					? "No tasks assigned to you."
					: "No incomplete tasks assigned to you.");
			PerfLog.logDone("RENDER", "panel=my_tasks page=my_shale userId=" + (appState == null ? null : appState.getUserId()) + " childCount=0", renderStartNanos);
			return;
		}
		for (CaseTaskListItemDto task : myTasks) {
			TaskCardFactory.TaskCardModel model = new TaskCardFactory.TaskCardModel(
					task.id(),
					task.caseId(),
					task.caseName(),
					task.caseResponsibleAttorney(),
					task.caseResponsibleAttorneyColor(),
					task.title(),
					task.description(),
					task.priorityColorHex(),
					task.dueAt(),
					task.completedAt(),
					myTaskAssignedUsers.getOrDefault(task.id(), List.of()));
			myTasksList.getChildren().add(taskCardFactory.create(model, TaskCardFactory.Variant.COMPACT));
		}
		setVisibleManaged(myTasksEmptyLabel, false);
		setVisibleManaged(myTasksScroll, true);
		PerfLog.logDone("RENDER", "panel=my_tasks page=my_shale userId=" + (appState == null ? null : appState.getUserId()) + " childCount=" + myTasksList.getChildren().size(), renderStartNanos);
	}

	private CaseTaskService.MyTasksSortOption selectedMyTaskSort() {
		if (MY_TASKS_SORT_DUE_DESC.equals(myTasksSortChoice == null ? null : myTasksSortChoice.getValue())) {
			return CaseTaskService.MyTasksSortOption.DUE_DATE_DESC;
		}
		return CaseTaskService.MyTasksSortOption.DUE_DATE_ASC;
	}

	private void updateMyTasksCompletionToggleLabel() {
		if (myTasksShowCompletedButton == null) {
			return;
		}
		myTasksShowCompletedButton.setText(showCompletedMyTasks ? "Hide Completed" : "Show Completed");
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
					caseTaskService.uncompleteTask(taskId, shaleClientId, appState.getUserId());
				} else {
					caseTaskService.completeTask(taskId, shaleClientId, appState.getUserId());
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
		if (!taskDetailDialogInFlight.compareAndSet(false, true)) {
			return;
		}

		new Thread(() -> {
			try {
				TaskDetailDto detail = caseTaskService.loadTaskDetail(taskId, shaleClientId);
					List<TaskPriorityOptionDto> priorities = caseTaskService.loadActivePriorities(shaleClientId);
					List<CaseTaskService.AssignedTaskUserOption> assignedTeam =
							detail == null
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

				runOnFx(() -> {
					try {
						if (detail == null) {
							showTaskActionError("Task was not found or may have been deleted.");
							refreshMyTasks();
							return;
						}
						TaskDetailDialog.TaskDetailModel model = new TaskDetailDialog.TaskDetailModel(
								detail.id(),
								detail.caseId(),
								detail.caseName(),
								detail.caseResponsibleAttorney(),
								detail.caseResponsibleAttorneyColor(),
								detail.title(),
								detail.description(),
								detail.dueAt(),
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
						Optional<TaskDetailDialog.TaskDetailResult> result =
								TaskDetailDialog.showAndWait(
										taskDialogOwner(),
										model,
										priorities,
										id -> caseTaskService.loadAssignableUsersForTask(id, shaleClientId),
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
											onOpenUser,
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
				runOnFx(() -> {
					taskDetailDialogInFlight.set(false);
					showTaskActionError("Failed to load task details. " + rootCauseMessage(ex));
				});
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

	private void deleteTaskFromDetail(long taskId, int shaleClientId, int currentUserId) {
		new Thread(() -> {
			try {
				caseTaskService.deleteTask(taskId, shaleClientId, currentUserId);
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
