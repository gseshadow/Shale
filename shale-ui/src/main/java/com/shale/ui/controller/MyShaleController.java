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
	private static final double MY_CASES_STATUS_COLUMN_MIN_WIDTH = 245;
	private static final double MY_CASES_STATUS_COLUMN_PREF_WIDTH = 280;
	private static final double MY_CASES_STATUS_COLUMN_MAX_WIDTH = 320;
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
	private StackPane sectionContentStack;
	@FXML
	private ScrollPane myCasesBoardScroll;
	@FXML
	private HBox myCasesBoardList;
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

	private final List<CaseCardVm> loaded = new ArrayList<>();
	private List<CaseTaskListItemDto> myTasks = List.of();
	private java.util.Map<Long, List<TaskCardFactory.AssignedUserModel>> myTaskAssignedUsers = java.util.Map.of();
	private java.util.Map<Integer, String> myTaskPrioritiesById = java.util.Map.of();
	private List<CaseCardVm> myAssignedCasesBoard = List.of();
	private boolean showCompletedMyTasks;
	private final Set<Integer> selectedStatusIds = new LinkedHashSet<>();
	private final Set<Long> pinnedTaskLaneCaseIds = new LinkedHashSet<>();
	private final Set<Long> collapsedTaskLaneCaseIds = new LinkedHashSet<>();
	private List<CaseListUiSupport.StatusFilterOption> statusFilterOptions = List.of();
	private final Map<String, Button> sectionButtons = new LinkedHashMap<>();
	private String activeSection = SECTION_OVERVIEW;
	private boolean suppressMyTaskPreferenceWrites;
	private Integer preferredMyTasksPriorityFilterId;
	private Long preferredMyTasksCaseFilterId;
	private String overviewSearchText = "";
	private Integer overviewPriorityFilterId;
	private Long overviewCaseFilterId;
	private boolean overviewOverdueOnly;
	private String overviewSortMode = OVERVIEW_SORT_DUE_ASC;
	private VBox overviewSectionsContainer;
	private TextField overviewSearchFieldControl;
	private ChoiceBox<PriorityFilterOption> overviewPriorityChoiceControl;
	private ChoiceBox<CaseFilterOption> overviewCaseChoiceControl;
	private CheckBox overviewOverdueOnlyCheckControl;
	private ChoiceBox<String> overviewSortChoiceControl;
	private boolean suppressOverviewControlEvents;
	private boolean loadingOverview;
	private boolean loadingMyTasks;
	private boolean loadingMyCases;
	private static final BoardStatusFilterOption ALL_BOARD_STATUSES_OPTION = new BoardStatusFilterOption(null, "All Statuses");

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
			myCasesBoardScroll.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		}
	}

	private void setupSections() {
		if (sectionButtonsBox == null) {
			return;
		}
		sectionButtons.clear();
		sectionButtonsBox.getChildren().clear();
		for (String section : List.of(SECTION_OVERVIEW, SECTION_TASKS, SECTION_MY_CASES)) {
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
		boolean showTasks = SECTION_TASKS.equals(section);
		boolean showMyCases = SECTION_MY_CASES.equals(section);
		setVisibleManaged(overviewSectionPane, showOverview);
		setVisibleManaged(tasksSectionPane, showTasks);
		setVisibleManaged(myCasesSectionPane, showMyCases);
		if (showOverview) {
			renderMyOverview();
			refreshMyTasks();
		}
		if (showTasks) {
			attachTasksPanel(tasksSectionContentHost);
			renderMyTasks();
			refreshMyTasks();
		}
		if (showMyCases) {
			renderMyCasesBoard();
			refreshMyCasesBoard();
		}
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
				syncMyCasesBoardStatusFilterOptions();
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
						refreshMyCasesBoard();
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
		loadingOverview = true;
		loadingMyTasks = true;
		renderMyOverview();
		renderMyTasks();
		Integer shaleClientId = appState.getShaleClientId();
		Integer userId = appState.getUserId();
		if (shaleClientId == null || shaleClientId <= 0 || userId == null || userId <= 0) {
			myTasks = List.of();
			myTaskAssignedUsers = java.util.Map.of();
			loadingOverview = false;
			loadingMyTasks = false;
			renderMyOverview();
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
				Set<Long> collapsedLaneCaseIds = loadCollapsedTaskLaneCaseIds(shaleClientIdValue, userIdValue);
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
						loadingOverview = false;
						loadingMyTasks = false;
						myTasks = tasks == null ? List.of() : tasks;
						pinnedTaskLaneCaseIds.clear();
						pinnedTaskLaneCaseIds.addAll(pinnedLaneCaseIds);
						collapsedTaskLaneCaseIds.clear();
						collapsedTaskLaneCaseIds.addAll(collapsedLaneCaseIds);
						myTaskAssignedUsers = assignedByTask;
						myTaskPrioritiesById = prioritiesById;
						syncMyTaskPriorityFilterOptions();
						syncMyTaskCaseFilterOptions();
						renderMyOverview();
						renderMyTasks();
					});
			} catch (Exception ex) {
				System.err.println("My tasks load failed: " + ex.getMessage());
				ex.printStackTrace();
				runOnFx(() -> {
					loadingOverview = false;
					loadingMyTasks = false;
					renderMyOverview();
					renderMyTasks();
					showTaskActionError("Failed to load your tasks.");
				});
			}
		});
	}

	private void refreshMyCasesBoard() {
		if (caseDao == null || appState == null) {
			return;
		}
		loadingMyCases = true;
		renderMyCasesBoard();
		Integer userId = appState.getUserId();
		Integer shaleClientId = appState.getShaleClientId();
		if (userId == null || userId <= 0 || shaleClientId == null || shaleClientId <= 0) {
			myAssignedCasesBoard = List.of();
			loadingMyCases = false;
			renderMyCasesBoard();
			return;
		}
		final int userIdValue = userId;
		dbExec.submit(() -> {
			try {
				List<CaseDao.CaseRow> rows = caseDao.listAssignedCasesForBoard(userIdValue);
				List<CaseCardVm> cases = (rows == null ? List.<CaseDao.CaseRow>of() : rows).stream()
						.filter(Objects::nonNull)
						.map(this::toVm)
						.toList();
				runOnFx(() -> {
					loadingMyCases = false;
					myAssignedCasesBoard = cases;
					renderMyCasesBoard();
				});
			} catch (Exception ex) {
				System.err.println("My cases board load failed: " + ex.getMessage());
				ex.printStackTrace();
				runOnFx(() -> {
					loadingMyCases = false;
					myAssignedCasesBoard = List.of();
					renderMyCasesBoard();
				});
			}
		});
	}

	private void renderMyCasesBoard() {
		if (myCasesBoardList == null || myCasesBoardEmptyLabel == null || myCasesBoardScroll == null) {
			return;
		}
		if (loadingMyCases) {
			myCasesBoardList.getChildren().clear();
			myCasesBoardEmptyLabel.setText("Loading your cases...");
			setVisibleManaged(myCasesBoardEmptyLabel, true);
			setVisibleManaged(myCasesBoardScroll, false);
			return;
		}
		myCasesBoardList.getChildren().clear();
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
		}
		if (!noStatus.isEmpty()) {
			List<CaseCardVm> sortedNoStatus = noStatus.stream()
					.sorted(laneSort)
					.toList();
			myCasesBoardList.getChildren().add(createMyCasesStatusLane("No Status", sortedNoStatus));
		}

		boolean hasAnyCards = myCasesBoardList.getChildren().stream().anyMatch(Objects::nonNull);
		setVisibleManaged(myCasesBoardEmptyLabel, !hasAnyCards);
		setVisibleManaged(myCasesBoardScroll, hasAnyCards);
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
			body.getChildren().add(buildCaseCard(vm));
		}
		return LaneBoardLayout.createLane(
				header,
				body,
				new LaneBoardLayout.LaneWidth(
						MY_CASES_STATUS_COLUMN_MIN_WIDTH,
						MY_CASES_STATUS_COLUMN_PREF_WIDTH,
						MY_CASES_STATUS_COLUMN_MAX_WIDTH));
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
		if (loadingMyTasks) {
			myTasksList.getChildren().clear();
			myTasksEmptyLabel.setText("Loading your tasks...");
			setVisibleManaged(myTasksEmptyLabel, true);
			setVisibleManaged(myTasksScroll, false);
			suppressMyTasksScrollTopRightCornerOverlay();
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
		setVisibleManaged(myTasksEmptyLabel, false);
		setVisibleManaged(myTasksScroll, true);
		PerfLog.logDone("RENDER", "panel=my_tasks page=my_shale userId=" + (appState == null ? null : appState.getUserId()) + " childCount=" + myTasksList.getChildren().size(),
				renderStartNanos);
	}

	private void renderMyOverview() {
		if (overviewMainRow == null) {
			return;
		}
		if (loadingOverview) {
			Label loadingLabel = new Label("Loading your overview...");
			loadingLabel.getStyleClass().add("muted-text");
			overviewMainRow.getChildren().setAll(loadingLabel);
			return;
		}
		ensureOverviewContentShell();
		List<CaseTaskListItemDto> overviewSource = overviewEligibleTasks(myTasks);
		syncOverviewControlOptions(overviewSource);
		renderOverviewSections(overviewSource);
	}

	private void ensureOverviewContentShell() {
		if (overviewSectionsContainer != null
				&& overviewMainRow.getChildren().contains(overviewSectionsContainer)
				&& overviewSearchFieldControl != null) {
			return;
		}
		VBox sections = new VBox(10);
		sections.setFillWidth(true);
		sections.getChildren().add(buildOverviewControlBar());
		overviewSectionsContainer = sections;
		overviewMainRow.getChildren().setAll(sections);
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
				"Today",
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
		dbExec.submit(() -> userBoardLanePreferencesDao.upsertLanePreference(
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
		dbExec.submit(() -> userBoardLanePreferencesDao.upsertLanePreference(
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
