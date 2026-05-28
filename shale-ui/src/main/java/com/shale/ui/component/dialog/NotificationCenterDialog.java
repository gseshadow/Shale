package com.shale.ui.component.dialog;

import com.shale.ui.component.NotificationCard;
import com.shale.ui.component.factory.NotificationCardFactory;
import com.shale.ui.notification.AppNotification;
import com.shale.ui.notification.NotificationCenterService;
import com.shale.ui.notification.NotificationCategory;
import com.shale.ui.notification.NotificationGroup;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.beans.binding.Bindings;
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

		ChoiceBox<CategoryFilter> categoryFilter = new ChoiceBox<>();
		categoryFilter.getStyleClass().add("app-toolbar-select");
		categoryFilter.setMinWidth(132);
		categoryFilter.setMaxWidth(150);

		CheckBox unreadOnlyFilter = new CheckBox("Unread only");
		unreadOnlyFilter.getStyleClass().add("notification-filter-check");

		TextField searchField = new TextField();
		searchField.setPromptText("Search notifications");
		searchField.getStyleClass().add("app-dialog-search-field");
		searchField.setMinWidth(0);
		searchField.setPrefWidth(220);
		searchField.setMaxWidth(Double.MAX_VALUE);

		HBox filterRow = new HBox(10, categoryFilter, unreadOnlyFilter, searchField);
		filterRow.getStyleClass().add("notification-filter-row");
		filterRow.setAlignment(Pos.CENTER_LEFT);
		filterRow.setMinWidth(0);
		filterRow.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(searchField, Priority.ALWAYS);

		ObservableList<NotificationGroup> notificationGroups = FXCollections.observableArrayList();
		Runnable rebuildGroups = () -> rebuildNotificationGroups(
				notificationService,
				notificationGroups,
				categoryFilter.getValue(),
				unreadOnlyFilter.isSelected(),
				searchField.getText());
		rebuildCategoryOptions(notificationService, categoryFilter);
		rebuildGroups.run();
		ListChangeListener<AppNotification> groupRebuildListener = change -> {
			rebuildCategoryOptions(notificationService, categoryFilter);
			rebuildGroups.run();
		};
		notificationService.getNotificationsNewestFirst().addListener(groupRebuildListener);
		ChangeListener<Object> filterChangeListener = (obs, oldValue, newValue) -> rebuildGroups.run();
		categoryFilter.getSelectionModel().selectedItemProperty().addListener(filterChangeListener);
		unreadOnlyFilter.selectedProperty().addListener(filterChangeListener);
		searchField.textProperty().addListener(filterChangeListener);

		MenuItem dismissReadItem = new MenuItem("Read");
		MenuItem dismissOlderItem = new MenuItem("Older than 30 days");
		MenuButton cleanupMenuButton = new MenuButton("Dismiss ▼", null, dismissReadItem, dismissOlderItem);
		cleanupMenuButton.getStyleClass().addAll("app-toolbar-button", "app-toolbar-button-neutral");
		Runnable updateCleanupMenuState = () -> updateCleanupMenuState(
				notificationService,
				dismissReadItem,
				dismissOlderItem,
				cleanupMenuButton);
		notificationGroups.addListener((ListChangeListener<NotificationGroup>) change -> updateCleanupMenuState.run());
		updateCleanupMenuState.run();

		ListView<NotificationGroup> listView = new ListView<>();
		listView.setMinWidth(0);
		listView.setMaxWidth(Double.MAX_VALUE);
		listView.setItems(notificationGroups);
		listView.getStyleClass().add("notification-list");
		listView.setCellFactory(view -> new NotificationCell(notificationService, onOpenTask, onActivateNotification, cardFactory));
		ChangeListener<Number> unreadRefreshListener = (obs, oldValue, newValue) -> {
			rebuildGroups.run();
			updateCleanupMenuState.run();
			listView.refresh();
		};
		notificationService.unreadCountProperty().addListener(unreadRefreshListener);
		stage.setOnHidden(event -> {
			notificationService.getNotificationsNewestFirst().removeListener(groupRebuildListener);
			notificationService.unreadCountProperty().removeListener(unreadRefreshListener);
		});

		Button markAllReadButton = new Button("Mark all read");
		markAllReadButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
		markAllReadButton.disableProperty().bind(notificationService.unreadCountProperty().lessThanOrEqualTo(0));
		markAllReadButton.setOnAction(event -> notificationService.markAllRead());

		dismissReadItem.setOnAction(event -> dismissReadNotifications(stage, notificationService));
		dismissOlderItem.setOnAction(event -> dismissOlderNotifications(stage, notificationService));

		Button closeButton = new Button("Close");
		closeButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
		closeButton.setOnAction(event -> stage.close());
		closeButton.setDefaultButton(true);
		closeButton.setCancelButton(true);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox actions = new HBox(10, markAllReadButton, cleanupMenuButton, spacer, closeButton);
		actions.getStyleClass().add("app-dialog-actions");
		actions.setAlignment(Pos.CENTER_RIGHT);

		VBox.setVgrow(listView, Priority.ALWAYS);
		VBox body = new VBox(10, heading, subtitle, filterRow, listView, actions);
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

	private static void rebuildNotificationGroups(
			NotificationCenterService notificationService,
			ObservableList<NotificationGroup> notificationGroups,
			CategoryFilter categoryFilter,
			boolean unreadOnly,
			String searchText) {
		CategoryFilter effectiveCategory = categoryFilter == null ? CategoryFilter.ALL : categoryFilter;
		String normalizedSearch = normalizeSearch(searchText);
		Map<String, List<AppNotification>> grouped = new LinkedHashMap<>();
		for (AppNotification notification : notificationService.getNotificationsNewestFirst()) {
			if (!matchesFilters(notification, effectiveCategory, unreadOnly, normalizedSearch)) {
				continue;
			}
			String groupKey = NotificationGroup.groupKeyFor(notification);
			grouped.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(notification);
		}
		List<NotificationGroup> groups = grouped.entrySet().stream()
				.map(entry -> new NotificationGroup(entry.getKey(), entry.getValue()))
				.sorted(Comparator.comparing(NotificationGroup::getLatestCreatedAt).reversed())
				.toList();
		notificationGroups.setAll(groups);
	}

	private static void rebuildCategoryOptions(
			NotificationCenterService notificationService,
			ChoiceBox<CategoryFilter> categoryFilter) {
		if (categoryFilter == null) {
			return;
		}
		CategoryFilter selected = categoryFilter.getValue();
		List<CategoryFilter> options = CategoryFilter.optionsFor(notificationService.getNotificationsNewestFirst());
		categoryFilter.getItems().setAll(options);
		if (selected != null && options.contains(selected)) {
			categoryFilter.getSelectionModel().select(selected);
		} else {
			categoryFilter.getSelectionModel().select(CategoryFilter.ALL);
		}
	}

	private static boolean matchesFilters(
			AppNotification notification,
			CategoryFilter categoryFilter,
			boolean unreadOnly,
			String normalizedSearch) {
		if (notification == null) {
			return false;
		}
		if (!categoryFilter.matches(notification.getCategory())) {
			return false;
		}
		if (unreadOnly && !notification.isUnread()) {
			return false;
		}
		return matchesSearch(notification, normalizedSearch);
	}

	private static boolean matchesSearch(AppNotification notification, String normalizedSearch) {
		if (normalizedSearch == null || normalizedSearch.isEmpty()) {
			return true;
		}
		return containsSearch(notification.getTitle(), normalizedSearch)
				|| containsSearch(notification.getMessage(), normalizedSearch)
				|| containsSearch(notification.getEntityTitle(), normalizedSearch)
				|| containsSearch(notification.getCaseName(), normalizedSearch)
				|| containsSearch(notification.getActorDisplayName(), normalizedSearch);
	}

	private static boolean containsSearch(String value, String normalizedSearch) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);
	}

	private static String normalizeSearch(String searchText) {
		return searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
	}

	private static void updateCleanupMenuState(
			NotificationCenterService notificationService,
			MenuItem dismissReadItem,
			MenuItem dismissOlderItem,
			MenuButton cleanupMenuButton) {
		int readCount = readNotifications(notificationService).size();
		int olderCount = olderNotifications(notificationService).size();
		dismissReadItem.setDisable(readCount == 0);
		dismissOlderItem.setDisable(olderCount == 0);
		cleanupMenuButton.setDisable(readCount == 0 && olderCount == 0);
	}

	private static void dismissReadNotifications(Stage owner, NotificationCenterService notificationService) {
		List<AppNotification> read = readNotifications(notificationService);
		if (read.isEmpty()) {
			return;
		}
		if (read.size() > 1 && !confirmDismiss(owner, "Dismiss all read?", read.size())) {
			return;
		}
		dismissNotifications(notificationService, read, "dismiss all read");
	}

	private static void dismissOlderNotifications(Stage owner, NotificationCenterService notificationService) {
		List<AppNotification> older = olderNotifications(notificationService);
		if (older.isEmpty()) {
			return;
		}
		if (older.size() > 1 && !confirmDismiss(owner, "Dismiss notifications older than 30 days?", older.size())) {
			return;
		}
		dismissNotifications(notificationService, older, "dismiss older than 30 days");
	}

	private static List<AppNotification> readNotifications(NotificationCenterService notificationService) {
		return notificationService.getNotificationsNewestFirst().stream()
				.filter(notification -> notification != null && !notification.isUnread())
				.toList();
	}

	private static List<AppNotification> olderNotifications(NotificationCenterService notificationService) {
		Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
		return notificationService.getNotificationsNewestFirst().stream()
				.filter(notification -> notification != null && notification.getCreatedAt().isBefore(cutoff))
				.toList();
	}

	private static boolean confirmDismiss(Stage owner, String title, int count) {
		ButtonType dismissType = new ButtonType("Dismiss", ButtonBar.ButtonData.OK_DONE);
		Alert alert = new Alert(
				Alert.AlertType.CONFIRMATION,
				"This will dismiss " + count + " notification" + (count == 1 ? "" : "s") + ". This does not delete them.",
				dismissType,
				ButtonType.CANCEL);
		alert.setTitle("Confirm notification cleanup");
		alert.setHeaderText(title);
		if (owner != null) {
			alert.initOwner(owner);
		}
		return alert.showAndWait().filter(dismissType::equals).isPresent();
	}

	private static void dismissNotifications(
			NotificationCenterService notificationService,
			List<AppNotification> notifications,
			String actionDescription) {
		try {
			notificationService.dismissAll(notifications);
		} catch (RuntimeException ex) {
			System.err.println("[NotificationCenterDialog] " + actionDescription + " failed for count=" + notifications.size());
			ex.printStackTrace(System.err);
			throw ex;
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

	private enum CategoryFilter {
		ALL("All"),
		TASKS("Tasks", NotificationCategory.TASK),
		CALENDAR("Calendar", NotificationCategory.CALENDAR),
		CASES("Cases", NotificationCategory.CASE),
		SYSTEM("System", NotificationCategory.SYSTEM),
		APP("App", NotificationCategory.APP_UPDATE),
		NETWORK("Network", NotificationCategory.NETWORK, NotificationCategory.CONNECTIVITY);

		private final String label;
		private final List<NotificationCategory> categories;

		CategoryFilter(String label, NotificationCategory... categories) {
			this.label = label;
			this.categories = List.of(categories);
		}

		private boolean matches(NotificationCategory category) {
			return this == ALL || categories.contains(category);
		}

		private boolean isAvailableFor(List<AppNotification> notifications) {
			if (this == ALL || this == TASKS || this == CALENDAR) {
				return true;
			}
			return notifications.stream()
					.map(AppNotification::getCategory)
					.anyMatch(categories::contains);
		}

		private static List<CategoryFilter> optionsFor(List<AppNotification> notifications) {
			List<AppNotification> safeNotifications = notifications == null ? List.of() : notifications;
			return List.of(values()).stream()
					.filter(option -> option.isAvailableFor(safeNotifications))
					.toList();
		}

		@Override
		public String toString() {
			return label;
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

	private static final class NotificationCell extends ListCell<NotificationGroup> {
		private static final double LIST_VIEW_WIDTH_GUTTER = 34;

		private final NotificationCenterService notificationService;
		private final Consumer<AppNotification> onActivateNotification;
		private final Consumer<Long> onOpenTask;
		private final NotificationCardFactory notificationCardFactory;
		private NotificationCard boundCard;

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
				NotificationGroup selected = getItem();
				if (selected != null) {
					notificationService.markReadMatching(selected.getNotificationsNewestFirst()::contains);
					Long taskId = selected.getTaskId();
					if (taskId != null && onOpenTask != null) {
						onOpenTask.accept(taskId);
					} else if (this.onActivateNotification != null) {
						this.onActivateNotification.accept(selected.getLatestNotification());
					}
				}
			});
		}

		@Override
		protected void updateItem(NotificationGroup item, boolean empty) {
			super.updateItem(item, empty);
			clearBoundGraphic();
			if (empty || item == null) {
				setText(null);
				return;
			}
			setText(null);
			NotificationCard card = notificationCardFactory.create(
					new NotificationCardFactory.NotificationCardModel(item),
					NotificationCardFactory.Variant.CENTER_ROW);
			card.setMinWidth(0);
			card.prefWidthProperty().bind(Bindings.max(0, getListView().widthProperty().subtract(LIST_VIEW_WIDTH_GUTTER)));
			card.maxWidthProperty().bind(card.prefWidthProperty());
			boundCard = card;
			setGraphic(card);
		}

		private void clearBoundGraphic() {
			if (boundCard != null) {
				boundCard.prefWidthProperty().unbind();
				boundCard.maxWidthProperty().unbind();
				boundCard = null;
			}
			setGraphic(null);
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

	}
}
