package com.shale.ui.component.dialog;

import com.shale.ui.component.NotificationCard;
import com.shale.ui.component.factory.NotificationCardFactory;
import com.shale.ui.notification.AppNotification;
import com.shale.ui.notification.NotificationCenterService;

import java.util.Objects;
import java.util.function.Consumer;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class NotificationCenterDialog {
	private static final double DEFAULT_WIDTH = 760;
	private static final double DEFAULT_HEIGHT = 560;
	private static final double MIN_WIDTH = 640;
	private static final double MIN_HEIGHT = 420;
	private static final double RESIZE_MARGIN = 8;

	private NotificationCenterDialog() {
	}

	public static void show(
			Window owner,
			NotificationCenterService notificationService,
			Consumer<Long> onOpenTask,
			Consumer<Integer> onOpenCase,
			Consumer<AppNotification> onActivateNotification) {
		Objects.requireNonNull(notificationService, "notificationService");

		Stage stage = new Stage();
		AppDialogs.applySecondaryWindowChrome(stage);
		if (owner != null) {
			stage.initOwner(owner);
		}
		stage.initModality(Modality.WINDOW_MODAL);
		stage.setTitle("Notifications");
		stage.setResizable(true);
		stage.setMinWidth(MIN_WIDTH);
		stage.setMinHeight(MIN_HEIGHT);

		Label heading = new Label("Notifications");
		heading.getStyleClass().add("app-dialog-title");
		Label subtitle = new Label("Newest first. Unread items are highlighted.");
		subtitle.getStyleClass().add("notification-window-subtitle");

		Button headerCloseButton = new Button("×");
		headerCloseButton.getStyleClass().addAll("secondary-window-close", "notification-window-close");
		headerCloseButton.setOnAction(event -> stage.close());

		VBox headerText = new VBox(1, heading, subtitle);
		headerText.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(headerText, Priority.ALWAYS);
		HBox header = new HBox(8, headerText, headerCloseButton);
		header.getStyleClass().addAll("secondary-window-header", "notification-window-header");
		header.setAlignment(Pos.CENTER_LEFT);

		NotificationCardFactory cardFactory = new NotificationCardFactory(
				item -> dismissNotification(notificationService, item),
				onOpenCase);

		ListView<AppNotification> listView = new ListView<>();
		listView.setItems(notificationService.getNotificationsNewestFirst());
		listView.getStyleClass().add("notification-list");
		listView.setCellFactory(view -> new NotificationCell(notificationService, onOpenTask, onActivateNotification, cardFactory));
		notificationService.unreadCountProperty().addListener((obs, oldValue, newValue) -> listView.refresh());

		Button markAllReadButton = new Button("Mark all read");
		markAllReadButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
		markAllReadButton.disableProperty().bind(notificationService.unreadCountProperty().lessThanOrEqualTo(0));
		markAllReadButton.setOnAction(event -> notificationService.markAllRead());

		Button closeButton = new Button("Close");
		closeButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
		closeButton.setOnAction(event -> stage.close());
		closeButton.setDefaultButton(true);
		closeButton.setCancelButton(true);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox actions = new HBox(10, markAllReadButton, spacer, closeButton);
		actions.getStyleClass().add("app-dialog-actions");
		actions.setAlignment(Pos.CENTER_RIGHT);

		VBox.setVgrow(listView, Priority.ALWAYS);
		VBox body = new VBox(10, heading, subtitle, listView, actions);
		body.setFillWidth(true);
		body.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		body.setPadding(new Insets(18, 20, 18, 20));
		VBox.setVgrow(body, Priority.ALWAYS);

		VBox root = AppDialogs.createSecondaryWindowShell(stage, "Notifications", stage::close, body);
		root.getStyleClass().add("notification-window-root");
		root.setMinWidth(MIN_WIDTH);
		root.setMinHeight(MIN_HEIGHT);
		root.setPrefWidth(DEFAULT_WIDTH);
		root.setPrefHeight(DEFAULT_HEIGHT);
		installResizeHandlers(stage, root);

		Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
		scene.getStylesheets().add(Objects.requireNonNull(
				NotificationCenterDialog.class.getResource("/css/app.css")).toExternalForm());
		stage.setScene(scene);
		stage.showAndWait();
	}

	public static void show(
			Window owner,
			NotificationCenterService notificationService,
			Consumer<Long> onOpenTask,
			Consumer<AppNotification> onActivateNotification) {
		show(owner, notificationService, onOpenTask, null, onActivateNotification);
	}

	private static void installResizeHandlers(Stage stage, Node root) {
		ResizeState state = new ResizeState();
		root.addEventHandler(MouseEvent.MOUSE_MOVED, event ->
		{
			if (state.edge == ResizeEdge.NONE) {
				root.setCursor(cursorFor(edgeFor(event, root)));
			}
		});
		root.addEventHandler(MouseEvent.MOUSE_EXITED, event ->
		{
			if (state.edge == ResizeEdge.NONE) {
				root.setCursor(Cursor.DEFAULT);
			}
		});
		root.addEventFilter(MouseEvent.MOUSE_PRESSED, event ->
		{
			ResizeEdge edge = edgeFor(event, root);
			if (edge == ResizeEdge.NONE) {
				return;
			}
			state.edge = edge;
			state.startScreenX = event.getScreenX();
			state.startScreenY = event.getScreenY();
			state.startX = stage.getX();
			state.startY = stage.getY();
			state.startWidth = stage.getWidth();
			state.startHeight = stage.getHeight();
			event.consume();
		});
		root.addEventFilter(MouseEvent.MOUSE_DRAGGED, event ->
		{
			if (state.edge == ResizeEdge.NONE) {
				return;
			}
			resizeStage(stage, state, event);
			event.consume();
		});
		root.addEventFilter(MouseEvent.MOUSE_RELEASED, event ->
		{
			state.edge = ResizeEdge.NONE;
			root.setCursor(cursorFor(edgeFor(event, root)));
		});
	}

	private static ResizeEdge edgeFor(MouseEvent event, Node root) {
		double x = event.getX();
		double y = event.getY();
		double width = root.getBoundsInLocal().getWidth();
		double height = root.getBoundsInLocal().getHeight();
		boolean left = x >= 0 && x <= RESIZE_MARGIN;
		boolean right = x >= width - RESIZE_MARGIN && x <= width;
		boolean top = y >= 0 && y <= RESIZE_MARGIN;
		boolean bottom = y >= height - RESIZE_MARGIN && y <= height;
		if (top && left)
			return ResizeEdge.TOP_LEFT;
		if (top && right)
			return ResizeEdge.TOP_RIGHT;
		if (bottom && left)
			return ResizeEdge.BOTTOM_LEFT;
		if (bottom && right)
			return ResizeEdge.BOTTOM_RIGHT;
		if (left)
			return ResizeEdge.LEFT;
		if (right)
			return ResizeEdge.RIGHT;
		if (top)
			return ResizeEdge.TOP;
		if (bottom)
			return ResizeEdge.BOTTOM;
		return ResizeEdge.NONE;
	}

	private static Cursor cursorFor(ResizeEdge edge) {
		return switch (edge) {
		case TOP_LEFT, BOTTOM_RIGHT -> Cursor.NW_RESIZE;
		case TOP_RIGHT, BOTTOM_LEFT -> Cursor.NE_RESIZE;
		case LEFT, RIGHT -> Cursor.E_RESIZE;
		case TOP, BOTTOM -> Cursor.N_RESIZE;
		case NONE -> Cursor.DEFAULT;
		};
	}

	private static void resizeStage(Stage stage, ResizeState state, MouseEvent event) {
		double deltaX = event.getScreenX() - state.startScreenX;
		double deltaY = event.getScreenY() - state.startScreenY;
		if (state.edge.resizesRight()) {
			stage.setWidth(Math.max(stage.getMinWidth(), state.startWidth + deltaX));
		}
		if (state.edge.resizesBottom()) {
			stage.setHeight(Math.max(stage.getMinHeight(), state.startHeight + deltaY));
		}
		if (state.edge.resizesLeft()) {
			double newWidth = Math.max(stage.getMinWidth(), state.startWidth - deltaX);
			stage.setX(state.startX + state.startWidth - newWidth);
			stage.setWidth(newWidth);
		}
		if (state.edge.resizesTop()) {
			double newHeight = Math.max(stage.getMinHeight(), state.startHeight - deltaY);
			stage.setY(state.startY + state.startHeight - newHeight);
			stage.setHeight(newHeight);
		}
	}

	private static void dismissNotification(NotificationCenterService notificationService, AppNotification item) {
		if (item == null) {
			return;
		}
		try {
			notificationService.dismiss(item);
		} catch (RuntimeException ex) {
			System.err.println("[NotificationCenterDialog] dismiss failed for notification id=" + item.getId());
			ex.printStackTrace(System.err);
			throw ex;
		}
	}

	private enum ResizeEdge {
		NONE,
		LEFT,
		RIGHT,
		TOP,
		BOTTOM,
		TOP_LEFT,
		TOP_RIGHT,
		BOTTOM_LEFT,
		BOTTOM_RIGHT;

		private boolean resizesLeft() {
			return this == LEFT || this == TOP_LEFT || this == BOTTOM_LEFT;
		}

		private boolean resizesRight() {
			return this == RIGHT || this == TOP_RIGHT || this == BOTTOM_RIGHT;
		}

		private boolean resizesTop() {
			return this == TOP || this == TOP_LEFT || this == TOP_RIGHT;
		}

		private boolean resizesBottom() {
			return this == BOTTOM || this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
		}
	}

	private static final class ResizeState {
		private ResizeEdge edge = ResizeEdge.NONE;
		private double startScreenX;
		private double startScreenY;
		private double startX;
		private double startY;
		private double startWidth;
		private double startHeight;
	}

	private static final class NotificationCell extends ListCell<AppNotification> {
		private final NotificationCenterService notificationService;
		private final Consumer<AppNotification> onActivateNotification;
		private final Consumer<Long> onOpenTask;
		private final NotificationCardFactory notificationCardFactory;
		private final ChangeListener<Boolean> unreadListener = (obs, oldValue, newValue) -> updateUnreadStyle();
		private AppNotification observedItem;

		private NotificationCell(
				NotificationCenterService notificationService,
				Consumer<Long> onOpenTask,
				Consumer<AppNotification> onActivateNotification,
				NotificationCardFactory notificationCardFactory) {
			this.notificationService = notificationService;
			this.onActivateNotification = onActivateNotification;
			this.onOpenTask = onOpenTask;
			this.notificationCardFactory = Objects.requireNonNull(notificationCardFactory, "notificationCardFactory");
			setOnMouseClicked(event ->
			{
				if (event.getButton() != MouseButton.PRIMARY || isFromInteractiveChild(event)) {
					return;
				}
				AppNotification selected = getItem();
				if (selected != null) {
					notificationService.markRead(selected);
					Long taskId = resolveTaskId(selected);
					if (taskId != null && onOpenTask != null) {
						onOpenTask.accept(taskId);
					} else if (this.onActivateNotification != null) {
						this.onActivateNotification.accept(selected);
					}
				}
			});
		}

		@Override
		protected void updateItem(AppNotification item, boolean empty) {
			super.updateItem(item, empty);
			if (observedItem != null) {
				observedItem.unreadProperty().removeListener(unreadListener);
				observedItem = null;
			}
			if (empty || item == null) {
				setText(null);
				setGraphic(null);
				return;
			}
			observedItem = item;
			observedItem.unreadProperty().addListener(unreadListener);
			setText(null);
			setGraphic(notificationCardFactory.create(
					new NotificationCardFactory.NotificationCardModel(item),
					NotificationCardFactory.Variant.CENTER_ROW));
			updateUnreadStyle();
		}

		private void updateUnreadStyle() {
			if (getGraphic() instanceof NotificationCard card) {
				AppNotification item = getItem();
				card.setUnread(item != null && item.isUnread());
			}
		}

		private static boolean isFromInteractiveChild(MouseEvent event) {
			if (event == null || !(event.getTarget() instanceof Node node)) {
				return false;
			}
			return hasStyleClassInAncestorChain(node, "notification-row-dismiss")
					|| hasStyleClassInAncestorChain(node, "notification-row-expand")
					|| hasStyleClassInAncestorChain(node, "notification-row-case-mini");
		}

		private static boolean hasStyleClassInAncestorChain(Node node, String styleClass) {
			Node current = node;
			while (current != null) {
				if (current.getStyleClass().contains(styleClass)) {
					return true;
				}
				current = current.getParent();
			}
			return false;
		}

		private static Long resolveTaskId(AppNotification item) {
			if (item == null || item.getEntityId() == null || item.getEntityId() <= 0) {
				return null;
			}
			String entityType = item.getEntityType();
			if (entityType == null || !"TASK".equalsIgnoreCase(entityType.trim())) {
				return null;
			}
			return item.getEntityId();
		}
	}
}
