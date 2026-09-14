package com.shale.ui.component.dialog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.util.DialogSizingUtil;
import com.shale.ui.util.WindowSizingUtil;

public final class AppDialogs {
	private static final double CONFIRMATION_DIALOG_MIN_WIDTH = 480;
	private static final double CONFIRMATION_DIALOG_MIN_HEIGHT = 220;

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
		return showChoice(owner, title, heading, message, actions, 460);
	}

	public static <T> Optional<T> showChoice(
			Window owner,
			String title,
			String heading,
			String message,
			List<DialogAction<T>> actions,
			double minWidth) {
		return showDialog(owner, title, heading, message, null, actions, minWidth);
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
		stage.setOnShown(event -> WindowSizingUtil.constrainToVisualBounds(stage, owner));
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
		pane.setHeader(header);
		pane.setGraphic(null);
		pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
			if (newScene != null) {
				newScene.setFill(Color.TRANSPARENT);
			}
		});
		Scene scene = pane.getScene();
		if (scene != null) {
			scene.setFill(Color.TRANSPARENT);
		}
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
		header.setMaxWidth(Double.MAX_VALUE);
		header.setPadding(new Insets(8, 10, 8, 12));

		installDragToMove(stage, header);
		return header;
	}

	public static VBox createSecondaryWindowShell(Stage stage, String title, Runnable onClose, Node body) {
		Objects.requireNonNull(stage, "stage");
		Objects.requireNonNull(body, "body");
		HBox header = createSecondaryWindowHeader(stage, title, onClose);
		VBox root = new VBox(header, body);
		root.getStyleClass().add("app-dialog-root");
		root.setSpacing(0);
		return root;
	}

	public static void installSecondaryWindowResizeHandlers(Stage stage, Node root) {
		Objects.requireNonNull(stage, "stage");
		Objects.requireNonNull(root, "root");
		final double margin = 8.0;
		final double[] start = new double[6];
		root.addEventHandler(MouseEvent.MOUSE_MOVED, event -> root.setCursor(resizeCursor(event, root, margin)));
		root.addEventHandler(MouseEvent.MOUSE_EXITED, event -> root.setCursor(Cursor.DEFAULT));
		root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
			Cursor cursor = resizeCursor(event, root, margin);
			if (cursor == Cursor.DEFAULT) return;
			start[0] = event.getScreenX(); start[1] = event.getScreenY(); start[2] = stage.getX();
			start[3] = stage.getY(); start[4] = stage.getWidth(); start[5] = stage.getHeight();
			root.setUserData(cursor); event.consume();
		});
		root.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
			if (!(root.getUserData() instanceof Cursor cursor) || cursor == Cursor.DEFAULT) return;
			double dx = event.getScreenX() - start[0], dy = event.getScreenY() - start[1];
			boolean left = cursor == Cursor.W_RESIZE || cursor == Cursor.NW_RESIZE || cursor == Cursor.SW_RESIZE;
			boolean right = cursor == Cursor.E_RESIZE || cursor == Cursor.NE_RESIZE || cursor == Cursor.SE_RESIZE;
			boolean top = cursor == Cursor.N_RESIZE || cursor == Cursor.NW_RESIZE || cursor == Cursor.NE_RESIZE;
			boolean bottom = cursor == Cursor.S_RESIZE || cursor == Cursor.SW_RESIZE || cursor == Cursor.SE_RESIZE;
			if (right) stage.setWidth(Math.max(stage.getMinWidth(), start[4] + dx));
			if (bottom) stage.setHeight(Math.max(stage.getMinHeight(), start[5] + dy));
			if (left) { double w = Math.max(stage.getMinWidth(), start[4] - dx); stage.setX(start[2] + start[4] - w); stage.setWidth(w); }
			if (top) { double h = Math.max(stage.getMinHeight(), start[5] - dy); stage.setY(start[3] + start[5] - h); stage.setHeight(h); }
			event.consume();
		});
		root.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> root.setUserData(null));
	}

	private static Cursor resizeCursor(MouseEvent event, Node root, double margin) {
		double x=event.getX(), y=event.getY(), w=root.getBoundsInLocal().getWidth(), h=root.getBoundsInLocal().getHeight();
		boolean l=x>=0&&x<=margin, r=x>=w-margin&&x<=w, t=y>=0&&y<=margin, b=y>=h-margin&&y<=h;
		if (t&&l) return Cursor.NW_RESIZE; if (t&&r) return Cursor.NE_RESIZE; if (b&&l) return Cursor.SW_RESIZE; if (b&&r) return Cursor.SE_RESIZE;
		if (l) return Cursor.W_RESIZE; if (r) return Cursor.E_RESIZE; if (t) return Cursor.N_RESIZE; if (b) return Cursor.S_RESIZE; return Cursor.DEFAULT;
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
		double safePrefWidth = Math.max(CONFIRMATION_DIALOG_MIN_WIDTH, minWidth);
		root.setMinWidth(CONFIRMATION_DIALOG_MIN_WIDTH);
		root.setPrefWidth(safePrefWidth);
		root.setMinHeight(CONFIRMATION_DIALOG_MIN_HEIGHT);

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

		FlowPane actionsRow = createActionsRow(actions, value -> {
			result.value = value;
			stage.close();
		});
		actionsRow.setPrefWrapLength(safePrefWidth - root.getPadding().getLeft() - root.getPadding().getRight());
		root.getChildren().add(actionsRow);

		Scene scene = new Scene(root);
		scene.getStylesheets().add(Objects.requireNonNull(
				AppDialogs.class.getResource("/css/app.css")).toExternalForm());
		stage.setScene(scene);
		DialogSizingUtil.applyConfirmationDialogSizing(
				stage,
				owner,
				root,
				safePrefWidth,
				CONFIRMATION_DIALOG_MIN_WIDTH,
				CONFIRMATION_DIALOG_MIN_HEIGHT);
		stage.showAndWait();
		return Optional.ofNullable(result.value);
	}

	static <T> FlowPane createActionsRow(List<DialogAction<T>> actions, Consumer<T> onAction) {
		Objects.requireNonNull(actions, "actions");
		Objects.requireNonNull(onAction, "onAction");
		FlowPane actionsRow = new FlowPane(10, 10);
		actionsRow.setAlignment(Pos.CENTER_RIGHT);
		actionsRow.setMaxWidth(Double.MAX_VALUE);
		actionsRow.getStyleClass().add("app-dialog-actions");
		for (DialogAction<T> action : actions) {
			Button button = ActionButtonFactory.semantic(action.text(), null,
					purposeFor(action.kind()), ControlStyles.Size.STANDARD);
			button.setDefaultButton(action.defaultAction());
			button.setCancelButton(action.cancelAction());
			button.setOnAction(event -> onAction.accept(action.value()));
			actionsRow.getChildren().add(button);
		}
		return actionsRow;
	}

	private static ControlStyles.Purpose purposeFor(DialogActionKind kind) {
		return switch (kind) {
			case PRIMARY -> ControlStyles.Purpose.PRIMARY;
			case SECONDARY -> ControlStyles.Purpose.SECONDARY;
			case DANGER -> ControlStyles.Purpose.DANGER;
		};
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
