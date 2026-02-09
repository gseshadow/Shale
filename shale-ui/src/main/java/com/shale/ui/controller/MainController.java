package com.shale.ui.controller;

import com.shale.ui.navigation.SceneManager;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
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

	// Sidebar nav buttons
	@FXML
	private Button navMyShaleButton;

	@FXML
	private Button navTasksButton;

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

	public MainController() {
		System.out.println("MainController()");//TODO remove
	}

	// Injected by SceneManager
	public void init(SceneManager sceneManager,
			AppState appState,
			UiRuntimeBridge runtimeBridge) {
		System.out.println("MainController.init()");//TODO remove
		this.sceneManager = sceneManager;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
	}

	@FXML
	private void initialize() {
		System.out.println("MainController.initialize()");//TODO remove
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

		showMyShalePlaceholder();
	}

	// === Global search (shell-level for now) ===

	@FXML
	private void onGlobalSearch() {
		String query = globalSearchField.getText();
		System.out.println("Global search (shell): " + query);//TODO remove

		highlightNav(null);
		sectionTitleLabel.setText("Search");
		sectionSubtitleLabel.setText(
				(query == null || query.isBlank())
						? "Search across cases, contacts, tasks, and more."
						: "Results for: \"" + query + "\" (placeholder)"
		);
		setSectionContentText("Search results view is not implemented yet.");
	}

	// === Navigation handlers ===

	@FXML
	private void onNavMyShale() {
		highlightNav(navMyShaleButton);
		sectionTitleLabel.setText("My Shale");
		sectionSubtitleLabel.setText("Overview of your tasks, assigned cases, and recent activity.");
		showMyShalePlaceholder();
	}

	@FXML
	private void onNavTasks() {
		highlightNav(navTasksButton);
		sectionTitleLabel.setText("Tasks");
		sectionSubtitleLabel.setText("View and manage your tasks.");
		setSectionContentText("Tasks tab is not implemented yet.");
	}

	@FXML
	private void onNavCases() {
		highlightNav(navCasesButton);
		sectionTitleLabel.setText("Cases");
		sectionSubtitleLabel.setText("Browse, search, and manage cases.");

		// Ask SceneManager to create the Cases view (from cases.fxml)
		Node casesRoot = sceneManager.createCasesView();
		sectionContent.getChildren().setAll(casesRoot);
	}

	@FXML
	private void onNavContacts() {
		highlightNav(navContactsButton);
		sectionTitleLabel.setText("Contacts");
		sectionSubtitleLabel.setText("Manage clients, experts, and other contacts.");
		setSectionContentText("Contacts tab is not implemented yet.");
	}

	@FXML
	private void onNavOrganizations() {
		highlightNav(navOrganizationsButton);
		sectionTitleLabel.setText("Organizations");
		sectionSubtitleLabel.setText("Manage organizations and firms.");
		setSectionContentText("Organizations tab is not implemented yet.");
	}

	@FXML
	private void onNavTeam() {
		highlightNav(navTeamButton);
		sectionTitleLabel.setText("Team");
		sectionSubtitleLabel.setText("See and manage your team members.");
		setSectionContentText("Team tab is not implemented yet.");
	}

	@FXML
	private void onNavSettings() {
		highlightNav(navSettingsButton);
		sectionTitleLabel.setText("Settings");
		sectionSubtitleLabel.setText("Configure Shale preferences and system settings.");
		setSectionContentText("Settings tab is not implemented yet.");
	}

	@FXML
	private void onLogout() {
		System.out.println("MainController.onLogout()");//TODO remove

		runtimeBridge.onLogout();

		if (appState != null) {
			appState.setUserId(0);
			appState.setShaleClientId(0);
			appState.setUserEmail(null);
		}

		sceneManager.showLogin();
	}

	// === Helpers ===

	private void showMyShalePlaceholder() {
		setSectionContentText("My Shale dashboard is not implemented yet.");
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
		navTasksButton.setDisable(false);
		navCasesButton.setDisable(false);
		navContactsButton.setDisable(false);
		navOrganizationsButton.setDisable(false);
		navTeamButton.setDisable(false);
		navSettingsButton.setDisable(false);

		if (active != null) {
			active.setDisable(true);
		}
	}
}
