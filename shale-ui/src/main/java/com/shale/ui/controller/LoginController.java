package com.shale.ui.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import com.shale.core.platform.AppPaths;
import com.shale.ui.navigation.SceneManager;
import com.shale.ui.services.AppVersionProvider;
import com.shale.ui.services.UiAuthService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.UiUpdateLauncher;
import com.shale.ui.services.UpdateFlowCoordinator;
import com.shale.ui.state.AppState;
import com.shale.ui.theme.Theme;
import com.shale.ui.util.ControlStyles;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoginController {
	private static final Logger LOG = LoggerFactory.getLogger(LoginController.class);
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
	@FXML
	private StackPane loginRoot;
	@FXML
	private HBox loginContent;
	@FXML
	private Pane brandPane;
	@FXML
	private Pane loginIllustration;
	@FXML
	private TextField visiblePasswordField;
	@FXML
	private ToggleButton passwordVisibilityButton;
	@FXML
	private CheckBox stayLoggedInCheckBox;
	@FXML
	private Label progressLabel;
	@FXML private Button retryRestoreButton;
	@FXML
	private Circle backgroundGlow;

	private SceneManager sceneManager;
	private AppState appState;
	private UiAuthService authService;
	private UiRuntimeBridge runtimeBridge;
	private static final String APP_NAME = "Shale";

	private UiUpdateLauncher updateLauncher;
	private UpdateFlowCoordinator updateFlowCoordinator;
	private final AtomicBoolean authenticationInProgress = new AtomicBoolean();
	private final java.util.concurrent.atomic.AtomicLong authenticationGeneration=new java.util.concurrent.atomic.AtomicLong();
	private Animation backgroundAnimation;
	private Animation entranceAnimation;

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
		boolean rememberedCredentialPresent=authService.hasRememberedCredential();
		LOG.info("Remembered sign-in startup credential present={}.",rememberedCredentialPresent);
		if(rememberedCredentialPresent)Platform.runLater(this::attemptRestore);
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
		ControlStyles.apply(signInButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
		ControlStyles.apply(retryRestoreButton,ControlStyles.Purpose.SECONDARY,ControlStyles.Size.SMALL);
		ControlStyles.formControl(emailField);
		ControlStyles.formControl(passwordField);
		ControlStyles.formControl(visiblePasswordField);
		passwordField.textProperty().bindBidirectional(visiblePasswordField.textProperty());
		stayLoggedInCheckBox.setSelected(false);
		stayLoggedInCheckBox.setDisable(false);

		emailField.setOnAction(e -> onSignIn());
		passwordField.setOnAction(e -> onSignIn());
		visiblePasswordField.setOnAction(e -> onSignIn());
		loginRoot.widthProperty().addListener((ignored, oldWidth, newWidth) -> updateResponsiveLayout());
		loginRoot.heightProperty().addListener((ignored, oldHeight, newHeight) -> updateResponsiveLayout());
		updateResponsiveLayout();
		startAnimations();

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
	private void onPasswordVisibilityChanged() {
		boolean reveal = passwordVisibilityButton.isSelected();
		TextField from = reveal ? passwordField : visiblePasswordField;
		TextField to = reveal ? visiblePasswordField : passwordField;
		int caret = from.getCaretPosition();
		int anchor = from.getAnchor();
		passwordField.setVisible(!reveal);
		passwordField.setManaged(!reveal);
		visiblePasswordField.setVisible(reveal);
		visiblePasswordField.setManaged(reveal);
		passwordVisibilityButton.setText(reveal ? "Hide password" : "Show password");
		to.requestFocus();
		to.selectRange(anchor, caret);
	}

	private void updateResponsiveLayout() {
		double width = loginRoot.getWidth();
		double height = loginRoot.getHeight();
		boolean initialLayout = width <= 0 || height <= 0;
		boolean showBranding = initialLayout || (width >= 980 && height >= 620);
		boolean showIllustration = initialLayout || (width >= 1140 && height >= 760);
		brandPane.setVisible(showBranding);
		brandPane.setManaged(showBranding);
		loginIllustration.setVisible(showBranding && showIllustration);
		loginIllustration.setManaged(showBranding && showIllustration);
		loginContent.setSpacing(showBranding ? 56 : 0);
	}

	private void startAnimations() {
		if (Boolean.getBoolean("shale.ui.reduceMotion")) return;
		loginContent.setOpacity(0);
		FadeTransition fade = new FadeTransition(Duration.millis(420), loginContent);
		fade.setFromValue(0);
		fade.setToValue(1);
		fade.play();
		entranceAnimation = fade;

		Timeline glow = new Timeline(
				new KeyFrame(Duration.ZERO, new KeyValue(backgroundGlow.translateXProperty(), -90)),
				new KeyFrame(Duration.seconds(12), new KeyValue(backgroundGlow.translateXProperty(), 35)));
		glow.setAutoReverse(true);
		glow.setCycleCount(Animation.INDEFINITE);
		glow.play();
		backgroundAnimation = glow;
	}

	/** Stops view-owned animation before the scene root is replaced. */
	public void dispose() {
		if (entranceAnimation != null) entranceAnimation.stop();
		if (backgroundAnimation != null) backgroundAnimation.stop();
		passwordField.textProperty().unbindBidirectional(visiblePasswordField.textProperty());
	}

	@FXML
	private void onSignIn() {
		if (!authenticationInProgress.compareAndSet(false, true)) return;
		final long generation=authenticationGeneration.incrementAndGet();
		System.out.println("LoginController.onSignIn()");// TODO
		setBusy(true);
		errorLabel.setText("");
		final String email = emailField.getText() == null ? "" : emailField.getText().trim();
		final String pass = passwordField.getText() == null ? "" : passwordField.getText();
		final boolean stayLoggedIn=stayLoggedInCheckBox.isSelected();
		LOG.info("Password sign-in submitted; remember requested={}.",stayLoggedIn);

		new Thread(() ->
		{
			try {
				UiAuthService.Result result = authService.login(email, pass,stayLoggedIn);
				if (result == null) {
					showError("Invalid email or password.");
					return;
				}
				if(generation!=authenticationGeneration.get())return;
				completeAuthentication(result,generation);
			} catch (Exception ex) {
				showError("Sign-in failed. " + ex.getMessage());
			} finally {
				setBusy(false);
				authenticationInProgress.set(false);
			}
		}, "login-thread").start();
	}

	private void attemptRestore(){
		LOG.info("Remembered sign-in asynchronous restore initiated.");
		if(!authenticationInProgress.compareAndSet(false,true))return;long generation=authenticationGeneration.incrementAndGet();setBusy(true);progressLabel.setText("Signing you in…");errorLabel.setText("");
		new Thread(()->{try{UiAuthService.Result result=authService.restore();if(result==null){showError("Saved sign-in is unavailable. Please sign in again.");return;}if(generation!=authenticationGeneration.get())return;completeAuthentication(result,generation);}catch(Exception ex){String name=ex.getClass().getSimpleName();if(name.contains("Enrollment")){showError("Shale could not reach the server. Retry or sign in manually.");Platform.runLater(()->{retryRestoreButton.setVisible(true);retryRestoreButton.setManaged(true);});}else showError(ex.getMessage());}finally{setBusy(false);authenticationInProgress.set(false);}},"remembered-sign-in").start();
	}
	@FXML private void onRetryRestore(){retryRestoreButton.setVisible(false);retryRestoreButton.setManaged(false);attemptRestore();}

	private void completeAuthentication(UiAuthService.Result result,long generation)throws Exception{
				Platform.runLater(passwordField::clear);
				appState.setUserId(result.userId());
				appState.setShaleClientId(result.shaleClientId());
				appState.setUserEmail(result.email());
				appState.setAdmin(result.admin());
				appState.setAttorney(result.attorney());

				runtimeBridge.onLoginSuccess(result.userId(), result.shaleClientId(), result.email());
				try {
					authService.commitRememberedCredential();
				} catch (Exception rememberFailure) {
					// A password login may have initialized JDBC and enrolled a server session before an
					// old server's HTTP 200 response is discovered to lack remember support. Return to
					// a clean login state instead of entering the application with a misleading choice.
					runtimeBridge.onLogout();
					appState.setUserId(0);
					appState.setShaleClientId(0);
					appState.setUserEmail(null);
					appState.setAdmin(false);
					appState.setAttorney(false);
					throw rememberFailure;
				}
				Theme appearance;
				try {
					appearance = sceneManager.loadAppearanceForAuthenticatedUser();
				} catch (RuntimeException preferenceFailure) {
					LOG.warn("Appearance preference could not be loaded; using Light for this authenticated session.");
					appearance = Theme.LIGHT;
				}
				final Theme resolvedAppearance = appearance;

				UiUpdateLauncher.UpdateCheckResult updateCheck;
				try {
					updateCheck = updateLauncher.checkForUpdate();
				} catch (RuntimeException updateCheckError) {
					Platform.runLater(() -> sceneManager.showError("Update check failed: " + updateCheckError.getMessage()));
					Platform.runLater(() -> {
						if (sceneManager.applyAppearanceBeforeMain(resolvedAppearance, result.userId(), result.shaleClientId())) sceneManager.showMain();
					});
					return;
				}

				Platform.runLater(() -> {
					if (!sceneManager.applyAppearanceBeforeMain(resolvedAppearance, result.userId(), result.shaleClientId())) return;
					sceneManager.onUpdateCheckCompleted(updateCheck);
					handlePostLoginFlow(updateCheck);
				});
	}

	private void handlePostLoginFlow(UiUpdateLauncher.UpdateCheckResult updateCheck) {
		if (requiresMandatoryUpdate(updateCheck)) {
			updateFlowCoordinator.presentAvailableUpdate(true, Platform::exit);
			return;
		}
		sceneManager.showMain();
	}

	static boolean requiresMandatoryUpdate(UiUpdateLauncher.UpdateCheckResult updateCheck) {
		return updateCheck != null && updateCheck.updateAvailable() && updateCheck.mandatory();
	}

	private void setBusy(boolean busy) {
		Platform.runLater(() ->
		{
			signInButton.setDisable(busy);
			emailField.setDisable(busy);
			passwordField.setDisable(busy);
			visiblePasswordField.setDisable(busy);
			passwordVisibilityButton.setDisable(busy);
			stayLoggedInCheckBox.setDisable(busy);
			progressLabel.setVisible(busy);
			progressLabel.setManaged(busy);
		});
	}

	private void showError(String msg) {
		Platform.runLater(() -> errorLabel.setText(msg));
	}
}
