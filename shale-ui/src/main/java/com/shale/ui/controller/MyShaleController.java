package com.shale.ui.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.shale.data.dao.UserBoardLanePreferencesDao;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.dialog.TaskDetailDialog;
import com.shale.ui.component.board.LaneBoardLayout;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.component.factory.TaskCardFactory;
import com.shale.ui.controller.support.CaseListUiSupport;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.services.PhiReadAuditService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.UserPreferencesService;
import com.shale.ui.state.AppState;
import com.shale.ui.util.NavButtonStyler;
import com.shale.ui.util.PerfLog;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class MyShaleController {

	private static final String SORT_NAME = "Name";
	private static final String SORT_INTAKE = "Date of Intake";
	private static final String SORT_SOL = "Statute of Limitations Date";
	private static final String MY_TASKS_SORT_DUE_ASC = "Due Date (Soonest)";
	private static final String MY_TASKS_SORT_DUE_DESC = "Due Date (Latest)";
	private static final String MY_TASKS_COLUMN_ORDER_CASE_NAME = "Case Name";
	private static final String MY_TASKS_COLUMN_ORDER_OLDEST_INCOMPLETE_DUE = "Oldest Incomplete Due Date";
	private static final CaseFilterOption ALL_CASES_OPTION = new CaseFilterOption(null, "All Cases");
	private static final PriorityFilterOption ALL_PRIORITIES_OPTION = new PriorityFilterOption(null, "All Priorities");
	private static final String SECTION_OVERVIEW = "Overview";
	private static final String SECTION_TASKS = "Tasks";
	private static final double TASKS_CASE_COLUMN_MIN_WIDTH = 225;
	private static final double TASKS_CASE_COLUMN_PREF_WIDTH = 260;
	private static final double TASKS_CASE_COLUMN_MAX_WIDTH = 300;
	private static final String NO_CASE_COLUMN_TITLE = "No Case";
	private static final String MY_TASKS_BOARD_KEY = "my_shale_tasks";
	private static final String MY_TASKS_LANE_TYPE_CASE = "CASE";
	private static final String PREF_MY_TASKS_SORT = "my_shale_tasks.task_sort";
	private static final String PREF_MY_TASKS_SHOW_COMPLETED = "my_shale_tasks.show_completed";
	private static final String PREF_MY_TASKS_PRIORITY_FILTER = "my_shale_tasks.priority_filter";
	private static final String PREF_MY_TASKS_LANE_ORDER = "my_shale_tasks.lane_order";
	private static final String PREF_MY_TASKS_CASE_FILTER = "my_shale_tasks.case_filter";

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
	private ChoiceBox<PriorityFilterOption> myTasksPriorityFilterChoice;
	@FXML
	private ChoiceBox<String> myTasksColumnOrderChoice;
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
	@FXML
	private StackPane sectionContentStack;

	private CaseDao caseDao;
	private CaseTaskService caseTaskService;
	private UserBoardLanePreferencesDao userBoardLanePreferencesDao;
	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private PhiReadAuditService phiReadAuditService;
	private UserPreferencesService userPreferencesService;
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
	private java.util.Map<Integer, String> myTaskPrioritiesById = java.util.Map.of();
	private boolean showCompletedMyTasks;
	private final Set<Integer> selectedStatusIds = new LinkedHashSet<>();
	private final Set<Long> pinnedTaskLaneCaseIds = new LinkedHashSet<>();
	private List<CaseListUiSupport.StatusFilterOption> statusFilterOptions = List.of();
	private final Map<String, Button> sectionButtons = new LinkedHashMap<>();
	private String activeSection = SECTION_OVERVIEW;
	private boolean suppressMyTaskPreferenceWrites;
	private Integer preferredMyTasksPriorityFilterId;
	private Long preferredMyTasksCaseFilterId;

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "my-cases-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(
			AppState appState,
			UiRuntimeBridge runtimeBridge,
			CaseDao caseDao,
			CaseTaskService caseTaskService,
			UserBoardLanePreferencesDao userBoardLanePreferencesDao,
			UserPreferencesService userPreferencesService,
			Consumer<Integer> onOpenCase,
			Consumer<Integer> onOpenUser,
			PhiReadAuditService phiReadAuditService) {
		this.caseDao = caseDao;
		this.caseTaskService = caseTaskService;
		this.userBoardLanePreferencesDao = userBoardLanePreferencesDao;
		this.userPreferencesService = userPreferencesService;
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
				onOpenUser == null ? id ->
				{
				} : onOpenUser);
	}

	@FXML
	private void initialize() {
		setupSections();
		configureSectionSizing();

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
			myTasksSortChoice.getSelectionModel().select(restoreMyTasksSortPreference());
			myTasksSortChoice.getSelectionModel().selectedItemProperty()
					.addListener((obs, oldV, newV) -> {
						persistMyTasksSortPreference(newV);
						refreshMyTasks();
					});
		}
		if (myTasksCaseFilterChoice != null) {
			myTasksCaseFilterChoice.getItems().setAll(ALL_CASES_OPTION);
			myTasksCaseFilterChoice.getSelectionModel().select(ALL_CASES_OPTION);
			myTasksCaseFilterChoice.getSelectionModel().selectedItemProperty()
					.addListener((obs, oldV, newV) -> {
						persistMyTasksCaseFilterPreference(newV);
						renderMyTasks();
					});
		}
		if (myTasksPriorityFilterChoice != null) {
			myTasksPriorityFilterChoice.getItems().setAll(ALL_PRIORITIES_OPTION);
			myTasksPriorityFilterChoice.getSelectionModel().select(ALL_PRIORITIES_OPTION);
			myTasksPriorityFilterChoice.getSelectionModel().selectedItemProperty()
					.addListener((obs, oldV, newV) -> {
						persistMyTasksPriorityFilterPreference(newV);
						renderMyTasks();
					});
		}
		if (myTasksColumnOrderChoice != null) {
			myTasksColumnOrderChoice.getItems().setAll(
					MY_TASKS_COLUMN_ORDER_CASE_NAME,
					MY_TASKS_COLUMN_ORDER_OLDEST_INCOMPLETE_DUE);
			myTasksColumnOrderChoice.getSelectionModel().select(restoreMyTasksLaneOrderPreference());
			myTasksColumnOrderChoice.getSelectionModel().selectedItemProperty()
					.addListener((obs, oldV, newV) -> {
						persistMyTasksLaneOrderPreference(newV);
						renderMyTasks();
					});
		}
		if (myTasksSearchField != null) {
			myTasksSearchField.textProperty().addListener((obs, oldV, newV) -> renderMyTasks());
		}
		if (myTasksShowCompletedButton != null) {
			showCompletedMyTasks = restoreMyTasksShowCompletedPreference();
			myTasksShowCompletedButton.setOnAction(e ->
			{
				showCompletedMyTasks = !showCompletedMyTasks;
				persistMyTasksShowCompletedPreference(showCompletedMyTasks);
				updateMyTasksCompletionToggleLabel();
				refreshMyTasks();
			});
			updateMyTasksCompletionToggleLabel();
		}
		preferredMyTasksPriorityFilterId = restoreMyTasksPriorityFilterPreference();
		preferredMyTasksCaseFilterId = restoreMyTasksCaseFilterPreference();

		reloadStatusFilterOptionsAndThen(this::rerender);

		Platform.runLater(() ->
		{
			onSectionSelected(SECTION_OVERVIEW);
			wireInfiniteScroll();
			loadFirstPage();
			refreshMyTasks();
		});

		if (myCasesFlow != null) {
			myCasesFlow.sceneProperty().addListener((obs, oldScene, newScene) ->
			{
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

	private void configureSectionSizing() {
		if (sectionContentStack != null) {
			sectionContentStack.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		}
		if (overviewSectionPane != null) {
			overviewSectionPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
			StackPane.setAlignment(overviewSectionPane, Pos.TOP_LEFT);
		}
		if (tasksSectionPane != null) {
			tasksSectionPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
			StackPane.setAlignment(tasksSectionPane, Pos.TOP_LEFT);
		}
		if (tasksSectionContentHost != null) {
			VBox.setVgrow(tasksSectionContentHost, Priority.ALWAYS);
			tasksSectionContentHost.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		}
		if (myTasksPanel != null) {
			VBox.setVgrow(myTasksPanel, Priority.ALWAYS);
			myTasksPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		}
		if (myTasksScroll != null) {
			VBox.setVgrow(myTasksScroll, Priority.ALWAYS);
			myTasksScroll.setFitToHeight(true);
			myTasksScroll.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		}
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
		if (!showOverview) {
			attachTasksPanel(tasksSectionContentHost);
		}
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
		if (host instanceof VBox) {
			VBox.setVgrow(myTasksPanel, Priority.ALWAYS);
			myTasksPanel.setPrefHeight(Region.USE_COMPUTED_SIZE);
		}
		if (host instanceof HBox) {
			HBox.setHgrow(myTasksPanel, Priority.ALWAYS);
		}
		myTasksPanel.setMaxHeight(Double.MAX_VALUE);
		myTasksPanel.setMaxWidth(Double.MAX_VALUE);
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

		dbExec.submit(() ->
		{
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

			Platform.runLater(() ->
			{
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
		dbExec.submit(() ->
		{
			try {
				CaseDao.CaseRow row = caseDao.getMyCaseRow(userId, caseId);
				Platform.runLater(() ->
				{
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
		myCasesScroll.vvalueProperty().addListener((obs, oldV, newV) ->
		{
			if (newV != null && newV.doubleValue() >= 0.95 && !isSearchActive()) {
				loadNextPage();
			}
		});
	}

	private void loadFirstPage() {
		PerfLog.log("PAGE", "start", "page=my_shale userId=" + (appState == null ? null : appState.getUserId()));
		loadGeneration++;
		System.out.println("[DEBUG LIVE][MY_CASES] loadFirstPage generation=" + loadGeneration + " sort=" + (myCasesSortChoice == null ? "<null>" : myCasesSortChoice.getValue())
				+ " query='" + normalizedSearchQuery() + "' selectedStatuses=" + selectedStatusIds.size());
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

		dbExec.submit(() ->
		{
			try {
				long daoStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=findMyCasesPage page=my_shale userId=" + userId + " pageIndex=" + pageToLoad);
				var page = caseDao.findMyCasesPage(userId, pageToLoad, pageSize, selectedSort(), false);
				PerfLog.logDone("DAO", "method=findMyCasesPage page=my_shale userId=" + userId + " pageIndex=" + pageToLoad + " rows=" + (page == null || page.items() == null ? 0
						: page.items().size()), daoStartNanos);
				List<CaseCardVm> newItems = page.items().stream()
						.map(this::toVm)
						.toList();

				Platform.runLater(() ->
				{
					if (generationAtSubmit != loadGeneration) {
						loading = false;
						return;
					}
					for (CaseCardVm vm : newItems) {
						upsertLoadedCase(vm);
					}
					System.out.println("[DEBUG LIVE][MY_CASES] page loaded page=" + pageToLoad + " items=" + newItems.size() + " total=" + page.total() + " loadedUnique=" + loaded
							.size());
					currentPage++;
					hasMore = loaded.size() < page.total();
					loading = false;
					rerender();
				});
			} catch (Exception ex) {
				Platform.runLater(() ->
				{
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
		PerfLog.logDone("RENDER", "panel=my_cases page=my_shale userId=" + (appState == null ? null : appState.getUserId()) + " childCount=" + myCasesFlow.getChildren().size(),
				renderStartNanos);
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
		final int shaleClientIdValue = shaleClientId;
		final int userIdValue = userId;
		dbExec.submit(() -> {
			try {
				long loadStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=loadMyTasks page=my_shale userId=" + userIdValue);
				List<CaseTaskListItemDto> tasks = caseTaskService.loadMyTasks(
						shaleClientIdValue,
						userIdValue,
						sortOption,
						includeCompleted);
				Set<Long> pinnedLaneCaseIds = loadPinnedTaskLaneCaseIds(shaleClientIdValue, userIdValue);
				List<Long> taskIds = (tasks == null ? List.<CaseTaskListItemDto>of() : tasks).stream()
						.map(CaseTaskListItemDto::id)
						.toList();
				PerfLog.logDone("DAO", "method=loadMyTasks page=my_shale userId=" + userIdValue + " rows=" + (tasks == null ? 0 : tasks.size()), loadStartNanos);
				long usersLoadStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=loadAssignedUsersForTasks page=my_shale userId=" + userIdValue);
					java.util.Map<Long, List<TaskCardFactory.AssignedUserModel>> assignedByTask = caseTaskService
							.loadAssignedUsersForTasks(taskIds, shaleClientIdValue)
						.stream()
						.collect(java.util.stream.Collectors.groupingBy(
								CaseTaskService.TaskAssignedUsersByTask::taskId,
								java.util.stream.Collectors.mapping(
										row -> new TaskCardFactory.AssignedUserModel(
												row.userId(),
												row.displayName(),
												row.color()),
											java.util.stream.Collectors.toList())));
					java.util.Map<Integer, String> prioritiesById = caseTaskService.loadActivePriorities(shaleClientIdValue).stream()
							.filter(Objects::nonNull)
							.collect(java.util.stream.Collectors.toMap(
									TaskPriorityOptionDto::id,
									option -> safe(option.name()).isBlank() ? ("Priority #" + option.id()) : option.name().trim(),
									(existing, replacement) -> existing,
									java.util.LinkedHashMap::new));
					PerfLog.logDone("DAO", "method=loadAssignedUsersForTasks page=my_shale userId=" + userIdValue + " rows=" + assignedByTask.size(), usersLoadStartNanos);
					runOnFx(() -> {
						myTasks = tasks == null ? List.of() : tasks;
						pinnedTaskLaneCaseIds.clear();
						pinnedTaskLaneCaseIds.addAll(pinnedLaneCaseIds);
						myTaskAssignedUsers = assignedByTask;
						myTaskPrioritiesById = prioritiesById;
						syncMyTaskPriorityFilterOptions();
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
		LaneBoardLayout.configureBoardRow(myTasksList);

		String searchQuery = normalizeSearchQuery(myTasksSearchField == null ? null : myTasksSearchField.getText());
		List<CaseTaskListItemDto> taskFiltered = filterAndRankMyTasks(myTasks, selectedPriorityFilterId(), searchQuery);
		List<CaseTaskListItemDto> filteredTasks = applyCaseColumnFilter(taskFiltered, selectedCaseFilterId());
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
		Map<TaskLaneKey, List<CaseTaskListItemDto>> tasksByLane = groupTasksByLane(filteredTasks);
		for (Map.Entry<TaskLaneKey, List<CaseTaskListItemDto>> entry : orderTaskLanes(tasksByLane)) {
			Node laneHeader = buildTaskLaneHeader(entry.getKey(), entry.getValue());
			Node laneBody = buildTaskLaneBody(entry.getValue(), fullVariant);
			VBox lane = LaneBoardLayout.createLane(
					laneHeader,
					laneBody,
					new LaneBoardLayout.LaneWidth(
							TASKS_CASE_COLUMN_MIN_WIDTH,
							TASKS_CASE_COLUMN_PREF_WIDTH,
							TASKS_CASE_COLUMN_MAX_WIDTH));
			myTasksList.getChildren().add(lane);
		}
		setVisibleManaged(myTasksEmptyLabel, false);
		setVisibleManaged(myTasksScroll, true);
		PerfLog.logDone("RENDER", "panel=my_tasks page=my_shale userId=" + (appState == null ? null : appState.getUserId()) + " childCount=" + myTasksList.getChildren().size(),
				renderStartNanos);
	}

	private Map<TaskLaneKey, List<CaseTaskListItemDto>> groupTasksByLane(List<CaseTaskListItemDto> tasks) {
		Map<TaskLaneKey, List<CaseTaskListItemDto>> grouped = new LinkedHashMap<>();
		if (tasks == null || tasks.isEmpty()) {
			return grouped;
		}
		for (CaseTaskListItemDto task : tasks) {
			TaskLaneKey key = taskLaneKey(task);
			grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(task);
		}
		return grouped;
	}

	private List<Map.Entry<TaskLaneKey, List<CaseTaskListItemDto>>> orderTaskLanes(Map<TaskLaneKey, List<CaseTaskListItemDto>> tasksByLane) {
		if (tasksByLane == null || tasksByLane.isEmpty()) {
			return List.of();
		}
		List<Map.Entry<TaskLaneKey, List<CaseTaskListItemDto>>> entries = new ArrayList<>(tasksByLane.entrySet());
		Map<TaskLaneKey, Integer> originalIndexes = new LinkedHashMap<>();
		for (int i = 0; i < entries.size(); i++) {
			originalIndexes.put(entries.get(i).getKey(), i);
		}

		boolean sortByDueDate = MY_TASKS_COLUMN_ORDER_OLDEST_INCOMPLETE_DUE.equals(
				myTasksColumnOrderChoice == null ? null : myTasksColumnOrderChoice.getValue());
		Comparator<Map.Entry<TaskLaneKey, List<CaseTaskListItemDto>>> comparator = taskLaneComparator(originalIndexes, sortByDueDate);
		List<Map.Entry<TaskLaneKey, List<CaseTaskListItemDto>>> noCase = new ArrayList<>();
		List<Map.Entry<TaskLaneKey, List<CaseTaskListItemDto>>> pinnedLanes = new ArrayList<>();
		List<Map.Entry<TaskLaneKey, List<CaseTaskListItemDto>>> unpinnedLanes = new ArrayList<>();

		for (Map.Entry<TaskLaneKey, List<CaseTaskListItemDto>> entry : entries) {
			TaskLaneKey key = entry.getKey();
			if (isUnassignedLane(key)) {
				noCase.add(entry);
			} else if (isPinnedLane(key)) {
				pinnedLanes.add(entry);
			} else {
				unpinnedLanes.add(entry);
			}
		}

		pinnedLanes.sort(comparator);
		unpinnedLanes.sort(comparator);

		List<Map.Entry<TaskLaneKey, List<CaseTaskListItemDto>>> ordered = new ArrayList<>(entries.size());
		ordered.addAll(pinnedLanes);
		ordered.addAll(unpinnedLanes);
		ordered.addAll(noCase);
		return ordered;
	}

	private Comparator<Map.Entry<TaskLaneKey, List<CaseTaskListItemDto>>> taskLaneComparator(
			Map<TaskLaneKey, Integer> originalIndexes,
			boolean sortByDueDate) {
		if (sortByDueDate) {
			return Comparator.<Map.Entry<TaskLaneKey, List<CaseTaskListItemDto>>, java.time.LocalDateTime>comparing(
					entry -> oldestIncompleteDueDate(entry.getValue()),
					Comparator.nullsLast(Comparator.naturalOrder()))
					.thenComparing(entry -> normalizeCaseName(entry.getKey().displayName()), Comparator.nullsLast(String::compareToIgnoreCase))
					.thenComparingInt(entry -> originalIndexes.getOrDefault(entry.getKey(), Integer.MAX_VALUE));
		}
		return Comparator.<Map.Entry<TaskLaneKey, List<CaseTaskListItemDto>>, String>comparing(
				entry -> normalizeCaseName(entry.getKey().displayName()),
				Comparator.nullsLast(String::compareToIgnoreCase))
				.thenComparingInt(entry -> originalIndexes.getOrDefault(entry.getKey(), Integer.MAX_VALUE));
	}

	private java.time.LocalDateTime oldestIncompleteDueDate(List<CaseTaskListItemDto> tasks) {
		if (tasks == null || tasks.isEmpty()) {
			return null;
		}
		return tasks.stream()
				.filter(task -> task != null && task.completedAt() == null && task.dueAt() != null)
				.map(CaseTaskListItemDto::dueAt)
				.min(Comparator.naturalOrder())
				.orElse(null);
	}

	private boolean isUnassignedLane(TaskLaneKey key) {
		return key == null || key.caseId() == null || key.caseId() <= 0;
	}

	private String normalizeCaseName(String caseName) {
		String normalized = safe(caseName).trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private TaskLaneKey taskLaneKey(CaseTaskListItemDto task) {
		if (task == null || task.caseId() <= 0) {
			return new TaskLaneKey(null, NO_CASE_COLUMN_TITLE, "", "", false);
		}
		String caseName = safe(task.caseName()).trim();
		if (caseName.isEmpty()) {
			caseName = "Case #" + task.caseId();
		}
		return new TaskLaneKey(
				task.caseId(),
				caseName,
				safe(task.caseResponsibleAttorney()),
				safe(task.caseResponsibleAttorneyColor()),
				Boolean.TRUE.equals(task.caseNonEngagementLetterSent()));
	}

	private Node buildTaskLaneHeader(TaskLaneKey key, List<CaseTaskListItemDto> tasksInLane) {
		int taskCount = tasksInLane == null ? 0 : tasksInLane.size();
		LaneUrgency laneUrgency = resolveLaneUrgency(tasksInLane);
		Node caseCard = caseCardFactory.create(
				new CaseCardModel(
						key == null || key.caseId() == null ? 0L : key.caseId(),
						key == null ? NO_CASE_COLUMN_TITLE : key.displayName(),
						null,
						null,
						key == null ? "" : key.responsibleAttorney(),
						key == null ? "" : key.responsibleAttorneyColor(),
						key != null && key.nonEngagementLetterSent()),
				CaseCardFactory.Variant.MINI);
		VBox header = new VBox(6);
		HBox headerTopRow = new HBox(8);
		headerTopRow.setAlignment(Pos.CENTER_LEFT);
		headerTopRow.getStyleClass().add("lane-header-top-row");
		headerTopRow.getChildren().add(caseCard);
		Label inlineCountLabel = new Label("(" + taskCount + ")");
		inlineCountLabel.getStyleClass().add("lane-task-count-inline");
		headerTopRow.getChildren().add(inlineCountLabel);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		headerTopRow.getChildren().add(spacer);
		if (key != null && key.caseId() != null && key.caseId() > 0) {
			boolean pinned = isPinnedLane(key);
			Button pinButton = new Button("📌");
			pinButton.setFocusTraversable(false);
			pinButton.getStyleClass().addAll(
					"lane-pin-button",
					pinned ? "lane-pin-button-pinned" : "lane-pin-button-unpinned");
			pinButton.setTooltip(new Tooltip(pinned ? "Unpin lane" : "Pin lane"));
			pinButton.setOnAction(event -> {
				boolean pinnedNow = toggleLanePinned(key);
				persistLanePinnedState(key, pinnedNow);
				renderMyTasks();
			});
			headerTopRow.getChildren().add(pinButton);
		}

		header.getChildren().add(headerTopRow);
		return header;
	}

	private LaneUrgency resolveLaneUrgency(List<CaseTaskListItemDto> tasksInLane) {
		if (tasksInLane == null || tasksInLane.isEmpty()) {
			return LaneUrgency.NONE;
		}
		LocalDateTime now = LocalDateTime.now();
		boolean hasDueSoon = false;
		for (CaseTaskListItemDto task : tasksInLane) {
			if (task == null || task.completedAt() != null || task.dueAt() == null) {
				continue;
			}
			if (task.dueAt().isBefore(now)) {
				return LaneUrgency.OVERDUE;
			}
			if (!task.dueAt().isAfter(now.plusWeeks(1))) {
				hasDueSoon = true;
			}
		}
		return hasDueSoon ? LaneUrgency.DUE_SOON : LaneUrgency.NONE;
	}

	private boolean isPinnedLane(TaskLaneKey key) {
		return key != null
				&& key.caseId() != null
				&& key.caseId() > 0
				&& pinnedTaskLaneCaseIds.contains(key.caseId());
	}

	private boolean toggleLanePinned(TaskLaneKey key) {
		if (key == null || key.caseId() == null || key.caseId() <= 0) {
			return false;
		}
		Long laneId = key.caseId();
		if (pinnedTaskLaneCaseIds.add(laneId)) {
			return true;
		}
		if (pinnedTaskLaneCaseIds.contains(laneId)) {
			pinnedTaskLaneCaseIds.remove(laneId);
		}
		return false;
	}

	private Set<Long> loadPinnedTaskLaneCaseIds(int shaleClientId, int userId) {
		if (userBoardLanePreferencesDao == null || shaleClientId <= 0 || userId <= 0) {
			return Set.of();
		}
		Set<String> laneKeys = userBoardLanePreferencesDao.listPinnedLaneKeys(
				shaleClientId,
				userId,
				MY_TASKS_BOARD_KEY,
				MY_TASKS_LANE_TYPE_CASE);
		if (laneKeys.isEmpty()) {
			return Set.of();
		}
		Set<Long> pinnedLaneIds = new LinkedHashSet<>();
		for (String laneKey : laneKeys) {
			if (laneKey == null || laneKey.isBlank()) {
				continue;
			}
			try {
				long laneId = Long.parseLong(laneKey.trim());
				if (laneId > 0) {
					pinnedLaneIds.add(laneId);
				}
			} catch (NumberFormatException ignored) {
			}
		}
		return pinnedLaneIds;
	}

	private void persistLanePinnedState(TaskLaneKey key, boolean isPinned) {
		if (key == null || key.caseId() == null || key.caseId() <= 0 || userBoardLanePreferencesDao == null || appState == null) {
			return;
		}
		Integer shaleClientId = appState.getShaleClientId();
		Integer userId = appState.getUserId();
		if (shaleClientId == null || shaleClientId <= 0 || userId == null || userId <= 0) {
			return;
		}
		String laneKey = String.valueOf(key.caseId());
		final int shaleClientIdValue = shaleClientId;
		final int userIdValue = userId;
		dbExec.submit(() -> userBoardLanePreferencesDao.upsertLanePreference(
				shaleClientIdValue,
				userIdValue,
				MY_TASKS_BOARD_KEY,
				MY_TASKS_LANE_TYPE_CASE,
				laneKey,
				isPinned,
				null,
				null,
				userIdValue));
	}

	private Node buildTaskLaneBody(List<CaseTaskListItemDto> tasksInLane, boolean fullVariant) {
		VBox taskCards = new VBox(10);
		taskCards.setFillWidth(true);
		if (tasksInLane == null || tasksInLane.isEmpty()) {
			Label emptyLabel = new Label("No tasks");
			emptyLabel.getStyleClass().add("lane-empty-state");
			taskCards.setAlignment(Pos.TOP_LEFT);
			taskCards.getChildren().add(emptyLabel);
			return taskCards;
		}
		for (CaseTaskListItemDto task : tasksInLane) {
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
				taskCards.getChildren().add(taskCardFactory.create(model, TaskCardFactory.Variant.FULL, true));
			} else {
				taskCards.getChildren().add(taskCardFactory.create(model, TaskCardFactory.Variant.COMPACT));
			}
		}
		return taskCards;
	}

	private String resolveMyTaskCardTitle(CaseTaskListItemDto task) {
		if (task == null) {
			return null;
		}
		String title = safe(task.title()).trim();
		return title.isBlank() ? "Task #" + task.id() : title;
	}

	private List<CaseTaskListItemDto> filterAndRankMyTasks(List<CaseTaskListItemDto> tasks, Integer selectedPriorityId, String normalizedQuery) {
		if (tasks == null || tasks.isEmpty()) {
			return List.of();
		}
		List<CaseTaskListItemDto> priorityFiltered = tasks.stream()
				.filter(task -> selectedPriorityId == null || Objects.equals(task.priorityId(), selectedPriorityId))
				.toList();
		if (normalizedQuery.isEmpty()) {
			return priorityFiltered;
		}
		record RankedTask(CaseTaskListItemDto task, int score, int originalIndex) {
		}
		List<RankedTask> ranked = new ArrayList<>();
		for (int i = 0; i < priorityFiltered.size(); i++) {
			CaseTaskListItemDto task = priorityFiltered.get(i);
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

	private List<CaseTaskListItemDto> applyCaseColumnFilter(List<CaseTaskListItemDto> tasks, Long selectedCaseId) {
		if (tasks == null || tasks.isEmpty()) {
			return List.of();
		}
		if (selectedCaseId == null) {
			return tasks;
		}
		return tasks.stream()
				.filter(task -> task.caseId() == selectedCaseId.longValue())
				.toList();
	}

	private void syncMyTaskPriorityFilterOptions() {
		if (myTasksPriorityFilterChoice == null) {
			return;
		}
		PriorityFilterOption selectedOption = myTasksPriorityFilterChoice.getSelectionModel().getSelectedItem();
		Integer selectedId = selectedOption == null ? null : selectedOption.priorityId();
		java.util.Map<Integer, String> priorities = myTaskPrioritiesById == null ? java.util.Map.of() : myTaskPrioritiesById;
		List<PriorityFilterOption> options = new ArrayList<>();
		options.add(ALL_PRIORITIES_OPTION);
		priorities.entrySet().stream()
				.map(entry -> new PriorityFilterOption(entry.getKey(), entry.getValue()))
				.sorted(Comparator.comparing(
						(PriorityFilterOption option) -> safe(option.displayName()).toLowerCase(Locale.ROOT),
						Comparator.nullsLast(String::compareToIgnoreCase)))
				.forEach(options::add);
		myTasksPriorityFilterChoice.getItems().setAll(options);
		Integer priorityIdToApply = selectedId != null ? selectedId : preferredMyTasksPriorityFilterId;
		if (priorityIdToApply != null && priorities.containsKey(priorityIdToApply)) {
			final Integer targetPriorityId = priorityIdToApply;
			myTasksPriorityFilterChoice.getSelectionModel().select(
					options.stream()
							.filter(option -> targetPriorityId.equals(option.priorityId()))
							.findFirst()
							.orElse(ALL_PRIORITIES_OPTION));
		} else {
			myTasksPriorityFilterChoice.getSelectionModel().select(ALL_PRIORITIES_OPTION);
		}
		preferredMyTasksPriorityFilterId = null;
	}

	private Integer selectedPriorityFilterId() {
		if (myTasksPriorityFilterChoice == null) {
			return null;
		}
		PriorityFilterOption option = myTasksPriorityFilterChoice.getSelectionModel().getSelectedItem();
		return option == null ? null : option.priorityId();
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
		Long caseIdToApply = selectedId != null ? selectedId : preferredMyTasksCaseFilterId;
		if (caseIdToApply != null && caseById.containsKey(caseIdToApply)) {
			final Long targetCaseId = caseIdToApply;
			myTasksCaseFilterChoice.getSelectionModel().select(
					options.stream()
							.filter(option -> targetCaseId.equals(option.caseId()))
							.findFirst()
							.orElse(ALL_CASES_OPTION));
		} else {
			myTasksCaseFilterChoice.getSelectionModel().select(ALL_CASES_OPTION);
		}
		preferredMyTasksCaseFilterId = null;
	}

	private String restoreMyTasksSortPreference() {
		String value = userPreferencesService == null ? null : userPreferencesService.getString(PREF_MY_TASKS_SORT, MY_TASKS_SORT_DUE_ASC);
		return MY_TASKS_SORT_DUE_DESC.equals(value) ? MY_TASKS_SORT_DUE_DESC : MY_TASKS_SORT_DUE_ASC;
	}

	private boolean restoreMyTasksShowCompletedPreference() {
		return userPreferencesService != null && userPreferencesService.getBoolean(PREF_MY_TASKS_SHOW_COMPLETED, false);
	}

	private Integer restoreMyTasksPriorityFilterPreference() {
		String value = userPreferencesService == null ? null : userPreferencesService.getString(PREF_MY_TASKS_PRIORITY_FILTER, "");
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			int parsed = Integer.parseInt(value.trim());
			return parsed > 0 ? parsed : null;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private String restoreMyTasksLaneOrderPreference() {
		String value = userPreferencesService == null ? null : userPreferencesService.getString(PREF_MY_TASKS_LANE_ORDER, MY_TASKS_COLUMN_ORDER_CASE_NAME);
		return MY_TASKS_COLUMN_ORDER_OLDEST_INCOMPLETE_DUE.equals(value)
				? MY_TASKS_COLUMN_ORDER_OLDEST_INCOMPLETE_DUE
				: MY_TASKS_COLUMN_ORDER_CASE_NAME;
	}

	private Long restoreMyTasksCaseFilterPreference() {
		String value = userPreferencesService == null ? null : userPreferencesService.getString(PREF_MY_TASKS_CASE_FILTER, "");
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			long parsed = Long.parseLong(value.trim());
			return parsed > 0 ? parsed : null;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private void persistMyTasksSortPreference(String value) {
		if (suppressMyTaskPreferenceWrites || userPreferencesService == null) {
			return;
		}
		userPreferencesService.putString(PREF_MY_TASKS_SORT, MY_TASKS_SORT_DUE_DESC.equals(value) ? MY_TASKS_SORT_DUE_DESC : MY_TASKS_SORT_DUE_ASC);
	}

	private void persistMyTasksShowCompletedPreference(boolean value) {
		if (suppressMyTaskPreferenceWrites || userPreferencesService == null) {
			return;
		}
		userPreferencesService.putBoolean(PREF_MY_TASKS_SHOW_COMPLETED, value);
	}

	private void persistMyTasksPriorityFilterPreference(PriorityFilterOption option) {
		if (suppressMyTaskPreferenceWrites || userPreferencesService == null) {
			return;
		}
		Integer priorityId = option == null ? null : option.priorityId();
		userPreferencesService.putString(PREF_MY_TASKS_PRIORITY_FILTER, priorityId == null ? "" : String.valueOf(priorityId));
	}

	private void persistMyTasksLaneOrderPreference(String value) {
		if (suppressMyTaskPreferenceWrites || userPreferencesService == null) {
			return;
		}
		userPreferencesService.putString(
				PREF_MY_TASKS_LANE_ORDER,
				MY_TASKS_COLUMN_ORDER_OLDEST_INCOMPLETE_DUE.equals(value)
						? MY_TASKS_COLUMN_ORDER_OLDEST_INCOMPLETE_DUE
						: MY_TASKS_COLUMN_ORDER_CASE_NAME);
	}

	private void persistMyTasksCaseFilterPreference(CaseFilterOption option) {
		if (suppressMyTaskPreferenceWrites || userPreferencesService == null) {
			return;
		}
		Long caseId = option == null ? null : option.caseId();
		userPreferencesService.putString(PREF_MY_TASKS_CASE_FILTER, caseId == null ? "" : String.valueOf(caseId));
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

		new Thread(() ->
		{
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
			Optional<TaskDetailDialog.TaskDetailResult> result = TaskDetailDialog.showAndWait(
					"MY_TASKS",
					clickReceivedAt,
					taskDialogOwner(),
					model,
					List.of(),
					List.of(),
					id ->
					{
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
		new Thread(() ->
		{
			try {
				caseTaskService.updateTask(request);
				runOnFx(this::refreshMyTasks);
			} catch (Exception ex) {
				runOnFx(() -> showTaskActionError("Failed to save task. " + rootCauseMessage(ex)));
			}
		}, "my-shale-task-save-" + taskId).start();
	}

	private void deleteTaskFromDetail(long taskId, int shaleClientId, int currentUserId) {
		new Thread(() ->
		{
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

	private record PriorityFilterOption(Integer priorityId, String displayName) {
		@Override
		public String toString() {
			String text = safe(displayName).trim();
			return text.isBlank() ? "All Priorities" : text;
		}
	}

	private record TaskLaneKey(
			Long caseId,
			String displayName,
			String responsibleAttorney,
			String responsibleAttorneyColor,
			boolean nonEngagementLetterSent
	) {
	}

	private enum LaneUrgency {
		NONE,
		DUE_SOON,
		OVERDUE
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
