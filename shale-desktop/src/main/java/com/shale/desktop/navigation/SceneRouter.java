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

import com.shale.desktop.update.DesktopUiUpdateLauncher;
import com.shale.desktop.notification.DesktopNotificationPresenterFactory;

import java.util.Objects;
import com.shale.desktop.identity.MachineIdentityResult;
import com.shale.data.dao.ApplicationInstanceDao;
import com.shale.data.service.adapter.ApplicationInstanceServiceAdapter;
import com.shale.desktop.session.*;

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
		this(stage,authService,dispatcher,runtimeSessionService,negotiateEndpointUrl,null);
	}

	public SceneRouter(Stage stage, AuthService authService, LiveEventDispatcher dispatcher,
			RuntimeSessionService runtimeSessionService, String negotiateEndpointUrl,
			MachineIdentityResult machineIdentity) {

		this.stage = Objects.requireNonNull(stage, "stage");
		Objects.requireNonNull(authService, "authService");
		Objects.requireNonNull(dispatcher, "dispatcher");
		Objects.requireNonNull(runtimeSessionService, "runtimeSessionService");
		Objects.requireNonNull(negotiateEndpointUrl, "negotiateEndpointUrl");

		this.stage.setTitle("Shale");
		this.stage.setOnCloseRequest(e -> Platform.exit());

		AppState appState = new AppState();
		String apiBase=System.getProperty("SHALE_SERVER_API_BASE_URL",System.getenv("SHALE_SERVER_API_BASE_URL"));
		var serverSession=new DesktopServerSession();
		var enrollment=new DesktopSessionEnrollmentLifecycle(apiBase==null||apiBase.isBlank()?null:new DesktopSessionEnrollmentClient(apiBase),serverSession);
		var uiAuthService = new DesktopUiAuthService(authService,enrollment);

		// Create ONE provider instance and share it with SceneManager + DesktopUiRuntimeBridge
		this.dbProvider = new DesktopRuntimeSessionProvider();

		// Desktop bridge will "arm" dbProvider on successful login
		var instanceService = new ApplicationInstanceServiceAdapter(new ApplicationInstanceDao(dbProvider));
		var runtimeBridge = new DesktopUiRuntimeBridge(dispatcher, dbProvider, negotiateEndpointUrl, machineIdentity, instanceService,enrollment);
		runtimeBridge.setRuntimeSessionService(runtimeSessionService);

		var updateLauncher = new DesktopUiUpdateLauncher();

		this.sceneManager = new SceneManager(
				stage,
				appState,
				uiAuthService,
				runtimeBridge,
				dbProvider,
				updateLauncher,
				DesktopNotificationPresenterFactory.create()
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

	public void shutdown() {
		sceneManager.shutdown();
	}
}
