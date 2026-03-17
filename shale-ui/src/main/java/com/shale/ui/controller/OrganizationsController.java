package com.shale.ui.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.shale.core.model.Organization;
import com.shale.data.dao.OrganizationDao;
import com.shale.ui.component.factory.OrganizationCardFactory;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.UiRuntimeBridge.EntityUpdatedEvent;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

public final class OrganizationsController {

	@FXML
	private TextField organizationsSearchField;
	@FXML
	private ScrollPane organizationsScroll;
	@FXML
	private FlowPane organizationsFlow;
	@FXML
	private Label organizationsEmptyStateLabel;

	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private OrganizationDao organizationDao;
	private OrganizationCardFactory organizationCardFactory;
	private Consumer<Integer> onOpenOrganization;
	private Consumer<EntityUpdatedEvent> liveOrganizationUpdatedHandler;
	private boolean liveSubscribed;

	private int currentPage = 0;
	private final int pageSize = 100;
	private boolean loading = false;
	private boolean hasMore = true;
	private int loadGeneration = 0;

	private final List<Organization> loaded = new ArrayList<>();

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "organizations-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(AppState appState, UiRuntimeBridge runtimeBridge, OrganizationDao organizationDao, Consumer<Integer> onOpenOrganization) {
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.organizationDao = organizationDao;
		this.onOpenOrganization = onOpenOrganization;
		this.organizationCardFactory = new OrganizationCardFactory(this::openOrganization);
	}

	@FXML
	private void initialize() {
		if (organizationsSearchField != null) {
			organizationsSearchField.textProperty().addListener((obs, oldV, newV) -> loadFirstPage());
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

	private void loadFirstPage() {
		loadGeneration++;
		currentPage = 0;
		loading = false;
		hasMore = true;

		loaded.clear();
		if (organizationsFlow != null) {
			organizationsFlow.getChildren().clear();
		}

		updateEmptyState(false);
		loadNextPage();
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

		dbExec.submit(() -> {
			try {
				OrganizationDao.PagedResult<Organization> page = organizationDao.findPage(pageToLoad, pageSize, search);

				Platform.runLater(() -> {
					if (generationAtSubmit != loadGeneration) {
						loading = false;
						return;
					}

					loaded.addAll(page.items());
					currentPage++;
					hasMore = loaded.size() < page.total();
					loading = false;

					rerender();
					System.out.println("Loaded organizations page " + pageToLoad + ": " + page.items().size()
							+ " (loaded=" + loaded.size() + " / total=" + page.total() + ")");
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
		if (organizationsFlow == null) {
			return;
		}

		List<Node> cards = loaded.stream()
				.map(this::buildCard)
				.toList();

		organizationsFlow.getChildren().setAll(cards);
		updateEmptyState(cards.isEmpty());
	}

	private Node buildCard(Organization org) {
		return organizationCardFactory.create(org, OrganizationCardFactory.Variant.FULL);
	}

	private void updateEmptyState(boolean empty) {
		if (organizationsEmptyStateLabel != null) {
			organizationsEmptyStateLabel.setVisible(empty);
			organizationsEmptyStateLabel.setManaged(empty);
		}
		if (organizationsScroll != null) {
			organizationsScroll.setVisible(!empty);
			organizationsScroll.setManaged(!empty);
		}
	}

	private String normalizedQuery() {
		if (organizationsSearchField == null || organizationsSearchField.getText() == null) {
			return "";
		}
		return organizationsSearchField.getText().trim();
	}

	private void openOrganization(Integer organizationId) {
		if (organizationId == null) {
			return;
		}
		if (onOpenOrganization != null) {
			onOpenOrganization.accept(organizationId);
		}
	}
}
