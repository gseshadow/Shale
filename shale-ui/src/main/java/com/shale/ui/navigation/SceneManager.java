package com.shale.ui.navigation;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.data.dao.CaseDao;
import com.shale.ui.controller.CaseController;
import com.shale.ui.controller.CasesController;
import com.shale.ui.controller.LoginController;
import com.shale.ui.controller.MainController;
import com.shale.ui.services.UiAuthService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class SceneManager {

	private final Stage stage;
	private final AppState appState;
	private final UiAuthService authService;
	private final UiRuntimeBridge runtimeBridge;

	private final DbSessionProvider dbSessionProvider;

	public SceneManager(Stage stage,
			AppState appState,
			UiAuthService authService,
			UiRuntimeBridge runtimeBridge,
			DbSessionProvider dbSessionProvider) {
		this.stage = stage;
		this.appState = appState;
		this.authService = authService;
		this.runtimeBridge = runtimeBridge;
		this.dbSessionProvider = Objects.requireNonNull(dbSessionProvider);

	}

	public void showLogin() {
		var root = load("/fxml/login.fxml", controller ->
		{
			LoginController c = (LoginController) controller;
			c.init(this, appState, authService, runtimeBridge);
			return c;
		});
		setScene(root, "Shale — Sign in");
	}

	public void showMain() {
		var root = load("/fxml/main.fxml", controller ->
		{
			MainController c = (MainController) controller;
			c.init(this, appState, runtimeBridge);
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

	public Parent createCaseView(int caseId) {
		return load("/fxml/case.fxml", controller ->
		{
			CaseController c = (CaseController) controller;

			CaseDao caseDao = new CaseDao(dbSessionProvider);
			c.init(caseId, caseDao);

			return c;
		});
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

			return loader.load();
		} catch (IOException e) {
			throw new RuntimeException("Failed to load FXML: " + fxmlPath, e);
		}
	}

	private void setScene(Parent root, String title) {
		Scene scene = stage.getScene();
		if (scene == null) {
			scene = new Scene(root, 1100, 720);
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
