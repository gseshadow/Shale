package com.shale.ui.controller;

import com.shale.ui.navigation.SceneManager;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.UiUpdateLauncher;
import com.shale.ui.state.AppState;
import com.shale.ui.util.NavButtonStyler;

import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

public final class MainController {

	// Top bar
	@FXML
	private Label userEmailLabel;

	@FXML
	private TextField globalSearchField;

	@FXML
	private Button globalSearchButton;

	@FXML
	private Button newIntakeButton;

	@FXML
	private Button updateButton;

	// Sidebar nav buttons
	@FXML
	private Button navMyShaleButton;

	@FXML
	private Button navCasesButton;

	@FXML
	private Button navContactsButton;

	@FXML
	private Button navOrganizationsButton;

	@FXML
	private Button navTeamButton;

	@FXML
	private Button navSettingsButton;

	@FXML
	private Button logoutButton;

	// Content shell
	@FXML
	private Label sectionTitleLabel;

	@FXML
	private Label sectionSubtitleLabel;

	@FXML
	private StackPane sectionContent;

	@FXML
	private Label placeholderLabel;

	private SceneManager sceneManager;
	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private UiUpdateLauncher updateLauncher;

	public MainController() {
		System.out.println("MainController()");// TODO remove
	}

	// Injected by SceneManager
	public void init(SceneManager sceneManager,
			AppState appState,
			UiRuntimeBridge runtimeBridge) {
		System.out.println("MainController.init()");// TODO remove
		this.sceneManager = sceneManager;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
	}

	@FXML
	private void initialize() {
		System.out.println("MainController.initialize()");// TODO remove
		styleNavigationButtons();
		highlightNav(navMyShaleButton);

		if (globalSearchField != null) {
			globalSearchField.setOnAction(e -> onGlobalSearch());
		}

		if (appState != null) {
			String email = appState.getUserEmail();
			if (email != null && !email.isBlank() && userEmailLabel != null) {
				userEmailLabel.setText(email);
			}
		}

		showMyShale();
	}

	// === Global search (shell-level for now) ===

	@FXML
	private void onGlobalSearch() {
		String query = globalSearchField == null || globalSearchField.getText() == null
				? ""
				: globalSearchField.getText().trim();
		if (query.isEmpty()) {
			return;
		}

		showSearchResults(query);
	}

	// === Navigation handlers ===

	@FXML
	private void onNavMyShale() {
		highlightNav(navMyShaleButton);
		sectionTitleLabel.setText("My Shale");
		sectionSubtitleLabel.setText("Overview of your tasks, assigned cases, and recent activity.");
		showMyShale();
	}

	@FXML
	private void onNavCases() {
		showCasesList();
	}

	@FXML
	private void onNavContacts() {
		showContactsList();
	}

	@FXML
	private void onNavOrganizations() {
		highlightNav(navOrganizationsButton);
		sectionTitleLabel.setText("Organizations");
		sectionSubtitleLabel.setText("Browse, search, and manage organizations.");

		Node organizationsRoot = sceneManager.createOrganizationsView(this::openOrganization);
		sectionContent.getChildren().setAll(organizationsRoot);
	}

	@FXML
	private void onNavTeam() {
		highlightNav(navTeamButton);
		sectionTitleLabel.setText("Team");
		sectionSubtitleLabel.setText("See and manage your team members.");

		Node teamRoot = sceneManager.createTeamView(sceneManager::openUserProfile);
		sectionContent.getChildren().setAll(teamRoot);
	}

	@FXML
	private void onNavSettings() {
		highlightNav(navSettingsButton);
		sectionTitleLabel.setText("Settings");
		sectionSubtitleLabel.setText("Configure Shale preferences and system settings.");
		setSectionContentText("Settings tab is not implemented yet.");
	}

	@FXML
	private void onNewIntake() {
		sceneManager.showNewIntakeDialog(this::openCase);
	}

	@FXML
	private void onUpdate() {
		if (updateLauncher != null) {
			try {
				updateLauncher.launchUpdater();
			} catch (RuntimeException ex) {
				sceneManager.showError(ex.getMessage());
			}
		} else {
			System.out.println("Update launcher not configured.");
		}
	}

	@FXML
	private void onLogout() {
		System.out.println("MainController.onLogout()");// TODO remove

		runtimeBridge.onLogout();

		if (appState != null) {
			appState.setUserId(0);
			appState.setShaleClientId(0);
			appState.setUserEmail(null);
			appState.setAdmin(false);
			appState.setAttorney(false);
		}

		sceneManager.showLogin();
	}

	// === Helpers ===
	public void openCase(int caseId) {
		highlightNav(navCasesButton);
		sectionTitleLabel.setText("Case");
		sectionSubtitleLabel.setText("Case #" + caseId);

		Node caseRoot = sceneManager.createCaseView(caseId, this::openOrganization, this::showCasesList);
		sectionContent.getChildren().setAll(caseRoot);
	}


	public void openOrganization(int organizationId) {
		highlightNav(navOrganizationsButton);
		sectionTitleLabel.setText("Organization");
		sectionSubtitleLabel.setText("Organization #" + organizationId);

		Node organizationRoot = sceneManager.createOrganizationView(organizationId, this::openCase, this::showOrganizationsList);
		sectionContent.getChildren().setAll(organizationRoot);
	}

	public void openUser(int userId) {
		sceneManager.openUserProfile(userId);
	}

	public void showUserView(int userId, Node userRoot) {
		highlightNav(navTeamButton);
		sectionTitleLabel.setText("User");
		sectionSubtitleLabel.setText("User #" + userId);
		sectionContent.getChildren().setAll(userRoot);
	}

	public void openContact(int contactId) {
		highlightNav(navContactsButton);
		sectionTitleLabel.setText("Contact");
		sectionSubtitleLabel.setText("Contact #" + contactId);

		Node contactRoot = sceneManager.createContactView(contactId, this::openCase, this::showContactsList);
		sectionContent.getChildren().setAll(contactRoot);
	}

	public void showContactView(int contactId, Node contactRoot) {
		highlightNav(navContactsButton);
		sectionTitleLabel.setText("Contact");
		sectionSubtitleLabel.setText("Contact #" + contactId);
		sectionContent.getChildren().setAll(contactRoot);
	}

	private void showCasesList() {
		highlightNav(navCasesButton);
		sectionTitleLabel.setText("Cases");
		sectionSubtitleLabel.setText("Browse, search, and manage cases.");

		Node casesRoot = sceneManager.createCasesView(this::openCase);
		sectionContent.getChildren().setAll(casesRoot);
	}

	private void showContactsList() {
		highlightNav(navContactsButton);
		sectionTitleLabel.setText("Contacts");
		sectionSubtitleLabel.setText("Manage clients, experts, and other contacts.");

		Node contactsRoot = sceneManager.createContactsView(this::openContact);
		sectionContent.getChildren().setAll(contactsRoot);
	}

	public void showContactsListView() {
		showContactsList();
	}

	private void showOrganizationsList() {
		highlightNav(navOrganizationsButton);
		sectionTitleLabel.setText("Organizations");
		sectionSubtitleLabel.setText("Browse, search, and manage organizations.");

		Node organizationsRoot = sceneManager.createOrganizationsView(this::openOrganization);
		sectionContent.getChildren().setAll(organizationsRoot);
	}

	private void showSearchResults(String query) {
		highlightNav(null);
		sectionTitleLabel.setText("Search");
		sectionSubtitleLabel.setText("Results for: \"" + query + "\"");

		Node searchRoot = sceneManager.createSearchView(query, this::openCase, this::openContact, this::openOrganization, sceneManager::openUserProfile);
		sectionContent.getChildren().setAll(searchRoot);
	}

	private void showMyShale() {
		Node myShaleRoot = sceneManager.createMyShaleView(this::openCase, sceneManager::openUserProfile);
		sectionContent.getChildren().setAll(myShaleRoot);
	}

	private void styleNavigationButtons() {
		for (Button button : getNavigationButtons()) {
			NavButtonStyler.applyBaseStyle(button);
		}
	}

	private List<Button> getNavigationButtons() {
		return List.of(
				navMyShaleButton,
				navCasesButton,
				navContactsButton,
				navOrganizationsButton,
				navTeamButton,
				navSettingsButton
		);
	}

	private void setSectionContentText(String text) {
		if (placeholderLabel == null) {
			placeholderLabel = new Label();
			sectionContent.getChildren().setAll(placeholderLabel);
		}
		placeholderLabel.setText(text);
		sectionContent.getChildren().setAll(placeholderLabel);
	}

	private void highlightNav(Button active) {
		if (navMyShaleButton == null)
			return;

		navMyShaleButton.setDisable(false);
		navCasesButton.setDisable(false);
		navContactsButton.setDisable(false);
		navOrganizationsButton.setDisable(false);
		navTeamButton.setDisable(false);
		navSettingsButton.setDisable(false);

		NavButtonStyler.setActive(active, getNavigationButtons());
	}

	public void setUpdateLauncher(UiUpdateLauncher updateLauncher) {
		this.updateLauncher = updateLauncher;
	}
}
