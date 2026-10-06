package com.shale.desktop;

import com.shale.data.auth.AuthService;
import com.shale.desktop.identity.MachineIdentityProvider;
import com.shale.desktop.identity.MachineIdentityResult;
import com.shale.desktop.live.LiveEventDispatcher;
import com.shale.desktop.navigation.SceneRouter;
import com.shale.desktop.security.SessionContext;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;
import java.time.Clock;
import com.shale.core.update.UpdateAttemptReconciler;
import com.shale.core.update.UpdateAttemptStore;
import com.shale.core.update.InstalledVersionMetadata;
import com.shale.ui.services.AppVersionProvider;

public final class MainApp extends Application {

	private SceneRouter router;
	private MachineIdentityResult machineIdentity;

	@Override
	public void start(Stage primaryStage) {
		System.out.println("MainApp.start()");// TODO remove
		reconcileUpdateAttempt();
 
		var iconStream = MainApp.class.getResourceAsStream("/images/ShaleNoText.png");
		if (iconStream != null) {
			Image icon = new Image(iconStream);
			primaryStage.getIcons().add(icon);
		} else {
			System.out.println("Icon resource not found: /images/ShaleNoText.png");
		}

		DesktopConfig config = DesktopConfig.load(); // builds AuthService, db config, etc.
		AuthService authService = config.getAuthService();
		LiveEventDispatcher dispatcher = new LiveEventDispatcher();

		router = new SceneRouter(primaryStage, authService, dispatcher, config.runtimeService, config.negotiateEndpointUrl, machineIdentity(), config.serverApiOrigin);

		router.showLogin();
	}

	private void reconcileUpdateAttempt() {
		reconcileInstalledVersionMetadata();
		try {
			var store = new UpdateAttemptStore(com.shale.desktop.update.DesktopUiUpdateLauncher.attemptDirectory());
			new UpdateAttemptReconciler(store, Clock.systemUTC()).reconcile(AppVersionProvider.currentVersion());
		} catch (Exception ex) {
			System.err.println("Update attempt reconciliation deferred: " + ex.getClass().getSimpleName());
		}
	}

	private void reconcileInstalledVersionMetadata() {
		String appPath=System.getProperty("jpackage.app-path","");
		if(appPath.isBlank()) return;
		try {
			var executable=java.nio.file.Path.of(appPath).toAbsolutePath().normalize();
			var file=executable.getParent().resolve("app").resolve(InstalledVersionMetadata.FILE_NAME);
			if(!java.nio.file.Files.exists(file)) return;
			var metadata=InstalledVersionMetadata.read(file);
			if(!metadata.version().toString().equals(AppVersionProvider.currentVersion()))
				System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
						"Installed-version metadata does not match the running payload; metadata was not modified");
		} catch(Exception failure) {
			System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
					"Installed-version metadata could not be reconciled: {0}",failure.getClass().getSimpleName());
		}
	}

	/**
	 * Lazy, authentication-independent desktop composition point for Phase 4A.
	 * The stable value is supplied to authenticated Phase 4B enrollment only after login.
	 */
	MachineIdentityResult machineIdentity() {
		if (machineIdentity == null) {
			machineIdentity = MachineIdentityProvider.resolvePlatformDefault();
		}
		return machineIdentity;
	}

	@Override
	public void stop() {
		if (router != null) {
			router.shutdown();
		}
		SessionContext sessionContext = new SessionContext();
		// If runtimeBridge needs shutdown, call it here
		sessionContext.clear();
	}

	public static void main(String[] args) {
		launch(args);
	}
}
