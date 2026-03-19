package com.shale.ui.navigation;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.ContactDao;
import com.shale.data.dao.OrganizationDao;
import com.shale.data.dao.UserDao;
import com.shale.ui.controller.CaseController;
import com.shale.ui.controller.CasesController;
import com.shale.ui.controller.ContactViewController;
import com.shale.ui.controller.ContactsController;
import com.shale.ui.controller.LoginController;
import com.shale.ui.controller.MainController;
import com.shale.ui.controller.MyShaleController;
import com.shale.ui.controller.NewIntakeController;
import com.shale.ui.controller.NewOrganizationController;
import com.shale.ui.controller.OrganizationController;
import com.shale.ui.controller.OrganizationsController;
import com.shale.ui.controller.TeamController;
import com.shale.ui.controller.UserController;
import com.shale.ui.services.UiAuthService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import com.shale.ui.services.UiUpdateLauncher;

public final class SceneManager {

	private final Stage stage;
	private final AppState appState;
	private final UiAuthService authService;
	private final UiRuntimeBridge runtimeBridge;

	private final DbSessionProvider dbSessionProvider;

	private final UiUpdateLauncher updateLauncher;

	public SceneManager(Stage stage,
			AppState appState,
			UiAuthService authService,
			UiRuntimeBridge runtimeBridge,
			DbSessionProvider dbSessionProvider,
			UiUpdateLauncher updateLauncher) {
		this.stage = stage;
		this.appState = appState;
		this.authService = authService;
		this.runtimeBridge = runtimeBridge;
		this.dbSessionProvider = Objects.requireNonNull(dbSessionProvider);
		this.updateLauncher = Objects.requireNonNull(updateLauncher);
	}

	public void showLogin() {
		var root = load("/fxml/login.fxml", controller ->
		{
			LoginController c = (LoginController) controller;
			c.init(this, appState, authService, runtimeBridge, updateLauncher);
			return c;
		});
		setScene(root, "Shale — Sign in");
	}

	public void showMain() {
		var root = load("/fxml/main.fxml", controller ->
		{
			MainController c = (MainController) controller;
			c.init(this, appState, runtimeBridge);
			c.setUpdateLauncher(updateLauncher);
			return c;
		});
		setScene(root, "Shale");
	}

	/** Backwards-compatible: no callback. */
	public Parent createCasesView() {
		return createCasesView(null);
	}

	/**
	 * Create the Cases view and optionally provide a callback that the CasesController can
	 * invoke when a case card is clicked (open case).
	 */
	public Parent createCasesView(Consumer<Integer> onOpenCase) {
		return load("/fxml/cases.fxml", controller ->
		{
			CasesController c = (CasesController) controller;

			// DB access is enforced inside DbSessionProvider (throws if not logged in)
			CaseDao caseDao = new CaseDao(dbSessionProvider);

			// NOTE: this requires you to update CasesController.init(...) to accept the callback
			c.init(appState, runtimeBridge, caseDao, onOpenCase);
			return c;
		});
	}


	public Parent createOrganizationsView(Consumer<Integer> onOpenOrganization) {
		return load("/fxml/organizations.fxml", controller ->
		{
			OrganizationsController c = (OrganizationsController) controller;
			OrganizationDao organizationDao = new OrganizationDao(dbSessionProvider);
			c.init(appState, runtimeBridge, organizationDao, onOpenOrganization, this);
			return c;
		});
	}

	public Parent createContactsView(Consumer<Integer> onOpenContact) {
		return load("/fxml/contacts.fxml", controller ->
		{
			ContactsController c = (ContactsController) controller;
			ContactDao contactDao = new ContactDao(dbSessionProvider);
			c.init(appState, contactDao, onOpenContact);
			return c;
		});
	}

	public Parent createTeamView(Consumer<Integer> onOpenUser) {
		return load("/fxml/team.fxml", controller ->
		{
			TeamController c = (TeamController) controller;
			UserDao userDao = new UserDao(dbSessionProvider);
			c.init(appState, userDao, onOpenUser);
			return c;
		});
	}

	private static final String ROOT_CONTROLLER_KEY = "sceneManager.controller";

	public Parent createUserView(int userId) {
		return load("/fxml/user.fxml", controller ->
		{
			UserController c = (UserController) controller;
			UserDao userDao = new UserDao(dbSessionProvider);
			c.init(userId, userDao, appState);
			return c;
		});
	}

	public Parent createContactView(int contactId, Consumer<Integer> onOpenCase) {
		return load("/fxml/contact.fxml", controller ->
		{
			ContactViewController c = (ContactViewController) controller;
			ContactDao contactDao = new ContactDao(dbSessionProvider);
			c.init(contactId, contactDao, appState, onOpenCase);
			return c;
		});
	}

	public Parent createContactView(int contactId) {
		return createContactView(contactId, null);
	}

	public Parent createOrganizationView(int organizationId, Consumer<Integer> onOpenCase, Runnable onOrganizationDeleted) {
		return load("/fxml/organization.fxml", controller ->
		{
			OrganizationController c = (OrganizationController) controller;
			OrganizationDao organizationDao = new OrganizationDao(dbSessionProvider);
			c.init(organizationId, organizationDao, appState, runtimeBridge, onOpenCase, onOrganizationDeleted);
			return c;
		});
	}


	public Parent createMyShaleView(Consumer<Integer> onOpenCase) {
		return load("/fxml/my-shale.fxml", controller ->
		{
			MyShaleController c = (MyShaleController) controller;
			CaseDao caseDao = new CaseDao(dbSessionProvider);
			c.init(appState, runtimeBridge, caseDao, onOpenCase);
			return c;
		});
	}

	public Parent createCaseView(int caseId, Consumer<Integer> onOpenOrganization) {
		return load("/fxml/case.fxml", controller ->
		{
			CaseController c = (CaseController) controller;

			CaseDao caseDao = new CaseDao(dbSessionProvider);
			OrganizationDao organizationDao = new OrganizationDao(dbSessionProvider);
			ContactDao contactDao = new ContactDao(dbSessionProvider);
			c.init(caseId, caseDao, organizationDao, contactDao, appState, runtimeBridge);

			c.setOnOpenUser(this::openUserProfile);
			c.setOnOpenStatus(this::openStatusProfile);
			c.setOnOpenContact(this::openContactProfile);
			c.setOnOpenOrganization(onOpenOrganization);
			return c;
		});
	}

	public void showNewOrganizationDialog(Consumer<Integer> onOrganizationCreated) {
		try {
			URL url = Objects.requireNonNull(getClass().getResource("/fxml/new-organization.fxml"), "Missing FXML: /fxml/new-organization.fxml");
			FXMLLoader loader = new FXMLLoader(url);
			Parent root = loader.load();

			Stage dialog = new Stage();
			dialog.initOwner(stage);
			dialog.initModality(Modality.WINDOW_MODAL);
			dialog.setTitle("New Organization");

			NewOrganizationController controller = loader.getController();
			OrganizationDao organizationDao = new OrganizationDao(dbSessionProvider);
			controller.init(appState, organizationDao, dialog, onOrganizationCreated);

			Scene dialogScene = new Scene(root);
			dialogScene.getStylesheets().add(Objects.requireNonNull(
					getClass().getResource("/css/app.css")).toExternalForm());
			dialog.setScene(dialogScene);
			dialog.setMinWidth(760);
			dialog.setMinHeight(720);
			dialog.showAndWait();
		} catch (IOException e) {
			throw new RuntimeException("Failed to open New Organization dialog", e);
		}
	}

	public void showNewIntakeDialog(Consumer<Integer> onCaseCreated) {
		try {
			URL url = Objects.requireNonNull(getClass().getResource("/fxml/new-intake.fxml"), "Missing FXML: /fxml/new-intake.fxml");
			FXMLLoader loader = new FXMLLoader(url);
			Parent root = loader.load();

			Stage dialog = new Stage();
			dialog.initOwner(stage);
			dialog.initModality(Modality.WINDOW_MODAL);
			dialog.setTitle("New Intake");

			NewIntakeController controller = loader.getController();
			CaseDao caseDao = new CaseDao(dbSessionProvider);
			controller.init(appState, caseDao, dialog, onCaseCreated);

			Scene dialogScene = new Scene(root);
			dialogScene.getStylesheets().add(Objects.requireNonNull(
					getClass().getResource("/css/app.css")).toExternalForm());
			dialog.setScene(dialogScene);
			dialog.setMinWidth(860);
			dialog.setMinHeight(760);
			dialog.showAndWait();
		} catch (IOException e) {
			throw new RuntimeException("Failed to open New Intake dialog", e);
		}
	}

	public void openUserProfile(Integer userId) {
		if (userId == null || userId <= 0) {
			System.err.println("Ignoring user navigation for invalid userId: " + userId);
			return;
		}

		try {
			Parent userRoot = createUserView(userId);
			MainController mainController = resolveMainController();
			if (mainController == null) {
				System.err.println("Unable to navigate to user profile; main controller is unavailable.");
				return;
			}
			mainController.showUserView(userId, userRoot);
		} catch (RuntimeException ex) {
			System.err.println("Failed to open user profile for userId " + userId + ": " + ex.getMessage());
		}
	}

	private void openStatusProfile(Integer statusId) {
		System.out.println("Navigate to Status: " + statusId);
		// TODO later:
		// navigate to status manager / filter view / status editor
	}

	public void openContactProfile(Integer contactId) {
		if (contactId == null || contactId <= 0) {
			System.err.println("Ignoring contact navigation for invalid contactId: " + contactId);
			return;
		}

		try {
			Parent contactRoot = createContactView(contactId);
			MainController mainController = resolveMainController();
			if (mainController == null) {
				System.err.println("Unable to navigate to contact profile; main controller is unavailable.");
				return;
			}
			mainController.showContactView(contactId, contactRoot);
		} catch (RuntimeException ex) {
			System.err.println("Failed to open contact profile for contactId " + contactId + ": " + ex.getMessage());
		}
	}


	private MainController resolveMainController() {
		Scene scene = stage.getScene();
		if (scene == null) {
			return null;
		}

		Parent root = scene.getRoot();
		if (root == null) {
			return null;
		}

		Object controller = root.getProperties().get(ROOT_CONTROLLER_KEY);
		if (controller instanceof MainController mainController) {
			return mainController;
		}

		return null;
	}

	private Parent load(String fxmlPath, Function<Object, Object> controllerConfigurer) {
		try {
			URL url = Objects.requireNonNull(getClass().getResource(fxmlPath), "Missing FXML: " + fxmlPath);
			FXMLLoader loader = new FXMLLoader(url);

			// Defer controller instantiation so we can inject dependencies
			loader.setControllerFactory(clz ->
			{
				try {
					Object controller = clz.getDeclaredConstructor().newInstance();
					return controllerConfigurer.apply(controller);
				} catch (Exception e) {
					throw new RuntimeException("Controller init failed for " + clz.getName(), e);
				}
			});

			Parent root = loader.load();
			root.getProperties().put(ROOT_CONTROLLER_KEY, loader.getController());
			return root;
		} catch (IOException e) {
			throw new RuntimeException("Failed to load FXML: " + fxmlPath, e);
		}
	}

	private void setScene(Parent root, String title) {
		Scene scene = stage.getScene();
		if (scene == null) {
			scene = new Scene(root, 1650, 900);
			scene.getStylesheets().add(Objects.requireNonNull(
					getClass().getResource("/css/app.css")).toExternalForm());
			stage.setScene(scene);
		} else {
			scene.setRoot(root);
		}
		stage.setTitle(title);
		stage.show();
	}

	public void showError(String message) {
		System.out.println("*******************SceneManager.showError() " + message);
	}
}
