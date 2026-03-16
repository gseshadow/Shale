package com.shale.ui.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

public final class MyShaleController {

	private static final String SORT_NAME = "Name";
	private static final String SORT_INTAKE = "Date of Intake";
	private static final String SORT_SOL = "Statute of Limitations Date";

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

	private CaseDao caseDao;
	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private CaseCardFactory caseCardFactory;
	private Consumer<UiRuntimeBridge.CaseUpdatedEvent> liveCaseUpdatedHandler;
	private boolean liveSubscribed;

	private int currentPage = 0;
	private final int pageSize = 100;
	private boolean loading = false;
	private boolean hasMore = true;
	private int loadGeneration = 0;

	private final List<CaseCardVm> loaded = new ArrayList<>();
	private final Set<Integer> selectedStatusIds = CaseListUiSupport.defaultSelectedStatuses();

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "my-cases-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(AppState appState, UiRuntimeBridge runtimeBridge, CaseDao caseDao, Consumer<Integer> onOpenCase) {
		this.caseDao = caseDao;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.caseCardFactory = new CaseCardFactory(onOpenCase);
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

		CaseListUiSupport.initializeStatusFilterMenu(myCasesStatusFilterMenuButton, selectedStatusIds, this::rerender);

		Platform.runLater(() -> {
			wireInfiniteScroll();
			loadFirstPage();
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

		System.out.println("[DEBUG LIVE][MY_CASES] event accepted -> scheduling refresh");
		runOnFx(() -> {
			System.out.println("[DEBUG LIVE][MY_CASES] executing refresh on FX thread");
			loadFirstPage();
		});
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
						.map(r -> new CaseCardVm(
								r.id(),
								safe(r.name()),
								r.intakeDate(),
								r.statuteOfLimitationsDate(),
								r.primaryStatusId(),
								safe(r.responsibleAttorneyName()),
								safe(r.responsibleAttorneyColor())))
						.toList();

				Platform.runLater(() -> {
					if (generationAtSubmit != loadGeneration) {
						loading = false;
						return;
					}
					loaded.addAll(newItems);
					System.out.println("[DEBUG LIVE][MY_CASES] page loaded page=" + pageToLoad + " items=" + newItems.size() + " total=" + page.total());
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
	}
}
