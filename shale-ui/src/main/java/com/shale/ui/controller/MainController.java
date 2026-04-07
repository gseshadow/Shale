package com.shale.ui.controller;

import com.shale.ui.component.dialog.NotificationCenterDialog;
import com.shale.ui.navigation.SceneManager;
import com.shale.ui.notification.AppNotification;
import com.shale.ui.notification.NotificationCenterService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.UiUpdateLauncher;
import com.shale.ui.state.AppState;
import com.shale.ui.util.NavButtonStyler;

import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
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
	private Button backButton;

	@FXML
	private Button updateButton;

	@FXML
	private HBox notificationBannerHost;

	@FXML
	private Label notificationBannerLabel;

	@FXML
	private Button notificationBellButton;

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
	private NotificationCenterService notificationCenterService;

	public MainController() {
		System.out.println("MainController()");// TODO remove
	}

	// Injected by SceneManager
	public void init(SceneManager sceneManager,
			AppState appState,
			UiRuntimeBridge runtimeBridge,
			NotificationCenterService notificationCenterService) {
		System.out.println("MainController.init()");// TODO remove
		this.sceneManager = sceneManager;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.notificationCenterService = notificationCenterService;
		refreshSessionLabel();
		bindNotificationShell();
	}

	@FXML
	private void initialize() {
		System.out.println("MainController.initialize()");// TODO remove
		styleNavigationButtons();
		highlightNav(navMyShaleButton);

		if (globalSearchField != null) {
			globalSearchField.setOnAction(e -> onGlobalSearch());
		}

		refreshSessionLabel();
		bindNotificationShell();
	}

	@FXML
	private void onGlobalSearch() {
		String query = globalSearchField == null || globalSearchField.getText() == null
				? ""
				: globalSearchField.getText().trim();
		if (query.isEmpty()) {
			return;
		}
		sceneManager.openSearchView(query);
	}

	@FXML
	private void onNavMyShale() {
		sceneManager.openMyShaleView();
	}

	@FXML
	private void onNavCases() {
		sceneManager.openCasesListView();
	}

	@FXML
	private void onNavContacts() {
		sceneManager.openContactsListView();
	}

	@FXML
	private void onNavOrganizations() {
		sceneManager.openOrganizationsListView();
	}

	@FXML
	private void onNavTeam() {
		sceneManager.openTeamListView();
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
		sceneManager.showNewIntakeDialog(caseId -> sceneManager.openCaseProfile(caseId, "OVERVIEW"));
	}

	@FXML
	private void onBack() {
		sceneManager.goBack();
	}

	@FXML
	private void onUpdate() {
		if (updateLauncher != null) {
			try {
				updateLauncher.launchUpdater();
				sceneManager.onUpdaterLaunchSucceeded();
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

	@FXML
	private void onOpenNotificationCenter() {
		if (notificationCenterService == null || notificationBellButton == null || notificationBellButton.getScene() == null) {
			return;
		}
		NotificationCenterDialog.show(notificationBellButton.getScene().getWindow(), notificationCenterService);
	}

	public void showMyShaleView() {
		highlightNav(navMyShaleButton);
		sectionTitleLabel.setText("My Shale");
		sectionSubtitleLabel.setText("Overview of your tasks, assigned cases, and recent activity.");
		Node myShaleRoot = sceneManager.createMyShaleView(
				caseId -> sceneManager.openCaseProfile(caseId, "OVERVIEW"),
				sceneManager::openUserProfile);
		sectionContent.getChildren().setAll(myShaleRoot);
	}

	public void showCasesListView() {
		highlightNav(navCasesButton);
		sectionTitleLabel.setText("Cases");
		sectionSubtitleLabel.setText("Browse, search, and manage cases.");
		Node casesRoot = sceneManager.createCasesView(caseId -> sceneManager.openCaseProfile(caseId, "OVERVIEW"));
		sectionContent.getChildren().setAll(casesRoot);
	}

	public void showContactsListView() {
		highlightNav(navContactsButton);
		sectionTitleLabel.setText("Contacts");
		sectionSubtitleLabel.setText("Manage clients, experts, and other contacts.");
		Node contactsRoot = sceneManager.createContactsView(sceneManager::openContactProfile);
		sectionContent.getChildren().setAll(contactsRoot);
	}

	public void showOrganizationsListView() {
		highlightNav(navOrganizationsButton);
		sectionTitleLabel.setText("Organizations");
		sectionSubtitleLabel.setText("Browse, search, and manage organizations.");
		Node organizationsRoot = sceneManager.createOrganizationsView(sceneManager::openOrganizationProfile);
		sectionContent.getChildren().setAll(organizationsRoot);
	}

	public void showTeamListView() {
		highlightNav(navTeamButton);
		sectionTitleLabel.setText("Team");
		sectionSubtitleLabel.setText("See and manage your team members.");
		Node teamRoot = sceneManager.createTeamView(sceneManager::openUserProfile);
		sectionContent.getChildren().setAll(teamRoot);
	}

	public void showSearchResultsView(String query) {
		highlightNav(null);
		sectionTitleLabel.setText("Search");
		sectionSubtitleLabel.setText("Results for: \"" + query + "\"");
		Node searchRoot = sceneManager.createSearchView(
				query,
				caseId -> sceneManager.openCaseProfile(caseId, "OVERVIEW"),
				sceneManager::openContactProfile,
				sceneManager::openOrganizationProfile,
				sceneManager::openUserProfile);
		sectionContent.getChildren().setAll(searchRoot);
	}

	public void showCaseProfileView(int caseId, String sectionKey) {
		highlightNav(navCasesButton);
		sectionTitleLabel.setText("Case");
		sectionSubtitleLabel.setText("Case #" + caseId);
		Node caseRoot = sceneManager.createCaseView(caseId, sectionKey, sceneManager::openOrganizationProfile, sceneManager::openCasesListView);
		sectionContent.getChildren().setAll(caseRoot);
	}

	public void showOrganizationProfileView(int organizationId, Node organizationRoot) {
		highlightNav(navOrganizationsButton);
		sectionTitleLabel.setText("Organization");
		sectionSubtitleLabel.setText("Organization #" + organizationId);
		sectionContent.getChildren().setAll(organizationRoot);
	}

	public void showUserView(int userId, Node userRoot) {
		highlightNav(navTeamButton);
		sectionTitleLabel.setText("User");
		sectionSubtitleLabel.setText("User #" + userId);
		sectionContent.getChildren().setAll(userRoot);
	}

	public void showContactView(int contactId, Node contactRoot) {
		highlightNav(navContactsButton);
		sectionTitleLabel.setText("Contact");
		sectionSubtitleLabel.setText("Contact #" + contactId);
		sectionContent.getChildren().setAll(contactRoot);
	}

	public void updateBackButtonState(boolean canGoBack) {
		if (backButton != null) {
			backButton.setDisable(!canGoBack);
		}
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

	private void refreshSessionLabel() {
		if (appState == null || userEmailLabel == null) {
			return;
		}
		String email = appState.getUserEmail();
		if (email != null && !email.isBlank()) {
			userEmailLabel.setText(email);
		}
	}

	private void bindNotificationShell() {
		if (notificationCenterService == null
				|| notificationBellButton == null
				|| notificationBannerHost == null
				|| notificationBannerLabel == null) {
			return;
		}

		notificationBellButton.textProperty().bind(
				Bindings.createStringBinding(
						() -> notificationCenterService.getUnreadCount() > 0
								? "🔔 " + notificationCenterService.getUnreadCount()
								: "🔔",
						notificationCenterService.unreadCountProperty()));

		notificationCenterService.unreadCountProperty().addListener((obs, oldValue, newValue) -> {
			if (newValue.intValue() > 0) {
				if (!notificationBellButton.getStyleClass().contains("notification-bell-button-unread")) {
					notificationBellButton.getStyleClass().add("notification-bell-button-unread");
				}
			} else {
				notificationBellButton.getStyleClass().remove("notification-bell-button-unread");
			}
		});
		if (notificationCenterService.getUnreadCount() > 0
				&& !notificationBellButton.getStyleClass().contains("notification-bell-button-unread")) {
			notificationBellButton.getStyleClass().add("notification-bell-button-unread");
		}

		notificationCenterService.activeBannerProperty().addListener((obs, oldValue, newValue) -> showBanner(newValue));
		showBanner(notificationCenterService.getActiveBanner().orElse(null));
	}

	private void showBanner(AppNotification bannerNotification) {
		boolean hasBanner = bannerNotification != null;
		notificationBannerHost.setVisible(hasBanner);
		notificationBannerHost.setManaged(hasBanner);
		if (hasBanner) {
			notificationBannerLabel.setText(bannerNotification.getTitle());
		}
	}
}
