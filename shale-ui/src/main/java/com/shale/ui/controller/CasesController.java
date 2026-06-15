package com.shale.ui.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.shale.data.dao.CaseDao;
import com.shale.data.dao.CaseDao.CaseSort;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.controller.support.CaseListUiSupport;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import com.shale.ui.util.PerfLog;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleGroup;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.util.Duration;

public final class CasesController {

	private static final int LATEST_CASE_UPDATE_PREVIEW_LIMIT = 100;

	// Existing controls (keep these IDs in FXML)
	@FXML
	private TextField casesSearchField;
	@FXML
	private ChoiceBox<String> casesSortChoice;
	@FXML
	private MenuButton statusFilterMenuButton;
	@FXML
	private Label resultsCountLabel;
	@FXML
	private ToggleButton cardsViewToggle;
	@FXML
	private ToggleButton gridViewToggle;
	@FXML
	private MenuButton columnMenuButton;
	@FXML
	private TableView<CaseCardVm> casesTable;

	// NEW: FlowPane layout (add these IDs in FXML)
	@FXML
	private ScrollPane casesScroll;
	@FXML
	private FlowPane casesFlow;

	private CaseDao caseDao;
	private AppState appState;
	private UiRuntimeBridge runtimeBridge;

	// Paging state
	private int currentPage = 0;
	private final int pageSize = 100;
	private boolean loading = false;
	private boolean hasMore = true;
	private int loadGeneration = 0;
	private int resultsCountGeneration = 0;
	private String lastCountQuery;
	private Set<Integer> lastCountStatuses;
	private long lastCountTotal = -1;
	private PauseTransition searchDebounce;

	// Loaded items (we keep these so search/sort can re-render)
	private final List<CaseCardVm> loaded = new ArrayList<>();

	private CaseCardFactory caseCardFactory;
	private Consumer<UiRuntimeBridge.CaseUpdatedEvent> liveCaseUpdatedHandler;
	private boolean liveSubscribed;

	private final Set<Integer> selectedStatusIds = new LinkedHashSet<>();
	private List<CaseListUiSupport.StatusFilterOption> statusFilterOptions = List.of();

	// Background DB executor (so UI doesn’t freeze)
	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "cases-loader");
		t.setDaemon(true);
		return t;
	});

	public CasesController() {
		System.out.println("CasesController()");
	}

	public void init(AppState appState,
			UiRuntimeBridge runtimeBridge,
			CaseDao caseDao,
			Consumer<Integer> onOpenCase) {
		System.out.println("CasesController.init()");
		PerfLog.log("CTRL", "start", "controller=CasesController page=cases_list");
		this.caseDao = caseDao;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.caseCardFactory = new CaseCardFactory(onOpenCase);

	}

	@FXML
	private void initialize() {
		System.out.println("CasesController.initialize()");

		// sort choices
		if (casesSortChoice != null) {
			casesSortChoice.getItems().setAll(
					"Intake date (newest first)",
					"Intake date (oldest first)",
					"Statute date (soonest first)",
					"Statute date (latest first)",
					"Case name (A–Z)",
					"Case name (Z–A)",
					"Responsible attorney (A–Z)",
					"Responsible attorney (Z–A)",
					"Case Status (A–Z)",
					"Case Status (Z–A)"
			);
			casesSortChoice.getSelectionModel().select(0);
			casesSortChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> loadFirstPage());
		}

		// search filter: debounce and restart the SQL-backed query instead of filtering loaded pages.
		if (casesSearchField != null) {
			searchDebounce = new PauseTransition(Duration.millis(300));
			searchDebounce.setOnFinished(event -> loadFirstPage());
			casesSearchField.textProperty().addListener((obs, oldV, newV) -> {
				if (Objects.equals(oldV, newV)) {
					return;
				}
				loadGeneration++;
				loading = false;
				if (searchDebounce != null) {
					searchDebounce.playFromStart();
				}
			});
		}

		initializeViewToggle();
		initializeGridColumns();
		initializeStatusFilter();

		Platform.runLater(() ->
		{
			if (caseDao == null) {
				System.out.println("CasesController: caseDao is null (not injected).");
				return;
			}
			wireInfiniteScroll();
			loadFirstPage();
		});
		if (casesFlow != null) {
			casesFlow.sceneProperty().addListener((obs, oldScene, newScene) -> {
				if (newScene == null) {
					unsubscribeLiveCaseUpdates();
				} else {
					subscribeLiveCaseUpdates();
				}
			});
		}

		subscribeLiveCaseUpdates();
	}

	private void initializeViewToggle() {
		if (cardsViewToggle == null || gridViewToggle == null) return;
		ToggleGroup group = new ToggleGroup();
		cardsViewToggle.setToggleGroup(group);
		gridViewToggle.setToggleGroup(group);
		group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
			if (newToggle == null) { cardsViewToggle.setSelected(true); return; }
			boolean grid = newToggle == gridViewToggle;
			if (casesScroll != null) { casesScroll.setVisible(!grid); casesScroll.setManaged(!grid); }
			if (casesTable != null) { casesTable.setVisible(grid); casesTable.setManaged(grid); }
			if (grid) {
				loadRemainingPagesForGrid();
			} else {
				rerender();
			}
		});
	}

	private void initializeGridColumns() {
		if (casesTable == null) return;
		casesTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
		addGridColumn("Case Name", vm -> vm.name, 180);
		addGridColumn("Client", vm -> vm.clientName, 160);
		addGridColumn("Intake Date / Caller Date", vm -> formatDate(vm.intakeDate), 150);
		addGridColumn("Case Status", vm -> vm.primaryStatusName, 130);
		addGridColumn("Opposing Parties", vm -> vm.opposingPartiesName, 160);
		addLatestCaseUpdateGridColumn();
		addGridColumn("Description", vm -> vm.description, 240);
		addGridColumn("Date of Incident", vm -> formatDate(vm.dateOfIncident), 130);
		addGridColumn("Statute of Limitations", vm -> formatDate(vm.solDate), 150);
		addGridColumn("Tort Claims Notice Deadline", vm -> formatDate(vm.tortClaimsNoticeDeadline), 190);
		addGridColumn("Responsible Attorney", vm -> vm.responsibleAttorney, 170);
		if (columnMenuButton != null) {
			columnMenuButton.getItems().clear();
			for (TableColumn<CaseCardVm, ?> column : casesTable.getColumns()) {
				CheckMenuItem item = new CheckMenuItem(column.getText());
				item.setSelected(column.isVisible());
				column.visibleProperty().bind(item.selectedProperty());
				columnMenuButton.getItems().add(item);
			}
		}
	}

	private void addGridColumn(String title, java.util.function.Function<CaseCardVm, String> valueFactory, double width) {
		TableColumn<CaseCardVm, String> column = new TableColumn<>(title);
		column.setPrefWidth(width);
		column.setCellValueFactory(data -> new ReadOnlyStringWrapper(valueFactory.apply(data.getValue())));
		casesTable.getColumns().add(column);
	}

	private void addLatestCaseUpdateGridColumn() {
		TableColumn<CaseCardVm, String> column = new TableColumn<>("Latest Case Update");
		column.setPrefWidth(220);
		column.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().latestCaseUpdate));
		column.setCellFactory(col -> new TableCell<>() {
			@Override
			protected void updateItem(String updateText, boolean empty) {
				super.updateItem(updateText, empty);
				if (empty || updateText == null || updateText.isEmpty()) {
					setText(null);
					setTooltip(null);
					return;
				}

				setText(previewLatestCaseUpdate(updateText));
				setTooltip(new Tooltip(updateText));
			}
		});
		casesTable.getColumns().add(column);
	}

	private static String previewLatestCaseUpdate(String updateText) {
		String safeText = safe(updateText);
		if (safeText.length() <= LATEST_CASE_UPDATE_PREVIEW_LIMIT) {
			return safeText;
		}
		return safeText.substring(0, LATEST_CASE_UPDATE_PREVIEW_LIMIT) + "...";
	}

	private static String formatDate(LocalDate date) {
		return date == null ? "" : date.toString();
	}

	private void subscribeLiveCaseUpdates() {
		if (runtimeBridge == null || liveSubscribed) {
			return;
		}

		liveCaseUpdatedHandler = event ->
		{
			String mine = runtimeBridge == null ? "" : runtimeBridge.getClientInstanceId();

			System.out.println("[DEBUG LIVE] CASES listenerUserId=" + (appState == null ? null : appState.getUserId())
					+ " event.updatedByUserId=" + event.updatedByUserId()
					+ " caseId=" + event.caseId()
					+ " newName=" + (event.newName() != null)
					+ " patchLen=" + (event.rawPatchJson() == null ? 0 : event.rawPatchJson().length())
					+ " mineInstance=" + mine
					+ " eventInstance=" + event.clientInstanceId());

			// Ignore only our own echo
			if (!mine.isBlank() && mine.equals(event.clientInstanceId())) {
				return;
			}

			// 1) Legacy support (newName-only)
			if (event.newName() != null) {
				runOnFx(() ->
				{
					boolean changed = applyCasePatchToList(event.caseId(), "name", event.newName());
					if (changed)
						rerender();
				});
				return;
			}

			// 2) Patch-based updates
			String rawPatch = event.rawPatchJson();
			if (rawPatch == null || rawPatch.isBlank()) {
				refreshCaseRowFromDb(event.caseId());
				return;
			}

			String patchedName = extractPatchString(rawPatch, "name");
			Integer patchedResponsibleAttorneyUserId = extractPatchInt(rawPatch, "responsibleAttorneyUserId");
			Integer patchedDeleted = extractPatchInt(rawPatch, "deleted");

			if (patchedDeleted != null) {
				refreshCaseRowFromDb(event.caseId());
				return;
			}

			// If nothing relevant to the list changed, ignore
			if (patchedName == null && patchedResponsibleAttorneyUserId == null) {
				System.out.println("[LIVE] Case update patch had no list-relevant fields; skipping list update for caseId="
						+ event.caseId());
				return;
			}

			// Name patch (fast in-memory update)
			if (patchedName != null) {
				runOnFx(() ->
				{
					boolean changed = applyCasePatchToList(event.caseId(), "name", patchedName);
					if (changed)
						rerender();
				});
			}

			// Responsible attorney patch (reload that row so we get name + color)
			if (patchedResponsibleAttorneyUserId != null) {
				refreshCaseRowFromDb(event.caseId());
			}
		};
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

	/**
	 * Apply a single field patch to the loaded list VM. Returns true if it changed anything.
	 */
	private boolean applyCasePatchToList(int caseId, String field, String newValue) {
		if (newValue == null)
			return false;

		String safeVal = safe(newValue).trim();
		if (safeVal.isBlank())
			return false;

		for (int i = 0; i < loaded.size(); i++) {
			CaseCardVm vm = loaded.get(i);
			if (vm.id == caseId) {

				// Only update what this list actually shows
				if ("name".equals(field)) {
					if (safeVal.equals(vm.name)) {
						return false; // no change
					}
					loaded.set(i, new CaseCardVm(vm.id, safeVal, vm.intakeDate, vm.solDate, vm.primaryStatusId, vm.responsibleAttorney, vm.responsibleAttorneyColor, vm.nonEngagementLetterSent, vm.primaryStatusName, vm.clientName, vm.opposingPartiesName, vm.latestCaseUpdate, vm.description, vm.dateOfIncident, vm.tortClaimsNoticeDeadline));
					return true;
				}

				return false;
			}
		}
		return false;
	}

	/**
	 * Minimal patch reader for {"name":"...","description":"..."} style patch. Supports
	 * string values; returns null if missing or malformed.
	 */
	private static String extractPatchString(String rawPatchJson, String key) {
		if (rawPatchJson == null || rawPatchJson.isBlank() || key == null || key.isBlank()) {
			return null;
		}
		String needle = "\"" + key + "\"";
		int k = rawPatchJson.indexOf(needle);
		if (k < 0)
			return null;

		int colon = rawPatchJson.indexOf(':', k + needle.length());
		if (colon < 0)
			return null;

		int firstQuote = rawPatchJson.indexOf('"', colon + 1);
		if (firstQuote < 0)
			return null;

		int secondQuote = rawPatchJson.indexOf('"', firstQuote + 1);
		if (secondQuote < 0)
			return null;

		return rawPatchJson.substring(firstQuote + 1, secondQuote);
	}

	private void applyLiveCaseNameUpdate(int caseId, String newName) {
		String safeName = safe(newName).trim();
		if (safeName.isBlank()) {
			return;
		}
		for (int i = 0; i < loaded.size(); i++) {
			CaseCardVm vm = loaded.get(i);
			if (vm.id == caseId) {
				loaded.set(i, new CaseCardVm(vm.id, safeName, vm.intakeDate, vm.solDate, vm.primaryStatusId, vm.responsibleAttorney, vm.responsibleAttorneyColor, vm.nonEngagementLetterSent, vm.primaryStatusName, vm.clientName, vm.opposingPartiesName, vm.latestCaseUpdate, vm.description, vm.dateOfIncident, vm.tortClaimsNoticeDeadline));
				rerender();
				return;
			}
		}
	}

	private void wireInfiniteScroll() {
		if (casesScroll == null)
			return;

		// vvalue is 0..1
		casesScroll.vvalueProperty().addListener((obs, oldV, newV) ->
		{
			if (newV != null && newV.doubleValue() >= 0.95 && !isGridViewActive()) {
				loadNextPage();
			}
		});
	}

	private void loadFirstPage() {
		PerfLog.log("PAGE", "start", "page=cases_list");
		loadGeneration++;
		currentPage = 0;
		loading = false;
		hasMore = true;

		loaded.clear();
		if (casesFlow != null)
			casesFlow.getChildren().clear();
		updateResultsCountLabel(0);

		loadNextPage();
	}

	private void loadNextPage() {
		if (loading || !hasMore)
			return;
		if (caseDao == null)
			return;

		loading = true;
		final int pageToLoad = currentPage;
		final int generationAtSubmit = loadGeneration;
		final String queryAtSubmit = normalizedSearchQuery();
		final Set<Integer> statusesAtSubmit = new LinkedHashSet<>(selectedStatusIds);
		final Long knownTotalAtSubmit = knownResultsTotal(queryAtSubmit, statusesAtSubmit);
		final boolean gridAtSubmit = isGridViewActive();
		final CaseSort sortAtSubmit = selectedSort();
		final boolean includeClosedDeniedAtSubmit = includeClosedDeniedInQuery();

		dbExec.submit(() ->
		{
			try {
				long daoStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=findCasesViewPage page=cases_list pageIndex=" + pageToLoad + " grid=" + gridAtSubmit);
				var page = caseDao.findCasesViewPage(pageToLoad, pageSize, sortAtSubmit, includeClosedDeniedAtSubmit, queryAtSubmit, statusesAtSubmit, knownTotalAtSubmit);
				PerfLog.logDone("DAO", "method=findCasesViewPage page=cases_list pageIndex=" + pageToLoad + " rows=" + (page == null || page.items() == null ? 0 : page.items().size()), daoStartNanos);

				// map DAO rows into UI VM
				long mapStartNanos = PerfLog.start();
				PerfLog.log("DAO_MAP", "start", "method=findPage page=cases_list rows=" + (page == null || page.items() == null ? 0 : page.items().size()));
				List<CaseCardVm> newItems = page.items().stream()
						.map(r -> new CaseCardVm(
								r.id(),
								safe(r.name()),
								r.intakeDate(),
								r.statuteOfLimitationsDate(),
								r.primaryStatusId(),
								safe(r.responsibleAttorneyName()),
								safe(r.responsibleAttorneyColor()),
								r.nonEngagementLetterSent(),
								safe(r.primaryStatusName()),
								safe(r.clientName()),
								safe(r.opposingPartiesName()),
								safe(r.latestCaseUpdate()),
								safe(r.description()),
								r.dateOfIncident(),
								r.tortClaimsNoticeDeadline()
						))
						.toList();
				PerfLog.logDone("DAO_MAP", "method=findPage page=cases_list rows=" + newItems.size(), mapStartNanos);

				Platform.runLater(() ->
				{
					if (generationAtSubmit != loadGeneration) {
						loading = false;
						return;
					}

					loaded.addAll(newItems);

					currentPage++;
					hasMore = loaded.size() < page.total();
					cacheResultsCount(queryAtSubmit, statusesAtSubmit, page.total());
					updateResultsCountLabel(page.total());
					loading = false;

					// Render according to current search/sort
					rerender();

					System.out.println("Loaded cases page " + pageToLoad + ": " + newItems.size()
							+ " (loaded=" + loaded.size() + " / total=" + page.total() + ")");
				});

			} catch (Exception ex) {
				Platform.runLater(() ->
				{
					if (generationAtSubmit != loadGeneration) {
						return;
					}
					loading = false;
					ex.printStackTrace();
				});
			}
		});
	}

	private boolean isGridViewActive() {
		return gridViewToggle != null && gridViewToggle.isSelected();
	}

	private void loadRemainingPagesForGrid() {
		if (loading || !hasMore || caseDao == null || !isGridViewActive()) {
			return;
		}
		loadNextPage();
	}

	private void rerender() {
		if (casesFlow == null)
			return;
		long renderStartNanos = PerfLog.start();
		PerfLog.log("RENDER", "start", "panel=cases_list page=cases_list");

		String q = normalizedSearchQuery();
		if (shouldRefreshResultsCount(q, selectedStatusIds)) {
			refreshResultsCountAsync(q, selectedStatusIds);
		}

		boolean grid = isGridViewActive();
		List<CaseCardVm> view = List.copyOf(loaded);

		long uiModelStartNanos = PerfLog.start();
		PerfLog.log("UI_MODEL", "start", "panel=cases_list grid=" + grid + " rows=" + view.size());
		var tableItems = FXCollections.observableArrayList(view);
		PerfLog.logDone("UI_MODEL", "panel=cases_list grid=" + grid + " rows=" + tableItems.size(), uiModelStartNanos);

		long tableRenderStartNanos = PerfLog.start();
		if (casesTable != null) {
			casesTable.setItems(tableItems);
		}
		PerfLog.logDone("RENDER", "component=cases_table page=cases_list rowCount=" + tableItems.size(), tableRenderStartNanos);

		long cardRenderStartNanos = PerfLog.start();
		if (!grid) {
			casesFlow.getChildren().setAll(view.stream().map(this::buildCaseCard).toList());
		}
		PerfLog.logDone("RENDER", "component=cases_cards page=cases_list childCount=" + casesFlow.getChildren().size() + " skipped=" + grid, cardRenderStartNanos);
		if (grid && hasMore && !loading) {
			loadRemainingPagesForGrid();
		}
		PerfLog.logDone("RENDER", "panel=cases_list page=cases_list rowCount=" + view.size(), renderStartNanos);
	}

	private Long knownResultsTotal(String normalizedQuery, Set<Integer> selectedStatuses) {
		String query = normalizedQuery == null ? "" : normalizedQuery;
		Set<Integer> statuses = new LinkedHashSet<>(selectedStatuses == null ? Set.of() : selectedStatuses);
		if (lastCountTotal >= 0 && Objects.equals(lastCountQuery, query) && Objects.equals(lastCountStatuses, statuses)) {
			return lastCountTotal;
		}
		return null;
	}

	private void cacheResultsCount(String normalizedQuery, Set<Integer> selectedStatuses, long total) {
		lastCountQuery = normalizedQuery == null ? "" : normalizedQuery;
		lastCountStatuses = new LinkedHashSet<>(selectedStatuses == null ? Set.of() : selectedStatuses);
		lastCountTotal = total;
	}

	private void refreshResultsCountAsync(String normalizedQuery, Set<Integer> selectedStatuses) {
		if (caseDao == null) {
			updateResultsCountLabel(0);
			return;
		}

		final int generationAtSubmit = ++resultsCountGeneration;
		final String query = normalizedQuery == null ? "" : normalizedQuery;
		final Set<Integer> statusesSnapshot = new LinkedHashSet<>(selectedStatuses);

		dbExec.submit(() ->
		{
			try {
				long daoStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=countForCasesView page=cases_list");
				long total = caseDao.countForCasesView(query, statusesSnapshot);
				PerfLog.logDone("DAO", "method=countForCasesView page=cases_list rows=1", daoStartNanos);
				Platform.runLater(() ->
				{
					if (generationAtSubmit != resultsCountGeneration) {
						return;
					}
					cacheResultsCount(query, statusesSnapshot, total);
					updateResultsCountLabel(total);
				});
			} catch (Exception ex) {
				Platform.runLater(ex::printStackTrace);
			}
		});
	}

	private boolean shouldRefreshResultsCount(String normalizedQuery, Set<Integer> selectedStatuses) {
		String nextQuery = normalizedQuery == null ? "" : normalizedQuery;
		Set<Integer> nextStatuses = new LinkedHashSet<>(selectedStatuses == null ? Set.of() : selectedStatuses);
		if (Objects.equals(lastCountQuery, nextQuery) && Objects.equals(lastCountStatuses, nextStatuses)) {
			if (lastCountTotal >= 0) {
				updateResultsCountLabel(lastCountTotal);
			}
			return false;
		}
		lastCountQuery = nextQuery;
		lastCountStatuses = nextStatuses;
		return true;
	}

	private void updateResultsCountLabel(long total) {
		if (resultsCountLabel == null) {
			return;
		}
		String suffix = total == 1 ? "result" : "results";
		resultsCountLabel.setText(total + " " + suffix);
	}

	private boolean includeClosedDeniedInQuery() {
		if (statusFilterOptions.isEmpty()) {
			return false;
		}
		for (CaseListUiSupport.StatusFilterOption option : statusFilterOptions) {
			if (option != null && option.terminal() && selectedStatusIds.contains(option.id())) {
				return true;
			}
		}
		return false;
	}

	private void initializeStatusFilter() {
		reloadStatusFilterOptionsAndThen(this::loadFirstPage);
	}

	private void reloadStatusFilterOptionsAndThen(Runnable onLoaded) {
		Integer tenantId = appState == null ? null : appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0 || caseDao == null) {
			statusFilterOptions = List.of();
			selectedStatusIds.clear();
			CaseListUiSupport.initializeStatusFilterMenu(statusFilterMenuButton, selectedStatusIds, statusFilterOptions, onLoaded);
			return;
		}

		dbExec.submit(() -> {
			long daoStartNanos = PerfLog.start();
			PerfLog.log("DAO", "start", "method=listStatusesForTenant page=cases_list organizationId=" + tenantId);
			List<CaseDao.StatusRow> statuses = caseDao.listStatusesForTenant(tenantId);
			PerfLog.logDone("DAO", "method=listStatusesForTenant page=cases_list organizationId=" + tenantId + " rows=" + (statuses == null ? 0 : statuses.size()), daoStartNanos);
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
				CaseListUiSupport.initializeStatusFilterMenu(statusFilterMenuButton, selectedStatusIds, statusFilterOptions, onLoaded);
			});
		});
	}

	private boolean matchesSelectedStatus(CaseCardVm vm) {
		if (vm.primaryStatusId == null) {
			return true;
		}
		return selectedStatusIds.contains(vm.primaryStatusId);
	}

	private boolean isSearchActive() {
		return !normalizedSearchQuery().isEmpty();
	}

	private String normalizedSearchQuery() {
		if (casesSearchField == null)
			return "";
		return safe(casesSearchField.getText()).trim().toLowerCase(Locale.ROOT);
	}

	private static boolean matchesQuery(CaseCardVm vm, String query) {
		if (query.isEmpty())
			return true;
		return vm.name.toLowerCase(Locale.ROOT).contains(query)
				|| vm.responsibleAttorney.toLowerCase(Locale.ROOT).contains(query);
	}

	private CaseSort selectedSort() {
		if (casesSortChoice == null || casesSortChoice.getValue() == null)
			return CaseSort.INTAKE_NEWEST;

		return switch (casesSortChoice.getValue()) {
		case "Intake date (oldest first)" -> CaseSort.INTAKE_OLDEST;
		case "Statute date (soonest first)" -> CaseSort.STATUTE_SOONEST;
		case "Statute date (latest first)" -> CaseSort.STATUTE_LATEST;
		case "Case name (A–Z)" -> CaseSort.CASE_NAME_ASC;
		case "Case name (Z–A)" -> CaseSort.CASE_NAME_DESC;
		case "Responsible attorney (A–Z)" -> CaseSort.RESPONSIBLE_ATTORNEY_ASC;
		case "Responsible attorney (Z–A)" -> CaseSort.RESPONSIBLE_ATTORNEY_DESC;
		case "Case Status (A–Z)" -> CaseSort.CASE_STATUS_ASC;
		case "Case Status (Z–A)" -> CaseSort.CASE_STATUS_DESC;
		default -> CaseSort.INTAKE_NEWEST;
		};
	}

	private Comparator<CaseCardVm> comparatorFor(String sortOption) {
		if (sortOption == null)
			sortOption = "Intake date (newest first)";

		return switch (sortOption) {
		case "Intake date (oldest first)" ->
			Comparator.comparing((CaseCardVm v) -> v.intakeDate, this::nullsLastDate);

		case "Statute date (soonest first)" ->
			Comparator.comparing((CaseCardVm v) -> v.solDate, this::nullsLastDate);

		case "Statute date (latest first)" ->
			Comparator.comparing((CaseCardVm v) -> v.solDate, this::nullsLastDate).reversed();

		case "Case name (A–Z)" ->
			Comparator.comparing((CaseCardVm v) -> v.name, this::nullsLastString);

		case "Case name (Z–A)" ->
			Comparator.comparing((CaseCardVm v) -> v.name, this::nullsLastString).reversed();

		case "Responsible attorney (A–Z)" ->
			Comparator.comparing((CaseCardVm v) -> v.responsibleAttorney, this::nullsLastString);

		case "Responsible attorney (Z–A)" ->
			Comparator.comparing((CaseCardVm v) -> v.responsibleAttorney, this::nullsLastString).reversed();

		case "Case Status (A–Z)" ->
			Comparator.comparing((CaseCardVm v) -> v.primaryStatusName, this::nullsLastString);

		case "Case Status (Z–A)" ->
			Comparator.comparing((CaseCardVm v) -> v.primaryStatusName, this::nullsLastString).reversed();

		// default: newest first
		default ->
			Comparator.comparing((CaseCardVm v) -> v.intakeDate, this::nullsLastDate).reversed();
		};
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
				vm.nonEngagementLetterSent
		));
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

	// Simple view-model for the card (keeps rendering logic separate from DAO record)
	private static final class CaseCardVm {
		final long id;
		final String name;
		final LocalDate intakeDate;
		final LocalDate solDate;
		final Integer primaryStatusId;
		final String responsibleAttorney;
		final String responsibleAttorneyColor;
		final Boolean nonEngagementLetterSent;
		final String primaryStatusName;
		final String clientName;
		final String opposingPartiesName;
		final String latestCaseUpdate;
		final String description;
		final LocalDate dateOfIncident;
		final LocalDate tortClaimsNoticeDeadline;

		CaseCardVm(long id, String name, LocalDate intakeDate, LocalDate solDate, Integer primaryStatusId, String responsibleAttorney,
				String responsibleAttorneyColor, Boolean nonEngagementLetterSent, String primaryStatusName, String clientName,
				String opposingPartiesName, String latestCaseUpdate, String description, LocalDate dateOfIncident,
				LocalDate tortClaimsNoticeDeadline) {
			this.id = id;
			this.name = Objects.requireNonNullElse(name, "");
			this.intakeDate = intakeDate;
			this.solDate = solDate;
			this.primaryStatusId = primaryStatusId;
			this.responsibleAttorney = Objects.requireNonNullElse(responsibleAttorney, "");
			this.responsibleAttorneyColor = Objects.requireNonNullElse(responsibleAttorneyColor, "");
			this.nonEngagementLetterSent = nonEngagementLetterSent;
			this.primaryStatusName = Objects.requireNonNullElse(primaryStatusName, "");
			this.clientName = Objects.requireNonNullElse(clientName, "");
			this.opposingPartiesName = Objects.requireNonNullElse(opposingPartiesName, "");
			this.latestCaseUpdate = Objects.requireNonNullElse(latestCaseUpdate, "");
			this.description = Objects.requireNonNullElse(description, "");
			this.dateOfIncident = dateOfIncident;
			this.tortClaimsNoticeDeadline = tortClaimsNoticeDeadline;
		}
	}

	private static Integer extractPatchInt(String rawPatchJson, String key) {
		if (rawPatchJson == null || rawPatchJson.isBlank() || key == null || key.isBlank())
			return null;

		String needle = "\"" + key + "\"";
		int k = rawPatchJson.indexOf(needle);
		if (k < 0)
			return null;

		int colon = rawPatchJson.indexOf(':', k + needle.length());
		if (colon < 0)
			return null;

		int i = colon + 1;
		while (i < rawPatchJson.length() && Character.isWhitespace(rawPatchJson.charAt(i)))
			i++;

		boolean quoted = i < rawPatchJson.length() && rawPatchJson.charAt(i) == '"';
		if (quoted)
			i++;

		int start = i;
		while (i < rawPatchJson.length() && Character.isDigit(rawPatchJson.charAt(i)))
			i++;

		if (i == start)
			return null;

		try {
			return Integer.parseInt(rawPatchJson.substring(start, i));
		} catch (Exception ignored) {
			return null;
		}
	}

	private void refreshCaseRowFromDb(long caseId) {
		if (caseDao == null)
			return;

		final int generationAtSubmit = loadGeneration;

		dbExec.submit(() ->
		{
			try {
				// simplest: fetch a 1-item "page" by filtering current page is awkward,
				// so add a DAO method later. For now: just reload the *current page* row via DAO helper.
				long daoStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=getCaseRow page=cases_list caseId=" + caseId);
				CaseDao.CaseRow row = caseDao.getCaseRow(caseId);
				PerfLog.logDone("DAO", "method=getCaseRow page=cases_list caseId=" + caseId + " rows=" + (row == null ? 0 : 1), daoStartNanos);

				Platform.runLater(() ->
				{
					if (generationAtSubmit != loadGeneration)
						return;

					boolean changed = row == null ? removeLoadedCase(caseId) : applyCaseRowToList(row);
					if (changed)
						rerender();
				});
			} catch (Exception ex) {
				Platform.runLater(ex::printStackTrace);
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

	private boolean applyCaseRowToList(CaseDao.CaseRow r) {
		for (int i = 0; i < loaded.size(); i++) {
			CaseCardVm vm = loaded.get(i);
			if (vm.id == r.id()) {
				String newName = safe(r.name());
				String newAtty = safe(r.responsibleAttorneyName());
				String newColor = safe(r.responsibleAttorneyColor());

				boolean same = newName.equals(vm.name)
						&& Objects.equals(r.intakeDate(), vm.intakeDate)
						&& Objects.equals(r.statuteOfLimitationsDate(), vm.solDate)
						&& Objects.equals(r.primaryStatusId(), vm.primaryStatusId)
						&& newAtty.equals(vm.responsibleAttorney)
						&& newColor.equals(vm.responsibleAttorneyColor)
						&& Objects.equals(r.nonEngagementLetterSent(), vm.nonEngagementLetterSent)
						&& safe(r.primaryStatusName()).equals(vm.primaryStatusName)
						&& safe(r.clientName()).equals(vm.clientName)
						&& safe(r.opposingPartiesName()).equals(vm.opposingPartiesName)
						&& safe(r.latestCaseUpdate()).equals(vm.latestCaseUpdate)
						&& safe(r.description()).equals(vm.description)
						&& Objects.equals(r.dateOfIncident(), vm.dateOfIncident)
						&& Objects.equals(r.tortClaimsNoticeDeadline(), vm.tortClaimsNoticeDeadline);

				if (same)
					return false;

				loaded.set(i, new CaseCardVm(
						r.id(),
						newName,
						r.intakeDate(),
						r.statuteOfLimitationsDate(),
						r.primaryStatusId(),
						newAtty,
						newColor,
						r.nonEngagementLetterSent(),
						safe(r.primaryStatusName()),
						safe(r.clientName()),
						safe(r.opposingPartiesName()),
						safe(r.latestCaseUpdate()),
						safe(r.description()),
						r.dateOfIncident(),
						r.tortClaimsNoticeDeadline()
				));
				return true;
			}
		}
		return false;
	}
}
