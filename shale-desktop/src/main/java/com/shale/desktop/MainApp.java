package com.shale.desktop;

import com.shale.data.auth.AuthService;
import com.shale.desktop.live.LiveEventDispatcher;
import com.shale.desktop.navigation.SceneRouter;
import com.shale.desktop.security.SessionContext;
import javafx.application.Application;
import javafx.stage.Stage;

public final class MainApp extends Application {

	private SceneRouter router;

	@Override
	public void start(Stage primaryStage) {
		System.out.println("MainApp.start()");// TODO remove

		DesktopConfig config = DesktopConfig.load(); // builds AuthService, db config, etc.

		AuthService authService = config.getAuthService();
		LiveEventDispatcher dispatcher = new LiveEventDispatcher();

		router = new SceneRouter(primaryStage, authService, dispatcher, config.runtimeService);

		router.showLogin();
	}

	@Override
	public void stop() {
		SessionContext sessionContext = new SessionContext();
		// If runtimeBridge needs shutdown, call it here
		sessionContext.clear();
	}

	public static void main(String[] args) {
		launch(args);
	}
}
