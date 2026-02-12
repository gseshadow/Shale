package com.shale.ui.controller;

import com.shale.data.dao.CaseDao;
import com.shale.data.dao.CaseDao.CaseSort;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private CaseDao caseDao;

	// Paging state
	private int currentPage = 0;
	private final int pageSize = 100;
	private boolean loading = false;
	private boolean hasMore = true;
	private int loadGeneration = 0;

	// Loaded items (we keep these so search/sort can re-render)
	private final List<CaseCardVm> loaded = new ArrayList<>();

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

	public void init(AppState appState, UiRuntimeBridge runtimeBridge, CaseDao caseDao) {
		System.out.println("CasesController.init()");
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.caseDao = caseDao;
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
		VBox card = new VBox(6);
		card.setPadding(new Insets(10));
		card.setPrefWidth(280);

		String backgroundColor = toCssBackgroundColor(vm.responsibleAttorneyColor);
		card.setStyle("""
					-fx-background-color: %s;
					-fx-background-radius: 14;
					-fx-border-radius: 14;
					-fx-border-color: #e5e5e5;
					-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0.2, 0, 2);
				""".formatted(backgroundColor));

		Label title = new Label(vm.name.isBlank() ? "(no name)" : vm.name);
		title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

		Label atty = new Label(vm.responsibleAttorney.isBlank() ? "" : vm.responsibleAttorney);
		atty.setStyle("-fx-font-size: 12px; -fx-opacity: 0.75;");

		HBox dates = new HBox(10);
		Label intake = new Label("Intake: " + (vm.intakeDate == null ? "" : vm.intakeDate.toString()));
		intake.setStyle("-fx-font-size: 12px;");

		Label sol = new Label("SOL: " + (vm.solDate == null ? "" : vm.solDate.toString()));
		sol.setStyle("-fx-font-size: 12px;");

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		dates.getChildren().addAll(intake, spacer, sol);

		card.getChildren().addAll(title, atty, dates);

		// Click handler placeholder (wire to your selection / navigation later)
		card.setOnMouseClicked(e ->
		{
			// Example: System.out.println("Clicked case id=" + vm.id);
		});

		return card;
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	private static String toCssBackgroundColor(String argbHex) {
		if (argbHex == null)
			return "white";

		String normalized = argbHex.trim();
		if (normalized.isEmpty()) {
			return "white";
		}
		if (normalized.startsWith("#")) {
			normalized = normalized.substring(1);
		}
		if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
			normalized = normalized.substring(2);
		}

		if (!normalized.matches("(?i)[0-9a-f]{8}")) {
			return "white";
		}

		// Stored values are RRGGBBAA (for example 0xe6994dff should render orange).
		// JavaFX accepts the same #RRGGBBAA layout directly.
		String rr = normalized.substring(0, 2);
		String gg = normalized.substring(2, 4);
		String bb = normalized.substring(4, 6);
		String aa = normalized.substring(6, 8);
		return "#" + rr + gg + bb + aa;
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
