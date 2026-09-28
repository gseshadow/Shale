package com.shale.ui.services;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.dialog.AppDialogs.DialogAction;
import com.shale.ui.component.dialog.AppDialogs.DialogActionKind;

import javafx.geometry.Pos;
import javafx.scene.control.Dialog;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
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
		boolean accepted = AppDialogs.showChoice(null,
				mandatory ? "Update Required" : "Update Available",
				mandatory ? "Update Shale to continue" : "A newer version of Shale is available",
				mandatory ? "This update is required before you can continue into Shale."
						: "Would you like to update now?",
				List.of(
						DialogAction.cancel(mandatory ? "Exit application" : "Skip this time", false),
						DialogAction.of("Update now", true, DialogActionKind.PRIMARY, true, false)))
				.orElse(false);
		if (accepted) {
			startUpdateAndBlock();
		} else {
			onDecline.run();
		}
	}

	public void startUpdateAndBlock() {
		if (!updaterLaunchInFlight.compareAndSet(false, true)) {
			return;
		}

		Dialog<Void> progress = createLaunchDialog();
		progress.show();

		try {
			updateLauncher.launchUpdater();
			onUpdaterLaunchSucceeded.run();
		} catch (RuntimeException ex) {
			updaterLaunchInFlight.set(false);
			progress.close();
			AppDialogs.showError(null, "Update Failed", "Shale could not launch the updater. Please try again.");
		}
	}

	private static Dialog<Void> createLaunchDialog() {
		Dialog<Void> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, "Updating Shale");
		if (dialog.getDialogPane().getHeader() instanceof HBox header) {
			header.getChildren().stream().filter(Button.class::isInstance).forEach(node -> {
				node.setVisible(false);
				node.setManaged(false);
			});
		}
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(false);
		dialog.getDialogPane().getStyleClass().add("update-launch-dialog");
		dialog.getDialogPane().setPrefWidth(480);

		ProgressIndicator indicator = new ProgressIndicator();
		indicator.setMaxSize(42, 42);
		Label heading = new Label("Launching the updater");
		heading.getStyleClass().add("app-dialog-title");
		Label message = new Label("Shale will close shortly. The updater will continue from there.");
		message.getStyleClass().add("app-dialog-message");
		message.setWrapText(true);
		message.setMaxWidth(Double.MAX_VALUE);
		VBox content = new VBox(12, indicator, heading, message);
		content.setAlignment(Pos.CENTER_LEFT);
		content.getStyleClass().add("update-launch-content");
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().clear();
		dialog.setOnCloseRequest(event -> event.consume());
		return dialog;
	}
}
