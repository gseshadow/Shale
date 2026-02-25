package com.shale.ui.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.shale.data.dao.CaseDao;
import com.shale.data.dao.CaseDao.CaseSort;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

public final class CasesController {

	// Existing controls (keep these IDs in FXML)
	@FXML
	private TextField casesSearchField;
	@FXML
	private ChoiceBox<String> casesSortChoice;
	@FXML
	private CheckBox includeClosedDeniedCheckBox;

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

	// Loaded items (we keep these so search/sort can re-render)
	private final List<CaseCardVm> loaded = new ArrayList<>();

	private CaseCardFactory caseCardFactory;

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
					"Responsible attorney (Z–A)"
			);
			casesSortChoice.getSelectionModel().select(0);
			casesSortChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> loadFirstPage());
		}

		// search filter
		if (casesSearchField != null) {
			casesSearchField.textProperty().addListener((obs, oldV, newV) -> rerender());
		}

		if (includeClosedDeniedCheckBox != null) {
			includeClosedDeniedCheckBox.setSelected(false);
			includeClosedDeniedCheckBox.selectedProperty().addListener((obs, oldV, newV) -> loadFirstPage());
		}

		Platform.runLater(() ->
		{
			if (caseDao == null) {
				System.out.println("CasesController: caseDao is null (not injected).");
				return;
			}
			wireInfiniteScroll();
			loadFirstPage();
		});
		subscribeLiveCaseUpdates();
	}

	private void subscribeLiveCaseUpdates() {
		if (runtimeBridge == null) {
			return;
		}

		runtimeBridge.subscribeCaseUpdated(event ->
		{
			String mine = runtimeBridge == null ? "" : runtimeBridge.getClientInstanceId();

			System.out.println("[DEBUG LIVE] CASES listenerUserId=" + (appState == null ? null : appState.getUserId())
					+ " event.updatedByUserId=" + event.updatedByUserId()
					+ " caseId=" + event.caseId()
					+ " newName=" + (event.newName() != null)
					+ " patchLen=" + (event.rawPatchJson() == null ? 0 : event.rawPatchJson().length())
					+ " mineInstance=" + mine
					+ " eventInstance=" + event.clientInstanceId());

			if (!mine.isBlank() && mine.equals(event.clientInstanceId())) {
				return; // ignore only my own echo
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

			// 2) Patch-based updates (generic)
			String rawPatch = event.rawPatchJson();
			if (rawPatch == null || rawPatch.isBlank()) {
				return;
			}

			// Decide which fields the CASE LIST cares about.
			// For now: only name. Later you can add "responsibleAttorney",
			// "responsibleAttorneyColor", etc.
			String patchedName = extractPatchString(rawPatch, "name");

			if (patchedName == null) {
				// Not an error anymore; just irrelevant to this list.
				System.out.println("[LIVE] Case update patch had no list-relevant fields; skipping list update for caseId=" + event.caseId());
				return;
			}

			runOnFx(() ->
			{
				boolean changed = applyCasePatchToList(event.caseId(), "name", patchedName);
				if (changed)
					rerender();
			});
		});
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
					loaded.set(i, new CaseCardVm(vm.id, safeVal, vm.intakeDate, vm.solDate, vm.responsibleAttorney, vm.responsibleAttorneyColor));
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
				loaded.set(i, new CaseCardVm(vm.id, safeName, vm.intakeDate, vm.solDate, vm.responsibleAttorney, vm.responsibleAttorneyColor));
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
			if (newV != null && newV.doubleValue() >= 0.95 && !isSearchActive()) {
				loadNextPage();
			}
		});
	}

	private void loadFirstPage() {
		loadGeneration++;
		currentPage = 0;
		loading = false;
		hasMore = true;

		loaded.clear();
		if (casesFlow != null)
			casesFlow.getChildren().clear();

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

		dbExec.submit(() ->
		{
			try {
				var page = caseDao.findPage(pageToLoad, pageSize, selectedSort(), includeClosedDenied());

				// map DAO rows into UI VM
				List<CaseCardVm> newItems = page.items().stream()
						.map(r -> new CaseCardVm(
								r.id(),
								safe(r.name()),
								r.intakeDate(),
								r.statuteOfLimitationsDate(),
								safe(r.responsibleAttorneyName()),
								safe(r.responsibleAttorneyColor())
						))
						.toList();

				Platform.runLater(() ->
				{
					if (generationAtSubmit != loadGeneration) {
						loading = false;
						return;
					}

					loaded.addAll(newItems);

					currentPage++;
					hasMore = loaded.size() < page.total();
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

	private void rerender() {
		if (casesFlow == null)
			return;

		String q = normalizedSearchQuery();
		String sort = casesSortChoice == null ? "Intake date (newest first)" : casesSortChoice.getValue();

		Comparator<CaseCardVm> comp = comparatorFor(sort);

		List<CaseCardVm> filtered = loaded.stream()
				.filter(vm -> matchesQuery(vm, q))
				.sorted(comp)
				.toList();

		if (!q.isEmpty() && filtered.size() < pageSize && hasMore && !loading) {
			loadNextPage();
		}

		List<CaseCardVm> view = q.isEmpty() ? filtered : filtered.stream().limit(pageSize).toList();

		casesFlow.getChildren().setAll(view.stream().map(this::buildCaseCard).toList());
	}

	private boolean includeClosedDenied() {
		return includeClosedDeniedCheckBox != null && includeClosedDeniedCheckBox.isSelected();
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
				vm.responsibleAttorneyColor
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
		final String responsibleAttorney;
		final String responsibleAttorneyColor;

		CaseCardVm(long id, String name, LocalDate intakeDate, LocalDate solDate, String responsibleAttorney,
				String responsibleAttorneyColor) {
			this.id = id;
			this.name = Objects.requireNonNullElse(name, "");
			this.intakeDate = intakeDate;
			this.solDate = solDate;
			this.responsibleAttorney = Objects.requireNonNullElse(responsibleAttorney, "");
			this.responsibleAttorneyColor = Objects.requireNonNullElse(responsibleAttorneyColor, "");
		}
	}
}
