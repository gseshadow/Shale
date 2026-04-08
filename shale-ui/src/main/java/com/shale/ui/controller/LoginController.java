package com.shale.ui.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import com.shale.core.platform.AppPaths;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.navigation.SceneManager;
import com.shale.ui.services.AppVersionProvider;
import com.shale.ui.services.UiAuthService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.UiUpdateLauncher;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

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

		if (updateCheck.mandatory()) {
			showMandatoryUpdateDialog();
			return;
		}

		showOptionalUpdateDialog();
	}

	private void showOptionalUpdateDialog() {
		ButtonType updateNow = new ButtonType("Update now", ButtonBar.ButtonData.OK_DONE);
		ButtonType skip = new ButtonType("Skip this time", ButtonBar.ButtonData.CANCEL_CLOSE);

		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		AppDialogs.applySecondaryWindowChrome(alert);
		alert.setTitle("Update Available");
		alert.setHeaderText("A newer version of Shale is available.");
		alert.setContentText("Would you like to update now?");
		alert.getButtonTypes().setAll(updateNow, skip);

		Optional<ButtonType> choice = alert.showAndWait();
		if (choice.isPresent() && choice.get() == updateNow) {
			startUpdateAndBlock();
		} else {
			sceneManager.showMain();
		}
	}

	private void showMandatoryUpdateDialog() {
		ButtonType updateNow = new ButtonType("Update now", ButtonBar.ButtonData.OK_DONE);
		ButtonType exit = new ButtonType("Exit application", ButtonBar.ButtonData.CANCEL_CLOSE);

		Alert alert = new Alert(Alert.AlertType.WARNING);
		AppDialogs.applySecondaryWindowChrome(alert);
		alert.setTitle("Update Required");
		alert.setHeaderText("You must update Shale before continuing.");
		alert.setContentText("An update is required to continue into the app.");
		alert.getButtonTypes().setAll(updateNow, exit);

		Optional<ButtonType> choice = alert.showAndWait();
		if (choice.isPresent() && choice.get() == updateNow) {
			startUpdateAndBlock();
		} else {
			Platform.exit();
		}
	}

	private void startUpdateAndBlock() {
		Alert progress = new Alert(Alert.AlertType.INFORMATION);
		AppDialogs.applySecondaryWindowChrome(progress);
		progress.setTitle("Update Started");
		progress.setHeaderText("Updating…");

		VBox content = new VBox();
		content.setAlignment(Pos.CENTER);
		content.setSpacing(15);

		var gifStream = getClass().getResourceAsStream("/images/ShaleLoading.gif");
		if (gifStream != null) {
			ImageView loadingImage = new ImageView(new Image(gifStream));
			loadingImage.setFitWidth(120);
			loadingImage.setPreserveRatio(true);
			loadingImage.setSmooth(true);
			content.getChildren().add(loadingImage);
		} else {
			System.out.println("Loading gif resource not found: /images/ShaleLoading.gif");
		}

		Label messageLabel = new Label("Shale is launching the updater and will close shortly.");
		messageLabel.setWrapText(true);
		messageLabel.setContentDisplay(ContentDisplay.TEXT_ONLY);
		content.getChildren().add(messageLabel);

		progress.getDialogPane().setContent(content);
		progress.getButtonTypes().setAll();
		progress.initModality(Modality.APPLICATION_MODAL);
		progress.show();

		try {
			System.out.println("[Updater] User accepted update; launching updater.");
			updateLauncher.launchUpdater();
			sceneManager.onUpdaterLaunchSucceeded();
		} catch (RuntimeException ex) {
			progress.close();
			Alert error = new Alert(Alert.AlertType.ERROR);
			AppDialogs.applySecondaryWindowChrome(error);
			error.setTitle("Update Failed");
			error.setHeaderText("Unable to launch updater.");
			error.setContentText(ex.getMessage());
			error.showAndWait();
			return;
		}

		Platform.runLater(() ->
		{
			if (!progress.isShowing()) {
				progress.show();
			}
		});
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
