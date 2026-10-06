package com.shale.ui.component.dialog;

import java.util.concurrent.atomic.AtomicBoolean;

import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.util.DialogSizingUtil;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Presentation-only prompt for the existing manual updater handoff. */
public final class UpdateDialog {
	private static final double MIN_WIDTH = 540;
	private static final double PREF_WIDTH = 600;
	private static final double MIN_HEIGHT = 430;

	private UpdateDialog() {
	}

	public static boolean show(Window owner, boolean mandatory, String currentVersion) {
		owner = resolveOwner(owner);
		Stage stage = AppDialogs.createModalStage(owner, mandatory ? "Update Required" : "Update Available");
		AtomicBoolean accepted = new AtomicBoolean(false);
		Runnable decline = stage::close;

		VBox body = createContent(mandatory, currentVersion, () -> {
			accepted.set(true);
			stage.close();
		}, decline);
		VBox shell = AppDialogs.createSecondaryWindowShell(stage,
				mandatory ? "Update Required" : "Update Available", decline, body);
		shell.getStyleClass().addAll("update-dialog", mandatory ? "update-dialog-mandatory" : "update-dialog-optional");

		Scene scene = new Scene(shell, PREF_WIDTH, MIN_HEIGHT);
		stage.setScene(scene);
		stage.setOnCloseRequest(event -> {
			event.consume();
			decline.run();
		});
		DialogSizingUtil.applyConfirmationDialogSizing(stage, owner, shell,
				PREF_WIDTH, MIN_WIDTH, MIN_HEIGHT);
		stage.showAndWait();
		return accepted.get();
	}

	static VBox createContent(boolean mandatory, String currentVersion, Runnable accept, Runnable decline) {
		Label status = new Label(mandatory ? "REQUIRED UPDATE" : "UPDATE AVAILABLE");
		status.getStyleClass().addAll("shale-semantic-chip",
				mandatory ? "shale-semantic-chip-warning" : "shale-semantic-chip-info",
				"update-dialog-status");

		Label title = new Label(mandatory ? "Update Shale to continue" : "A newer version of Shale is ready");
		title.setWrapText(true);
		title.getStyleClass().add("shale-page-title");

		Label summary = wrapping(mandatory
				? "This update is required before you can continue into Shale."
				: "Install the latest release now, or keep working and update later.", "shale-body-text");
		VBox heading = new VBox(8, status, title, summary);
		heading.getStyleClass().add("update-dialog-heading");

		VBox current = versionBlock("CURRENT VERSION", normalizedVersion(currentVersion));
		VBox available = versionBlock("AVAILABLE VERSION", "Latest release");
		HBox versions = new HBox(12, current, available);
		versions.getStyleClass().add("update-dialog-versions");
		HBox.setHgrow(current, Priority.ALWAYS);
		HBox.setHgrow(available, Priority.ALWAYS);

		Label detailsTitle = new Label("Update details");
		detailsTitle.getStyleClass().add("shale-subsection-title");
		Label details = wrapping(
				"Shale will close after the updater starts. The updater will verify and install the latest available release, then reopen Shale when installation completes.",
				"update-dialog-details-text");
		VBox detailsContent = new VBox(8, detailsTitle, details);
		detailsContent.getStyleClass().add("update-dialog-details-content");
		ScrollPane detailsScroll = new ScrollPane(detailsContent);
		detailsScroll.setFitToWidth(true);
		detailsScroll.setPannable(true);
		detailsScroll.setMinHeight(112);
		detailsScroll.setPrefHeight(132);
		detailsScroll.setMaxHeight(180);
		detailsScroll.getStyleClass().add("update-dialog-details-scroll");
		VBox.setVgrow(detailsScroll, Priority.ALWAYS);

		Button secondary = ActionButtonFactory.semantic(mandatory ? "Exit application" : "Not now",
				event -> decline.run(), ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		secondary.setCancelButton(true);
		Button primary = ActionButtonFactory.semantic("Update now", event -> accept.run(),
				ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
		primary.setDefaultButton(true);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox actions = new HBox(10, spacer, secondary, primary);
		actions.setAlignment(Pos.CENTER_RIGHT);
		actions.getStyleClass().add("update-dialog-actions");

		VBox root = new VBox(18, heading, versions, detailsScroll, actions);
		root.getStyleClass().add("update-dialog-content");
		return root;
	}

	private static Window resolveOwner(Window requestedOwner) {
		if (requestedOwner != null) {
			return requestedOwner;
		}
		return Window.getWindows().stream()
				.filter(Window::isShowing)
				.filter(window -> !(window instanceof Stage candidate) || candidate.getModality() == javafx.stage.Modality.NONE)
				.findFirst()
				.orElse(null);
	}

	private static VBox versionBlock(String caption, String value) {
		Label captionLabel = new Label(caption);
		captionLabel.getStyleClass().add("shale-field-label");
		Label valueLabel = new Label(value);
		valueLabel.setWrapText(true);
		valueLabel.getStyleClass().add("shale-field-value");
		VBox block = new VBox(5, captionLabel, valueLabel);
		block.setMaxWidth(Double.MAX_VALUE);
		block.getStyleClass().add("update-dialog-version-card");
		return block;
	}

	private static Label wrapping(String text, String styleClass) {
		Label label = new Label(text);
		label.setWrapText(true);
		label.setMaxWidth(Double.MAX_VALUE);
		label.getStyleClass().add(styleClass);
		return label;
	}

	private static String normalizedVersion(String version) {
		return version == null || version.isBlank() ? "Unknown" : version.trim();
	}
}
