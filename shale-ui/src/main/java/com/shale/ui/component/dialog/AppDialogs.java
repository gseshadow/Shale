package com.shale.ui.component.dialog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class AppDialogs {

	private AppDialogs() {
	}

	public static void showInfo(Window owner, String title, String message) {
		showMessage(owner, title, null, message, "OK", DialogActionKind.PRIMARY);
	}

	public static void showError(Window owner, String title, String message) {
		showMessage(owner, title, "Something went wrong", message, "OK", DialogActionKind.DANGER);
	}

	public static boolean showConfirmation(
			Window owner,
			String title,
			String heading,
			String message,
			String confirmText,
			DialogActionKind confirmKind) {
		List<DialogAction<Boolean>> actions = List.of(
				DialogAction.cancel("Cancel", false),
				DialogAction.of(confirmText, true, confirmKind, true, false));
		Optional<Boolean> result = showDialog(owner, title, heading, message, null, actions, 420);
		return result.orElse(false);
	}

	public static <T> Optional<T> showChoice(
			Window owner,
			String title,
			String heading,
			String message,
			List<DialogAction<T>> actions) {
		return showDialog(owner, title, heading, message, null, actions, 460);
	}

	public static Stage createModalStage(Window owner, String title) {
		Stage stage = new Stage();
		applySecondaryWindowChrome(stage);
		if (owner != null) {
			stage.initOwner(owner);
		}
		stage.initModality(Modality.WINDOW_MODAL);
		stage.setTitle(title);
		stage.setResizable(false);
		return stage;
	}

	public static void applySecondaryWindowChrome(Stage stage) {
		if (stage != null) {
			stage.initStyle(StageStyle.UNDECORATED);
		}
	}

	public static void applySecondaryWindowChrome(Dialog<?> dialog) {
		if (dialog != null) {
			dialog.initStyle(StageStyle.UNDECORATED);
		}
	}

	private static void showMessage(
			Window owner,
			String title,
			String heading,
			String message,
			String buttonText,
			DialogActionKind buttonKind) {
		showDialog(owner, title, heading, message, null,
				List.of(DialogAction.of(buttonText, null, buttonKind, true, true)), 400);
	}

	private static <T> Optional<T> showDialog(
			Window owner,
			String title,
			String heading,
			String message,
			VBox customContent,
			List<DialogAction<T>> actions,
			double minWidth) {
		Stage stage = createModalStage(owner, title);
		ResultHolder<T> result = new ResultHolder<>();

		VBox root = new VBox(18);
		root.getStyleClass().add("app-dialog-root");
		root.setPadding(new Insets(18));
		root.setMinWidth(minWidth);

		if (!isBlank(heading) || !isBlank(message)) {
			VBox headerBox = new VBox(8);
			headerBox.getStyleClass().add("app-dialog-header");
			if (!isBlank(heading)) {
				Label headingLabel = new Label(heading);
				headingLabel.getStyleClass().add("app-dialog-title");
				headingLabel.setWrapText(true);
				headerBox.getChildren().add(headingLabel);
			}
			if (!isBlank(message)) {
				Label messageLabel = new Label(message);
				messageLabel.getStyleClass().add("app-dialog-message");
				messageLabel.setWrapText(true);
				headerBox.getChildren().add(messageLabel);
			}
			root.getChildren().add(headerBox);
		}

		if (customContent != null) {
			root.getChildren().add(customContent);
		}

		HBox actionsRow = new HBox(10);
		actionsRow.setAlignment(Pos.CENTER_RIGHT);
		actionsRow.getStyleClass().add("app-dialog-actions");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		actionsRow.getChildren().add(spacer);
		for (DialogAction<T> action : actions) {
			Button button = new Button(action.text());
			button.getStyleClass().add("app-dialog-button");
			button.getStyleClass().add(action.kind().styleClass());
			button.setDefaultButton(action.defaultAction());
			button.setCancelButton(action.cancelAction());
			button.setOnAction(event -> {
				result.value = action.value();
				stage.close();
			});
			actionsRow.getChildren().add(button);
		}
		root.getChildren().add(actionsRow);

		Scene scene = new Scene(root);
		scene.getStylesheets().add(Objects.requireNonNull(
				AppDialogs.class.getResource("/css/app.css")).toExternalForm());
		stage.setScene(scene);
		stage.showAndWait();
		return Optional.ofNullable(result.value);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	public enum DialogActionKind {
		PRIMARY("app-dialog-button-primary"),
		SECONDARY("app-dialog-button-secondary"),
		DANGER("app-dialog-button-danger");

		private final String styleClass;

		DialogActionKind(String styleClass) {
			this.styleClass = styleClass;
		}

		String styleClass() {
			return styleClass;
		}
	}

	public static final class DialogAction<T> {
		private final String text;
		private final T value;
		private final DialogActionKind kind;
		private final boolean defaultAction;
		private final boolean cancelAction;

		private DialogAction(String text, T value, DialogActionKind kind, boolean defaultAction, boolean cancelAction) {
			this.text = Objects.requireNonNull(text);
			this.value = value;
			this.kind = Objects.requireNonNull(kind);
			this.defaultAction = defaultAction;
			this.cancelAction = cancelAction;
		}

		public static <T> DialogAction<T> of(String text, T value, DialogActionKind kind, boolean defaultAction, boolean cancelAction) {
			return new DialogAction<>(text, value, kind, defaultAction, cancelAction);
		}

		public static <T> DialogAction<T> cancel(String text, T value) {
			return new DialogAction<>(text, value, DialogActionKind.SECONDARY, false, true);
		}

		String text() {
			return text;
		}

		T value() {
			return value;
		}

		DialogActionKind kind() {
			return kind;
		}

		boolean defaultAction() {
			return defaultAction;
		}

		boolean cancelAction() {
			return cancelAction;
		}
	}

	private static final class ResultHolder<T> {
		private T value;
	}
}
