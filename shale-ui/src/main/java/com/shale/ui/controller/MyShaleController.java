package com.shale.ui.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.shale.core.dto.TaskStatusOptionDto;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.CaseDao.CaseSort;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.dialog.TaskDetailDialog;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.component.factory.TaskCardFactory;
import com.shale.ui.controller.support.CaseListUiSupport;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.services.PhiReadAuditService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import com.shale.ui.util.NavButtonStyler;
import com.shale.ui.util.PerfLog;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class MyShaleController {

	private static final String SORT_NAME = "Name";
	private static final String SORT_INTAKE = "Date of Intake";
	private static final String SORT_SOL = "Statute of Limitations Date";
	private static final String MY_TASKS_SORT_DUE_ASC = "Due Date (Soonest)";
	private static final String MY_TASKS_SORT_DUE_DESC = "Due Date (Latest)";
	private static final CaseFilterOption ALL_CASES_OPTION = new CaseFilterOption(null, "All Cases");
	private static final String SECTION_OVERVIEW = "Overview";
	private static final String SECTION_TASKS = "Tasks";
	private static final double TASKS_CASE_COLUMN_MIN_WIDTH = 320;
	private static final double TASKS_CASE_COLUMN_PREF_WIDTH = 370;
	private static final double TASKS_CASE_COLUMN_MAX_WIDTH = 430;
	private static final String NO_CASE_COLUMN_TITLE = "No Case";

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
	private ChoiceBox<CaseFilterOption> myTasksCaseFilterChoice;
	@FXML
	private TextField myTasksSearchField;
	@FXML
	private Button myTasksShowCompletedButton;
	@FXML
	private ScrollPane myTasksScroll;
	@FXML
	private HBox myTasksList;
	@FXML
	private Label myTasksEmptyLabel;
	@FXML
	private VBox sectionButtonsBox;
	@FXML
	private VBox overviewSectionPane;
	@FXML
	private VBox tasksSectionPane;
	@FXML
	private VBox myTasksPanel;
	@FXML
	private VBox tasksSectionContentHost;
	@FXML
	private HBox overviewMainRow;

	private CaseDao caseDao;
	private CaseTaskService caseTaskService;
	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private PhiReadAuditService phiReadAuditService;
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
	private final Map<String, Button> sectionButtons = new LinkedHashMap<>();
	private String activeSection = SECTION_OVERVIEW;

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
			Consumer<Integer> onOpenUser,
			PhiReadAuditService phiReadAuditService) {
		this.caseDao = caseDao;
		this.caseTaskService = caseTaskService;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.phiReadAuditService = phiReadAuditService;
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
		setupSections();

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
		if (myTasksCaseFilterChoice != null) {
			myTasksCaseFilterChoice.getItems().setAll(ALL_CASES_OPTION);
			myTasksCaseFilterChoice.getSelectionModel().select(ALL_CASES_OPTION);
			myTasksCaseFilterChoice.getSelectionModel().selectedItemProperty()
					.addListener((obs, oldV, newV) -> renderMyTasks());
		}
		if (myTasksSearchField != null) {
			myTasksSearchField.textProperty().addListener((obs, oldV, newV) -> renderMyTasks());
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
			onSectionSelected(SECTION_OVERVIEW);
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

	private void setupSections() {
		if (sectionButtonsBox == null) {
			return;
		}
		sectionButtons.clear();
		sectionButtonsBox.getChildren().clear();
		for (String section : List.of(SECTION_OVERVIEW, SECTION_TASKS)) {
			Button button = new Button(section);
			button.setMaxWidth(Double.MAX_VALUE);
			button.setAlignment(Pos.CENTER_LEFT);
			NavButtonStyler.applyBaseStyle(button);
			button.setOnAction(e -> onSectionSelected(section));
			VBox.setVgrow(button, Priority.NEVER);
			sectionButtons.put(section, button);
			sectionButtonsBox.getChildren().add(button);
		}
	}

	private void onSectionSelected(String section) {
		if (section == null) {
			return;
		}
		activeSection = section;
		Button activeButton = sectionButtons.get(section);
		NavButtonStyler.setActive(activeButton, sectionButtons.values());
		boolean showOverview = SECTION_OVERVIEW.equals(section);
		setVisibleManaged(overviewSectionPane, showOverview);
		setVisibleManaged(tasksSectionPane, !showOverview);
		attachTasksPanel(showOverview ? overviewMainRow : tasksSectionContentHost);
		renderMyTasks();
	}

	private void attachTasksPanel(Pane host) {
		if (host == null || myTasksPanel == null) {
			return;
		}
		var parent = myTasksPanel.getParent();
		if (parent instanceof HBox hBoxParent) {
			hBoxParent.getChildren().remove(myTasksPanel);
		} else if (parent instanceof VBox vBoxParent) {
			vBoxParent.getChildren().remove(myTasksPanel);
		}
		if (!host.getChildren().contains(myTasksPanel)) {
			host.getChildren().add(myTasksPanel);
		}
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
			safe(r.responsibleAttorneyColor()),
			r.nonEngagementLetterSent()
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
				vm.responsibleAttorneyColor,
				vm.nonEngagementLetterSent));
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
					syncMyTaskCaseFilterOptions();
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

		String searchQuery = normalizeSearchQuery(myTasksSearchField == null ? null : myTasksSearchField.getText());
		List<CaseTaskListItemDto> filteredTasks = filterAndRankMyTasks(myTasks, selectedCaseFilterId(), searchQuery);
		if (myTasks == null || myTasks.isEmpty()) {
			setVisibleManaged(myTasksEmptyLabel, true);
			setVisibleManaged(myTasksScroll, false);
			myTasksEmptyLabel.setText(showCompletedMyTasks
					? "No tasks assigned to you."
					: "No incomplete tasks assigned to you.");
			PerfLog.logDone("RENDER", "panel=my_tasks page=my_shale userId=" + (appState == null ? null : appState.getUserId()) + " childCount=0", renderStartNanos);
			return;
		}
		if (filteredTasks.isEmpty()) {
			setVisibleManaged(myTasksEmptyLabel, true);
			setVisibleManaged(myTasksScroll, false);
			myTasksEmptyLabel.setText("No tasks found.");
			PerfLog.logDone("RENDER", "panel=my_tasks page=my_shale userId=" + (appState == null ? null : appState.getUserId()) + " childCount=0", renderStartNanos);
			return;
		}
		boolean fullVariant = SECTION_TASKS.equals(activeSection);
		Map<CaseColumnKey, List<CaseTaskListItemDto>> tasksByCase = groupTasksByCase(filteredTasks);
		for (Map.Entry<CaseColumnKey, List<CaseTaskListItemDto>> entry : tasksByCase.entrySet()) {
			VBox caseColumn = new VBox(8);
			caseColumn.setMinWidth(TASKS_CASE_COLUMN_MIN_WIDTH);
			caseColumn.setPrefWidth(TASKS_CASE_COLUMN_PREF_WIDTH);
			caseColumn.setMaxWidth(TASKS_CASE_COLUMN_MAX_WIDTH);
			caseColumn.setPadding(new Insets(8));
			caseColumn.getStyleClass().addAll("strong-panel", "glass-panel");
			caseColumn.prefHeightProperty().bind(myTasksScroll.heightProperty().subtract(20));

			Label caseHeader = new Label(entry.getKey().displayName());
			caseHeader.getStyleClass().add("sidebar-header");

			VBox caseTaskCards = new VBox(10);
			caseTaskCards.setFillWidth(true);

			for (CaseTaskListItemDto task : entry.getValue()) {
				TaskCardFactory.TaskCardModel model = new TaskCardFactory.TaskCardModel(
						task.id(),
						task.caseId(),
						task.caseName(),
						task.caseResponsibleAttorney(),
						task.caseResponsibleAttorneyColor(),
						task.caseNonEngagementLetterSent(),
						resolveMyTaskCardTitle(task),
						task.description(),
						task.createdByDisplayName(),
						task.priorityColorHex(),
						task.dueAt(),
						task.completedAt(),
						myTaskAssignedUsers.getOrDefault(task.id(), List.of()));
				if (fullVariant) {
					caseTaskCards.getChildren().add(taskCardFactory.create(model, TaskCardFactory.Variant.FULL, true));
				} else {
					caseTaskCards.getChildren().add(taskCardFactory.create(model, TaskCardFactory.Variant.COMPACT));
				}
			}

			ScrollPane caseColumnScroll = new ScrollPane(caseTaskCards);
			caseColumnScroll.setFitToWidth(true);
			caseColumnScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
			caseColumnScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
			caseColumnScroll.getStyleClass().add("surface-scroll");
			VBox.setVgrow(caseColumnScroll, Priority.ALWAYS);

			caseColumn.getChildren().addAll(caseHeader, caseColumnScroll);
			myTasksList.getChildren().add(caseColumn);
		}
		setVisibleManaged(myTasksEmptyLabel, false);
		setVisibleManaged(myTasksScroll, true);
		PerfLog.logDone("RENDER", "panel=my_tasks page=my_shale userId=" + (appState == null ? null : appState.getUserId()) + " childCount=" + myTasksList.getChildren().size(), renderStartNanos);
	}

	private Map<CaseColumnKey, List<CaseTaskListItemDto>> groupTasksByCase(List<CaseTaskListItemDto> tasks) {
		Map<CaseColumnKey, List<CaseTaskListItemDto>> grouped = new LinkedHashMap<>();
		if (tasks == null || tasks.isEmpty()) {
			return grouped;
		}
		for (CaseTaskListItemDto task : tasks) {
			CaseColumnKey key = caseColumnKey(task);
			grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(task);
		}
		return grouped;
	}

	private CaseColumnKey caseColumnKey(CaseTaskListItemDto task) {
		if (task == null || task.caseId() <= 0) {
			return new CaseColumnKey(null, NO_CASE_COLUMN_TITLE);
		}
		String caseName = safe(task.caseName()).trim();
		if (caseName.isEmpty()) {
			caseName = "Case #" + task.caseId();
		}
		return new CaseColumnKey(task.caseId(), caseName);
	}

	private String resolveMyTaskCardTitle(CaseTaskListItemDto task) {
		if (task == null) {
			return null;
		}
		String title = safe(task.title()).trim();
		return title.isBlank() ? "Task #" + task.id() : title;
	}

	private List<CaseTaskListItemDto> filterAndRankMyTasks(List<CaseTaskListItemDto> tasks, Long selectedCaseId, String normalizedQuery) {
		if (tasks == null || tasks.isEmpty()) {
			return List.of();
		}
		List<CaseTaskListItemDto> caseFiltered = tasks.stream()
				.filter(task -> selectedCaseId == null || task.caseId() == selectedCaseId.longValue())
				.toList();
		if (normalizedQuery.isEmpty()) {
			return caseFiltered;
		}
		record RankedTask(CaseTaskListItemDto task, int score, int originalIndex) {
		}
		List<RankedTask> ranked = new ArrayList<>();
		for (int i = 0; i < caseFiltered.size(); i++) {
			CaseTaskListItemDto task = caseFiltered.get(i);
			int score = myTaskSearchScore(task, normalizedQuery);
			if (score > 0) {
				ranked.add(new RankedTask(task, score, i));
			}
		}
		ranked.sort(Comparator
				.comparingInt(RankedTask::score).reversed()
				.thenComparingInt(RankedTask::originalIndex));
		return ranked.stream().map(RankedTask::task).toList();
	}


	private void syncMyTaskCaseFilterOptions() {
		if (myTasksCaseFilterChoice == null) {
			return;
		}
		CaseFilterOption selectedOption = myTasksCaseFilterChoice.getSelectionModel().getSelectedItem();
		Long selectedId = selectedOption == null ? null : selectedOption.caseId();

		java.util.Map<Long, String> caseById = new java.util.LinkedHashMap<>();
		for (CaseTaskListItemDto task : myTasks) {
			if (task == null || task.caseId() <= 0) {
				continue;
			}
			caseById.putIfAbsent(task.caseId(), safe(task.caseName()));
		}

		List<CaseFilterOption> options = new ArrayList<>();
		options.add(ALL_CASES_OPTION);
		caseById.entrySet().stream()
				.map(entry -> new CaseFilterOption(entry.getKey(), entry.getValue()))
				.sorted(Comparator.comparing(
						(CaseFilterOption option) -> normalizeCaseFilterSortKey(option.displayName()),
						Comparator.nullsLast(String::compareToIgnoreCase)))
				.forEach(options::add);

		myTasksCaseFilterChoice.getItems().setAll(options);
		if (selectedId != null && caseById.containsKey(selectedId)) {
			myTasksCaseFilterChoice.getSelectionModel().select(
					options.stream()
							.filter(option -> selectedId.equals(option.caseId()))
							.findFirst()
							.orElse(ALL_CASES_OPTION));
		} else {
			myTasksCaseFilterChoice.getSelectionModel().select(ALL_CASES_OPTION);
		}
	}

	private Long selectedCaseFilterId() {
		if (myTasksCaseFilterChoice == null) {
			return null;
		}
		CaseFilterOption option = myTasksCaseFilterChoice.getSelectionModel().getSelectedItem();
		return option == null ? null : option.caseId();
	}

	private String normalizeCaseFilterSortKey(String caseName) {
		String trimmed = safe(caseName).trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		return trimmed.toLowerCase(Locale.ROOT);
	}

	private int myTaskSearchScore(CaseTaskListItemDto task, String normalizedQuery) {
		if (task == null || normalizedQuery == null || normalizedQuery.isEmpty()) {
			return 0;
		}
		if (containsIgnoreCase(task.title(), normalizedQuery)) {
			return 4;
		}
		if (containsIgnoreCase(task.description(), normalizedQuery)) {
			return 3;
		}
		if (containsIgnoreCase(task.caseName(), normalizedQuery)) {
			return 2;
		}
		if (containsIgnoreCase(task.createdByDisplayName(), normalizedQuery)) {
			return 1;
		}
		return 0;
	}

	private String normalizeSearchQuery(String rawQuery) {
		if (rawQuery == null) {
			return "";
		}
		return rawQuery.trim().toLowerCase(Locale.ROOT);
	}

	private boolean containsIgnoreCase(String value, String normalizedQuery) {
		return safe(value).toLowerCase(Locale.ROOT).contains(normalizedQuery);
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
		long clickReceivedAt = System.nanoTime();
		System.out.println("[TASK_DETAIL_TIMING][MY_TASKS] click_received taskId=" + taskId);
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
			System.out.println("[TASK_DETAIL_TIMING][MY_TASKS] open_skipped_in_flight taskId=" + taskId);
			return;
		}
		Optional<CaseTaskListItemDto> summary = findMyTaskById(taskId);
		TaskDetailDialog.TaskDetailModel model = new TaskDetailDialog.TaskDetailModel(
				taskId,
				summary.map(CaseTaskListItemDto::caseId).orElse(0L),
				summary.map(CaseTaskListItemDto::caseName).orElse(""),
				summary.map(CaseTaskListItemDto::caseResponsibleAttorney).orElse(""),
				summary.map(CaseTaskListItemDto::caseResponsibleAttorneyColor).orElse(""),
				summary.map(CaseTaskListItemDto::caseNonEngagementLetterSent).orElse(null),
				summary.map(CaseTaskListItemDto::title).orElse(""),
				summary.map(CaseTaskListItemDto::description).orElse(""),
				summary.map(CaseTaskListItemDto::dueAt).orElse(null),
				null,
				null,
				summary.map(CaseTaskListItemDto::createdByDisplayName).orElse(""),
				List.of(),
				List.of(),
				List.of(),
				summary.map(item -> item.completedAt() != null).orElse(false));
		System.out.println("[TASK_DETAIL_TIMING][MY_TASKS] shell_stage_created_ms="
				+ ((System.nanoTime() - clickReceivedAt) / 1_000_000L) + " taskId=" + taskId);
			try {
				auditTaskRead(taskId);
				Optional<TaskDetailDialog.TaskDetailResult> result =
						TaskDetailDialog.showAndWait(
							"MY_TASKS",
							clickReceivedAt,
							taskDialogOwner(),
							model,
							List.of(),
							List.of(),
							id -> {
								TaskDetailDto detail = caseTaskService.loadTaskDetail(id, shaleClientId);
								List<TaskStatusOptionDto> statuses = caseTaskService.loadActiveTaskStatuses(shaleClientId);
								List<TaskPriorityOptionDto> priorities = caseTaskService.loadActivePriorities(shaleClientId);
								if (detail == null) {
									throw new IllegalStateException("Task was not found or may have been deleted.");
								}
								return new TaskDetailDialog.CoreTaskHydration(detail, statuses, priorities);
							},
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
		} catch (Exception ex) {
			showTaskActionError("Failed to load task details. " + rootCauseMessage(ex));
		} finally {
			taskDetailDialogInFlight.set(false);
		}
	}

	private void auditTaskRead(Long taskId) {
		if (phiReadAuditService == null || taskId == null || taskId <= 0) {
			return;
		}
		phiReadAuditService.auditRead("Task.Detail.Read", "Task.Detail", "Task", taskId);
		phiReadAuditService.auditRead("Task.Activity.Read", "Task.Activity", "Task", taskId);
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
				payload.statusId(),
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

	private record CaseFilterOption(Long caseId, String displayName) {
		@Override
		public String toString() {
			return safe(displayName);
		}
	}

	private record CaseColumnKey(Long caseId, String displayName) {
	}

	private static final class CaseCardVm {
		final long id;
		final String name;
		final LocalDate intakeDate;
		final LocalDate solDate;
		final Integer primaryStatusId;
		final String responsibleAttorney;
		final String responsibleAttorneyColor;
		final Boolean nonEngagementLetterSent;

		CaseCardVm(long id, String name, LocalDate intakeDate, LocalDate solDate, Integer primaryStatusId,
				String responsibleAttorney, String responsibleAttorneyColor, Boolean nonEngagementLetterSent) {
			this.id = id;
			this.name = Objects.requireNonNullElse(name, "");
			this.intakeDate = intakeDate;
			this.solDate = solDate;
			this.primaryStatusId = primaryStatusId;
			this.responsibleAttorney = Objects.requireNonNullElse(responsibleAttorney, "");
			this.responsibleAttorneyColor = Objects.requireNonNullElse(responsibleAttorneyColor, "");
			this.nonEngagementLetterSent = nonEngagementLetterSent;
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
					&& Objects.equals(responsibleAttorneyColor, other.responsibleAttorneyColor)
					&& Objects.equals(nonEngagementLetterSent, other.nonEngagementLetterSent);
		}
	}
}
