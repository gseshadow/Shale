package com.shale.ui.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

import com.shale.data.dao.OrganizationDao.DirectoryOrganizationRow;
import com.shale.data.dao.OrganizationDao;
import com.shale.ui.component.factory.OrganizationCardFactory;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.navigation.SceneManager;
import com.shale.ui.services.UiRuntimeBridge.EntityUpdatedEvent;
import com.shale.ui.state.AppState;
import com.shale.ui.util.PerfLog;
import com.shale.ui.util.UiStateLabels;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

public final class OrganizationsController {

	private static final Duration SEARCH_DEBOUNCE = Duration.millis(300);
	private static final OrganizationCardFactory.Variant ORGANIZATION_CARD_VARIANT = OrganizationCardFactory.Variant.FULL;

	@FXML
	private TextField organizationsSearchField;
	@FXML
	private javafx.scene.control.Button addOrganizationButton;
	@FXML
	private ScrollPane organizationsScroll;
	@FXML
	private FlowPane organizationsFlow;
	@FXML
	private Label organizationsEmptyStateLabel;
	@FXML
	private Label organizationsLoadingStateLabel;

	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private OrganizationDao organizationDao;
	private OrganizationCardFactory organizationCardFactory;
	private Consumer<Integer> onOpenOrganization;
	private SceneManager sceneManager;
	private Consumer<EntityUpdatedEvent> liveOrganizationUpdatedHandler;
	private boolean liveSubscribed;

	private int currentPage = 0;
	private final int pageSize = 100;
	private boolean loading = false;
	private boolean hasMore = true;
	private volatile int loadGeneration = 0;
	private volatile String latestRequestedQuery = "";
	private PauseTransition searchDebounce;
	private long pageLoadStartedNanos;

	private final List<DirectoryOrganizationRow> loaded = new ArrayList<>();

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "organizations-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(AppState appState, UiRuntimeBridge runtimeBridge, OrganizationDao organizationDao, Consumer<Integer> onOpenOrganization, SceneManager sceneManager) {
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.organizationDao = organizationDao;
		this.onOpenOrganization = onOpenOrganization;
		this.sceneManager = sceneManager;
		this.organizationCardFactory = new OrganizationCardFactory(this::openOrganization);
	}

	@FXML
	private void initialize() {
		if (organizationsSearchField != null) {
			searchDebounce = new PauseTransition(SEARCH_DEBOUNCE);
			searchDebounce.setOnFinished(e -> {
				latestRequestedQuery = normalizedQuery();
				PerfLog.log("organizations.search.debounce", "fired", "queryLength=" + latestRequestedQuery.length() + " nextGeneration=" + (loadGeneration + 1));
				loadFirstPage();
			});
			organizationsSearchField.textProperty().addListener((obs, oldV, newV) -> scheduleSearchReload());
		}
		if (addOrganizationButton != null) {
			addOrganizationButton.setOnAction(e -> onAddOrganization());
		}

		Platform.runLater(() -> {
			if (organizationDao == null) {
				System.out.println("OrganizationsController: organizationDao is null (not injected).");
				updateEmptyState(true);
				return;
			}
			wireInfiniteScroll();
			loadFirstPage();
		});

		if (organizationsFlow != null) {
			organizationsFlow.sceneProperty().addListener((obs, oldScene, newScene) -> {
				if (newScene == null) {
					unsubscribeLiveOrganizationUpdates();
				} else {
					subscribeLiveOrganizationUpdates();
				}
			});
		}

		subscribeLiveOrganizationUpdates();
	}


	private void subscribeLiveOrganizationUpdates() {
		if (runtimeBridge == null || liveSubscribed) {
			return;
		}

		liveOrganizationUpdatedHandler = this::handleLiveOrganizationUpdated;
		runtimeBridge.subscribeEntityUpdated(liveOrganizationUpdatedHandler);
		liveSubscribed = true;
	}

	private void unsubscribeLiveOrganizationUpdates() {
		if (!liveSubscribed || runtimeBridge == null || liveOrganizationUpdatedHandler == null) {
			return;
		}

		runtimeBridge.unsubscribeEntityUpdated(liveOrganizationUpdatedHandler);
		liveSubscribed = false;
	}

	private void handleLiveOrganizationUpdated(EntityUpdatedEvent event) {
		if (event == null || event.entityType() == null) {
			return;
		}
		if (!"Organization".equals(event.entityType())) {
			return;
		}

		String mine = runtimeBridge == null ? "" : runtimeBridge.getClientInstanceId();
		if (!mine.isBlank() && mine.equals(event.clientInstanceId())) {
			return;
		}

		Platform.runLater(this::loadFirstPage);
	}

	private void wireInfiniteScroll() {
		if (organizationsScroll == null) {
			return;
		}
		organizationsScroll.vvalueProperty().addListener((obs, oldV, newV) -> {
			if (newV != null && newV.doubleValue() >= 0.95) {
				loadNextPage();
			}
		});
	}

	private void scheduleSearchReload() {
		latestRequestedQuery = normalizedQuery();
		if (searchDebounce == null) {
			loadFirstPage();
			return;
		}
		PerfLog.log("organizations.search.debounce", "scheduled", "queryLength=" + latestRequestedQuery.length() + " delayMs=" + Math.round(SEARCH_DEBOUNCE.toMillis()) + " currentGeneration=" + loadGeneration);
		searchDebounce.playFromStart();
	}

	private void loadFirstPage() {
		long started = PerfLog.start();
		if (searchDebounce != null) {
			searchDebounce.stop();
		}
		latestRequestedQuery = normalizedQuery();
		loadGeneration++;
		currentPage = 0;
		loading = false;
		hasMore = true;

		loaded.clear();
		if (organizationsFlow != null) {
			organizationsFlow.getChildren().clear();
		}

		updateEmptyState(false);
		PerfLog.log("organizations.page", "load.start", "generation=" + loadGeneration + " queryLength=" + latestRequestedQuery.length());
		loadNextPage();
		PerfLog.logDone("organizations.page", "phase=reset generation=" + loadGeneration, started);
	}

	private void loadNextPage() {
		if (loading || !hasMore) {
			return;
		}
		if (organizationDao == null) {
			return;
		}

		loading = true;
		final int pageToLoad = currentPage;
		final int generationAtSubmit = loadGeneration;
		final String search = normalizedQuery();
		latestRequestedQuery = search;
		final long queryStarted = PerfLog.start();
		if (pageToLoad == 0) { pageLoadStartedNanos = queryStarted; }
		PerfLog.log("organizations.search", "queued", "generation=" + generationAtSubmit + " page=" + pageToLoad + " pageSize=" + pageSize + " queryLength=" + search.length());
		if (loaded.isEmpty()) {
			updateLoadingState(true);
		} else {
			rerender();
		}

		dbExec.submit(() -> {
			try {
				if (generationAtSubmit != loadGeneration || !search.equals(latestRequestedQuery)) {
					PerfLog.logDone("organizations.search", "phase=skipBeforeDao generation=" + generationAtSubmit + " page=" + pageToLoad + " reason=staleQueued latestGeneration=" + loadGeneration, queryStarted);
					Platform.runLater(() -> {
						if (generationAtSubmit == loadGeneration) {
							loading = false;
						}
					});
					return;
				}
				long daoStarted = PerfLog.start();
				PerfLog.log("organizations.search.dao", "start", "generation=" + generationAtSubmit + " page=" + pageToLoad + " queryLength=" + search.length() + " fullDetailHydration=false");
				OrganizationDao.PagedResult<DirectoryOrganizationRow> page = organizationDao.findDirectoryPage(pageToLoad, pageSize, search);
				PerfLog.logDone("organizations.search.dao", "generation=" + generationAtSubmit + " page=" + pageToLoad + " rows=" + page.items().size() + " total=" + page.total() + " fullDetailHydration=false", daoStarted);

				Platform.runLater(() -> {
					if (generationAtSubmit != loadGeneration || !search.equals(normalizedQuery())) {
						loading = false;
						PerfLog.logDone("organizations.search", "phase=discardAfterDao generation=" + generationAtSubmit + " page=" + pageToLoad + " reason=stale latestGeneration=" + loadGeneration, queryStarted);
						return;
					}

					loaded.addAll(page.items());
					currentPage++;
					hasMore = loaded.size() < page.total();
					loading = false;

					rerender();
					PerfLog.logDone("organizations.search", "phase=apply generation=" + generationAtSubmit + " page=" + pageToLoad + " loaded=" + loaded.size() + " hasMore=" + hasMore, queryStarted);
					if (pageToLoad == 0) { PerfLog.logDone("organizations.page", "phase=initialLoad generation=" + generationAtSubmit + " rows=" + loaded.size(), pageLoadStartedNanos); }
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					if (generationAtSubmit != loadGeneration) {
						return;
					}
					loading = false;
					ex.printStackTrace();
					updateEmptyState(loaded.isEmpty());
				});
			}
		});
	}

	private void rerender() {
		long renderStarted = PerfLog.start();
		if (organizationsFlow == null) {
			return;
		}

		List<Node> cards = loaded.stream()
				.map(this::buildCard)
				.toList();

		organizationsFlow.getChildren().setAll(cards);
		updateLoadingState(false);
		updateEmptyState(loaded.isEmpty());
		PerfLog.logDone("organizations.render", "cards=" + cards.size() + " loaded=" + loaded.size() + " loading=" + loading + " fxThread=" + Platform.isFxApplicationThread(), renderStarted);
	}

	private Node buildCard(DirectoryOrganizationRow org) {
		return organizationCardFactory.create(new OrganizationCardFactory.OrganizationCardModel(
				org.id(),
				org.name(),
				org.organizationTypeId(),
				org.organizationTypeName(),
				org.phone(),
				org.email(),
				org.website(),
				null,
				null,
				org.city(),
				org.state(),
				null,
				null,
				null,
				null), ORGANIZATION_CARD_VARIANT);
	}

	private void updateEmptyState(boolean empty) {
		if (empty) {
			UiStateLabels.showEmpty(organizationsEmptyStateLabel);
		} else {
			UiStateLabels.hide(organizationsEmptyStateLabel);
		}
		if (organizationsScroll != null) {
			organizationsScroll.setVisible(!empty);
			organizationsScroll.setManaged(!empty);
		}
	}

	private void updateLoadingState(boolean loadingStateVisible) {
		if (loadingStateVisible) {
			UiStateLabels.showLoading(organizationsLoadingStateLabel);
			UiStateLabels.hide(organizationsEmptyStateLabel);
			if (organizationsScroll != null) {
				organizationsScroll.setVisible(false);
				organizationsScroll.setManaged(false);
			}
		} else {
			UiStateLabels.hide(organizationsLoadingStateLabel);
		}
	}

	private String normalizedQuery() {
		if (organizationsSearchField == null || organizationsSearchField.getText() == null) {
			return "";
		}
		return organizationsSearchField.getText().trim();
	}

	private void onAddOrganization() {
		if (sceneManager == null) {
			return;
		}
		sceneManager.showNewOrganizationDialog(this::handleOrganizationCreated);
	}

	private void handleOrganizationCreated(Integer organizationId) {
		if (organizationId == null || organizationId <= 0) {
			return;
		}
		loadFirstPage();
		publishOrganizationUpdated(organizationId);
		openOrganization(organizationId);
	}

	private void publishOrganizationUpdated(Integer organizationId) {
		if (organizationId == null || organizationId <= 0 || appState == null || runtimeBridge == null
				|| appState.getShaleClientId() == null || appState.getUserId() == null) {
			return;
		}

		try {
			runtimeBridge.publishOrganizationUpdated(organizationId, appState.getShaleClientId(), appState.getUserId());
		} catch (Exception ex) {
			System.out.println("OrganizationUpdated publish skipped: " + ex.getMessage());
		}
	}

	private void openOrganization(Integer organizationId) {
		if (organizationId == null) {
			return;
		}
		if (onOpenOrganization != null) {
			PerfLog.log("organizations.detail.open", "requested", "organizationId=" + organizationId);
			onOpenOrganization.accept(organizationId);
		}
	}
}
