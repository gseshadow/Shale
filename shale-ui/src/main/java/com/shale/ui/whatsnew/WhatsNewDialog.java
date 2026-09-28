package com.shale.ui.whatsnew;

import java.util.concurrent.atomic.AtomicBoolean;

import com.shale.core.model.ReleaseItemType;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.theme.ThemeManager;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;

import javafx.geometry.Pos;
import javafx.scene.Node;
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

/** Shale-styled, aggregate desktop release-notes dialog. */
public final class WhatsNewDialog {
	private WhatsNewDialog() {}

	public static void show(Window owner, WhatsNewPresentation presentation, Runnable onDismissed) {
		Stage stage = AppDialogs.createModalStage(owner, "What's New in Shale");
		stage.setResizable(true);
		stage.setMinWidth(560);
		stage.setMinHeight(420);
		AtomicBoolean completed = new AtomicBoolean();
		Runnable finish = () -> {
			if (completed.compareAndSet(false, true) && onDismissed != null) onDismissed.run();
			stage.close();
		};
		stage.setOnCloseRequest(event -> {
			if (completed.compareAndSet(false, true) && onDismissed != null) onDismissed.run();
		});

		VBox body = createContent(presentation, finish);
		VBox shell = AppDialogs.createSecondaryWindowShell(stage, "What's New in Shale", finish, body);
		shell.getStyleClass().add("whats-new-dialog");
		Scene scene = new Scene(shell, 680, 620);
		ThemeManager.application().register(scene);
		stage.setScene(scene);
		stage.show();
	}

	static VBox createContent(WhatsNewPresentation presentation, Runnable dismiss) {
		Label eyebrow = new Label("UPDATES SINCE YOUR LAST VISIT");
		eyebrow.getStyleClass().add("whats-new-eyebrow");
		Label version = new Label("Shale " + presentation.targetVersion());
		version.getStyleClass().add("whats-new-current-version");
		VBox heading = new VBox(4, eyebrow, version);
		heading.getStyleClass().add("whats-new-heading");

		VBox sections = new VBox(16);
		sections.getStyleClass().add("whats-new-sections");
		for (WhatsNewPresentation.ReleaseSection release : presentation.releases()) sections.getChildren().add(releaseSection(release));
		ScrollPane scroll = new ScrollPane(sections);
		scroll.setFitToWidth(true);
		scroll.setPannable(true);
		scroll.getStyleClass().add("whats-new-scroll");
		VBox.setVgrow(scroll, Priority.ALWAYS);

		Button done = ActionButtonFactory.semantic("Got it", event -> dismiss.run(),
				ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
		done.setDefaultButton(true);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox actions = new HBox(spacer, done);
		actions.setAlignment(Pos.CENTER_RIGHT);
		actions.getStyleClass().add("whats-new-actions");

		VBox root = new VBox(heading, scroll, actions);
		root.getStyleClass().add("whats-new-content");
		return root;
	}

	private static Node releaseSection(WhatsNewPresentation.ReleaseSection release) {
		Label version = new Label("Version " + release.version());
		version.getStyleClass().add("whats-new-release-version");
		VBox section = new VBox(8, version);
		section.getStyleClass().add("whats-new-release");
		if (release.summary() != null && release.items().stream().noneMatch(item -> release.summary().equals(item.title())
				|| release.summary().equals(item.body()))) {
			Label summary = wrapping(release.summary(), "whats-new-release-summary");
			section.getChildren().add(summary);
		}
		for (WhatsNewPresentation.Item item : release.items()) section.getChildren().add(item(item));
		return section;
	}

	private static Node item(WhatsNewPresentation.Item item) {
		Label type = new Label(typeLabel(item.type()));
		type.getStyleClass().add("whats-new-item-type");
		Label title = wrapping(item.title(), "whats-new-item-title");
		HBox header = new HBox(8, type, title);
		header.setAlignment(Pos.CENTER_LEFT);
		VBox box = new VBox(5, header);
		box.getStyleClass().add("whats-new-item");
		if (item.type() == ReleaseItemType.IMPORTANT) box.getStyleClass().add("whats-new-item-important");
		if (item.body() != null) box.getChildren().add(wrapping(item.body(), "whats-new-item-body"));
		if ((item.type() == ReleaseItemType.LINK || item.type() == ReleaseItemType.VIDEO) && item.resourceUrl() != null)
			box.getChildren().add(wrapping(item.resourceUrl(), "whats-new-resource"));
		return box;
	}

	private static Label wrapping(String text, String styleClass) {
		Label label = new Label(text == null ? "" : text);
		label.setWrapText(true);
		label.setMaxWidth(Double.MAX_VALUE);
		label.getStyleClass().add(styleClass);
		return label;
	}

	private static String typeLabel(ReleaseItemType type) {
		return switch (type) {
			case FEATURE -> "NEW";
			case FIX -> "FIX";
			case IMPROVEMENT -> "IMPROVED";
			case IMPORTANT -> "IMPORTANT";
			case LINK -> "LINK";
			case VIDEO -> "VIDEO";
		};
	}
}
