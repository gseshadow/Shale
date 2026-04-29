package com.shale.ui.controller;

import com.shale.ui.component.dialog.NotificationCenterDialog;
import com.shale.ui.navigation.SceneManager;
import com.shale.ui.notification.AppNotification;
import com.shale.ui.notification.NotificationCenterService;
import com.shale.ui.notification.NotificationCategory;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.UiUpdateLauncher;
import com.shale.ui.services.UpdateFlowCoordinator;
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
import javafx.scene.layout.VBox;

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
	private Button navCalendarButton;

	@FXML
	private Button logoutButton;

	// Content shell
	@FXML
	private Label sectionTitleLabel;

	@FXML
	private Label sectionSubtitleLabel;

	@FXML
	private VBox sectionHeaderBox;

	@FXML
	private StackPane sectionContent;

	@FXML
	private Label placeholderLabel;

	private SceneManager sceneManager;
	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private UiUpdateLauncher updateLauncher;
	private NotificationCenterService notificationCenterService;
	private UpdateFlowCoordinator updateFlowCoordinator;

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
	private void onNavCalendar() {
		sceneManager.openCalendarView();
	}

	@FXML
	private void onNavSettings() {
		sceneManager.openSettingsView();
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
		NotificationCenterDialog.show(
				notificationBellButton.getScene().getWindow(),
				notificationCenterService,
				sceneManager::openTaskProfile,
				this::onNotificationActivated);
	}


	private void onNotificationActivated(AppNotification notification) {
		if (notification == null || updateFlowCoordinator == null || !isAvailableUpdateNotification(notification)) {
			return;
		}
		updateFlowCoordinator.presentAvailableUpdate(isMandatoryUpdateNotification(notification), () -> {});
	}

	private static boolean isAvailableUpdateNotification(AppNotification notification) {
		return notification.getCategory() == NotificationCategory.APP_UPDATE
				&& notification.getId() != null
				&& notification.getId().startsWith("update-available-");
	}

	private static boolean isMandatoryUpdateNotification(AppNotification notification) {
		if (notification == null) {
			return false;
		}
		String id = notification.getId();
		if (id != null && id.contains("mandatory")) {
			return true;
		}
		String title = notification.getTitle();
		return title != null && title.toLowerCase().contains("required");
	}

	public void showMyShaleView() {
		highlightNav(navMyShaleButton);
		setSectionHeader("My Shale", "Overview of your tasks, assigned cases, and recent activity.", true);
		Node myShaleRoot = sceneManager.createMyShaleView(
				caseId -> sceneManager.openCaseProfile(caseId, "OVERVIEW"),
				sceneManager::openUserProfile);
		sectionContent.getChildren().setAll(myShaleRoot);
	}

	public void showCasesListView() {
		highlightNav(navCasesButton);
		setSectionHeader("Cases", "Browse, search, and manage cases.", true);
		Node casesRoot = sceneManager.createCasesView(caseId -> sceneManager.openCaseProfile(caseId, "OVERVIEW"));
		sectionContent.getChildren().setAll(casesRoot);
	}

	public void showContactsListView() {
		highlightNav(navContactsButton);
		setSectionHeader("Contacts", "Manage clients, experts, and other contacts.", true);
		Node contactsRoot = sceneManager.createContactsView(sceneManager::openContactProfile);
		sectionContent.getChildren().setAll(contactsRoot);
	}

	public void showOrganizationsListView() {
		highlightNav(navOrganizationsButton);
		setSectionHeader("Organizations", "Browse, search, and manage organizations.", true);
		Node organizationsRoot = sceneManager.createOrganizationsView(sceneManager::openOrganizationProfile);
		sectionContent.getChildren().setAll(organizationsRoot);
	}

	public void showTeamListView() {
		highlightNav(navTeamButton);
		setSectionHeader("Team", "See and manage your team members.", true);
		Node teamRoot = sceneManager.createTeamView(sceneManager::openUserProfile);
		sectionContent.getChildren().setAll(teamRoot);
	}

	public void showCalendarView() {
		highlightNav(navCalendarButton);
		setSectionHeader("Calendar", "Calendar events and case/task deadlines will appear here.", true);
		Node calendarRoot = sceneManager.createCalendarView();
		sectionContent.getChildren().setAll(calendarRoot);
	}

	public void showSettingsView() {
		highlightNav(navSettingsButton);
		setSectionHeader("Settings", "Configure Shale preferences and system settings.", true);
		Node settingsRoot = sceneManager.createSettingsView();
		sectionContent.getChildren().setAll(settingsRoot);
	}

	public void showSearchResultsView(String query) {
		highlightNav(null);
		setSectionHeader("Search", "Results for: \"" + query + "\"", true);
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
		setSectionHeader("", "", false);
		Node caseRoot = sceneManager.createCaseView(caseId, sectionKey, sceneManager::openOrganizationProfile, sceneManager::openCasesListView);
		sectionContent.getChildren().setAll(caseRoot);
	}

	public void showOrganizationProfileView(int organizationId, Node organizationRoot) {
		highlightNav(navOrganizationsButton);
		setSectionHeader("", "", false);
		sectionContent.getChildren().setAll(organizationRoot);
	}

	public void showUserView(int userId, Node userRoot) {
		highlightNav(navTeamButton);
		setSectionHeader("", "", false);
		sectionContent.getChildren().setAll(userRoot);
	}

	public void showContactView(int contactId, Node contactRoot) {
		highlightNav(navContactsButton);
		setSectionHeader("", "", false);
		sectionContent.getChildren().setAll(contactRoot);
	}

	private void setSectionHeader(String title, String subtitle, boolean visible) {
		if (sectionTitleLabel != null) {
			sectionTitleLabel.setText(title == null ? "" : title);
		}
		if (sectionSubtitleLabel != null) {
			sectionSubtitleLabel.setText(subtitle == null ? "" : subtitle);
		}
		if (sectionHeaderBox != null) {
			sectionHeaderBox.setVisible(visible);
			sectionHeaderBox.setManaged(visible);
		}
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
				navCalendarButton,
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
		navCalendarButton.setDisable(false);
		navSettingsButton.setDisable(false);

		NavButtonStyler.setActive(active, getNavigationButtons());
	}

	public void setUpdateLauncher(UiUpdateLauncher updateLauncher) {
		this.updateLauncher = updateLauncher;
		this.updateFlowCoordinator = new UpdateFlowCoordinator(updateLauncher, sceneManager::onUpdaterLaunchSucceeded);
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
