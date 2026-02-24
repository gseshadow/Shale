package com.shale.desktop.navigation;

import com.shale.data.auth.AuthService;
import com.shale.data.runtime.RuntimeSessionService;
import com.shale.desktop.live.LiveEventDispatcher;
import com.shale.desktop.runtime.DesktopRuntimeSessionProvider;
import com.shale.desktop.ui.DesktopUiAuthService;
import com.shale.desktop.ui.DesktopUiRuntimeBridge;
import com.shale.ui.navigation.SceneManager;
import com.shale.ui.state.AppState;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.Objects;

public final class SceneRouter {

	private final Stage stage;
	private final SceneManager sceneManager;

	// Core DB provider implementation lives in desktop, but is passed into shale-ui as
	// DbSessionProvider
	private final DesktopRuntimeSessionProvider dbProvider;

	public SceneRouter(Stage stage,
			AuthService authService,
			LiveEventDispatcher dispatcher,
			RuntimeSessionService runtimeSessionService,
			String negotiateEndpointUrl) {

		this.stage = Objects.requireNonNull(stage, "stage");
		Objects.requireNonNull(authService, "authService");
		Objects.requireNonNull(dispatcher, "dispatcher");
		Objects.requireNonNull(runtimeSessionService, "runtimeSessionService");
		Objects.requireNonNull(negotiateEndpointUrl, "negotiateEndpointUrl");

		this.stage.setTitle("Shale");
		this.stage.setOnCloseRequest(e -> Platform.exit());

		AppState appState = new AppState();
		var uiAuthService = new DesktopUiAuthService(authService);

		// Create ONE provider instance and share it with SceneManager + DesktopUiRuntimeBridge
		this.dbProvider = new DesktopRuntimeSessionProvider();

		// Desktop bridge will "arm" dbProvider on successful login
		var runtimeBridge = new DesktopUiRuntimeBridge(dispatcher, dbProvider, negotiateEndpointUrl);
		runtimeBridge.setRuntimeSessionService(runtimeSessionService);

		this.sceneManager = new SceneManager(
				stage,
				appState,
				uiAuthService,
				runtimeBridge,
				dbProvider // passed as DbSessionProvider to shale-ui
		);
	}

	public void showLogin() {
		sceneManager.showLogin();
	}

	public void showMain() {
		sceneManager.showMain();
	}

	public void showError(String message) {
		sceneManager.showError(message);
	}

	public void close() {
		stage.close();
	}
}
