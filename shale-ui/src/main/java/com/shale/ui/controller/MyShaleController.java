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
import com.shale.ui.component.factory.DashboardWidgetFactory;
import com.shale.ui.component.factory.StatusIndicatorFactory;
import com.shale.ui.component.factory.TaskCardFactory;
import com.shale.ui.controller.support.CaseListUiSupport;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.services.PhiReadAuditService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.UserPreferencesService;
import com.shale.ui.state.AppState;
import com.shale.ui.util.AppSectionTabs;
import com.shale.ui.util.PerfLog;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
	private static final String SECTION_TASKS = "My Tasks";
	private static final String SECTION_MY_CASES = "My Cases";
	private static final double TASKS_CASE_COLUMN_MIN_WIDTH = 225;
	private static final double TASKS_CASE_COLUMN_PREF_WIDTH = 260;
	private static final double TASKS_CASE_COLUMN_MAX_WIDTH = 300;
	private static final double MY_CASES_STATUS_COLUMN_MIN_WIDTH = 320;
	private static final double MY_CASES_STATUS_COLUMN_PREF_WIDTH = 360;
	private static final double MY_CASES_STATUS_COLUMN_MAX_WIDTH = 400;
	private static final double OVERVIEW_CARD_GAP = 10;
	private static final double OVERVIEW_SECTION_HORIZONTAL_PADDING = 10;
	private static final double OVERVIEW_COMPACT_TASK_CARD_WIDTH = 210;
	private static final String OVERVIEW_SORT_DUE_ASC = "Due Date (earliest first)";
	private static final String OVERVIEW_SORT_DUE_DESC = "Due Date (latest first)";
	private static final String OVERVIEW_SORT_PRIORITY = "Priority";
	private static final String OVERVIEW_SORT_CASE_NAME = "Case Name";
	private static final String OVERVIEW_SORT_TITLE = "Title";
	private static final String NO_CASE_COLUMN_TITLE = "No Case";
	private static final String MY_TASKS_BOARD_KEY = "my_shale_tasks";
	private static final String MY_TASKS_LANE_TYPE_CASE = "CASE";
	private static final String PREF_MY_TASKS_SORT = "my_shale_tasks.task_sort";
	private static final String PREF_MY_TASKS_SHOW_COMPLETED = "my_shale_tasks.show_completed";
	private static final String PREF_MY_TASKS_PRIORITY_FILTER = "my_shale_tasks.priority_filter";
	private static final String PREF_MY_TASKS_LANE_ORDER = "my_shale_tasks.lane_order";
	private static final String PREF_MY_TASKS_CASE_FILTER = "my_shale_tasks.case_filter";
	private static final double MY_TASKS_GRID_HGAP = 10;
	private static final double MY_TASKS_GRID_VGAP = 10;
	private static final long MY_SHALE_PRIORITY_CACHE_TTL_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(5);

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
	private ChoiceBox<MyTasksSource> myTasksSourceChoice;
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
	private Button myTasksBoardViewButton;
	@FXML
	private Button myTasksGridViewButton;
	@FXML
	private ScrollPane myTasksScroll;
	@FXML
	private HBox myTasksList;
	@FXML
	private Label myTasksLoadingLabel;
	@FXML
	private Label myTasksEmptyLabel;
	@FXML
	private HBox sectionTabsBar;
	@FXML
	private VBox overviewSectionPane;
	@FXML
	private VBox tasksSectionPane;
	@FXML
	private VBox myCasesSectionPane;
	@FXML
	private VBox myTasksPanel;
	@FXML
	private VBox tasksSectionContentHost;
	@FXML
	private VBox myCasesSectionContentHost;
	@FXML
	private VBox overviewMainRow;
	@FXML
	private ScrollPane overviewScroll;
	@FXML
	private Label overviewLoadingLabel;
	@FXML
	private StackPane sectionContentStack;
	@FXML
	private ScrollPane myCasesBoardScroll;
	@FXML
	private HBox myCasesBoardList;
	@FXML
	private Label myCasesLoadingLabel;
	@FXML
	private Label myCasesBoardEmptyLabel;
	@FXML
	private TextField myCasesBoardSearchField;
	@FXML
	private ChoiceBox<String> myCasesBoardSortChoice;
	@FXML
	private ChoiceBox<BoardStatusFilterOption> myCasesBoardStatusFilterChoice;

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
	private int taskLoadGeneration = 0;
	private int myCasesBoardLoadGeneration = 0;

	private final List<CaseCardVm> loaded = new ArrayList<>();
	private List<CaseTaskListItemDto> myTasks = List.of();
	private java.util.Map<Long, List<TaskCardFactory.AssignedUserModel>> myTaskAssignedUsers = java.util.Map.of();
	private java.util.Map<Integer, String> myTaskPrioritiesById = java.util.Map.of();
	private java.util.Map<Integer, String> cachedPriorityNamesById = java.util.Map.of();
	private Integer cachedPriorityTenantId;
	private long cachedPriorityLoadedAtNanos;
	private List<CaseCardVm> myAssignedCasesBoard = List.of();
	private boolean loadingOverview;
	private boolean loadingMyTasks;
	private boolean loadingMyCases;
	private boolean myTasksLoadedOnce;
	private boolean myTasksDirty = true;
	private boolean myCasesLoadedOnce;
	private boolean myCasesDirty = true;
	private Integer cachedTasksUserId;
	private Integer cachedTasksTenantId;
	private Integer cachedCasesUserId;
	private Integer cachedCasesTenantId;
	private boolean myCasesLoadFailed;
	private boolean showCompletedMyTasks;
	private final Set<Integer> selectedStatusIds = new LinkedHashSet<>();
	private final Set<Long> pinnedTaskLaneCaseIds = new LinkedHashSet<>();
	private final Set<Long> collapsedTaskLaneCaseIds = new LinkedHashSet<>();
	private List<CaseListUiSupport.StatusFilterOption> statusFilterOptions = List.of();
	private final Map<String, Button> sectionTabs = new LinkedHashMap<>();
	private String activeSection = SECTION_OVERVIEW;
	private boolean suppressMyTaskPreferenceWrites;
	private MyTasksViewMode myTasksViewMode = MyTasksViewMode.BOARD;
	private Integer preferredMyTasksPriorityFilterId;
	private Long preferredMyTasksCaseFilterId;
	private String overviewSearchText = "";
	private Integer overviewPriorityFilterId;
	private Long overviewCaseFilterId;
	private boolean overviewOverdueOnly;
	private String overviewSortMode = OVERVIEW_SORT_DUE_ASC;
	private VBox overviewSectionsContainer;
	private VBox overviewWidgetsContainer;
	private TextField overviewSearchFieldControl;
	private ChoiceBox<PriorityFilterOption> overviewPriorityChoiceControl;
	private ChoiceBox<CaseFilterOption> overviewCaseChoiceControl;
	private CheckBox overviewOverdueOnlyCheckControl;
	private ChoiceBox<String> overviewSortChoiceControl;
	private boolean suppressOverviewControlEvents;
	private static final BoardStatusFilterOption ALL_BOARD_STATUSES_OPTION = new BoardStatusFilterOption(null, "All Statuses");
	private FlowPane myTasksGrid;

	private enum MyTasksViewMode {
		BOARD,
		GRID
	}

	private enum MyTasksSource {
		ASSIGNED_TO_ME("Assigned to Me"),
		CREATED_BY_ME("Created by Me");

		private final String label;

		MyTasksSource(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private MyTasksSource myTasksSource = MyTasksSource.ASSIGNED_TO_ME;

	private final ExecutorService casesDbExec = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "my-shale-cases-loader");
		t.setDaemon(true);
		return t;
	});
	private final ExecutorService tasksDbExec = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "my-shale-tasks-loader");
		t.setDaemon(true);
		return t;
	});
	private final ExecutorService prefsDbExec = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "my-shale-prefs-loader");
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
		if (myTasksSourceChoice != null) {
			myTasksSourceChoice.getItems().setAll(MyTasksSource.values());
			myTasksSourceChoice.getSelectionModel().select(MyTasksSource.ASSIGNED_TO_ME);
			myTasksSourceChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
				MyTasksSource selected = newV == null ? MyTasksSource.ASSIGNED_TO_ME : newV;
				if (selected == myTasksSource) {
					return;
				}
				myTasksSource = selected;
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
		if (myTasksBoardViewButton != null) {
			myTasksBoardViewButton.setOnAction(e -> setMyTasksViewMode(MyTasksViewMode.BOARD));
		}
		if (myTasksGridViewButton != null) {
			myTasksGridViewButton.setOnAction(e -> setMyTasksViewMode(MyTasksViewMode.GRID));
		}
		updateMyTasksViewToggleStyles();
		preferredMyTasksPriorityFilterId = restoreMyTasksPriorityFilterPreference();
		preferredMyTasksCaseFilterId = restoreMyTasksCaseFilterPreference();
		if (myCasesBoardSortChoice != null) {
			myCasesBoardSortChoice.getItems().setAll(SORT_NAME, SORT_INTAKE, SORT_SOL);
			myCasesBoardSortChoice.getSelectionModel().select(SORT_NAME);
			myCasesBoardSortChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> renderMyCasesBoard());
		}
		if (myCasesBoardSearchField != null) {
			myCasesBoardSearchField.textProperty().addListener((obs, oldV, newV) -> renderMyCasesBoard());
		}
		if (myCasesBoardStatusFilterChoice != null) {
			myCasesBoardStatusFilterChoice.getItems().setAll(ALL_BOARD_STATUSES_OPTION);
			myCasesBoardStatusFilterChoice.getSelectionModel().select(ALL_BOARD_STATUSES_OPTION);
			myCasesBoardStatusFilterChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> renderMyCasesBoard());
		}

		reloadStatusFilterOptionsAndThen(() -> {
			renderMyCasesBoard();
		});

		Platform.runLater(() ->
		{
			onSectionSelected(SECTION_OVERVIEW);
			wireInfiniteScroll();
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
		if (overviewScroll != null) {
			VBox.setVgrow(overviewScroll, Priority.ALWAYS);
			overviewScroll.setFitToWidth(true);
			overviewScroll.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		}
		if (overviewMainRow != null) {
			VBox.setVgrow(overviewMainRow, Priority.ALWAYS);
			overviewMainRow.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		}
		if (tasksSectionPane != null) {
			tasksSectionPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
			StackPane.setAlignment(tasksSectionPane, Pos.TOP_LEFT);
		}
		if (myCasesSectionPane != null) {
			myCasesSectionPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
			StackPane.setAlignment(myCasesSectionPane, Pos.TOP_LEFT);
		}
		if (tasksSectionContentHost != null) {
			VBox.setVgrow(tasksSectionContentHost, Priority.ALWAYS);
			tasksSectionContentHost.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		}
		if (myCasesSectionContentHost != null) {
			VBox.setVgrow(myCasesSectionContentHost, Priority.ALWAYS);
			myCasesSectionContentHost.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
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
		if (myCasesBoardScroll != null) {
			VBox.setVgrow(myCasesBoardScroll, Priority.ALWAYS);
			myCasesBoardScroll.setFitToHeight(true);
			myCasesBoardScroll.setFitToWidth(false);
			myCasesBoardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
			myCasesBoardScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
			myCasesBoardScroll.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		}
	}

	private void setupSections() {
		if (sectionTabsBar == null) {
			return;
		}
		sectionTabs.clear();
		sectionTabs.putAll(AppSectionTabs.buildTabs(
				sectionTabsBar,
				List.of(
						new AppSectionTabs.TabSpec<>(SECTION_OVERVIEW, SECTION_OVERVIEW),
						new AppSectionTabs.TabSpec<>(SECTION_TASKS, SECTION_TASKS),
						new AppSectionTabs.TabSpec<>(SECTION_MY_CASES, SECTION_MY_CASES)),
				this::onSectionSelected));
	}

	private void onSectionSelected(String section) {
		if (section == null) {
			return;
		}
		long switchStartNanos = PerfLog.start();
		activeSection = section;
		Button activeButton = sectionTabs.get(section);
		AppSectionTabs.setActive(activeButton, sectionTabs.values());
		boolean showOverview = SECTION_OVERVIEW.equals(section);
		boolean showTasks = SECTION_TASKS.equals(section);
		boolean showMyCases = SECTION_MY_CASES.equals(section);
		setVisibleManaged(overviewSectionPane, showOverview);
		setVisibleManaged(tasksSectionPane, showTasks);
		setVisibleManaged(myCasesSectionPane, showMyCases);
		if (showOverview) {
			primeTasksLoadingStateForFirstLoad();
			if (myTasksLoadedOnce && !myTasksDirty && !loadingMyTasks) {
				renderMyOverview();
			}
			ensureMyTasksFresh(false);
			ensureMyCasesFresh(false);
		}
		if (showTasks) {
			primeTasksLoadingStateForFirstLoad();
			attachTasksPanel(tasksSectionContentHost);
			if (myTasksLoadedOnce || loadingMyTasks) {
				renderMyTasks();
			}
			ensureMyTasksFresh(false);
		}
		if (showMyCases) {
			primeMyCasesLoadingStateForFirstLoad();
			if (myCasesLoadedOnce || loadingMyCases) {
				renderMyCasesBoard();
			}
			ensureMyCasesFresh(false);
		}
		PerfLog.logDone("RENDER", "panel=my_shale_sections section=" + section, switchStartNanos);
	}

	private void primeTasksLoadingStateForFirstLoad() {
		if (!myTasksLoadedOnce && !loadingMyTasks) {
			loadingOverview = true;
		}
	}

	private void primeMyCasesLoadingStateForFirstLoad() {
		// Intentionally left blank: loadingMyCases should only be controlled
		// by refreshMyCasesBoard(...), which owns in-flight state.
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

		casesDbExec.submit(() ->
		{
			long statusStartNanos = PerfLog.start();
			PerfLog.log("DAO", "start", "method=listStatusesForTenant page=my_shale organizationId=" + tenantId);
			List<CaseDao.StatusRow> statuses = caseDao.listStatusesForTenant(tenantId);
			PerfLog.logDone("DAO", "method=listStatusesForTenant page=my_shale organizationId=" + tenantId + " rows=" + (statuses == null ? 0 : statuses.size()), statusStartNanos);
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
				syncMyCasesBoardStatusFilterOptions();
				renderOverviewWidgets();
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
		casesDbExec.submit(() ->
		{
			try {
				long daoStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=getMyCaseRow page=my_shale caseId=" + caseId + " userId=" + userId);
				CaseDao.CaseRow row = caseDao.getMyCaseRow(userId, caseId);
				PerfLog.logDone("DAO", "method=getMyCaseRow page=my_shale caseId=" + caseId + " userId=" + userId + " rows=" + (row == null ? 0 : 1), daoStartNanos);
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
						myCasesDirty = false;
						myCasesLoadedOnce = true;
						rerender();
						renderMyCasesBoard();
						renderOverviewWidgets();
					}
				});
				} catch (Exception ex) {
					System.out.println("[DEBUG LIVE][MY_CASES] targeted refresh failed caseId=" + caseId + " message=" + ex.getMessage());
					runOnFx(() -> {
						myCasesDirty = true;
						refreshMyCasesBoard(true);
					});
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
				r.tortClaimsNoticeDeadline(),
				r.primaryStatusId(),
				safe(r.responsibleAttorneyName()),
				safe(r.responsibleAttorneyColor()),
				r.nonEngagementLetterSent(),
				safe(r.primaryStatusName()),
				safe(r.primaryStatusColor()),
				safe(r.practiceAreaColor())
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

		casesDbExec.submit(() ->
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
				vm.nonEngagementLetterSent,
				vm.primaryStatusName,
				vm.primaryStatusColor,
				vm.practiceAreaColor));
	}

	private void refreshMyTasks() {
		refreshMyTasks(true);
	}

	private void ensureMyTasksFresh(boolean force) {
		invalidateTaskCacheIfContextChanged();
		if (!force && myTasksLoadedOnce && !myTasksDirty && !loadingMyTasks) {
			PerfLog.log("CTRL", "cache_hit", "panel=my_tasks page=my_shale");
			return;
		}
		refreshMyTasks(force);
	}

	private void invalidateTaskCacheIfContextChanged() {
		Integer currentUserId = appState == null ? null : appState.getUserId();
		Integer currentTenantId = appState == null ? null : appState.getShaleClientId();
		if (!Objects.equals(cachedTasksUserId, currentUserId) || !Objects.equals(cachedTasksTenantId, currentTenantId)) {
			myTasksLoadedOnce = false;
			myTasksDirty = true;
			cachedPriorityTenantId = null;
			cachedPriorityNamesById = java.util.Map.of();
			cachedPriorityLoadedAtNanos = 0L;
		}
	}

	private void renderActiveTaskViews() {
		if (SECTION_OVERVIEW.equals(activeSection)) {
			renderMyOverview();
			return;
		}
		if (SECTION_TASKS.equals(activeSection)) {
			renderMyTasks();
		}
	}

	private java.util.Map<Integer, String> loadMyShalePriorityNames(int shaleClientId) {
		long now = System.nanoTime();
		if (Objects.equals(cachedPriorityTenantId, shaleClientId)
				&& cachedPriorityLoadedAtNanos > 0L
				&& now - cachedPriorityLoadedAtNanos < MY_SHALE_PRIORITY_CACHE_TTL_NANOS) {
			PerfLog.log("DAO", "cache_hit", "method=loadActivePriorities page=my_shale organizationId=" + shaleClientId + " rows=" + cachedPriorityNamesById.size());
			return cachedPriorityNamesById;
		}

		long prioritiesLoadStartNanos = PerfLog.start();
		PerfLog.log("DAO", "start", "method=loadActivePriorities page=my_shale organizationId=" + shaleClientId);
		java.util.Map<Integer, String> prioritiesById = caseTaskService.loadActivePriorities(shaleClientId).stream()
				.filter(Objects::nonNull)
				.collect(java.util.stream.Collectors.toMap(
						TaskPriorityOptionDto::id,
						option -> safe(option.name()).isBlank() ? ("Priority #" + option.id()) : option.name().trim(),
						(existing, replacement) -> existing,
						java.util.LinkedHashMap::new));
		cachedPriorityTenantId = shaleClientId;
		cachedPriorityNamesById = prioritiesById;
		cachedPriorityLoadedAtNanos = System.nanoTime();
		PerfLog.logDone("DAO", "method=loadActivePriorities page=my_shale organizationId=" + shaleClientId + " rows=" + prioritiesById.size(), prioritiesLoadStartNanos);
		return prioritiesById;
	}

	private void refreshMyTasks(boolean force) {
		if (caseTaskService == null || appState == null) {
			return;
		}
		if (loadingMyTasks) {
			if (!force) {
				return;
			}
			PerfLog.log("CTRL", "supersede", "panel=my_tasks page=my_shale generation=" + (taskLoadGeneration + 1));
		}
		loadingOverview = true;
		loadingMyTasks = true;
		renderActiveTaskViews();
		Integer shaleClientId = appState.getShaleClientId();
		Integer userId = appState.getUserId();
			if (shaleClientId == null || shaleClientId <= 0 || userId == null || userId <= 0) {
				myTasks = List.of();
				myTaskAssignedUsers = java.util.Map.of();
				myTaskPrioritiesById = java.util.Map.of();
				cachedTasksUserId = userId;
				cachedTasksTenantId = shaleClientId;
				myTasksLoadedOnce = true;
				myTasksDirty = false;
			loadingOverview = false;
			loadingMyTasks = false;
			renderActiveTaskViews();
			return;
		}

		CaseTaskService.MyTasksSortOption sortOption = selectedMyTaskSort();
		final boolean includeCompleted = showCompletedMyTasks;
		final MyTasksSource sourceAtSubmit = myTasksSource;
		final int shaleClientIdValue = shaleClientId;
		final int userIdValue = userId;
		final int generationAtSubmit = ++taskLoadGeneration;
		tasksDbExec.submit(() -> {
			try {
				long loadStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=loadMyTasks page=my_shale userId=" + userIdValue);
				List<CaseTaskListItemDto> tasks = sourceAtSubmit == MyTasksSource.CREATED_BY_ME
						? caseTaskService.loadTasksCreatedByUser(
								shaleClientIdValue,
								userIdValue,
								sortOption,
								includeCompleted)
						: caseTaskService.loadMyTasks(
								shaleClientIdValue,
								userIdValue,
								sortOption,
								includeCompleted);
				Set<Long> pinnedLaneCaseIds = loadPinnedTaskLaneCaseIds(shaleClientIdValue, userIdValue);
				Set<Long> collapsedLaneCaseIds = loadCollapsedTaskLaneCaseIds(shaleClientIdValue, userIdValue);
				List<Long> taskIds = (tasks == null ? List.<CaseTaskListItemDto>of() : tasks).stream()
						.map(CaseTaskListItemDto::id)
						.toList();
				PerfLog.logDone("DAO", "method=loadMyTasks page=my_shale userId=" + userIdValue + " rows=" + (tasks == null ? 0 : tasks.size()), loadStartNanos);
				long usersLoadStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=loadAssignedUsersForTasks page=my_shale userId=" + userIdValue + " taskCount=" + taskIds.size());
					java.util.Map<Long, List<TaskCardFactory.AssignedUserModel>> assignedByTask = taskIds.isEmpty()
							? java.util.Map.of()
							: caseTaskService.loadAssignedUsersForTasks(taskIds, shaleClientIdValue)
						.stream()
						.collect(java.util.stream.Collectors.groupingBy(
								CaseTaskService.TaskAssignedUsersByTask::taskId,
								java.util.stream.Collectors.mapping(
										row -> new TaskCardFactory.AssignedUserModel(
												row.userId(),
												row.displayName(),
												row.color()),
											java.util.stream.Collectors.toList())));
					java.util.Map<Integer, String> prioritiesById = loadMyShalePriorityNames(shaleClientIdValue);
					PerfLog.logDone("DAO", "method=loadAssignedUsersForTasks page=my_shale userId=" + userIdValue + " rows=" + assignedByTask.size(), usersLoadStartNanos);
					runOnFx(() -> {
						if (generationAtSubmit != taskLoadGeneration) {
							PerfLog.log("CTRL", "stale", "panel=my_tasks page=my_shale generation=" + generationAtSubmit);
							return;
						}
						loadingOverview = false;
						loadingMyTasks = false;
						myTasks = tasks == null ? List.of() : tasks;
						pinnedTaskLaneCaseIds.clear();
						pinnedTaskLaneCaseIds.addAll(pinnedLaneCaseIds);
						collapsedTaskLaneCaseIds.clear();
						collapsedTaskLaneCaseIds.addAll(collapsedLaneCaseIds);
							myTaskAssignedUsers = assignedByTask;
							myTaskPrioritiesById = prioritiesById;
							cachedTasksUserId = userIdValue;
							cachedTasksTenantId = shaleClientIdValue;
							myTasksLoadedOnce = true;
							myTasksDirty = false;
						syncMyTaskPriorityFilterOptions();
						syncMyTaskCaseFilterOptions();
						renderActiveTaskViews();
					});
			} catch (Exception ex) {
				System.err.println("My tasks load failed: " + ex.getMessage());
				ex.printStackTrace();
				runOnFx(() -> {
					loadingOverview = false;
					loadingMyTasks = false;
					myTasksDirty = true;
					renderActiveTaskViews();
					showTaskActionError("Failed to load your tasks.");
				});
			}
		});
	}

	private void refreshMyCasesBoard() {
		refreshMyCasesBoard(true);
	}

	private void ensureMyCasesFresh(boolean force) {
		invalidateMyCasesCacheIfContextChanged();
		if (!force && myCasesLoadedOnce && !myCasesDirty && !loadingMyCases) {
			PerfLog.log("CTRL", "cache_hit", "panel=my_cases_board page=my_shale");
			return;
		}
		refreshMyCasesBoard(force);
	}

	private void invalidateMyCasesCacheIfContextChanged() {
		Integer currentUserId = appState == null ? null : appState.getUserId();
		Integer currentTenantId = appState == null ? null : appState.getShaleClientId();
		if (!Objects.equals(cachedCasesUserId, currentUserId) || !Objects.equals(cachedCasesTenantId, currentTenantId)) {
			myCasesLoadedOnce = false;
			myCasesDirty = true;
		}
	}

	private void refreshMyCasesBoard(boolean force) {
		if (caseDao == null || appState == null) {
			return;
		}
		if (!force && loadingMyCases) {
			return;
		}
		loadingMyCases = true;
		renderMyCasesBoard();
		renderOverviewWidgets();
		Integer userId = appState.getUserId();
		Integer shaleClientId = appState.getShaleClientId();
		System.out.println("[TRACE ASSIGNED_CASES][MyShaleController.refreshMyCasesBoard] load started userId=" + userId
				+ " selectedUserId=" + userId);
		loadingMyCases = true;
		myCasesLoadFailed = false;
		renderMyCasesBoard();
		renderOverviewWidgets();
			if (userId == null || userId <= 0 || shaleClientId == null || shaleClientId <= 0) {
				myAssignedCasesBoard = List.of();
				loadingMyCases = false;
				myCasesLoadFailed = false;
				cachedCasesUserId = userId;
				cachedCasesTenantId = shaleClientId;
				myCasesLoadedOnce = true;
				myCasesDirty = false;
			renderMyCasesBoard();
			renderOverviewWidgets();
			return;
		}
		final int userIdValue = userId;
		final int generationAtSubmit = ++myCasesBoardLoadGeneration;
		casesDbExec.submit(() -> {
			try {
				long daoStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=listAssignedCasesForBoard page=my_shale userId=" + userIdValue);
				List<CaseDao.CaseRow> rows = caseDao.listAssignedCasesForBoard(userIdValue);
				PerfLog.logDone("DAO", "method=listAssignedCasesForBoard page=my_shale userId=" + userIdValue + " rows=" + (rows == null ? 0 : rows.size()), daoStartNanos);
				int rowCount = rows == null ? 0 : rows.size();
				System.out.println("[TRACE ASSIGNED_CASES][MyShaleController.refreshMyCasesBoard] dao returned rowCount=" + rowCount
						+ " userId=" + userIdValue);
				List<CaseCardVm> cases = (rows == null ? List.<CaseDao.CaseRow>of() : rows).stream()
						.filter(Objects::nonNull)
						.map(this::toVm)
						.toList();
				runOnFx(() -> {
					if (generationAtSubmit != myCasesBoardLoadGeneration) {
						PerfLog.log("CTRL", "stale", "panel=my_cases_board page=my_shale generation=" + generationAtSubmit);
						return;
					}
						loadingMyCases = false;
						myCasesLoadFailed = false;
						myAssignedCasesBoard = cases;
						cachedCasesUserId = userIdValue;
						cachedCasesTenantId = shaleClientId;
						myCasesLoadedOnce = true;
						myCasesDirty = false;
					renderMyCasesBoard();
					renderOverviewWidgets();
				});
			} catch (Exception ex) {
				System.err.println("My cases board load failed userId=" + userIdValue + ": " + ex.getMessage());
				ex.printStackTrace();
				runOnFx(() -> {
					loadingMyCases = false;
					myCasesLoadFailed = true;
					myCasesDirty = true;
					myAssignedCasesBoard = List.of();
					renderMyCasesBoard();
					renderOverviewWidgets();
				});
			}
		});
	}

	private void renderMyCasesBoard() {
		if (myCasesBoardList == null || myCasesBoardEmptyLabel == null || myCasesBoardScroll == null || myCasesLoadingLabel == null) {
			return;
		}
		long renderStartNanos = PerfLog.start();
		PerfLog.log("RENDER", "start", "panel=my_cases_board page=my_shale userId=" + (appState == null ? null : appState.getUserId()));
		if (loadingMyCases) {
			myCasesBoardList.getChildren().clear();
			myCasesBoardEmptyLabel.setText("Loading your cases...");
			setVisibleManaged(myCasesBoardEmptyLabel, true);
			setVisibleManaged(myCasesBoardScroll, false);
			setVisibleManaged(myCasesLoadingLabel, false);
			PerfLog.logDone("RENDER", "panel=my_cases_board page=my_shale state=loading childCount=0", renderStartNanos);
			return;
		}
		myCasesBoardList.getChildren().clear();
		setVisibleManaged(myCasesLoadingLabel, false);
		if (myCasesLoadFailed) {
			myCasesBoardEmptyLabel.setText("Unable to load assigned cases.");
			setVisibleManaged(myCasesBoardEmptyLabel, true);
			setVisibleManaged(myCasesBoardScroll, false);
			System.out.println("[TRACE ASSIGNED_CASES][MyShaleController.renderMyCasesBoard] error state rendered");
			PerfLog.logDone("RENDER", "panel=my_cases_board page=my_shale state=error childCount=0", renderStartNanos);
			return;
		}

		LaneBoardLayout.configureBoardRow(myCasesBoardList);
		String searchQuery = normalizeSearchQuery(myCasesBoardSearchField == null ? null : myCasesBoardSearchField.getText());
		Comparator<CaseCardVm> laneSort = myCasesLaneComparator(myCasesBoardSortChoice == null ? SORT_NAME : myCasesBoardSortChoice.getValue());
		Integer selectedStatusId = selectedMyCasesBoardStatusId();

		Map<Integer, List<CaseCardVm>> byStatus = new LinkedHashMap<>();
		for (CaseListUiSupport.StatusFilterOption status : statusFilterOptions) {
			if (status != null) {
				byStatus.putIfAbsent(status.id(), new ArrayList<>());
			}
		}
		List<CaseCardVm> noStatus = new ArrayList<>();
		for (CaseCardVm vm : myAssignedCasesBoard) {
			if (vm == null || vm.id <= 0) {
				continue;
			}
			if (!matchesMyCasesBoardSearch(vm, searchQuery)) {
				continue;
			}
			if (selectedStatusId != null && !Objects.equals(selectedStatusId, vm.primaryStatusId)) {
				continue;
			}
			Integer statusId = vm.primaryStatusId;
			if (statusId == null) {
				noStatus.add(vm);
				continue;
			}
			byStatus.computeIfAbsent(statusId, ignored -> new ArrayList<>()).add(vm);
		}

		int laneCount = 0;
		int cardCount = 0;
		for (CaseListUiSupport.StatusFilterOption status : statusFilterOptions) {
			if (status == null) {
				continue;
			}
			String statusName = safe(status.label()).isBlank() ? ("Status #" + status.id()) : safe(status.label()).trim();
			List<CaseCardVm> laneCases = byStatus.getOrDefault(status.id(), List.of()).stream()
					.sorted(laneSort)
					.toList();
			if (laneCases.isEmpty()) {
				continue;
			}
			myCasesBoardList.getChildren().add(createMyCasesStatusLane(statusName, laneCases));
			laneCount++;
			cardCount += laneCases.size();
		}
		if (!noStatus.isEmpty()) {
			List<CaseCardVm> sortedNoStatus = noStatus.stream()
					.sorted(laneSort)
					.toList();
			myCasesBoardList.getChildren().add(createMyCasesStatusLane("No Status", sortedNoStatus));
			laneCount++;
			cardCount += sortedNoStatus.size();
		}

		boolean hasAnyCards = cardCount > 0;
		if (!hasAnyCards) {
			myCasesBoardEmptyLabel.setText("No assigned cases found.");
			setVisibleManaged(myCasesBoardEmptyLabel, true);
			setVisibleManaged(myCasesBoardScroll, false);
			System.out.println("[TRACE ASSIGNED_CASES][MyShaleController.renderMyCasesBoard] empty state rendered");
			PerfLog.logDone("RENDER", "panel=my_cases_board page=my_shale state=empty childCount=0", renderStartNanos);
			return;
		}

		setVisibleManaged(myCasesBoardEmptyLabel, false);
		setVisibleManaged(myCasesBoardScroll, true);
		System.out.println("[TRACE ASSIGNED_CASES][MyShaleController.renderMyCasesBoard] board rendered laneCount=" + laneCount
				+ " cardCount=" + cardCount);
		PerfLog.logDone("RENDER", "panel=my_cases_board page=my_shale laneCount=" + laneCount + " childCount=" + cardCount, renderStartNanos);
	}

	private void syncMyCasesBoardStatusFilterOptions() {
		if (myCasesBoardStatusFilterChoice == null) {
			return;
		}
		BoardStatusFilterOption previouslySelected = myCasesBoardStatusFilterChoice.getValue();
		Integer previousStatusId = previouslySelected == null ? null : previouslySelected.statusId();
		List<BoardStatusFilterOption> options = new ArrayList<>();
		options.add(ALL_BOARD_STATUSES_OPTION);
		for (CaseListUiSupport.StatusFilterOption status : statusFilterOptions) {
			if (status == null) {
				continue;
			}
			String label = safe(status.label()).isBlank() ? ("Status #" + status.id()) : safe(status.label()).trim();
			options.add(new BoardStatusFilterOption(status.id(), label));
		}
		myCasesBoardStatusFilterChoice.getItems().setAll(options);
		if (previousStatusId == null) {
			myCasesBoardStatusFilterChoice.getSelectionModel().select(ALL_BOARD_STATUSES_OPTION);
			return;
		}
		Optional<BoardStatusFilterOption> matching = options.stream()
				.filter(option -> Objects.equals(option.statusId(), previousStatusId))
				.findFirst();
		myCasesBoardStatusFilterChoice.getSelectionModel().select(matching.orElse(ALL_BOARD_STATUSES_OPTION));
	}

	private Integer selectedMyCasesBoardStatusId() {
		if (myCasesBoardStatusFilterChoice == null) {
			return null;
		}
		BoardStatusFilterOption selected = myCasesBoardStatusFilterChoice.getValue();
		return selected == null ? null : selected.statusId();
	}

	private VBox createMyCasesStatusLane(String statusName, List<CaseCardVm> laneCases) {
		int caseCount = laneCases == null ? 0 : laneCases.size();
		HBox header = new HBox(8);
		header.setAlignment(Pos.CENTER_LEFT);
		header.getStyleClass().add("lane-header-top-row");
		Label titleLabel = new Label(statusName);
		titleLabel.getStyleClass().add("my-cases-lane-title");
		Label countLabel = new Label("(" + caseCount + ")");
		countLabel.getStyleClass().add("my-cases-lane-count");
		header.getChildren().addAll(titleLabel, countLabel);

		VBox body = new VBox(10);
		body.setFillWidth(true);
		for (CaseCardVm vm : laneCases) {
			body.getChildren().add(buildMyCasesBoardCard(vm));
		}
		return LaneBoardLayout.createLane(
				header,
				body,
				new LaneBoardLayout.LaneWidth(
						MY_CASES_STATUS_COLUMN_MIN_WIDTH,
						MY_CASES_STATUS_COLUMN_PREF_WIDTH,
						MY_CASES_STATUS_COLUMN_MAX_WIDTH));
	}

	private Node buildMyCasesBoardCard(CaseCardVm vm) {
		Node card = buildCaseCard(vm);
		if (card instanceof Region region) {
			region.setMaxWidth(Double.MAX_VALUE);
		}
		return card;
	}

	private Comparator<CaseCardVm> myCasesLaneComparator(String sortOption) {
		if (SORT_INTAKE.equals(sortOption)) {
			return Comparator.comparing((CaseCardVm vm) -> vm.intakeDate, Comparator.nullsLast(Comparator.reverseOrder()))
					.thenComparing(vm -> normalizeCaseName(vm.name), Comparator.nullsLast(String::compareToIgnoreCase))
					.thenComparingLong(vm -> vm.id);
		}
		if (SORT_SOL.equals(sortOption)) {
			return Comparator.comparing((CaseCardVm vm) -> vm.solDate, Comparator.nullsLast(Comparator.naturalOrder()))
					.thenComparing(vm -> normalizeCaseName(vm.name), Comparator.nullsLast(String::compareToIgnoreCase))
					.thenComparingLong(vm -> vm.id);
		}
		return Comparator.comparing((CaseCardVm vm) -> normalizeCaseName(vm.name), Comparator.nullsLast(String::compareToIgnoreCase))
				.thenComparingLong(vm -> vm.id);
	}

	private boolean matchesMyCasesBoardSearch(CaseCardVm vm, String query) {
		if (vm == null) {
			return false;
		}
		if (query == null || query.isBlank()) {
			return true;
		}
		String normalized = query.toLowerCase(Locale.ROOT);
		return safe(vm.name).toLowerCase(Locale.ROOT).contains(normalized)
				|| String.valueOf(vm.id).contains(normalized);
	}

	private void renderMyTasks() {
		if (myTasksList == null || myTasksEmptyLabel == null || myTasksScroll == null) {
			return;
		}
		updateMyTasksViewToggleStyles();
		if (loadingMyTasks) {
			myTasksList.getChildren().clear();
			FlowPane grid = myTasksGrid;
			if (grid != null) {
				grid.getChildren().clear();
			}
			myTasksEmptyLabel.setText("Loading your tasks...");
			setVisibleManaged(myTasksEmptyLabel, true);
			setVisibleManaged(myTasksScroll, false);
			suppressMyTasksScrollTopRightCornerOverlay();
			return;
		}
		long renderStartNanos = PerfLog.start();
		PerfLog.log("RENDER", "start", "panel=my_tasks page=my_shale userId=" + (appState == null ? null : appState.getUserId()));
		myTasksList.getChildren().clear();

		String searchQuery = normalizeSearchQuery(myTasksSearchField == null ? null : myTasksSearchField.getText());
		List<CaseTaskListItemDto> taskFiltered = filterAndRankMyTasks(myTasks, selectedPriorityFilterId(), searchQuery);
		List<CaseTaskListItemDto> filteredTasks = applyCaseColumnFilter(taskFiltered, selectedCaseFilterId());
		if (myTasks == null || myTasks.isEmpty()) {
			setVisibleManaged(myTasksEmptyLabel, true);
			setVisibleManaged(myTasksScroll, false);
			myTasksEmptyLabel.setText(myTasksSource == MyTasksSource.CREATED_BY_ME
					? "No tasks created by you found."
					: "No assigned tasks found.");
			suppressMyTasksScrollTopRightCornerOverlay();
			PerfLog.logDone("RENDER", "panel=my_tasks page=my_shale userId=" + (appState == null ? null : appState.getUserId()) + " childCount=0", renderStartNanos);
			return;
		}
		if (filteredTasks.isEmpty()) {
			setVisibleManaged(myTasksEmptyLabel, true);
			setVisibleManaged(myTasksScroll, false);
			myTasksEmptyLabel.setText("No tasks found.");
			suppressMyTasksScrollTopRightCornerOverlay();
			PerfLog.logDone("RENDER", "panel=my_tasks page=my_shale userId=" + (appState == null ? null : appState.getUserId()) + " childCount=0", renderStartNanos);
			return;
		}
		if (myTasksViewMode == MyTasksViewMode.BOARD) {
			renderMyTasksBoard(filteredTasks);
		} else {
			renderMyTasksGrid(filteredTasks);
		}
		setVisibleManaged(myTasksEmptyLabel, false);
		setVisibleManaged(myTasksScroll, true);
		int childCount = myTasksViewMode == MyTasksViewMode.BOARD ? myTasksList.getChildren().size() : ensureMyTasksGrid().getChildren().size();
		PerfLog.logDone("RENDER", "panel=my_tasks page=my_shale userId=" + (appState == null ? null : appState.getUserId()) + " childCount=" + childCount,
				renderStartNanos);
	}

	private void renderMyTasksBoard(List<CaseTaskListItemDto> filteredTasks) {
		LaneBoardLayout.configureBoardRow(myTasksList);
		myTasksScroll.setFitToHeight(true);
		myTasksScroll.setFitToWidth(false);
		myTasksScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		myTasksScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		if (myTasksScroll.getContent() != myTasksList) {
			myTasksScroll.setContent(myTasksList);
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
			if (isCollapsedLane(entry.getKey())) {
				if (lane.getChildren().size() > 1) {
					Node laneBodyScroll = lane.getChildren().get(1);
					laneBodyScroll.setVisible(false);
					laneBodyScroll.setManaged(false);
				}
				lane.setMinHeight(Region.USE_PREF_SIZE);
			}
			myTasksList.getChildren().add(lane);
		}
	}

	private void renderMyTasksGrid(List<CaseTaskListItemDto> filteredTasks) {
		FlowPane grid = ensureMyTasksGrid();
		grid.getChildren().clear();
		myTasksScroll.setFitToHeight(false);
		myTasksScroll.setFitToWidth(true);
		myTasksScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		myTasksScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		if (myTasksScroll.getContent() != grid) {
			myTasksScroll.setContent(grid);
		}
		for (CaseTaskListItemDto task : filteredTasks) {
			TaskCardFactory.TaskCardModel model = new TaskCardFactory.TaskCardModel(
					task.id(),
					task.caseId(),
					task.caseName(),
					task.casePrimaryStatusName(),
					task.casePrimaryStatusColor(),
					task.casePracticeAreaColor(),
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
			var taskCard = taskCardFactory.create(model, TaskCardFactory.Variant.MY_TASKS, true);
			taskCard.getStyleClass().add("my-tasks-grid-card");
			taskCard.setMinWidth(TASKS_CASE_COLUMN_PREF_WIDTH);
			taskCard.setPrefWidth(TASKS_CASE_COLUMN_PREF_WIDTH);
			taskCard.setMaxWidth(TASKS_CASE_COLUMN_PREF_WIDTH);
			grid.getChildren().add(taskCard);
		}
	}

	private FlowPane ensureMyTasksGrid() {
		if (myTasksGrid == null) {
			myTasksGrid = new FlowPane();
			myTasksGrid.setHgap(MY_TASKS_GRID_HGAP);
			myTasksGrid.setVgap(MY_TASKS_GRID_VGAP);
			myTasksGrid.setPrefWrapLength(900);
			myTasksGrid.getStyleClass().addAll("cases-content-surface", "glass-panel", "my-tasks-grid-container");
			myTasksGrid.setMaxWidth(Double.MAX_VALUE);
			myTasksGrid.prefWrapLengthProperty().bind(Bindings.createDoubleBinding(
					() -> Math.max(320, myTasksScroll.getViewportBounds().getWidth() - 20),
					myTasksScroll.viewportBoundsProperty()));
		}
		return myTasksGrid;
	}

	private void setMyTasksViewMode(MyTasksViewMode viewMode) {
		if (viewMode == null || viewMode == myTasksViewMode) {
			return;
		}
		myTasksViewMode = viewMode;
		renderMyTasks();
	}

	private void updateMyTasksViewToggleStyles() {
		updateViewToggleButtonStyles(myTasksBoardViewButton, myTasksViewMode == MyTasksViewMode.BOARD);
		updateViewToggleButtonStyles(myTasksGridViewButton, myTasksViewMode == MyTasksViewMode.GRID);
	}

	private void updateViewToggleButtonStyles(Button button, boolean selected) {
		if (button == null) {
			return;
		}
		button.getStyleClass().removeAll("my-tasks-view-toggle-selected", "my-tasks-view-toggle-unselected");
		button.getStyleClass().add(selected ? "my-tasks-view-toggle-selected" : "my-tasks-view-toggle-unselected");
	}

	private void renderMyOverview() {
		if (overviewMainRow == null) {
			return;
		}
		long renderStartNanos = PerfLog.start();
		PerfLog.log("RENDER", "start", "panel=overview page=my_shale userId=" + (appState == null ? null : appState.getUserId()));
		if (loadingOverview) {
			Label loadingLabel = new Label("Loading your overview...");
			loadingLabel.getStyleClass().add("muted-text");
			overviewMainRow.getChildren().setAll(loadingLabel);
			PerfLog.logDone("RENDER", "panel=overview page=my_shale state=loading childCount=1", renderStartNanos);
			return;
		}
		ensureOverviewContentShell();
		List<CaseTaskListItemDto> overviewSource = overviewEligibleTasks(myTasks);
		syncOverviewControlOptions(overviewSource);
		renderOverviewSections(overviewSource);
		PerfLog.logDone("RENDER", "panel=overview page=my_shale sourceRows=" + (overviewSource == null ? 0 : overviewSource.size()) + " childCount=" + overviewMainRow.getChildren().size(), renderStartNanos);
	}

	private void ensureOverviewContentShell() {
		if (overviewSectionsContainer != null
				&& overviewWidgetsContainer != null
				&& overviewSearchFieldControl != null) {
			return;
		}
		HBox dashboard = new HBox(12);
		dashboard.getStyleClass().add("my-shale-overview-dashboard");
		dashboard.setAlignment(Pos.TOP_LEFT);
		dashboard.setMaxWidth(Double.MAX_VALUE);

		VBox sections = new VBox(10);
		sections.getStyleClass().add("my-shale-overview-primary-column");
		sections.setFillWidth(true);
		sections.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(sections, Priority.ALWAYS);

		VBox widgets = new VBox(10);
		widgets.getStyleClass().add("my-shale-overview-briefing-column");
		widgets.setFillWidth(true);
		widgets.setMinWidth(300);
		widgets.setPrefWidth(360);
		widgets.setMaxWidth(430);

		sections.prefWidthProperty().bind(dashboard.widthProperty().multiply(0.67));
		widgets.prefWidthProperty().bind(dashboard.widthProperty().multiply(0.33));

		sections.getChildren().add(buildOverviewControlBar());
		widgets.getChildren().setAll(buildOverviewDashboardWidgets());
		overviewSectionsContainer = sections;
		overviewWidgetsContainer = widgets;
		dashboard.getChildren().setAll(sections, widgets);
		overviewMainRow.getChildren().setAll(dashboard);
	}

	private void renderOverviewSections(List<CaseTaskListItemDto> overviewSource) {
		if (overviewSectionsContainer == null) {
			return;
		}
		LocalDate today = LocalDate.now();
		List<CaseTaskListItemDto> filteredOverviewTasks = applyOverviewFilters(overviewSource, today);
		Map<String, List<CaseTaskListItemDto>> buckets = bucketOverviewTasksByDueWindow(filteredOverviewTasks, today);
		List<CaseTaskListItemDto> todayTasks = sortOverviewTasks(buckets.getOrDefault("today", List.of()));
		List<CaseTaskListItemDto> upcomingTasks = sortOverviewTasks(buckets.getOrDefault("upcoming", List.of()));
		List<CaseTaskListItemDto> laterTasks = sortOverviewTasks(buckets.getOrDefault("later", List.of()));

		List<Node> sectionNodes = new ArrayList<>();
		if (overviewSectionsContainer.getChildren().isEmpty()) {
			sectionNodes.add(buildOverviewControlBar());
		} else {
			sectionNodes.add(overviewSectionsContainer.getChildren().get(0));
		}
		sectionNodes.add(buildOverviewTaskSection(
				"Today’s Tasks",
				todayTasks,
				"Nothing due today",
				true));
		sectionNodes.add(buildOverviewTaskSection(
				"Upcoming",
				upcomingTasks,
				"No tasks due in the next 7 days",
				false));
		sectionNodes.add(buildOverviewTaskSection(
				"Later",
				laterTasks,
				"No tasks due later this month",
				false));
		overviewSectionsContainer.getChildren().setAll(sectionNodes);
		if (overviewWidgetsContainer != null) {
			overviewWidgetsContainer.getChildren().setAll(buildOverviewDashboardWidgets());
		}
	}

	private List<Node> buildOverviewDashboardWidgets() {
		return List.of(
				buildCaseRadarWidget(),
				DashboardWidgetFactory.placeholder("Important Dates", "No upcoming important dates."),
				DashboardWidgetFactory.placeholder("Notifications", "You’re all caught up."),
				DashboardWidgetFactory.placeholder("Recent Case Activity", "No recent case activity."),
				buildMyCaseSummaryWidget());
	}

	private void renderOverviewWidgets() {
		if (overviewWidgetsContainer == null) {
			return;
		}
		overviewWidgetsContainer.getChildren().setAll(buildOverviewDashboardWidgets());
	}

	private Node buildCaseRadarWidget() {
		if (loadingMyTasks || loadingMyCases) {
			return DashboardWidgetFactory.widget("Case Radar", null, null, null, true, false);
		}
		if (myCasesLoadFailed) {
			return DashboardWidgetFactory.widget(
					"Case Radar",
					null,
					null,
					DashboardWidgetFactory.errorState("Unable to load case radar."),
					false,
					false);
		}
		List<CaseRadarRow> rows = buildCaseRadarRows(LocalDate.now());
		if (rows.isEmpty()) {
			return DashboardWidgetFactory.widget(
					"Case Radar",
					null,
					null,
					DashboardWidgetFactory.emptyState("No urgent items."),
					false,
					true);
		}
		VBox content = new VBox(6);
		content.getStyleClass().add("case-radar-list");
		content.setFillWidth(true);
		for (CaseRadarRow row : rows) {
			content.getChildren().add(buildCaseRadarRow(row));
		}
		long attentionCount = rows.stream()
				.filter(row -> row.severity() == CaseRadarSeverity.CRITICAL || row.severity() == CaseRadarSeverity.WARNING)
				.mapToLong(CaseRadarRow::count)
				.sum();
		return DashboardWidgetFactory.widget(
				"Case Radar",
				attentionCount > 0 ? String.valueOf(attentionCount) : null,
				null,
				content,
				false,
				false);
	}

	private List<CaseRadarRow> buildCaseRadarRows(LocalDate today) {
		LocalDate effectiveToday = today == null ? LocalDate.now() : today;
		LocalDate soon = effectiveToday.plusDays(14);
		LocalDate month = effectiveToday.plusDays(30);
		long overdueTasks = overviewEligibleTasks(myTasks).stream()
				.filter(task -> task != null && !task.deleted() && task.completedAt() == null)
				.filter(task -> task.dueAt() != null && task.dueAt().toLocalDate().isBefore(effectiveToday))
				.count();

		List<CaseCardVm> activeCases = activeAssignedCaseRadarSource();
		long solCritical = countCasesInDateWindow(activeCases, effectiveToday, soon, caseVm -> caseVm.solDate);
		long solWarning = countCasesInDateWindow(activeCases, soon.plusDays(1), month, caseVm -> caseVm.solDate);
		long tortCritical = countCasesInDateWindow(activeCases, effectiveToday, soon, caseVm -> caseVm.tortNoticeDate);
		long tortWarning = countCasesInDateWindow(activeCases, soon.plusDays(1), month, caseVm -> caseVm.tortNoticeDate);

		List<CaseRadarRow> rows = new ArrayList<>();
		if (overdueTasks > 0) {
			rows.add(new CaseRadarRow(CaseRadarSeverity.CRITICAL, "Overdue tasks", overdueTasks, "Assigned to you and past due."));
		}
		if (solCritical > 0) {
			rows.add(new CaseRadarRow(CaseRadarSeverity.CRITICAL, "SOL due ≤ 14 days", solCritical, "Assigned active cases."));
		}
		if (tortCritical > 0) {
			rows.add(new CaseRadarRow(CaseRadarSeverity.CRITICAL, "Tort notice due ≤ 14 days", tortCritical, "Assigned active cases."));
		}
		if (solWarning > 0) {
			rows.add(new CaseRadarRow(CaseRadarSeverity.WARNING, "SOL due in 15–30 days", solWarning, "Assigned active cases."));
		}
		if (tortWarning > 0) {
			rows.add(new CaseRadarRow(CaseRadarSeverity.WARNING, "Tort notice due in 15–30 days", tortWarning, "Assigned active cases."));
		}
		// TODO: Add inactive/recently-updated radar rows once a reliable activity/UpdatedAt field is present in this loaded overview model.
		return rows;
	}

	private List<CaseCardVm> activeAssignedCaseRadarSource() {
		if (myAssignedCasesBoard == null || myAssignedCasesBoard.isEmpty()) {
			return List.of();
		}
		Set<Integer> terminalStatusIds = statusFilterOptions.stream()
				.filter(Objects::nonNull)
				.filter(CaseListUiSupport.StatusFilterOption::terminal)
				.map(CaseListUiSupport.StatusFilterOption::id)
				.collect(java.util.stream.Collectors.toSet());
		return myAssignedCasesBoard.stream()
				.filter(Objects::nonNull)
				.filter(caseVm -> caseVm.id > 0)
				.filter(caseVm -> caseVm.primaryStatusId == null || !terminalStatusIds.contains(caseVm.primaryStatusId))
				.toList();
	}

	private long countCasesInDateWindow(List<CaseCardVm> cases, LocalDate start, LocalDate end, java.util.function.Function<CaseCardVm, LocalDate> dateExtractor) {
		if (cases == null || cases.isEmpty() || start == null || end == null || dateExtractor == null) {
			return 0;
		}
		return cases.stream()
				.map(dateExtractor)
				.filter(Objects::nonNull)
				.filter(date -> !date.isBefore(start) && !date.isAfter(end))
				.count();
	}

	private Node buildCaseRadarRow(CaseRadarRow row) {
		HBox radarRow = new HBox(8);
		radarRow.getStyleClass().addAll("case-radar-row", "case-radar-row-" + row.severity().styleSuffix());
		radarRow.setAlignment(Pos.CENTER_LEFT);
		radarRow.setMaxWidth(Double.MAX_VALUE);
		radarRow.setOnMouseClicked(event -> onCaseRadarRowClicked(row));

		Region indicator = new Region();
		indicator.getStyleClass().addAll("case-radar-severity-dot", "case-radar-severity-" + row.severity().styleSuffix());

		VBox text = new VBox(1);
		text.setFillWidth(true);
		Label label = new Label(row.label());
		label.getStyleClass().add("case-radar-label");
		text.getChildren().add(label);
		if (!safe(row.helperText()).isBlank()) {
			Label helper = new Label(row.helperText());
			helper.getStyleClass().add("case-radar-helper");
			helper.setWrapText(true);
			text.getChildren().add(helper);
		}
		HBox.setHgrow(text, Priority.ALWAYS);

		Label count = new Label(String.valueOf(row.count()));
		count.getStyleClass().add("case-radar-count");
		radarRow.getChildren().addAll(indicator, text, count);
		return radarRow;
	}

	private void onCaseRadarRowClicked(CaseRadarRow row) {
		// TODO: Wire to a filtered tasks/cases navigation target when dashboard row navigation exists.
	}

	private Node buildMyCaseSummaryWidget() {
		if (loadingMyCases) {
			return DashboardWidgetFactory.widget("My Case Summary", null, null, null, true, false);
		}
		if (myCasesLoadFailed) {
			return DashboardWidgetFactory.widget(
					"My Case Summary",
					null,
					null,
					DashboardWidgetFactory.errorState("Unable to load case summary."),
					false,
					false);
		}
		List<MyCaseSummaryRow> rows = buildMyCaseSummaryRows();
		if (rows.isEmpty()) {
			return DashboardWidgetFactory.widget(
					"My Case Summary",
					null,
					null,
					DashboardWidgetFactory.emptyState("No case summary available."),
					false,
					false);
		}
		VBox content = new VBox(6);
		content.getStyleClass().add("my-case-summary-list");
		content.setFillWidth(true);
		for (MyCaseSummaryRow row : rows) {
			content.getChildren().add(buildMyCaseSummaryRow(row));
		}
		return DashboardWidgetFactory.widget(
				"My Case Summary",
				String.valueOf(rows.stream().mapToLong(MyCaseSummaryRow::count).sum()),
				null,
				content,
				false,
				false);
	}

	private List<MyCaseSummaryRow> buildMyCaseSummaryRows() {
		if (myAssignedCasesBoard == null || myAssignedCasesBoard.isEmpty()) {
			return List.of();
		}
		Map<Integer, Long> countsByStatusId = myAssignedCasesBoard.stream()
				.filter(Objects::nonNull)
				.map(vm -> vm.primaryStatusId)
				.filter(Objects::nonNull)
				.collect(java.util.stream.Collectors.groupingBy(
						statusId -> statusId,
						LinkedHashMap::new,
						java.util.stream.Collectors.counting()));
		if (countsByStatusId.isEmpty()) {
			return List.of();
		}
		List<MyCaseSummaryRow> rows = new ArrayList<>();
		Set<Integer> knownStatusIds = new LinkedHashSet<>();
		for (CaseListUiSupport.StatusFilterOption status : statusFilterOptions) {
			if (status == null || !countsByStatusId.containsKey(status.id())) {
				continue;
			}
			knownStatusIds.add(status.id());
			CaseCardVm representative = firstCaseWithStatus(status.id());
			rows.add(new MyCaseSummaryRow(
					status.id(),
					safe(status.label()).isBlank() ? ("Status #" + status.id()) : safe(status.label()).trim(),
					representative == null ? "" : representative.primaryStatusColor,
					countsByStatusId.getOrDefault(status.id(), 0L)));
		}
		for (Map.Entry<Integer, Long> entry : countsByStatusId.entrySet()) {
			if (knownStatusIds.contains(entry.getKey())) {
				continue;
			}
			CaseCardVm representative = firstCaseWithStatus(entry.getKey());
			rows.add(new MyCaseSummaryRow(
					entry.getKey(),
					representative == null || safe(representative.primaryStatusName).isBlank()
							? ("Status #" + entry.getKey())
							: safe(representative.primaryStatusName).trim(),
					representative == null ? "" : representative.primaryStatusColor,
					entry.getValue()));
		}
		return rows;
	}

	private CaseCardVm firstCaseWithStatus(Integer statusId) {
		if (statusId == null || myAssignedCasesBoard == null) {
			return null;
		}
		return myAssignedCasesBoard.stream()
				.filter(vm -> vm != null && Objects.equals(statusId, vm.primaryStatusId))
				.findFirst()
				.orElse(null);
	}

	private Node buildMyCaseSummaryRow(MyCaseSummaryRow row) {
		HBox summaryRow = new HBox(8);
		summaryRow.getStyleClass().add("my-case-summary-row");
		summaryRow.setAlignment(Pos.CENTER_LEFT);
		summaryRow.setMaxWidth(Double.MAX_VALUE);
		summaryRow.setOnMouseClicked(event -> onMyCaseSummaryStatusClicked(row));

		Node statusBadge = StatusIndicatorFactory.createStatusBadge(row.statusName(), row.statusColor());
		HBox.setHgrow(statusBadge, Priority.ALWAYS);

		Label count = new Label(String.valueOf(row.count()));
		count.getStyleClass().add("my-case-summary-count");

		summaryRow.getChildren().addAll(statusBadge, count);
		return summaryRow;
	}

	private void onMyCaseSummaryStatusClicked(MyCaseSummaryRow row) {
		// TODO: Wire to the future My Shale status-filtered case navigation route.
	}

	private Node buildOverviewControlBar() {
		HBox controls = new HBox(8);
		controls.setAlignment(Pos.CENTER_LEFT);
		controls.getStyleClass().add("glass-panel");
		controls.setPadding(new javafx.geometry.Insets(8, 10, 8, 10));

		overviewSearchFieldControl = new TextField(safe(overviewSearchText));
		overviewSearchFieldControl.setPromptText("Search title, case, or creator…");
		HBox.setHgrow(overviewSearchFieldControl, Priority.ALWAYS);
		overviewSearchFieldControl.textProperty().addListener((obs, oldV, newV) -> {
			if (suppressOverviewControlEvents) {
				return;
			}
			overviewSearchText = safe(newV);
			renderOverviewSections(overviewEligibleTasks(myTasks));
		});

		overviewPriorityChoiceControl = new ChoiceBox<>();
		overviewPriorityChoiceControl.getStyleClass().add("app-toolbar-select");
		overviewPriorityChoiceControl.setPrefWidth(190);
		overviewPriorityChoiceControl.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
			if (suppressOverviewControlEvents) {
				return;
			}
			overviewPriorityFilterId = newV == null ? null : newV.priorityId();
			renderOverviewSections(overviewEligibleTasks(myTasks));
		});

		overviewCaseChoiceControl = new ChoiceBox<>();
		overviewCaseChoiceControl.getStyleClass().add("app-toolbar-select");
		overviewCaseChoiceControl.setPrefWidth(200);
		overviewCaseChoiceControl.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
			if (suppressOverviewControlEvents) {
				return;
			}
			overviewCaseFilterId = newV == null ? null : newV.caseId();
			renderOverviewSections(overviewEligibleTasks(myTasks));
		});

		overviewOverdueOnlyCheckControl = new CheckBox("Overdue only");
		overviewOverdueOnlyCheckControl.setSelected(overviewOverdueOnly);
		overviewOverdueOnlyCheckControl.selectedProperty().addListener((obs, oldV, newV) -> {
			if (suppressOverviewControlEvents) {
				return;
			}
			overviewOverdueOnly = Boolean.TRUE.equals(newV);
			renderOverviewSections(overviewEligibleTasks(myTasks));
		});

		overviewSortChoiceControl = new ChoiceBox<>();
		overviewSortChoiceControl.getStyleClass().add("app-toolbar-select");
		overviewSortChoiceControl.setPrefWidth(210);
		overviewSortChoiceControl.getItems().setAll(
				OVERVIEW_SORT_DUE_ASC,
				OVERVIEW_SORT_DUE_DESC,
				OVERVIEW_SORT_PRIORITY,
				OVERVIEW_SORT_CASE_NAME,
				OVERVIEW_SORT_TITLE);
		overviewSortChoiceControl.getSelectionModel().select(
				overviewSortChoiceControl.getItems().contains(overviewSortMode) ? overviewSortMode : OVERVIEW_SORT_DUE_ASC);
		overviewSortChoiceControl.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
			if (suppressOverviewControlEvents) {
				return;
			}
			overviewSortMode = safe(newV).isBlank() ? OVERVIEW_SORT_DUE_ASC : newV;
			renderOverviewSections(overviewEligibleTasks(myTasks));
		});

		controls.getChildren().addAll(
				overviewSearchFieldControl,
				overviewPriorityChoiceControl,
				overviewCaseChoiceControl,
				overviewOverdueOnlyCheckControl,
				overviewSortChoiceControl);
		return controls;
	}

	private void syncOverviewControlOptions(List<CaseTaskListItemDto> overviewSource) {
		if (overviewPriorityChoiceControl == null
				|| overviewCaseChoiceControl == null
				|| overviewSearchFieldControl == null
				|| overviewSortChoiceControl == null
				|| overviewOverdueOnlyCheckControl == null) {
			return;
		}
		suppressOverviewControlEvents = true;
		try {
			overviewSearchFieldControl.setText(safe(overviewSearchText));
			overviewOverdueOnlyCheckControl.setSelected(overviewOverdueOnly);
			overviewSortChoiceControl.getSelectionModel().select(
					overviewSortChoiceControl.getItems().contains(overviewSortMode) ? overviewSortMode : OVERVIEW_SORT_DUE_ASC);

			List<PriorityFilterOption> priorityOptions = new ArrayList<>();
			priorityOptions.add(ALL_PRIORITIES_OPTION);
			overviewSource.stream()
					.filter(Objects::nonNull)
					.map(CaseTaskListItemDto::priorityId)
					.filter(Objects::nonNull)
					.distinct()
					.sorted(Comparator.naturalOrder())
					.forEach(priorityId -> priorityOptions.add(new PriorityFilterOption(priorityId, resolvePriorityName(priorityId))));
			overviewPriorityChoiceControl.getItems().setAll(priorityOptions);
			PriorityFilterOption selectedPriority = priorityOptions.stream()
					.filter(option -> Objects.equals(option.priorityId(), overviewPriorityFilterId))
					.findFirst()
					.orElse(ALL_PRIORITIES_OPTION);
			overviewPriorityChoiceControl.getSelectionModel().select(selectedPriority);

			List<CaseFilterOption> caseOptions = new ArrayList<>();
			caseOptions.add(ALL_CASES_OPTION);
			overviewSource.stream()
					.filter(Objects::nonNull)
					.filter(task -> task.caseId() > 0)
					.collect(java.util.stream.Collectors.toMap(
							CaseTaskListItemDto::caseId,
							task -> normalizeOverviewCaseName(task.caseName(), task.caseId()),
							(existing, ignored) -> existing,
							LinkedHashMap::new))
					.entrySet().stream()
					.sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
					.forEach(entry -> caseOptions.add(new CaseFilterOption(entry.getKey(), entry.getValue())));
			overviewCaseChoiceControl.getItems().setAll(caseOptions);
			CaseFilterOption selectedCase = caseOptions.stream()
					.filter(option -> Objects.equals(option.caseId(), overviewCaseFilterId))
					.findFirst()
					.orElse(ALL_CASES_OPTION);
			overviewCaseChoiceControl.getSelectionModel().select(selectedCase);
		} finally {
			suppressOverviewControlEvents = false;
		}
	}

	private List<CaseTaskListItemDto> overviewEligibleTasks(List<CaseTaskListItemDto> tasks) {
		if (tasks == null || tasks.isEmpty()) {
			return List.of();
		}
		return tasks.stream()
				.filter(Objects::nonNull)
				.filter(task -> task.completedAt() == null)
				.filter(task -> task.dueAt() != null)
				.toList();
	}

	private List<CaseTaskListItemDto> applyOverviewFilters(List<CaseTaskListItemDto> tasks, LocalDate today) {
		if (tasks == null || tasks.isEmpty()) {
			return List.of();
		}
		String normalizedQuery = safe(overviewSearchText).trim().toLowerCase(Locale.ROOT);
		return tasks.stream()
				.filter(task -> matchesOverviewSearch(task, normalizedQuery))
				.filter(this::matchesOverviewPriorityFilter)
				.filter(this::matchesOverviewCaseFilter)
				.filter(task -> matchesOverviewOverdueOnly(task, today))
				.toList();
	}

	private boolean matchesOverviewSearch(CaseTaskListItemDto task, String normalizedQuery) {
		if (task == null) {
			return false;
		}
		if (normalizedQuery == null || normalizedQuery.isBlank()) {
			return true;
		}
		return safe(task.title()).toLowerCase(Locale.ROOT).contains(normalizedQuery)
				|| safe(task.caseName()).toLowerCase(Locale.ROOT).contains(normalizedQuery)
				|| safe(task.createdByDisplayName()).toLowerCase(Locale.ROOT).contains(normalizedQuery);
	}

	private boolean matchesOverviewPriorityFilter(CaseTaskListItemDto task) {
		return overviewPriorityFilterId == null || Objects.equals(task.priorityId(), overviewPriorityFilterId);
	}

	private boolean matchesOverviewCaseFilter(CaseTaskListItemDto task) {
		return overviewCaseFilterId == null || Objects.equals(task.caseId(), overviewCaseFilterId);
	}

	private boolean matchesOverviewOverdueOnly(CaseTaskListItemDto task, LocalDate today) {
		if (!overviewOverdueOnly) {
			return true;
		}
		LocalDate dueDate = task == null || task.dueAt() == null ? null : task.dueAt().toLocalDate();
		return dueDate != null && dueDate.isBefore(today);
	}

	private Map<String, List<CaseTaskListItemDto>> bucketOverviewTasksByDueWindow(List<CaseTaskListItemDto> tasks, LocalDate today) {
		List<CaseTaskListItemDto> todayTasks = new ArrayList<>();
		List<CaseTaskListItemDto> upcomingTasks = new ArrayList<>();
		List<CaseTaskListItemDto> laterTasks = new ArrayList<>();
		if (tasks != null) {
			for (CaseTaskListItemDto task : tasks) {
				if (task == null || task.dueAt() == null) {
					continue;
				}
				if (isTaskInTodayBucket(task, today)) {
					todayTasks.add(task);
				} else if (isTaskInUpcomingBucket(task, today)) {
					upcomingTasks.add(task);
				} else if (isTaskInLaterBucket(task, today)) {
					laterTasks.add(task);
				}
			}
		}
		Map<String, List<CaseTaskListItemDto>> buckets = new LinkedHashMap<>();
		buckets.put("today", todayTasks);
		buckets.put("upcoming", upcomingTasks);
		buckets.put("later", laterTasks);
		return buckets;
	}

	private List<CaseTaskListItemDto> sortOverviewTasks(List<CaseTaskListItemDto> tasks) {
		if (tasks == null || tasks.isEmpty()) {
			return List.of();
		}
		Comparator<CaseTaskListItemDto> dueAscThenTitle = Comparator
				.comparing(CaseTaskListItemDto::dueAt)
				.thenComparing(task -> safe(resolveMyTaskCardTitle(task)), String.CASE_INSENSITIVE_ORDER);
		Comparator<CaseTaskListItemDto> comparator = switch (safe(overviewSortMode)) {
			case OVERVIEW_SORT_DUE_DESC -> Comparator
					.comparing(CaseTaskListItemDto::dueAt, Comparator.reverseOrder())
					.thenComparing(task -> safe(resolveMyTaskCardTitle(task)), String.CASE_INSENSITIVE_ORDER);
			case OVERVIEW_SORT_PRIORITY -> Comparator
					.comparing((CaseTaskListItemDto task) -> resolvePriorityName(task.priorityId()), String.CASE_INSENSITIVE_ORDER)
					.thenComparing(CaseTaskListItemDto::dueAt)
					.thenComparing(task -> safe(resolveMyTaskCardTitle(task)), String.CASE_INSENSITIVE_ORDER);
			case OVERVIEW_SORT_CASE_NAME -> Comparator
					.comparing((CaseTaskListItemDto task) -> normalizeOverviewCaseName(task.caseName(), task.caseId()), String.CASE_INSENSITIVE_ORDER)
					.thenComparing(CaseTaskListItemDto::dueAt)
					.thenComparing(task -> safe(resolveMyTaskCardTitle(task)), String.CASE_INSENSITIVE_ORDER);
			case OVERVIEW_SORT_TITLE -> Comparator
					.comparing((CaseTaskListItemDto task) -> safe(resolveMyTaskCardTitle(task)), String.CASE_INSENSITIVE_ORDER)
					.thenComparing(CaseTaskListItemDto::dueAt);
			default -> dueAscThenTitle;
		};
		return tasks.stream()
				.sorted(comparator)
				.toList();
	}

	private String resolvePriorityName(Integer priorityId) {
		if (priorityId == null) {
			return "zzzzzz";
		}
		String name = myTaskPrioritiesById.get(priorityId);
		if (safe(name).isBlank()) {
			return "Priority #" + priorityId;
		}
		return safe(name).trim();
	}

	private String normalizeOverviewCaseName(String caseName, long caseId) {
		String normalized = safe(caseName).trim();
		return normalized.isBlank() ? ("Case #" + caseId) : normalized;
	}

	private boolean isTaskInTodayBucket(CaseTaskListItemDto task, LocalDate today) {
		LocalDate dueDate = task == null || task.dueAt() == null ? null : task.dueAt().toLocalDate();
		return dueDate != null && (dueDate.isBefore(today) || dueDate.isEqual(today));
	}

	private boolean isTaskInUpcomingBucket(CaseTaskListItemDto task, LocalDate today) {
		LocalDate dueDate = task == null || task.dueAt() == null ? null : task.dueAt().toLocalDate();
		if (dueDate == null) {
			return false;
		}
		LocalDate start = today.plusDays(1);
		LocalDate end = today.plusDays(7);
		return !dueDate.isBefore(start) && !dueDate.isAfter(end);
	}

	private boolean isTaskInLaterBucket(CaseTaskListItemDto task, LocalDate today) {
		LocalDate dueDate = task == null || task.dueAt() == null ? null : task.dueAt().toLocalDate();
		if (dueDate == null) {
			return false;
		}
		LocalDate start = today.plusDays(8);
		LocalDate end = today.plusDays(30);
		return !dueDate.isBefore(start) && !dueDate.isAfter(end);
	}

	private Node buildOverviewTaskSection(String title, List<CaseTaskListItemDto> tasks, String emptyState, boolean prominent) {
		VBox section = new VBox(8);
		section.setFillWidth(true);
		section.getStyleClass().add(prominent ? "strong-panel" : "glass-panel");
		section.setPadding(new javafx.geometry.Insets(10));

		Label header = new Label(title + " (" + (tasks == null ? 0 : tasks.size()) + ")");
		header.getStyleClass().add(prominent ? "page-heading" : "sidebar-header");
		section.getChildren().add(header);

		FlowPane taskCards = new FlowPane();
		taskCards.setHgap(OVERVIEW_CARD_GAP);
		taskCards.setVgap(OVERVIEW_CARD_GAP);
		taskCards.setPrefWrapLength(700);
		taskCards.setMaxWidth(Double.MAX_VALUE);
		taskCards.prefWrapLengthProperty().bind(section.widthProperty()
				.subtract((OVERVIEW_SECTION_HORIZONTAL_PADDING * 2) + 2));
		if (tasks == null || tasks.isEmpty()) {
			Label emptyLabel = new Label(emptyState);
			emptyLabel.getStyleClass().add("lane-empty-state");
			taskCards.getChildren().add(emptyLabel);
		} else {
			for (CaseTaskListItemDto task : tasks) {
				TaskCardFactory.TaskCardModel model = new TaskCardFactory.TaskCardModel(
						task.id(),
						task.caseId(),
						task.caseName(),
						task.casePrimaryStatusName(),
						task.casePrimaryStatusColor(),
						task.casePracticeAreaColor(),
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
				Node card = taskCardFactory.create(model, TaskCardFactory.Variant.COMPACT);
				if (card instanceof Region regionCard) {
					regionCard.setMinWidth(OVERVIEW_COMPACT_TASK_CARD_WIDTH);
					regionCard.setPrefWidth(OVERVIEW_COMPACT_TASK_CARD_WIDTH);
					regionCard.setMaxWidth(OVERVIEW_COMPACT_TASK_CARD_WIDTH);
				}
				taskCards.getChildren().add(card);
			}
		}
		section.getChildren().add(taskCards);
		return section;
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
		boolean laneCollapsed = isCollapsedLane(key);
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
			Button collapseButton = new Button(laneCollapsed ? "▸" : "▾");
			collapseButton.setFocusTraversable(false);
			collapseButton.getStyleClass().add("lane-collapse-button");
			collapseButton.setTooltip(new Tooltip(laneCollapsed ? "Expand lane" : "Collapse lane"));
			collapseButton.setOnAction(event -> {
				boolean collapsedNow = toggleLaneCollapsed(key);
				persistLaneCollapseState(key, collapsedNow);
				renderMyTasks();
			});
			headerTopRow.getChildren().add(collapseButton);

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

	private boolean isCollapsedLane(TaskLaneKey key) {
		return key != null
				&& key.caseId() != null
				&& key.caseId() > 0
				&& collapsedTaskLaneCaseIds.contains(key.caseId());
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

	private boolean toggleLaneCollapsed(TaskLaneKey key) {
		if (key == null || key.caseId() == null || key.caseId() <= 0) {
			return false;
		}
		Long laneId = key.caseId();
		if (collapsedTaskLaneCaseIds.add(laneId)) {
			return true;
		}
		if (collapsedTaskLaneCaseIds.contains(laneId)) {
			collapsedTaskLaneCaseIds.remove(laneId);
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

	private Set<Long> loadCollapsedTaskLaneCaseIds(int shaleClientId, int userId) {
		if (userBoardLanePreferencesDao == null || shaleClientId <= 0 || userId <= 0) {
			return Set.of();
		}
		Set<String> laneKeys = userBoardLanePreferencesDao.listCollapsedLaneKeys(
				shaleClientId,
				userId,
				MY_TASKS_BOARD_KEY,
				MY_TASKS_LANE_TYPE_CASE);
		if (laneKeys.isEmpty()) {
			return Set.of();
		}
		Set<Long> collapsedLaneIds = new LinkedHashSet<>();
		for (String laneKey : laneKeys) {
			if (laneKey == null || laneKey.isBlank()) {
				continue;
			}
			try {
				long laneId = Long.parseLong(laneKey.trim());
				if (laneId > 0) {
					collapsedLaneIds.add(laneId);
				}
			} catch (NumberFormatException ignored) {
			}
		}
		return collapsedLaneIds;
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
		final boolean collapsed = isCollapsedLane(key);
		prefsDbExec.submit(() -> userBoardLanePreferencesDao.upsertLanePreference(
				shaleClientIdValue,
				userIdValue,
				MY_TASKS_BOARD_KEY,
				MY_TASKS_LANE_TYPE_CASE,
				laneKey,
				isPinned,
				null,
				collapsed,
				userIdValue));
	}

	private void persistLaneCollapseState(TaskLaneKey key, boolean isCollapsed) {
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
		final boolean pinned = isPinnedLane(key);
		prefsDbExec.submit(() -> userBoardLanePreferencesDao.upsertLanePreference(
				shaleClientIdValue,
				userIdValue,
				MY_TASKS_BOARD_KEY,
				MY_TASKS_LANE_TYPE_CASE,
				laneKey,
				pinned,
				null,
				isCollapsed,
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
					task.casePrimaryStatusName(),
					task.casePrimaryStatusColor(),
					task.casePracticeAreaColor(),
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
				taskCards.getChildren().add(taskCardFactory.create(model, TaskCardFactory.Variant.MY_TASKS, true));
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
				runOnFx(() -> {
					myTasksDirty = true;
					refreshMyTasks(true);
				});
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
		long clickReceivedAt = PerfLog.start();
		PerfLog.log("TASK_DETAIL_TIMING", "click_received", "context=MY_TASKS taskId=" + taskId);
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
			PerfLog.log("TASK_DETAIL_TIMING", "open_skipped_in_flight", "context=MY_TASKS taskId=" + taskId);
			return;
		}
		Optional<CaseTaskListItemDto> summary = findMyTaskById(taskId);
		final AtomicBoolean dialogMutatedAssignments = new AtomicBoolean(false);
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
		PerfLog.logElapsed("TASK_DETAIL_TIMING", "shell_stage_created", "context=MY_TASKS taskId=" + taskId, PerfLog.elapsedMs(clickReceivedAt));
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
							dialogMutatedAssignments.set(true);
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
							dialogMutatedAssignments.set(true);
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
				if (dialogMutatedAssignments.get()) {
					myTasksDirty = true;
					refreshMyTasks(true);
				}
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
				runOnFx(() -> {
					myTasksDirty = true;
					refreshMyTasks(true);
				});
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
				runOnFx(() -> {
					myTasksDirty = true;
					refreshMyTasks(true);
				});
			} catch (Exception ex) {
				runOnFx(() -> showTaskActionError("Failed to delete task. " + rootCauseMessage(ex)));
			}
		}, "my-shale-task-delete-" + taskId).start();
	}

	private void showTaskActionError(String message) {
		AppDialogs.showError(taskDialogOwner(), "Tasks", message);
	}

	private void suppressMyTasksScrollTopRightCornerOverlay() {
		// no-op: retained to keep existing my-tasks rendering flow stable
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

	private enum CaseRadarSeverity {
		CRITICAL("critical"),
		WARNING("warning"),
		POSITIVE("positive"),
		NEUTRAL("neutral");

		private final String styleSuffix;

		CaseRadarSeverity(String styleSuffix) {
			this.styleSuffix = styleSuffix;
		}

		String styleSuffix() {
			return styleSuffix;
		}
	}

	private record CaseRadarRow(CaseRadarSeverity severity, String label, long count, String helperText) {
	}

	private record MyCaseSummaryRow(Integer statusId, String statusName, String statusColor, long count) {
	}

	private record BoardStatusFilterOption(Integer statusId, String displayName) {
		@Override
		public String toString() {
			String text = safe(displayName).trim();
			return text.isBlank() ? "All Statuses" : text;
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
		final LocalDate tortNoticeDate;
		final Integer primaryStatusId;
		final String responsibleAttorney;
		final String responsibleAttorneyColor;
		final Boolean nonEngagementLetterSent;
		final String primaryStatusName;
		final String primaryStatusColor;
		final String practiceAreaColor;

		CaseCardVm(long id, String name, LocalDate intakeDate, LocalDate solDate, LocalDate tortNoticeDate, Integer primaryStatusId,
				String responsibleAttorney, String responsibleAttorneyColor, Boolean nonEngagementLetterSent,
				String primaryStatusName, String primaryStatusColor, String practiceAreaColor) {
			this.id = id;
			this.name = Objects.requireNonNullElse(name, "");
			this.intakeDate = intakeDate;
			this.solDate = solDate;
			this.tortNoticeDate = tortNoticeDate;
			this.primaryStatusId = primaryStatusId;
			this.responsibleAttorney = Objects.requireNonNullElse(responsibleAttorney, "");
			this.responsibleAttorneyColor = Objects.requireNonNullElse(responsibleAttorneyColor, "");
			this.nonEngagementLetterSent = nonEngagementLetterSent;
			this.primaryStatusName = Objects.requireNonNullElse(primaryStatusName, "");
			this.primaryStatusColor = Objects.requireNonNullElse(primaryStatusColor, "");
			this.practiceAreaColor = Objects.requireNonNullElse(practiceAreaColor, "");
		}

		boolean sameContent(CaseCardVm other) {
			if (other == null) {
				return false;
			}
			return id == other.id
					&& Objects.equals(name, other.name)
					&& Objects.equals(intakeDate, other.intakeDate)
					&& Objects.equals(solDate, other.solDate)
					&& Objects.equals(tortNoticeDate, other.tortNoticeDate)
					&& Objects.equals(primaryStatusId, other.primaryStatusId)
					&& Objects.equals(responsibleAttorney, other.responsibleAttorney)
					&& Objects.equals(responsibleAttorneyColor, other.responsibleAttorneyColor)
					&& Objects.equals(nonEngagementLetterSent, other.nonEngagementLetterSent)
					&& Objects.equals(primaryStatusName, other.primaryStatusName)
					&& Objects.equals(primaryStatusColor, other.primaryStatusColor)
					&& Objects.equals(practiceAreaColor, other.practiceAreaColor);
		}
	}
}
