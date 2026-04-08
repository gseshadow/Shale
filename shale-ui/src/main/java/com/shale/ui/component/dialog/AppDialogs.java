package com.shale.ui.component.dialog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
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
			stage.initStyle(StageStyle.TRANSPARENT);
			stage.sceneProperty().addListener((obs, oldScene, newScene) -> {
				if (newScene != null) {
					newScene.setFill(Color.TRANSPARENT);
				}
			});
		}
	}

	public static void applySecondaryWindowChrome(Dialog<?> dialog) {
		if (dialog != null) {
			dialog.initStyle(StageStyle.UNDECORATED);
		}
	}

	public static void applySecondaryDialogShell(Dialog<?> dialog, String title) {
		if (dialog == null) {
			return;
		}
		dialog.initStyle(StageStyle.TRANSPARENT);
		DialogPane pane = dialog.getDialogPane();
		if (pane == null) {
			return;
		}
		if (!pane.getStyleClass().contains("secondary-window-shell")) {
			pane.getStyleClass().add("secondary-window-shell");
		}
		String appCss = Objects.requireNonNull(AppDialogs.class.getResource("/css/app.css")).toExternalForm();
		if (!pane.getStylesheets().contains(appCss)) {
			pane.getStylesheets().add(appCss);
		}
		Node header = createSecondaryDialogHeader(dialog, title);
		pane.setHeader(null);
		pane.setGraphic(header);
		pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
			if (newScene != null) {
				newScene.setFill(Color.TRANSPARENT);
			}
		});
	}

	public static HBox createSecondaryWindowHeader(Stage stage, String title, Runnable onClose) {
		Objects.requireNonNull(stage, "stage");
		Label titleLabel = new Label(isBlank(title) ? "" : title);
		titleLabel.getStyleClass().add("secondary-window-title");

		Button closeButton = new Button("✕");
		closeButton.setFocusTraversable(false);
		closeButton.getStyleClass().add("secondary-window-close");
		closeButton.setOnAction(event -> {
			if (onClose != null) {
				onClose.run();
			} else {
				stage.close();
			}
		});

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		HBox header = new HBox(10, titleLabel, spacer, closeButton);
		header.getStyleClass().add("secondary-window-header");
		header.setAlignment(Pos.CENTER_LEFT);
		header.setPadding(new Insets(8, 10, 8, 12));

		installDragToMove(stage, header);
		return header;
	}

	public static void installDragToMove(Stage stage, Node dragHandle) {
		if (stage == null || dragHandle == null) {
			return;
		}
		final double[] dragOffset = new double[2];
		dragHandle.setOnMousePressed(event -> {
			if (event.getButton() != MouseButton.PRIMARY) {
				return;
			}
			dragOffset[0] = event.getScreenX() - stage.getX();
			dragOffset[1] = event.getScreenY() - stage.getY();
		});
		dragHandle.setOnMouseDragged(event -> {
			if (!event.isPrimaryButtonDown()) {
				return;
			}
			stage.setX(event.getScreenX() - dragOffset[0]);
			stage.setY(event.getScreenY() - dragOffset[1]);
		});
	}

	private static Node createSecondaryDialogHeader(Dialog<?> dialog, String title) {
		Label titleLabel = new Label(isBlank(title) ? "" : title);
		titleLabel.getStyleClass().add("secondary-window-title");

		Button closeButton = new Button("✕");
		closeButton.setFocusTraversable(false);
		closeButton.getStyleClass().add("secondary-window-close");
		closeButton.setOnAction(event -> dialog.close());

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		HBox header = new HBox(10, titleLabel, spacer, closeButton);
		header.getStyleClass().add("secondary-window-header");
		header.setAlignment(Pos.CENTER_LEFT);
		header.setPadding(new Insets(8, 10, 8, 12));
		header.sceneProperty().addListener((obs, oldScene, newScene) -> {
			if (newScene == null || !(newScene.getWindow() instanceof Stage stage)) {
				return;
			}
			installDragToMove(stage, header);
		});
		return header;
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
