package com.shale.ui.services;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.shale.ui.component.dialog.AppDialogs;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

public final class UpdateFlowCoordinator {
	private final UiUpdateLauncher updateLauncher;
	private final Runnable onUpdaterLaunchSucceeded;
	private final AtomicBoolean updaterLaunchInFlight = new AtomicBoolean(false);

	public UpdateFlowCoordinator(UiUpdateLauncher updateLauncher, Runnable onUpdaterLaunchSucceeded) {
		this.updateLauncher = updateLauncher;
		this.onUpdaterLaunchSucceeded = onUpdaterLaunchSucceeded;
	}

	public void presentAvailableUpdate(boolean mandatory, Runnable onDecline) {
		if (mandatory) {
			showMandatoryUpdateDialog(onDecline);
			return;
		}
		showOptionalUpdateDialog(onDecline);
	}

	public void startUpdateAndBlock() {
		if (!updaterLaunchInFlight.compareAndSet(false, true)) {
			return;
		}

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
			onUpdaterLaunchSucceeded.run();
		} catch (RuntimeException ex) {
			updaterLaunchInFlight.set(false);
			progress.close();
			Alert error = new Alert(Alert.AlertType.ERROR);
			AppDialogs.applySecondaryWindowChrome(error);
			error.setTitle("Update Failed");
			error.setHeaderText("Unable to launch updater.");
			error.setContentText(ex.getMessage());
			error.showAndWait();
			return;
		}

		Platform.runLater(() -> {
			if (!progress.isShowing()) {
				progress.show();
			}
		});
	}

	private void showOptionalUpdateDialog(Runnable onDecline) {
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
			onDecline.run();
		}
	}

	private void showMandatoryUpdateDialog(Runnable onDecline) {
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
			onDecline.run();
		}
	}
}
