package com.shale.ui.controller;

import com.shale.core.model.Organization;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.ContactDao;
import com.shale.data.dao.UserDao;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.component.factory.ContactCardFactory;
import com.shale.ui.component.factory.ContactCardFactory.ContactCardModel;
import com.shale.ui.component.factory.OrganizationCardFactory;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.services.CaseDetailService;
import com.shale.ui.services.SearchService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class SearchController {

	private static final double CASE_CARD_WIDTH = 300;
	private static final double CONTACT_CARD_WIDTH = 340;
	private static final double ORGANIZATION_CARD_WIDTH = 340;
	private static final double USER_CARD_WIDTH = 280;

	@FXML
	private Label searchSummaryLabel;
	@FXML
	private Label searchLoadingLabel;
	@FXML
	private FlowPane casesFlow;
	@FXML
	private Label casesEmptyLabel;
	@FXML
	private VBox deletedCasesSection;
	@FXML
	private FlowPane deletedCasesFlow;
	@FXML
	private Label deletedCasesEmptyLabel;
	@FXML
	private FlowPane contactsFlow;
	@FXML
	private Label contactsEmptyLabel;
	@FXML
	private FlowPane organizationsFlow;
	@FXML
	private Label organizationsEmptyLabel;
	@FXML
	private FlowPane usersFlow;
	@FXML
	private Label usersEmptyLabel;

	private AppState appState;
	private SearchService searchService;
	private CaseDetailService caseDetailService;
	private UiRuntimeBridge runtimeBridge;
	private String query = "";
	private CaseCardFactory caseCardFactory;
	private ContactCardFactory contactCardFactory;
	private OrganizationCardFactory organizationCardFactory;
	private UserCardFactory userCardFactory;
	private int loadGeneration = 0;
	private Consumer<UiRuntimeBridge.CaseUpdatedEvent> liveCaseUpdatedHandler;
	private boolean liveSubscribed;

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "global-search-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(AppState appState,
			SearchService searchService,
			CaseDetailService caseDetailService,
			UiRuntimeBridge runtimeBridge,
			String query,
			Consumer<Integer> onOpenCase,
			Consumer<Integer> onOpenContact,
			Consumer<Integer> onOpenOrganization,
			Consumer<Integer> onOpenUser) {
		this.appState = appState;
		this.searchService = searchService;
		this.caseDetailService = caseDetailService;
		this.runtimeBridge = runtimeBridge;
		this.query = query == null ? "" : query.trim();
		this.caseCardFactory = new CaseCardFactory(onOpenCase == null ? id -> {
		} : onOpenCase);
		this.contactCardFactory = new ContactCardFactory(onOpenContact == null ? id -> {
		} : onOpenContact);
		this.organizationCardFactory = new OrganizationCardFactory(onOpenOrganization == null ? id -> {
		} : onOpenOrganization);
		this.userCardFactory = new UserCardFactory(onOpenUser == null ? id -> {
		} : onOpenUser);
	}

	@FXML
	private void initialize() {
		configureFlow(casesFlow, 16, 16, 1040);
		configureFlow(deletedCasesFlow, 16, 16, 1040);
		configureFlow(contactsFlow, 16, 16, 1040);
		configureFlow(organizationsFlow, 16, 16, 1040);
		configureFlow(usersFlow, 16, 16, 1040);
		Platform.runLater(this::loadResults);
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

	private void subscribeLiveCaseUpdates() {
		if (runtimeBridge == null || liveSubscribed) {
			return;
		}
		liveCaseUpdatedHandler = event -> {
			String mine = runtimeBridge == null ? "" : runtimeBridge.getClientInstanceId();
			if (!mine.isBlank() && mine.equals(event.clientInstanceId())) {
				return;
			}
			Platform.runLater(this::loadResults);
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

	private void loadResults() {
		loadGeneration++;
		final int generationAtSubmit = loadGeneration;
		String trimmedQuery = query == null ? "" : query.trim();

		if (searchSummaryLabel != null) {
			searchSummaryLabel.setText(trimmedQuery.isBlank() ? "Enter a search term to see results." : "Showing results for \"" + trimmedQuery + "\".");
		}
		if (trimmedQuery.isBlank()) {
			showResults(SearchService.SearchResults.empty(""));
			updateLoadingState(false);
			return;
		}

		Integer tenantId = appState == null ? null : appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0) {
			showResults(SearchService.SearchResults.empty(trimmedQuery));
			if (searchSummaryLabel != null) {
				searchSummaryLabel.setText("Search is unavailable because no tenant is selected.");
			}
			updateLoadingState(false);
			return;
		}

		updateLoadingState(true);
		dbExec.submit(() -> {
			try {
				SearchService.SearchResults results = searchService.searchAll(tenantId, trimmedQuery, canViewDeletedCasesInSearch());
				Platform.runLater(() -> {
					if (generationAtSubmit != loadGeneration) {
						return;
					}
					showResults(results);
					updateLoadingState(false);
				});
			} catch (RuntimeException ex) {
				Platform.runLater(() -> {
					if (generationAtSubmit != loadGeneration) {
						return;
					}
					showResults(SearchService.SearchResults.empty(trimmedQuery));
					if (searchSummaryLabel != null) {
						searchSummaryLabel.setText("Unable to load search results right now.");
					}
					updateLoadingState(false);
				});
			}
		});
	}

	private void showResults(SearchService.SearchResults results) {
		renderCases(results.cases());
		renderDeletedCases(results.deletedCases());
		renderContacts(results.contacts());
		renderOrganizations(results.organizations());
		renderUsers(results.users());
	}

	private void renderDeletedCases(List<CaseDao.CaseRow> deletedCases) {
		boolean authorized = canViewDeletedCasesInSearch();
		if (deletedCasesSection != null) {
			deletedCasesSection.setVisible(authorized);
			deletedCasesSection.setManaged(authorized);
		}
		if (!authorized || deletedCasesFlow == null) {
			return;
		}
		List<Node> cards = new ArrayList<>(deletedCases.size());
		for (CaseDao.CaseRow row : deletedCases) {
			Node card = caseCardFactory.create(new CaseCardModel(
					row.id(),
					row.name(),
					row.intakeDate(),
					row.statuteOfLimitationsDate(),
					row.responsibleAttorneyName(),
					row.responsibleAttorneyColor()));
			if (card instanceof Region region) {
				region.setPrefWidth(CASE_CARD_WIDTH);
				region.setMaxWidth(CASE_CARD_WIDTH);
			}
			var restoreButton = new javafx.scene.control.Button("Restore Case");
			restoreButton.getStyleClass().add("button-secondary");
			restoreButton.setOnAction(e -> onRestoreCase(row));
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			HBox actions = new HBox(8, spacer, restoreButton);
			VBox container = new VBox(6, card, actions);
			container.setPrefWidth(CASE_CARD_WIDTH);
			container.setMaxWidth(CASE_CARD_WIDTH);
			cards.add(container);
		}
		deletedCasesFlow.getChildren().setAll(cards);
		updateSectionState(deletedCasesFlow, deletedCasesEmptyLabel, cards.isEmpty());
	}

	private void renderCases(List<CaseDao.CaseRow> cases) {
		if (casesFlow == null) {
			return;
		}
		List<Node> cards = new ArrayList<>(cases.size());
		for (CaseDao.CaseRow row : cases) {
			Node card = caseCardFactory.create(new CaseCardModel(
					row.id(),
					row.name(),
					row.intakeDate(),
					row.statuteOfLimitationsDate(),
					row.responsibleAttorneyName(),
					row.responsibleAttorneyColor()));
			if (card instanceof Region region) {
				region.setPrefWidth(CASE_CARD_WIDTH);
				region.setMaxWidth(CASE_CARD_WIDTH);
			}
			cards.add(card);
		}
		casesFlow.getChildren().setAll(cards);
		updateSectionState(casesFlow, casesEmptyLabel, cards.isEmpty());
	}

	private void renderContacts(List<ContactDao.DirectoryContactRow> contacts) {
		if (contactsFlow == null) {
			return;
		}
		List<Node> cards = contacts.stream()
				.map(row -> {
					var card = contactCardFactory.create(new ContactCardModel(
							row.id(),
							row.displayName(),
							null,
							row.email(),
							row.phone()), ContactCardFactory.Variant.FULL);
					card.setPrefWidth(CONTACT_CARD_WIDTH);
					card.setMaxWidth(CONTACT_CARD_WIDTH);
					return (Node) card;
				})
				.toList();
		contactsFlow.getChildren().setAll(cards);
		updateSectionState(contactsFlow, contactsEmptyLabel, cards.isEmpty());
	}

	private void renderOrganizations(List<Organization> organizations) {
		if (organizationsFlow == null) {
			return;
		}
		List<Node> cards = organizations.stream()
				.map(organization -> {
					var card = organizationCardFactory.create(organization, OrganizationCardFactory.Variant.FULL);
					card.setPrefWidth(ORGANIZATION_CARD_WIDTH);
					card.setMaxWidth(ORGANIZATION_CARD_WIDTH);
					return (Node) card;
				})
				.toList();
		organizationsFlow.getChildren().setAll(cards);
		updateSectionState(organizationsFlow, organizationsEmptyLabel, cards.isEmpty());
	}

	private void renderUsers(List<UserDao.DirectoryUserRow> users) {
		if (usersFlow == null) {
			return;
		}
		List<Node> cards = users.stream()
				.map(row -> {
					var card = userCardFactory.create(new UserCardModel(
							row.id(),
							row.displayName(),
							row.color(),
							row.initials()), UserCardFactory.Variant.FULL);
					card.setPrefWidth(USER_CARD_WIDTH);
					card.setMaxWidth(USER_CARD_WIDTH);
					return (Node) card;
				})
				.toList();
		usersFlow.getChildren().setAll(cards);
		updateSectionState(usersFlow, usersEmptyLabel, cards.isEmpty());
	}

	private void onRestoreCase(CaseDao.CaseRow row) {
		if (row == null || caseDetailService == null) {
			return;
		}
		if (!canRestoreDeletedCases()) {
			AppDialogs.showError(dialogOwner(), "Restore Case", "Only admin and attorney users can restore cases.");
			return;
		}
		Integer tenantId = appState == null ? null : appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0) {
			AppDialogs.showError(dialogOwner(), "Restore Case", "Restore is unavailable because no tenant is selected.");
			return;
		}
		String caseName = row.name() == null || row.name().isBlank() ? "This case" : "\"" + row.name() + "\"";
		boolean confirmed = AppDialogs.showConfirmation(
				dialogOwner(),
				"Restore Case",
				"Restore this case?",
				caseName + " will return to normal active views.",
				"Restore Case",
				AppDialogs.DialogActionKind.PRIMARY);
		if (!confirmed) {
			return;
		}
		dbExec.submit(() -> {
			try {
				boolean restored = caseDetailService.restoreCase(row.id(), tenantId);
				Platform.runLater(() -> {
					if (!restored) {
						AppDialogs.showError(dialogOwner(), "Restore Case", "Case could not be restored.");
						return;
					}
					publishDeletedStateUpdate(row.id(), 0);
					loadResults();
				});
			} catch (RuntimeException ex) {
				Platform.runLater(() -> AppDialogs.showError(dialogOwner(), "Restore Case", "Failed to restore case."));
			}
		});
	}

	private boolean canRestoreDeletedCases() {
		return caseDetailService != null && caseDetailService.canRestoreCase();
	}

	private boolean canViewDeletedCasesInSearch() {
		return caseDetailService != null && caseDetailService.canViewDeletedCasesInSearch();
	}

	private void publishDeletedStateUpdate(long caseId, int deletedValue) {
		if (runtimeBridge == null || appState == null || appState.getShaleClientId() == null || appState.getUserId() == null) {
			return;
		}
		runtimeBridge.publishEntityFieldUpdated("Case", caseId, appState.getShaleClientId(), appState.getUserId(), "deleted", deletedValue);
	}

	private javafx.stage.Window dialogOwner() {
		if (searchSummaryLabel != null && searchSummaryLabel.getScene() != null) {
			return searchSummaryLabel.getScene().getWindow();
		}
		return null;
	}

	private void updateSectionState(FlowPane flowPane, Label emptyLabel, boolean empty) {
		if (flowPane != null) {
			flowPane.setVisible(!empty);
			flowPane.setManaged(!empty);
		}
		if (emptyLabel != null) {
			emptyLabel.setVisible(empty);
			emptyLabel.setManaged(empty);
		}
	}

	private void updateLoadingState(boolean loading) {
		if (searchLoadingLabel != null) {
			searchLoadingLabel.setVisible(loading);
			searchLoadingLabel.setManaged(loading);
		}
	}

	private static void configureFlow(FlowPane flowPane, double hgap, double vgap, double wrapLength) {
		if (flowPane == null) {
			return;
		}
		flowPane.setHgap(hgap);
		flowPane.setVgap(vgap);
		flowPane.setPrefWrapLength(wrapLength);
	}
}
