package com.shale.ui.controller;

import com.shale.ui.navigation.SceneManager;
import com.shale.ui.services.UiAuthService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public final class LoginController {
	@FXML
	private TextField emailField;
	@FXML
	private PasswordField passwordField;
	@FXML
	private Button signInButton;
	@FXML
	private Label errorLabel;

	private SceneManager sceneManager;
	private AppState appState;
	private UiAuthService authService;
	private UiRuntimeBridge runtimeBridge;

	public LoginController() {
		System.out.println("LoginController()");// TODO
	}

	public void init(SceneManager sceneManager, AppState appState,
			UiAuthService authService, UiRuntimeBridge runtimeBridge) {
		System.out.println("LoginController.init()");// TODO
		this.sceneManager = sceneManager;
		this.appState = appState;
		this.authService = authService;
		this.runtimeBridge = runtimeBridge;
	}

	@FXML
	private void initialize() {
		System.out.println("LoginController.initialize()");// TODO
		errorLabel.setText("");

		// Make the sign-in button the default button for the form
		signInButton.setDefaultButton(true);

		// Hitting Enter in either field will trigger sign-in
		emailField.setOnAction(e -> onSignIn());
		passwordField.setOnAction(e -> onSignIn());

		// DEV ONLY: auto login shortcut
		// TODO REMOVE BEFORE PRODUCTION

		Platform.runLater(() ->
		{
			emailField.setText("brian@curtislawfirm.org");
			passwordField.setText("Quazi1der@");
		});

	}

	@FXML
	private void onSignIn() {
		System.out.println("LoginController.onSignIn()");// TODO
		setBusy(true);
		errorLabel.setText("");
		final String email = emailField.getText() == null ? "" : emailField.getText().trim();
		final String pass = passwordField.getText() == null ? "" : passwordField.getText();

		// Offload to background thread to keep UI responsive
		new Thread(() ->
		{
			try {
				UiAuthService.Result result = authService.login(email, pass);
				if (result == null) {
					showError("Invalid email or password.");
				} else {
					// Persist minimal session state for UI
					appState.setUserId(result.userId());
					appState.setShaleClientId(result.shaleClientId());
					appState.setUserEmail(result.email());

					// Let desktop initialize runtime + negotiate live URL
					runtimeBridge.onLoginSuccess(result.userId(), result.shaleClientId(), result.email());

					// Move to main scene
					Platform.runLater(() -> sceneManager.showMain());
				}
			} catch (Exception ex) {
				showError("Sign-in failed. " + ex.getMessage());
			} finally {
				setBusy(false);
			}
		}, "login-thread").start();
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
