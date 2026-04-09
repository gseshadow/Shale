package com.shale.ui.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import com.shale.core.platform.AppPaths;
import com.shale.ui.navigation.SceneManager;
import com.shale.ui.services.AppVersionProvider;
import com.shale.ui.services.UiAuthService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.UiUpdateLauncher;
import com.shale.ui.services.UpdateFlowCoordinator;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public final class LoginController {
	@FXML
	private TextField emailField;
	@FXML
	private PasswordField passwordField;
	@FXML
	private Button signInButton;
	@FXML
	private Label errorLabel;
	@FXML
	private ImageView logoImage;
	@FXML
	private Label versionLabel;

	private SceneManager sceneManager;
	private AppState appState;
	private UiAuthService authService;
	private UiRuntimeBridge runtimeBridge;
	private static final String APP_NAME = "Shale";

	private UiUpdateLauncher updateLauncher;
	private UpdateFlowCoordinator updateFlowCoordinator;

	public LoginController() {
		System.out.println("LoginController()");// TODO
	}

	public void init(SceneManager sceneManager, AppState appState,
			UiAuthService authService, UiRuntimeBridge runtimeBridge,
			UiUpdateLauncher updateLauncher) {
		System.out.println("LoginController.init()");// TODO
		this.sceneManager = sceneManager;
		this.appState = appState;
		this.authService = authService;
		this.runtimeBridge = runtimeBridge;
		this.updateLauncher = updateLauncher;
		this.updateFlowCoordinator = new UpdateFlowCoordinator(updateLauncher, sceneManager::onUpdaterLaunchSucceeded);
	}

	@FXML
	private void initialize() {
		Path logPath = AppPaths.appLogFile(APP_NAME, "startup.log");

		try {
			Files.createDirectories(logPath.getParent());

			Files.writeString(
					logPath,
					"LoginController.initialize() called\n",
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND
			);
		} catch (Exception ignored) {
		}

		errorLabel.setText("");
		versionLabel.setText("Version - " + AppVersionProvider.currentVersion());

		signInButton.setDefaultButton(true);

		emailField.setOnAction(e -> onSignIn());
		passwordField.setOnAction(e -> onSignIn());

		try {
			var logoUrl = getClass().getResource("/images/Shale.png");

			Files.writeString(
					logPath,
					"Logo resource URL: " + logoUrl + "\n",
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND
			);

			if (logoUrl != null) {
				logoImage.setImage(new Image(logoUrl.toExternalForm()));

				Files.writeString(
						logPath,
						"Logo image successfully loaded\n",
						StandardOpenOption.CREATE,
						StandardOpenOption.APPEND
				);
			} else {
				Files.writeString(
						logPath,
						"Logo resource NOT FOUND at /images/Shale.png\n",
						StandardOpenOption.CREATE,
						StandardOpenOption.APPEND
				);
			}

		} catch (Exception ex) {
			try {
				Files.writeString(
						logPath,
						"Exception loading logo: " + ex + "\n",
						StandardOpenOption.CREATE,
						StandardOpenOption.APPEND
				);
			} catch (Exception ignored) {
			}
		}
	}

	@FXML
	private void onSignIn() {
		System.out.println("LoginController.onSignIn()");// TODO
		setBusy(true);
		errorLabel.setText("");
		final String email = emailField.getText() == null ? "" : emailField.getText().trim();
		final String pass = passwordField.getText() == null ? "" : passwordField.getText();

		new Thread(() ->
		{
			try {
				UiAuthService.Result result = authService.login(email, pass);
				if (result == null) {
					showError("Invalid email or password.");
					return;
				}

				appState.setUserId(result.userId());
				appState.setShaleClientId(result.shaleClientId());
				appState.setUserEmail(result.email());
				appState.setAdmin(result.admin());
				appState.setAttorney(result.attorney());

				runtimeBridge.onLoginSuccess(result.userId(), result.shaleClientId(), result.email());

				UiUpdateLauncher.UpdateCheckResult updateCheck;
				try {
					updateCheck = updateLauncher.checkForUpdate();
				} catch (RuntimeException updateCheckError) {
					Platform.runLater(() -> sceneManager.showError("Update check failed: " + updateCheckError.getMessage()));
					Platform.runLater(sceneManager::showMain);
					return;
				}

				Platform.runLater(() -> {
					sceneManager.onUpdateCheckCompleted(updateCheck);
					handlePostLoginFlow(updateCheck);
				});
			} catch (Exception ex) {
				showError("Sign-in failed. " + ex.getMessage());
			} finally {
				setBusy(false);
			}
		}, "login-thread").start();
	}

	private void handlePostLoginFlow(UiUpdateLauncher.UpdateCheckResult updateCheck) {
		if (!updateCheck.updateAvailable()) {
			sceneManager.showMain();
			return;
		}

		Runnable onDecline = updateCheck.mandatory() ? Platform::exit : sceneManager::showMain;
		updateFlowCoordinator.presentAvailableUpdate(updateCheck.mandatory(), onDecline);
	}

	private void setBusy(boolean busy) {
		Platform.runLater(() ->
		{
			signInButton.setDisable(busy);
			emailField.setDisable(busy);
			passwordField.setDisable(busy);
		});
	}

	private void showError(String msg) {
		Platform.runLater(() -> errorLabel.setText(msg));
	}
}
