package com.shale.ui.controller;

import com.shale.core.model.CalendarCaseFilterOptions;
import com.shale.core.model.CalendarFeedCategory;
import com.shale.core.model.CalendarFeedClickTarget;
import com.shale.core.model.CalendarFeedFilters;
import com.shale.core.model.CalendarFeedItem;
import com.shale.core.model.CalendarFeedSourceFilter;
import com.shale.core.model.CalendarOverlaySelection;
import com.shale.data.dao.CalendarFeedDao;
import com.shale.data.dao.CaseSummaryDao;
import com.shale.ui.component.dialog.NewCalendarEventDialog;
import com.shale.ui.component.dialog.NewEventWizard;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.CaseServicePort.CreateCaseDateCommand;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.factory.CalendarEventCardFactory;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.TaskCardFactory;
import com.shale.ui.services.CalendarService;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.services.LiveUpdateEvents;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ColorUtil;
import com.shale.ui.util.ControlStyles;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.css.PseudoClass;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.util.Duration;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import com.shale.ui.util.PerfLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CalendarController {
    private static final Logger log = LoggerFactory.getLogger(CalendarController.class);
    private static final DateTimeFormatter WEEK_RANGE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DAY_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_RANGE_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final String VIEW_WEEK = "Week";
    private static final String VIEW_FIVE_DAY = "5 Day";
    private static final String VIEW_DAY = "Day";
    private static final String VIEW_MONTH = "Month";
    private static final double HALF_HOUR_HEIGHT = 34.0;
    private static final PseudoClass TODAY_PSEUDO_CLASS = PseudoClass.getPseudoClass("today");
    private static final PseudoClass WEEKEND_PSEUDO_CLASS = PseudoClass.getPseudoClass("weekend");
    private static final PseudoClass HOUR_PSEUDO_CLASS = PseudoClass.getPseudoClass("hour");
    private static final PseudoClass HALF_HOUR_PSEUDO_CLASS = PseudoClass.getPseudoClass("half-hour");
    private static final CalendarCaseFilterOptions.CaseOption ALL_CASES_OPTION = CalendarCaseFilterOptions.ALL_CASES;
    private static final EventTypeFilterOption ALL_TYPES_OPTION = new EventTypeFilterOption("", "All types");

    @FXML private ToggleButton weekViewButton;
    @FXML private ToggleButton fiveDayViewButton;
    @FXML private ToggleButton dayViewButton;
    @FXML private ToggleButton monthViewButton;
    @FXML private Button todayButton;
    @FXML private Button prevWeekButton;
    @FXML private Button nextWeekButton;
    @FXML private Button newEventButton;
    @FXML private Label weekRangeLabel;
    @FXML private Label calendarLoadingLabel;
    @FXML private Label calendarErrorLabel;
    @FXML private HBox weekBoard;
    @FXML private TextField searchTextField;
    @FXML private ComboBox<CalendarCaseFilterOptions.CaseOption> caseFilterCombo;
    @FXML private ComboBox<EventTypeFilterOption> eventTypeFilterCombo;
    @FXML private Button clearFiltersButton;
    @FXML private VBox calendarRowsBox;
    @FXML private Button selectAllCalendarsButton;
    @FXML private Button clearAllCalendarsButton;
    @FXML private Button resetCalendarsButton;
    @FXML private CheckBox eventsLayerCheckBox;
    @FXML private CheckBox tasksLayerCheckBox;
    @FXML private CheckBox deadlinesLayerCheckBox;
    @FXML private CheckBox caseDatesLayerCheckBox;

    private AppState appState;
    private CalendarService calendarService;
    private CalendarFeedDao calendarFeedDao;
    private Consumer<Integer> onOpenCase;
    private BiConsumer<Integer, Long> onOpenCaseDates;
    private Consumer<Long> onOpenTask;
    private CaseTaskService caseTaskService;
    private CaseSummaryDao caseSummaryDao;
    private CaseServicePort caseService;
    private UiRuntimeBridge runtimeBridge;
    private final AtomicBoolean caseDatesRefreshQueued = new AtomicBoolean();
    private final Set<String> seenCaseDatesEventIds = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Consumer<UiRuntimeBridge.EntityUpdatedEvent> entityUpdatedHandler = this::handleEntityUpdated;
    private int loadGeneration;
    private LocalDate selectedDate;
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(300));
    private List<CalendarFeedItem> loadedItems = List.of();
    private String searchText = "";
    private Integer selectedCaseId;
    private String selectedEventTypeKey = "";
    private final Set<Integer> openingEditDialogEventIds = new HashSet<>();
    private boolean autoScrollTimedViewsPending = false;
    private LocalDate lastLoadedRangeStart;
    private LocalDate lastLoadedRangeEndInclusive;
    private boolean suppressAutoScroll;
    private ScrollPane timedScrollPane;
    private boolean allDayCollapsed;
    private CalendarFeedSourceFilter sourceFilter = CalendarFeedSourceFilter.defaults();
    private CalendarOverlaySelection calendarOverlaySelection = CalendarOverlaySelection.defaults(null);
    private final Map<Integer, ToggleButton> userCalendarButtons = new LinkedHashMap<>();
    private ToggleButton sharedCalendarButton;
    private boolean suppressOverlayControlEvents;

    private final CalendarEventCardFactory calendarEventCardFactory = new CalendarEventCardFactory();
    private CaseCardFactory caseCardFactory = new CaseCardFactory(id -> {});
    private TaskCardFactory taskCardFactory = new TaskCardFactory(id -> {}, id -> {}, id -> {}, id -> {});
    private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "calendar-feed-loader"); t.setDaemon(true); return t; });

    public void init(AppState appState, CalendarService calendarService, CalendarFeedDao calendarFeedDao, CaseTaskService caseTaskService, CaseSummaryDao caseSummaryDao, CaseServicePort caseService, UiRuntimeBridge runtimeBridge, Consumer<Integer> onOpenCase, BiConsumer<Integer, Long> onOpenCaseDates, Consumer<Long> onOpenTask) {
        this.appState = appState; this.calendarService = calendarService; this.calendarFeedDao = calendarFeedDao;
        this.caseTaskService = caseTaskService;
        this.caseSummaryDao = caseSummaryDao;
        this.caseService = caseService;
        this.runtimeBridge = runtimeBridge;
        this.onOpenCase = onOpenCase == null ? id -> {} : onOpenCase;
        this.onOpenCaseDates = onOpenCaseDates == null ? (caseId, caseDateId) -> {} : onOpenCaseDates;
        this.onOpenTask = onOpenTask == null ? id -> {} : onOpenTask;
        resetCalendarOverlayDefaults();
        configureCalendarOverlayControls();
        this.caseCardFactory = new CaseCardFactory(this.onOpenCase);
        this.taskCardFactory = new TaskCardFactory(this.onOpenTask, id -> {}, this.onOpenCase, id -> {});
        if (runtimeBridge != null) runtimeBridge.subscribeEntityUpdated(entityUpdatedHandler);
    }

    private void handleEntityUpdated(UiRuntimeBridge.EntityUpdatedEvent event) {
        if (event == null || !LiveUpdateEvents.ENTITY_CASE_DATES.equals(event.entityType()) || appState == null) return;
        Integer tenantId = appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0 || event.shaleClientId() != tenantId || event.entityId() <= 0) return;
        String localInstance = runtimeBridge == null ? "" : runtimeBridge.getClientInstanceId();
        if (localInstance != null && !localInstance.isBlank() && localInstance.equals(event.clientInstanceId())) return;
        if (!rememberCaseDatesEvent(event.eventId()) || !caseDatesRefreshQueued.compareAndSet(false, true)) return;
        Platform.runLater(() -> {
            caseDatesRefreshQueued.set(false);
            loadCurrentRange(false); // load generation discards any older in-flight response
        });
    }

    private boolean rememberCaseDatesEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) return true;
        synchronized (seenCaseDatesEventIds) {
            if (!seenCaseDatesEventIds.add(eventId)) return false;
            while (seenCaseDatesEventIds.size() > 256) seenCaseDatesEventIds.remove(seenCaseDatesEventIds.iterator().next());
            return true;
        }
    }

    @FXML private void initialize() {
        configureSemanticControls();
        configureViewModeSelector();
        selectedDate = LocalDate.now();
        configureFilters();
        configureSourceLayerFilters();
        configureCalendarOverlayControls();
        renderCurrentShell();
        Platform.runLater(() -> loadCurrentRange(false));
    }

    private void configureSemanticControls() {
        ControlStyles.apply(todayButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
        ControlStyles.apply(prevWeekButton, ControlStyles.Purpose.NAVIGATION, ControlStyles.Size.SMALL);
        ControlStyles.iconOnly(prevWeekButton);
        prevWeekButton.setAccessibleText("Previous calendar period");
        Tooltip.install(prevWeekButton, new Tooltip("Previous period"));
        ControlStyles.apply(nextWeekButton, ControlStyles.Purpose.NAVIGATION, ControlStyles.Size.SMALL);
        ControlStyles.iconOnly(nextWeekButton);
        nextWeekButton.setAccessibleText("Next calendar period");
        Tooltip.install(nextWeekButton, new Tooltip("Next period"));
        ControlStyles.apply(newEventButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
        ControlStyles.apply(selectAllCalendarsButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
        ControlStyles.apply(clearAllCalendarsButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
        ControlStyles.apply(resetCalendarsButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
        ControlStyles.apply(clearFiltersButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
        ControlStyles.formControl(searchTextField);
        ControlStyles.formControl(caseFilterCombo);
        ControlStyles.formControl(eventTypeFilterCombo);
    }

    private void configureViewModeSelector() {
        ToggleGroup group = new ToggleGroup();
        List.of(weekViewButton, fiveDayViewButton, dayViewButton, monthViewButton).forEach(button -> button.setToggleGroup(group));
        weekViewButton.setUserData(VIEW_WEEK);
        fiveDayViewButton.setUserData(VIEW_FIVE_DAY);
        dayViewButton.setUserData(VIEW_DAY);
        monthViewButton.setUserData(VIEW_MONTH);
        weekViewButton.setSelected(true);
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                if (oldToggle != null) oldToggle.setSelected(true);
                return;
            }
            if (oldToggle != null && oldToggle != newToggle) loadCurrentRange(false);
        });
    }

    private String selectedViewMode() {
        for (ToggleButton button : List.of(weekViewButton, fiveDayViewButton, dayViewButton, monthViewButton)) {
            if (button != null && button.isSelected()) return String.valueOf(button.getUserData());
        }
        return VIEW_WEEK;
    }
    private void configureFilters() {
        caseFilterCombo.setButtonCell(new ListCell<>() { @Override protected void updateItem(CalendarCaseFilterOptions.CaseOption item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? "All cases" : item.displayName()); }});
        caseFilterCombo.setCellFactory(v -> new ListCell<>() { @Override protected void updateItem(CalendarCaseFilterOptions.CaseOption item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? "" : item.displayName()); }});
        caseFilterCombo.valueProperty().addListener((obs, o, n) -> {
            selectedCaseId = (n == null || n.isAll()) ? null : n.caseId();
            updateClearFiltersState();
            applyFiltersAndRender();
        });
        eventTypeFilterCombo.setButtonCell(new ListCell<>() { @Override protected void updateItem(EventTypeFilterOption item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? "All types" : item.displayName()); }});
        eventTypeFilterCombo.setCellFactory(v -> new ListCell<>() { @Override protected void updateItem(EventTypeFilterOption item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? "" : item.displayName()); }});
        eventTypeFilterCombo.valueProperty().addListener((obs, o, n) -> {
            selectedEventTypeKey = (n == null || n.isAll()) ? "" : safe(n.matchKey());
            updateClearFiltersState();
            applyFiltersAndRender();
        });
        searchTextField.textProperty().addListener((obs, o, n) -> { searchDebounce.stop(); searchDebounce.setOnFinished(evt -> { searchText = safe(n).trim(); updateClearFiltersState(); applyFiltersAndRender(); }); searchDebounce.playFromStart(); });
        caseFilterCombo.getItems().setAll(ALL_CASES_OPTION);
        caseFilterCombo.setValue(ALL_CASES_OPTION);
        eventTypeFilterCombo.getItems().setAll(ALL_TYPES_OPTION);
        eventTypeFilterCombo.setValue(ALL_TYPES_OPTION);
        updateClearFiltersState();
    }

    private void configureSourceLayerFilters() {
        setLayerDefaults();
        configureLayerCheckBox(eventsLayerCheckBox, "Show calendar events layer");
        configureLayerCheckBox(tasksLayerCheckBox, "Show task due dates layer");
        configureLayerCheckBox(deadlinesLayerCheckBox, "Show case deadlines layer");
        configureLayerCheckBox(caseDatesLayerCheckBox, "Show other case dates layer");
        updateSourceFilterFromControls();
        updateClearFiltersState();
    }

    private void configureLayerCheckBox(CheckBox checkBox, String accessibleText) {
        if (checkBox == null) return;
        checkBox.setAccessibleText(accessibleText);
        checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            updateSourceFilterFromControls();
            updateClearFiltersState();
            applyFiltersAndRender();
        });
    }

    private void setLayerDefaults() {
        if (eventsLayerCheckBox != null) eventsLayerCheckBox.setSelected(true);
        if (tasksLayerCheckBox != null) tasksLayerCheckBox.setSelected(true);
        if (deadlinesLayerCheckBox != null) deadlinesLayerCheckBox.setSelected(true);
        if (caseDatesLayerCheckBox != null) caseDatesLayerCheckBox.setSelected(false);
        sourceFilter = CalendarFeedSourceFilter.defaults();
    }

    private void updateSourceFilterFromControls() {
        EnumSet<CalendarFeedCategory> enabled = EnumSet.noneOf(CalendarFeedCategory.class);
        if (eventsLayerCheckBox == null || eventsLayerCheckBox.isSelected()) enabled.add(CalendarFeedCategory.CALENDAR_EVENTS);
        if (tasksLayerCheckBox == null || tasksLayerCheckBox.isSelected()) enabled.add(CalendarFeedCategory.TASKS);
        if (deadlinesLayerCheckBox == null || deadlinesLayerCheckBox.isSelected()) enabled.add(CalendarFeedCategory.CASE_DEADLINES);
        if (caseDatesLayerCheckBox != null && caseDatesLayerCheckBox.isSelected()) enabled.add(CalendarFeedCategory.OTHER_CASE_DATES);
        sourceFilter = new CalendarFeedSourceFilter(enabled);
    }


    private void configureCalendarOverlayControls() {
        if (calendarRowsBox == null) return;
        calendarRowsBox.getChildren().clear();
        userCalendarButtons.clear();
        sharedCalendarButton = createCalendarRowButton("Shared Calendar", "Shared Calendar", null, calendarOverlaySelection == null || calendarOverlaySelection.sharedEnabled(), true);
        calendarRowsBox.getChildren().add(sharedCalendarButton);

        Integer currentUserId = currentUserId();
        List<NewCalendarEventDialog.AssignedUserOption> users = assignedUserOptionsForPicker(appState == null || appState.getShaleClientId() == null ? 0 : appState.getShaleClientId(), currentUserId);
        NewCalendarEventDialog.AssignedUserOption currentUser = users.stream()
                .filter(user -> user != null && Objects.equals(user.userId(), currentUserId))
                .findFirst()
                .orElse(null);
        if (currentUser != null) {
            ToggleButton mine = createCalendarRowButton("My Calendar", "My Calendar (" + safe(currentUser.displayName()) + ")", currentUser.color(), calendarOverlaySelection != null && calendarOverlaySelection.enabledUserIds().contains(currentUser.userId()), false);
            userCalendarButtons.put(currentUser.userId(), mine);
            calendarRowsBox.getChildren().add(mine);
        }

        List<NewCalendarEventDialog.AssignedUserOption> otherUsers = users.stream()
                .filter(user -> user != null && user.userId() != null && user.userId() > 0 && !Objects.equals(user.userId(), currentUserId))
                .sorted(Comparator.comparing((NewCalendarEventDialog.AssignedUserOption user) -> safe(user.displayName()).toLowerCase(Locale.ROOT))
                        .thenComparing(user -> user.userId() == null ? Integer.MAX_VALUE : user.userId()))
                .toList();
        if (!otherUsers.isEmpty()) {
            Label usersLabel = new Label("Users");
            usersLabel.getStyleClass().add("calendar-sidebar-section-heading");
            calendarRowsBox.getChildren().add(usersLabel);
            for (NewCalendarEventDialog.AssignedUserOption user : otherUsers) {
                ToggleButton button = createCalendarRowButton(safe(user.displayName()), "Calendar for " + safe(user.displayName()), user.color(), calendarOverlaySelection != null && calendarOverlaySelection.enabledUserIds().contains(user.userId()), false);
                userCalendarButtons.put(user.userId(), button);
                calendarRowsBox.getChildren().add(button);
            }
        }
        configureCalendarBulkActionTooltips();
    }

    private ToggleButton createCalendarRowButton(String labelText, String accessibleText, String color, boolean selected, boolean shared) {
        ToggleButton button = new ToggleButton();
        button.setMaxWidth(Double.MAX_VALUE);
        button.setFocusTraversable(true);
        button.setSelected(selected);
        button.setAccessibleText(accessibleText);
        button.getStyleClass().addAll("calendar-overlay-row", shared ? "calendar-overlay-row-shared" : "calendar-overlay-row-user");
        button.setGraphic(createCalendarRowGraphic(labelText, color, selected, shared));
        button.selectedProperty().addListener((obs, oldValue, newValue) -> {
            button.setGraphic(createCalendarRowGraphic(labelText, color, newValue, shared));
            if (!suppressOverlayControlEvents) updateCalendarOverlaySelectionFromRows();
        });
        return button;
    }

    private Node createCalendarRowGraphic(String labelText, String color, boolean selected, boolean shared) {
        Label marker = new Label(shared ? "◈" : "●");
        marker.getStyleClass().add(shared ? "calendar-overlay-shared-marker" : "calendar-overlay-color-marker");
        String userColorCss = calendarOverlayUserColorCss(color);
        if (!shared && userColorCss != null) marker.setStyle("-fx-text-fill: " + userColorCss + ";");
        Label label = new Label(labelText);
        label.getStyleClass().add("calendar-overlay-row-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label check = new Label(selected ? "✓" : "");
        check.getStyleClass().add("calendar-overlay-row-check");
        HBox row = new HBox(7, marker, label, spacer, check);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private static String calendarOverlayUserColorCss(String storedColor) {
        return ColorUtil.toCssBackgroundColorOrNull(storedColor);
    }

    private void configureCalendarBulkActionTooltips() {
        if (selectAllCalendarsButton != null) Tooltip.install(selectAllCalendarsButton, new Tooltip("Show Shared, My Calendar, and all active user calendars"));
        if (clearAllCalendarsButton != null) Tooltip.install(clearAllCalendarsButton, new Tooltip("Hide every calendar; does not change other filters"));
        if (resetCalendarsButton != null) Tooltip.install(resetCalendarsButton, new Tooltip("Reset calendars to Shared + My Calendar only"));
    }

    private void resetCalendarOverlayDefaults() {
        calendarOverlaySelection = CalendarOverlaySelection.defaults(currentUserId());
    }

    private void updateCalendarOverlaySelectionFromRows() {
        LinkedHashSet<Integer> selectedUsers = new LinkedHashSet<>();
        userCalendarButtons.forEach((id, button) -> { if (button.isSelected()) selectedUsers.add(id); });
        calendarOverlaySelection = new CalendarOverlaySelection(sharedCalendarButton != null && sharedCalendarButton.isSelected(), selectedUsers);
        updateClearFiltersState();
        applyFiltersAndRender();
    }

    private void syncCalendarRowsFromSelection() {
        suppressOverlayControlEvents = true;
        try {
            if (sharedCalendarButton != null) sharedCalendarButton.setSelected(calendarOverlaySelection != null && calendarOverlaySelection.sharedEnabled());
            userCalendarButtons.forEach((id, button) -> button.setSelected(calendarOverlaySelection != null && calendarOverlaySelection.enabledUserIds().contains(id)));
        } finally {
            suppressOverlayControlEvents = false;
        }
    }

    @FXML private void onSelectAllCalendars() {
        LinkedHashSet<Integer> allUsers = new LinkedHashSet<>(userCalendarButtons.keySet());
        calendarOverlaySelection = new CalendarOverlaySelection(true, allUsers);
        syncCalendarRowsFromSelection();
        updateClearFiltersState();
        applyFiltersAndRender();
    }

    @FXML private void onClearAllCalendars() {
        calendarOverlaySelection = new CalendarOverlaySelection(false, Set.of());
        syncCalendarRowsFromSelection();
        updateClearFiltersState();
        applyFiltersAndRender();
    }

    @FXML private void onResetCalendars() {
        resetCalendarOverlayDefaults();
        syncCalendarRowsFromSelection();
        updateClearFiltersState();
        applyFiltersAndRender();
    }

    private Integer currentUserId() {
        Integer userId = appState == null ? null : appState.getUserId();
        return userId == null || userId <= 0 ? null : userId;
    }

    @FXML private void onToday() { selectedDate = LocalDate.now(); loadCurrentRange(true); }
    @FXML private void onPreviousWeek() { selectedDate = shiftSelectedDate(-1); loadCurrentRange(false); }
    @FXML private void onNextWeek() { selectedDate = shiftSelectedDate(1); loadCurrentRange(false); }
    @FXML private void onClearFilters() {
        searchTextField.clear();
        selectedCaseId = null;
        selectedEventTypeKey = "";
        caseFilterCombo.setValue(ALL_CASES_OPTION);
        eventTypeFilterCombo.setValue(ALL_TYPES_OPTION);
        setLayerDefaults();
        resetCalendarOverlayDefaults();
        configureCalendarOverlayControls();
        updateClearFiltersState();
        applyFiltersAndRender();
    }

    @FXML private void onNewEvent() {
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        Integer actorId = appState == null ? null : appState.getUserId();
        if (tenantId == null || tenantId <= 0 || actorId == null || actorId <= 0 || calendarService == null || caseService == null) { showError("Calendar is unavailable because no tenant is selected."); return; }
        long dialogStart = PerfLog.start();
        PerfLog.log("DIALOG", "start", "calendar new-event shell");
        NewEventWizard.Handle dialog = NewEventWizard.show(weekBoard.getScene() == null ? null : weekBoard.getScene().getWindow(), tenantId, LocalDate.now(), () -> caseOptionsForPicker(null), () -> assignedUserOptionsForPicker(tenantId, null), request -> CompletableFuture.supplyAsync(() -> {
            if (!Objects.equals(appState.getShaleClientId(), tenantId) || !Objects.equals(appState.getUserId(), actorId)) return "Your tenant or session changed. Close this wizard and try again.";
            try {
                if (request.sourceKind() == NewEventWizard.SourceKind.GENERAL_EVENT) {
                    var input=request.general(); LocalDateTime startsAt=input.allDay()?input.date().atStartOfDay():input.date().atTime(input.startTime()); LocalDateTime endsAt=input.allDay()?null:startsAt.plusMinutes(input.durationMinutes());
                    calendarService.createEvent(new com.shale.core.model.CalendarEvent(null,tenantId,input.calendarEventTypeId(),input.caseId(),null,input.title(),input.description(),startsAt,endsAt,input.allDay(),"MANUAL",null,null,input.assignedToUserId(),false,false,actorId,null,null));
                } else {
                    var input=request.caseDate();caseService.createCaseDate(new CreateCaseDateCommand(tenantId,actorId,input.caseId(),input.caseDateTypeId(),input.startsAt(),input.endsAt(),input.allDay(),input.notes()));
                    if(runtimeBridge!=null)runtimeBridge.publishCaseDatesChanged(input.caseId(),tenantId,actorId,LiveUpdateEvents.CHANGE_CREATED);
                }
                Platform.runLater(()->{showError(null);loadCurrentRange(false);});return null;
            } catch(RuntimeException ex){return request.sourceKind()==NewEventWizard.SourceKind.GENERAL_EVENT?"Could not save event. Please check values and try again.":rootMessage(ex);}
        },dbExec),dbExec);
        PerfLog.logDone("DIALOG", "calendar new-event shell shown", dialogStart);
        int requestGeneration=dialog.beginTypeLoad(); dbExec.submit(() -> {
            long loadStart = PerfLog.start();
            PerfLog.log("DAO", "start", "calendar new-event types load");
            try {
                var eventTypes = calendarService.listEffectiveEventTypes(tenantId); var caseDateTypes=caseService.listEffectiveCaseDateTypes(tenantId,actorId);
                PerfLog.logDone("DAO", "calendar new-event types load", loadStart);
                Platform.runLater(() -> { if (Objects.equals(appState.getShaleClientId(),tenantId) && Objects.equals(appState.getUserId(),actorId)) dialog.populateTypes(tenantId,eventTypes,caseDateTypes,requestGeneration); });
            } catch (RuntimeException ex) {
                log.warn("Unable to load calendar event types for tenantId={}", tenantId, ex);
                Platform.runLater(() -> {
                    if (Objects.equals(appState.getShaleClientId(),tenantId) && Objects.equals(appState.getUserId(),actorId)) dialog.showTypeLoadError(tenantId,requestGeneration,"Unable to load event types.");
                });
            }
        });
    }

    private static String rootMessage(RuntimeException ex) {
        Throwable current=ex; while(current.getCause()!=null)current=current.getCause();
        return current.getMessage()==null||current.getMessage().isBlank()?"Could not save Case Event. Please try again.":current.getMessage();
    }

    public void refreshCurrentRange() { loadCurrentRange(false); }
    private void loadCurrentRange() { loadCurrentRange(false); }
    private void loadCurrentRange(boolean fromTodayAction) {
        LocalDate rangeStart = currentRangeStart();
        LocalDate rangeEnd = currentRangeEndInclusive();
        boolean initialLoad = lastLoadedRangeStart == null || lastLoadedRangeEndInclusive == null;
        boolean rangeChanged = !Objects.equals(lastLoadedRangeStart, rangeStart) || !Objects.equals(lastLoadedRangeEndInclusive, rangeEnd);
        autoScrollTimedViewsPending = fromTodayAction || initialLoad || rangeChanged;
        loadGeneration++; int current = loadGeneration; suppressAutoScroll = true; renderCurrentShell(); suppressAutoScroll = false; setLoading(true); showError(null);
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0 || calendarService == null) { setLoading(false); showError("Calendar is unavailable because no tenant is selected."); return; }
        LocalDateTime start = rangeStart.atStartOfDay(); LocalDateTime end = rangeEnd.plusDays(1).atStartOfDay();
        dbExec.submit(() -> {
            try {
                List<CalendarFeedItem> items = calendarService.listCalendarFeed(tenantId, start, end);
                Platform.runLater(() -> { if (current != loadGeneration) return; setLoading(false); loadedItems = items == null ? List.of() : items; refreshFilterOptions(); applyFiltersAndRender(); lastLoadedRangeStart = rangeStart; lastLoadedRangeEndInclusive = rangeEnd; });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> { if (current != loadGeneration) return; setLoading(false); showError("Could not load calendar for this period."); renderCurrent(List.of()); });
            }
        });
    }

    private void renderCurrentShell() { weekRangeLabel.setText(currentRangeLabel()); renderCurrent(List.of()); }
    private void applyFiltersAndRender() {
        renderCurrent(filterItems(loadedItems));
    }
    private List<CalendarFeedItem> filterItems(List<CalendarFeedItem> items) {
        String search = safe(searchText).toLowerCase(Locale.ROOT);
        CalendarCaseFilterOptions.CaseOption activeCaseFilter = caseFilterCombo == null ? null : caseFilterCombo.getValue();
        EventTypeFilterOption activeTypeFilter = eventTypeFilterCombo == null ? null : eventTypeFilterCombo.getValue();
        Integer activeCaseId = (activeCaseFilter == null || activeCaseFilter.isAll()) ? null : activeCaseFilter.caseId();
        String activeTypeKey = (activeTypeFilter == null || activeTypeFilter.isAll()) ? "" : safe(activeTypeFilter.matchKey());
        CalendarFeedSourceFilter activeSourceFilter = sourceFilter == null ? CalendarFeedSourceFilter.defaults() : sourceFilter;
        CalendarOverlaySelection activeOverlaySelection = calendarOverlaySelection == null ? CalendarOverlaySelection.defaults(currentUserId()) : calendarOverlaySelection;
        if (!activeOverlaySelection.hasAnyEnabled() || !activeSourceFilter.hasAnyEnabled()) return List.of();
        return items.stream()
                .filter(activeOverlaySelection::matches)
                .filter(item -> CalendarFeedFilters.matches(item, activeSourceFilter, search, activeCaseId, activeTypeKey))
                .toList();
    }
    private void refreshFilterOptions() {
        List<CalendarCaseFilterOptions.CaseOption> allCaseOptions = CalendarCaseFilterOptions.fromFeedItems(loadedItems);
        List<CalendarCaseFilterOptions.CaseOption> caseOptions = allCaseOptions.stream()
                .filter(option -> option != null && !option.isAll())
                .toList();
        caseFilterCombo.getItems().setAll(allCaseOptions);
        if (selectedCaseId == null) caseFilterCombo.setValue(ALL_CASES_OPTION);
        else caseOptions.stream().filter(o -> Objects.equals(o.caseId(), selectedCaseId)).findFirst().ifPresentOrElse(caseFilterCombo::setValue, () -> { selectedCaseId = null; caseFilterCombo.setValue(ALL_CASES_OPTION); });

        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        List<EventTypeFilterOption> typeOptions = new ArrayList<>();
        if (tenantId != null && tenantId > 0 && calendarService != null) {
            calendarService.listEffectiveEventTypes(tenantId).forEach(t -> {
                String matchKey = safe(t.systemKey()).isBlank() ? t.name() : t.systemKey();
                typeOptions.add(new EventTypeFilterOption(matchKey, t.name()));
            });
        }
        List<EventTypeFilterOption> sortedTypeOptions = typeOptions.stream().sorted(Comparator.comparing(o -> safe(o.displayName()).toLowerCase(Locale.ROOT))).toList();
        List<EventTypeFilterOption> allTypeOptions = new ArrayList<>();
        allTypeOptions.add(ALL_TYPES_OPTION);
        allTypeOptions.addAll(sortedTypeOptions);
        eventTypeFilterCombo.getItems().setAll(allTypeOptions);
        if (selectedEventTypeKey.isBlank()) eventTypeFilterCombo.setValue(ALL_TYPES_OPTION);
        else sortedTypeOptions.stream().filter(o -> safe(o.matchKey()).equalsIgnoreCase(selectedEventTypeKey)).findFirst().ifPresentOrElse(eventTypeFilterCombo::setValue, () -> { selectedEventTypeKey = ""; eventTypeFilterCombo.setValue(ALL_TYPES_OPTION); });
    }
    private void renderCurrent(List<CalendarFeedItem> items) {
        if (calendarOverlaySelection != null && !calendarOverlaySelection.hasAnyEnabled()) {
            renderEmptyCalendarState("No calendars selected.");
            return;
        }
        if (sourceFilter != null && !sourceFilter.hasAnyEnabled()) {
            renderEmptyCalendarState("No calendar layers selected.");
            return;
        }
        switch (selectedViewMode()) {
            case VIEW_DAY -> renderDay(items);
            case VIEW_MONTH -> renderMonth(items);
            case VIEW_FIVE_DAY -> renderWeekLike(items, true);
            default -> renderWeekLike(items, false);
        }
    }

    private void renderEmptyCalendarState(String message) {
        weekBoard.getChildren().clear();
        Label empty = new Label(message);
        empty.getStyleClass().add("shale-empty-state");
        weekBoard.getChildren().add(empty);
    }

    private void updateClearFiltersState() {
        if (clearFiltersButton == null) return;
        boolean dirty = !safe(searchTextField == null ? searchText : searchTextField.getText()).trim().isBlank()
                || selectedCaseId != null
                || !safe(selectedEventTypeKey).isBlank()
                || !Objects.equals(sourceFilter, CalendarFeedSourceFilter.defaults())
                || !Objects.equals(calendarOverlaySelection, CalendarOverlaySelection.defaults(currentUserId()));
        clearFiltersButton.setDisable(!dirty);
    }

    private void renderWeekLike(List<CalendarFeedItem> items, boolean fiveDay) {
        weekBoard.getChildren().clear();
        LocalDate start = fiveDay ? workWeekStartFor(selectedDate) : weekStartFor(selectedDate);
        int dayCount = fiveDay ? 5 : 7;
        Map<LocalDate, List<CalendarFeedItem>> grouped = groupAndSort(items, start, dayCount);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        VBox board = new VBox(6);
        GridPane headerRow = new GridPane();
        GridPane allDayRow = new GridPane();
        configureSharedColumns(headerRow, dayCount);
        configureSharedColumns(allDayRow, dayCount);
        List<LocalDate> visibleDays = new ArrayList<>();
        for (int i = 0; i < dayCount; i++) visibleDays.add(start.plusDays(i));
        GridPane timedGrid = createTimedGrid(today, now, visibleDays, grouped);

        headerRow.setHgap(6);
        allDayRow.setHgap(6);
        Label hourSpacer = createTimeGutterSpacer();
        headerRow.add(hourSpacer, 0, 0);
        allDayRow.add(createAllDayLabelColumn(), 0, 0);

        for (int i = 0; i < dayCount; i++) {
            LocalDate day = start.plusDays(i);
            VBox header = new VBox(2);
            header.getStyleClass().add("calendar-day-header");
            applyCalendarDayState(header, day, today);
            header.getChildren().addAll(
                    new Label(day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())),
                    new Label(DAY_DATE_FORMAT.format(day)),
                    new Label(grouped.getOrDefault(day, List.of()).size() + " items"));
            GridPane.setHgrow(header, Priority.ALWAYS);
            header.setMaxWidth(Double.MAX_VALUE);
            headerRow.add(header, i + 1, 0);
            allDayRow.add(createAllDaySection(grouped.getOrDefault(day, List.of()), allDayCollapsed), i + 1, 0);
        }

        ScrollPane timedScroll = new ScrollPane(timedGrid);
        timedScrollPane = timedScroll;
        timedScroll.setFitToWidth(true);
        timedScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        timedScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(timedScroll, Priority.ALWAYS);

        board.getChildren().addAll(headerRow, allDayRow, timedScroll);
        maybeAutoScrollTimedView(timedScroll, visibleDays);
        HBox.setHgrow(board, Priority.ALWAYS);
        weekBoard.getChildren().add(board);
    }

    private void renderDay(List<CalendarFeedItem> items) {
        weekBoard.getChildren().clear();
        Map<LocalDate, List<CalendarFeedItem>> grouped = groupAndSort(items, selectedDate, 1);
        VBox board = new VBox(6);
        board.getStyleClass().add("calendar-day-lane");
        board.setPadding(new Insets(8));

        HBox allDayRow = new HBox(6);
        allDayRow.getChildren().addAll(createAllDayLabelColumn(), createAllDaySection(grouped.getOrDefault(selectedDate, List.of()), allDayCollapsed));

        GridPane timedGrid = createTimedGrid(LocalDate.now(), LocalDateTime.now(), List.of(selectedDate), grouped);
        ScrollPane timedScroll = new ScrollPane(timedGrid);
        timedScrollPane = timedScroll;
        timedScroll.setFitToWidth(true);
        timedScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        timedScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(timedScroll, Priority.ALWAYS);

        board.getChildren().addAll(allDayRow, timedScroll);
        maybeAutoScrollTimedView(timedScroll, List.of(selectedDate));
        HBox.setHgrow(board, Priority.ALWAYS);
        weekBoard.getChildren().add(board);
    }

    private void renderMonth(List<CalendarFeedItem> items) {
        weekBoard.getChildren().clear(); LocalDate monthStart = selectedDate.withDayOfMonth(1); LocalDate gridStart = weekStartFor(monthStart);
        Map<LocalDate, List<CalendarFeedItem>> grouped = groupAndSort(items, gridStart, 42); GridPane grid = new GridPane(); grid.setHgap(6); grid.setVgap(6);
        for (int i = 0; i < 42; i++) {
            LocalDate day = gridStart.plusDays(i); VBox cell = new VBox(2); cell.getStyleClass().add("calendar-day-lane"); cell.getStyleClass().add("calendar-month-day-cell"); applyCalendarDayState(cell, day, LocalDate.now()); cell.setPadding(new Insets(6));
            configureMonthDayCellDrillDown(cell, day);
            Button dayButton = createMonthDayButton(day);
            cell.getChildren().add(dayButton); List<CalendarFeedItem> dayItems = grouped.getOrDefault(day, List.of());
            for (int j = 0; j < Math.min(3, dayItems.size()); j++) { Node bubble = calendarEventCardFactory.createAllDayBubble(dayItems.get(j)); configureCalendarCardClick(bubble, dayItems.get(j)); cell.getChildren().add(bubble); }
            if (dayItems.size() > 3) cell.getChildren().add(createMonthMoreButton(day, dayItems.size() - 3));
            grid.add(cell, i % 7, i / 7);
        }
        weekBoard.getChildren().add(grid);
    }

    void openDayView(LocalDate date) {
        if (date == null) return;
        selectedDate = date;
        boolean alreadyDay = VIEW_DAY.equals(selectedViewMode());
        if (dayViewButton != null) dayViewButton.setSelected(true);
        if (alreadyDay || dayViewButton == null) {
            loadCurrentRange(false);
        }
    }

    private void configureMonthDayCellDrillDown(Pane cell, LocalDate day) {
        if (cell == null || day == null) return;
        cell.setCursor(Cursor.HAND);
        cell.setAccessibleText("Open " + day.format(WEEK_RANGE_FORMAT) + " in Day view");
        Tooltip.install(cell, new Tooltip("Open " + day.format(WEEK_RANGE_FORMAT) + " in Day view"));
        cell.setOnMouseClicked(evt -> {
            openDayView(day);
            evt.consume();
        });
    }

    private Button createMonthDayButton(LocalDate day) {
        Button dayButton = new Button(String.valueOf(day.getDayOfMonth()));
        ControlStyles.apply(dayButton, ControlStyles.Purpose.NAVIGATION, ControlStyles.Size.SMALL);
        dayButton.getStyleClass().add("calendar-month-day-link");
        dayButton.setMaxWidth(Region.USE_PREF_SIZE);
        dayButton.setCursor(Cursor.HAND);
        dayButton.setFocusTraversable(true);
        String accessible = "Open " + day.format(WEEK_RANGE_FORMAT) + " in Day view";
        dayButton.setAccessibleText(accessible);
        Tooltip.install(dayButton, new Tooltip(accessible));
        dayButton.setOnAction(evt -> {
            openDayView(day);
            evt.consume();
        });
        dayButton.setOnMouseClicked(evt -> evt.consume());
        return dayButton;
    }

    private Button createMonthMoreButton(LocalDate day, int hiddenCount) {
        Button moreButton = new Button("+" + hiddenCount + " more");
        ControlStyles.apply(moreButton, ControlStyles.Purpose.NAVIGATION, ControlStyles.Size.SMALL);
        moreButton.getStyleClass().add("calendar-month-more-link");
        moreButton.setMaxWidth(Region.USE_PREF_SIZE);
        moreButton.setCursor(Cursor.HAND);
        moreButton.setFocusTraversable(true);
        String accessible = "Show " + hiddenCount + " more items for " + day.format(WEEK_RANGE_FORMAT);
        moreButton.setAccessibleText(accessible);
        Tooltip.install(moreButton, new Tooltip(accessible));
        moreButton.setOnAction(evt -> {
            openDayView(day);
            evt.consume();
        });
        moreButton.setOnMouseClicked(evt -> evt.consume());
        return moreButton;
    }

    private VBox createAllDayLabelColumn() {
        VBox box = new VBox(4);
        box.setMinWidth(64);
        box.setPrefWidth(64);
        box.setMaxWidth(64);

        HBox inline = new HBox(4);
        Label label = new Label("All day");
        label.getStyleClass().add("calendar-all-day-label");
        Button toggle = new Button(allDayCollapsed ? "▾" : "▸");
        ControlStyles.apply(toggle, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
        ControlStyles.iconOnly(toggle);
        toggle.setAccessibleText(allDayCollapsed ? "Expand all-day events" : "Collapse all-day events");
        toggle.getStyleClass().add("calendar-disclosure-toggle");
        toggle.setOnAction(evt -> {
            allDayCollapsed = !allDayCollapsed;
            renderCurrent(filterItems(loadedItems));
        });
        inline.getChildren().addAll(label, toggle);
        box.getChildren().add(inline);
        return box;
    }

    private Label createTimeGutterSpacer() {
        Label spacer = new Label("");
        spacer.setMinWidth(64);
        spacer.setPrefWidth(64);
        spacer.setMaxWidth(64);
        return spacer;
    }

    private void configureSharedColumns(GridPane grid, int dayCount) {
        grid.getColumnConstraints().clear();
        ColumnConstraints gutter = new ColumnConstraints();
        gutter.setMinWidth(64);
        gutter.setPrefWidth(64);
        gutter.setMaxWidth(64);
        grid.getColumnConstraints().add(gutter);
        double dayPercent = dayCount <= 0 ? 0 : 100.0 / dayCount;
        for (int i = 0; i < dayCount; i++) {
            ColumnConstraints dayCol = new ColumnConstraints();
            dayCol.setHgrow(Priority.ALWAYS);
            dayCol.setFillWidth(true);
            dayCol.setPercentWidth(dayPercent);
            grid.getColumnConstraints().add(dayCol);
        }
    }

    private VBox createAllDaySection(List<CalendarFeedItem> dayItems, boolean collapsed) {
        VBox allDaySection = new VBox(4);
        allDaySection.getStyleClass().addAll("calendar-day-lane", "calendar-all-day-section");
        allDaySection.setPadding(new Insets(6));
        List<CalendarFeedItem> allDayItems = dayItems.stream().filter(CalendarFeedItem::allDay).toList();

        if (collapsed) {
            Label summary = new Label(allDayItems.isEmpty() ? "No all-day" : (allDayItems.size() + " all-day"));
            summary.getStyleClass().add("calendar-all-day-summary");
            allDaySection.getChildren().add(summary);
            allDaySection.setMinHeight(36);
            allDaySection.setPrefHeight(36);
            allDaySection.setMaxHeight(36);
        } else {
            if (allDayItems.isEmpty()) { Label empty = new Label("No all-day items"); empty.getStyleClass().add("calendar-all-day-empty"); allDaySection.getChildren().add(empty); }
            else for (CalendarFeedItem i : allDayItems) { Node b = calendarEventCardFactory.createAllDayBubble(i); configureCalendarCardClick(b, i); allDaySection.getChildren().add(b); }
            allDaySection.setMinHeight(Region.USE_COMPUTED_SIZE);
            allDaySection.setPrefHeight(Region.USE_COMPUTED_SIZE);
            allDaySection.setMaxHeight(Double.MAX_VALUE);
        }
        HBox.setHgrow(allDaySection, Priority.ALWAYS);
        return allDaySection;
    }

    private GridPane createTimedGrid(LocalDate today, LocalDateTime now, List<LocalDate> visibleDays, Map<LocalDate, List<CalendarFeedItem>> grouped) {
        int dayCount = visibleDays == null ? 0 : visibleDays.size();
        GridPane timedGrid = new GridPane();
        timedGrid.setHgap(6);
        timedGrid.getStyleClass().add("calendar-timed-grid");
        configureSharedColumns(timedGrid, dayCount);
        StackPane[] dayWidthAnchors = new StackPane[Math.max(0, dayCount)];

        Map<LocalDate, Integer> dayIndexByDate = new HashMap<>();
        for (int i = 0; i < dayCount; i++) dayIndexByDate.put(visibleDays.get(i), i);
        Map<Integer, List<CalendarFeedItem>> timedEventsByDay = new HashMap<>();
        for (CalendarFeedItem item : grouped.values().stream().flatMap(List::stream).toList()) {
            if (item == null || item.allDay() || item.startsAt() == null) continue;
            LocalDate eventDate = item.startsAt().toLocalDate();
            Integer dayIndex = dayIndexByDate.get(eventDate);
            if (dayIndex == null || dayIndex < 0) continue;
            timedEventsByDay.computeIfAbsent(dayIndex, k -> new ArrayList<>()).add(item);
        }

        Integer nowDayIndex = null;
        Integer nowMinutesFromMidnight = null;
        if (now != null) {
            nowDayIndex = dayIndexByDate.get(now.toLocalDate());
            if (nowDayIndex != null) nowMinutesFromMidnight = now.getHour() * 60 + now.getMinute();
        }

        for (int slot = 0; slot < 48; slot++) {
            RowConstraints rc = new RowConstraints();
            rc.setPrefHeight(HALF_HOUR_HEIGHT);
            timedGrid.getRowConstraints().add(rc);
            Label hour = new Label(slot % 2 == 0 ? formatHourLabel(slot / 2) : "");
            hour.getStyleClass().add("calendar-time-gutter-label");
            hour.pseudoClassStateChanged(HOUR_PSEUDO_CLASS, slot % 2 == 0);
            hour.pseudoClassStateChanged(HALF_HOUR_PSEUDO_CLASS, slot % 2 != 0);
            timedGrid.add(hour, 0, slot);
        }

        for (int dayIndex = 0; dayIndex < dayCount; dayIndex++) {
            for (int slot = 0; slot < 48; slot++) {
                StackPane box = new StackPane();
                box.setPrefHeight(HALF_HOUR_HEIGHT);
                box.setMaxWidth(Double.MAX_VALUE);
                box.getStyleClass().add("calendar-timed-day-cell");
                box.pseudoClassStateChanged(HOUR_PSEUDO_CLASS, slot % 2 == 0);
                box.pseudoClassStateChanged(HALF_HOUR_PSEUDO_CLASS, slot % 2 != 0);
                if (visibleDays != null && dayIndex < visibleDays.size()) applyCalendarDayState(box, visibleDays.get(dayIndex), today);
                GridPane.setHgrow(box, Priority.ALWAYS);
                timedGrid.add(box, dayIndex + 1, slot);
                if (slot == 0) dayWidthAnchors[dayIndex] = box;
            }
            Pane eventsOverlay = new Pane();
            eventsOverlay.setPickOnBounds(false);
            GridPane.setHgrow(eventsOverlay, Priority.ALWAYS);
            GridPane.setVgrow(eventsOverlay, Priority.ALWAYS);
            GridPane.setRowSpan(eventsOverlay, 48);
            timedGrid.add(eventsOverlay, dayIndex + 1, 0);
            final int dayIndexFinal = dayIndex;
            Runnable relayout = () -> {
                double dayWidth = dayWidthAnchors[dayIndexFinal] == null ? eventsOverlay.getWidth() : dayWidthAnchors[dayIndexFinal].getWidth();
                layoutTimedEvents(eventsOverlay, timedEventsByDay.getOrDefault(dayIndexFinal, List.of()), today, now, dayWidth);
            };
            Platform.runLater(relayout);
            eventsOverlay.widthProperty().addListener((obs, oldV, newV) -> relayout.run());
            if (dayWidthAnchors[dayIndexFinal] != null) dayWidthAnchors[dayIndexFinal].widthProperty().addListener((obs, oldV, newV) -> relayout.run());
        }

        if (nowDayIndex != null && nowMinutesFromMidnight != null) {
            Node nowOverlay = createNowIndicatorOverlay(nowMinutesFromMidnight);
            GridPane.setHgrow(nowOverlay, Priority.ALWAYS);
            GridPane.setVgrow(nowOverlay, Priority.ALWAYS);
            GridPane.setFillHeight(nowOverlay, true);
            GridPane.setRowSpan(nowOverlay, 48);
            timedGrid.add(nowOverlay, nowDayIndex + 1, 0);
        }
        return timedGrid;
    }
    // TODO: Implement side-by-side overlap layout after timed grid structure is simplified.

    private void layoutTimedEvents(Pane overlay, List<CalendarFeedItem> events, LocalDate today, LocalDateTime now, double dayColumnWidth) {
        if (overlay == null) return;
        overlay.getChildren().clear();
        if (events == null || events.isEmpty()) return;
        List<CalendarFeedItem> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(CalendarFeedItem::startsAt).thenComparing(CalendarFeedItem::key));
        List<List<CalendarFeedItem>> clusters = new ArrayList<>();
        List<CalendarFeedItem> current = new ArrayList<>();
        LocalDateTime clusterEnd = null;
        for (CalendarFeedItem e : sorted) {
            LocalDateTime start = e.startsAt();
            LocalDateTime end = eventEnd(e);
            if (current.isEmpty() || (clusterEnd != null && start.isBefore(clusterEnd))) {
                current.add(e);
                if (clusterEnd == null || end.isAfter(clusterEnd)) clusterEnd = end;
            } else {
                clusters.add(current);
                current = new ArrayList<>();
                current.add(e);
                clusterEnd = end;
            }
        }
        if (!current.isEmpty()) clusters.add(current);
        for (List<CalendarFeedItem> cluster : clusters) placeCluster(overlay, cluster, today, now, dayColumnWidth);
    }

    private void placeCluster(Pane overlay, List<CalendarFeedItem> cluster, LocalDate today, LocalDateTime now, double dayColumnWidth) {
        List<CalendarFeedItem> ordered = new ArrayList<>(cluster);
        ordered.sort(Comparator.comparing(CalendarFeedItem::startsAt).thenComparing(CalendarFeedItem::key));
        List<LocalDateTime> columnEndTimes = new ArrayList<>();
        Map<CalendarFeedItem, Integer> eventColumns = new HashMap<>();
        for (CalendarFeedItem e : ordered) {
            int column = -1;
            for (int i = 0; i < columnEndTimes.size(); i++) {
                if (!e.startsAt().isBefore(columnEndTimes.get(i))) { column = i; break; }
            }
            if (column == -1) { column = columnEndTimes.size(); columnEndTimes.add(eventEnd(e)); }
            else columnEndTimes.set(column, eventEnd(e));
            eventColumns.put(e, column);
        }
        int columnCount = Math.max(1, columnEndTimes.size());
        double overlayWidth = Math.max(1, dayColumnWidth);
        double pxPerMinute = (HALF_HOUR_HEIGHT * 48.0) / (24.0 * 60.0);
        for (CalendarFeedItem e : ordered) {
            Node card = calendarEventCardFactory.create(e, today, now);
            configureCalendarCardClick(card, e);
            int startMinutes = e.startsAt().getHour() * 60 + e.startsAt().getMinute();
            long durationMinutes = Math.max(30, java.time.Duration.between(e.startsAt(), eventEnd(e)).toMinutes());
            double y = startMinutes * pxPerMinute;
            double h = Math.max(HALF_HOUR_HEIGHT - 2, durationMinutes * pxPerMinute - 2);
            int col = eventColumns.getOrDefault(e, 0);
            double colWidth = overlayWidth / columnCount;
            double localX = col * colWidth + 1;
            double localWidth = Math.max(20, colWidth - 2);
            applyTimedCardSizing(card, localWidth, h);
            if (log.isDebugEnabled()) {
                log.debug("Calendar timed layout event='{}' date={} start={} end={} dayWidth={} columns={} columnIndex={} localX={} localWidth={}",
                        safe(e.title()), e.startsAt() == null ? null : e.startsAt().toLocalDate(), e.startsAt(), eventEnd(e), overlayWidth, columnCount, col, localX, localWidth);
            }
            card.resizeRelocate(localX, y + 1, localWidth, h);
            overlay.getChildren().add(card);
        }
    }

    private void applyTimedCardSizing(Node card, double width, double height) {
        if (card == null) return;
        card.getStyleClass().removeAll("calendar-event-card-compact", "calendar-event-card-normal", "calendar-event-card-tall");
        if (height <= HALF_HOUR_HEIGHT + 4) card.getStyleClass().add("calendar-event-card-compact");
        else if (height < HALF_HOUR_HEIGHT * 3) card.getStyleClass().add("calendar-event-card-normal");
        else card.getStyleClass().add("calendar-event-card-tall");
        if (card instanceof Region region) {
            region.setMinHeight(height);
            region.setPrefHeight(height);
            region.setMaxHeight(height);
            region.setMinWidth(width);
            region.setPrefWidth(width);
            region.setMaxWidth(width);
            Rectangle clip = new Rectangle(width, height);
            clip.widthProperty().bind(region.widthProperty());
            clip.heightProperty().bind(region.heightProperty());
            region.setClip(clip);
        }
    }

    private static LocalDateTime eventEnd(CalendarFeedItem e) {
        if (e == null || e.startsAt() == null) return LocalDateTime.MIN;
        if (e.endsAt() != null && e.endsAt().isAfter(e.startsAt())) return e.endsAt();
        return e.startsAt().plusMinutes(30);
    }


    private void maybeAutoScrollTimedView(ScrollPane timedScroll, List<LocalDate> visibleDays) {
        if (suppressAutoScroll || !autoScrollTimedViewsPending || timedScroll == null || timedScroll != timedScrollPane) return;
        LocalDate today = LocalDate.now();
        LocalTime targetTime = (visibleDays != null && visibleDays.contains(today)) ? LocalTime.now() : LocalTime.of(8, 0);
        positionTimedScroll(timedScroll, targetTime, 0);
    }

    private void positionTimedScroll(ScrollPane timedScroll, LocalTime targetTime, int attempt) {
        if (timedScroll == null || attempt > 4) return;
        Platform.runLater(() -> Platform.runLater(() -> {
            Node content = timedScroll.getContent();
            if (content == null) return;
            double contentHeight = content.getBoundsInLocal().getHeight();
            double viewportHeight = timedScroll.getViewportBounds().getHeight();
            if (contentHeight <= 0 || viewportHeight <= 0) {
                positionTimedScroll(timedScroll, targetTime, attempt + 1);
                return;
            }
            int minutesFromMidnight = targetTime.getHour() * 60 + targetTime.getMinute();
            double targetY = (minutesFromMidnight / (24.0 * 60.0)) * contentHeight;
            double scrollableHeight = contentHeight - viewportHeight;
            double desiredOffset = targetY - (viewportHeight / 2.0);
            double vvalue = scrollableHeight <= 0 ? 0.0 : desiredOffset / scrollableHeight;
            timedScroll.setVvalue(Math.max(0.0, Math.min(1.0, vvalue)));
            autoScrollTimedViewsPending = false;
        }));
    }

    private Node createNowIndicatorOverlay(int minutesFromMidnight) {
        Pane overlay = new Pane();
        overlay.setMouseTransparent(true);
        overlay.setPickOnBounds(false);
        overlay.setManaged(true);
        overlay.setMaxWidth(Double.MAX_VALUE);
        overlay.setPrefHeight(48 * HALF_HOUR_HEIGHT);

        Circle dot = new Circle(4);
        dot.getStyleClass().add("calendar-now-dot");
        dot.setFill(Color.RED);
        dot.setMouseTransparent(true);

        Line line = new Line();
        line.getStyleClass().add("calendar-now-line");
        line.setStroke(Color.RED);
        line.setStrokeWidth(2);
        line.setMouseTransparent(true);

        overlay.getChildren().addAll(line, dot);

        Runnable positionMarker = () -> {
            double width = overlay.getWidth();
            double height = overlay.getHeight();
            if (width <= 0 || height <= 0) {
                PerfLog.log("UI", "calendar-now-overlay-zero", "width=" + width + " height=" + height);
                return;
            }
            double y = (minutesFromMidnight / (24.0 * 60.0)) * height;
            y = Math.max(0.0, Math.min(height, y));
            line.setStartX(8);
            line.setEndX(Math.max(8, width));
            line.setStartY(y);
            line.setEndY(y);
            dot.setCenterX(4);
            dot.setCenterY(y);
            PerfLog.log("UI", "calendar-now-overlay", "width=" + width + " height=" + height + " y=" + y);
        };

        overlay.widthProperty().addListener((obs, oldVal, newVal) -> positionMarker.run());
        overlay.heightProperty().addListener((obs, oldVal, newVal) -> positionMarker.run());
        Platform.runLater(() -> Platform.runLater(positionMarker));
        return overlay;
    }

    private static void applyCalendarDayState(Node node, LocalDate day, LocalDate today) {
        if (node == null || day == null) return;
        boolean isToday = today != null && day.equals(today);
        boolean isWeekend = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
        node.getStyleClass().removeAll("calendar-day-today", "calendar-day-weekend");
        if (isToday) node.getStyleClass().add("calendar-day-today");
        if (isWeekend) node.getStyleClass().add("calendar-day-weekend");
        node.pseudoClassStateChanged(TODAY_PSEUDO_CLASS, isToday);
        node.pseudoClassStateChanged(WEEKEND_PSEUDO_CLASS, isWeekend);
    }

    private Map<LocalDate, List<CalendarFeedItem>> groupAndSort(List<CalendarFeedItem> items, LocalDate start, int dayCount) {
        Map<LocalDate, List<CalendarFeedItem>> grouped = new LinkedHashMap<>(); for (int i = 0; i < dayCount; i++) grouped.put(start.plusDays(i), new ArrayList<>());
        for (CalendarFeedItem item : items) { if (item == null || item.startsAt() == null) continue; LocalDate date = item.startsAt().toLocalDate(); if (grouped.containsKey(date)) grouped.get(date).add(item); }
        Comparator<CalendarFeedItem> cmp = Comparator.comparing((CalendarFeedItem i) -> !i.allDay()).thenComparing(CalendarFeedItem::startsAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(i -> safe(i.displayTypeName())).thenComparing(i -> safe(i.title()));
        grouped.values().forEach(list -> list.sort(cmp)); return grouped;
    }

    private void configureCalendarCardClick(Node card, CalendarFeedItem item) {
        if (card == null) return;
        CalendarFeedClickTarget target = CalendarFeedClickTarget.resolve(item);
        if (!target.actionable()) return;
        card.setCursor(Cursor.HAND);
        Runnable activate = () -> {
            switch (target.kind()) {
                case CALENDAR_EVENT -> openEditEventDialog(Math.toIntExact(target.id()));
                case TASK -> onOpenTask.accept(target.id());
                case CASE -> onOpenCase.accept(Math.toIntExact(target.id()));
                case CASE_DATES -> onOpenCaseDates.accept(target.caseId(), target.id());
                case NONE -> { }
            }
        };
        card.setFocusTraversable(true);
        card.setAccessibleText("Open " + safe(item == null ? null : item.title()));
        card.setOnMouseClicked(evt -> {
            if (evt.getButton() != javafx.scene.input.MouseButton.PRIMARY || !evt.isStillSincePress() || isEmbeddedAction(evt.getTarget(), card)) return;
            activate.run(); evt.consume();
        });
        card.setOnKeyPressed(evt -> {
            if (evt.getCode() == javafx.scene.input.KeyCode.ENTER || evt.getCode() == javafx.scene.input.KeyCode.SPACE) {
                activate.run(); evt.consume();
            }
        });
    }

    private static boolean isEmbeddedAction(Object target, Node card) {
        Node node = target instanceof Node n ? n : null;
        while (node != null && node != card) {
            if (node instanceof javafx.scene.control.ButtonBase || node instanceof javafx.scene.control.TextInputControl
                    || node instanceof javafx.scene.control.ComboBoxBase<?> || node instanceof javafx.scene.control.Hyperlink) return true;
            node = node.getParent();
        }
        return false;
    }

    private void openEditEventDialog(int eventId) {
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0 || calendarService == null) return;
        if (!openingEditDialogEventIds.add(eventId)) return;
        long clickStart = PerfLog.start();
        PerfLog.log("DIALOG", "start", "calendar edit-event click eventId=" + eventId);
        NewCalendarEventDialog.EditDialogHandle dialog = NewCalendarEventDialog.showEditDialogAsyncShell(weekBoard.getScene() == null ? null : weekBoard.getScene().getWindow());
        PerfLog.logDone("DIALOG", "calendar edit-event shell shown eventId=" + eventId, clickStart);
        dbExec.submit(() -> {
            try {
                long loadStart = PerfLog.start();
                PerfLog.log("DAO", "start", "calendar edit-event hydrate eventId=" + eventId);
                var event = calendarService.getEventById(eventId, tenantId);
                if (event == null) {
                    log.info("Calendar event open failed reason=not_found eventId={} tenantId={}", eventId, tenantId);
                    Platform.runLater(() -> {
                        openingEditDialogEventIds.remove(eventId);
                        dialog.showLoadError("Calendar event could not be opened.");
                    });
                    return;
                }
                var initial = new NewCalendarEventDialog.CreateCalendarEventInput(event.title(), event.calendarEventTypeId(), event.startsAt().toLocalDate(), event.allDay(), event.allDay() ? null : event.startsAt().toLocalTime(), resolveDurationMinutes(event), event.description(), event.caseId(), event.assignedToUserId());
                CaseSummaryDao.CalendarCaseRow caseRow = loadCaseRowForEvent(event, tenantId);
                CalendarFeedDao.CalendarTaskCardRow taskRow = loadTaskRowForEvent(event, tenantId);
                var eventTypes = calendarService.listEffectiveEventTypes(tenantId);
                PerfLog.logDone("DAO", "calendar edit-event hydrate eventId=" + eventId, loadStart);
                Platform.runLater(() -> {
                    if (!dialog.isShowing() || appState == null || !Objects.equals(appState.getShaleClientId(), tenantId)) {
                        openingEditDialogEventIds.remove(eventId);
                        return;
                    }
                    Node rc = caseRow == null ? null : createRelatedCaseNode(caseRow);
                    Node rt = taskRow == null ? null : createRelatedTaskNode(taskRow);
                    var summary = caseRow == null ? null : caseRow.summary();
                    dialog.populate(eventTypes, initial, input -> saveEditedEvent(event, input), () -> deleteEvent(event.calendarEventId(), tenantId), rc, rt, () -> caseOptionsForPicker(event.caseId()), () -> assignedUserOptionsForPicker(tenantId, event.assignedToUserId()), onOpenCase, summary == null ? null : new NewCalendarEventDialog.CaseOption(Math.toIntExact(summary.caseId()), summary.caseName(), summary.responsibleAttorneyName(), summary.responsibleAttorneyColor(), caseRow.nonEngagementLetterSent()), dbExec);
                    openingEditDialogEventIds.remove(eventId);
                });
            } catch (RuntimeException ex) {
                log.warn("Calendar event open failed reason=exception eventId={} tenantId={}", eventId, tenantId, ex);
                Platform.runLater(() -> { openingEditDialogEventIds.remove(eventId); dialog.showLoadError("Calendar event could not be opened."); });
            }
        });
    }

    public void openCalendarEventFromNotification(long eventId) {
        if (eventId <= 0 || eventId > Integer.MAX_VALUE) {
            log.info("Calendar notification open skipped reason=invalid_event_id eventId={} tenantId={}", eventId, appState == null ? null : appState.getShaleClientId());
            AppDialogs.showError(weekBoard == null || weekBoard.getScene() == null ? null : weekBoard.getScene().getWindow(), "Calendar", "Calendar event could not be opened.");
            return;
        }
        openEditEventDialog((int) eventId);
    }
    private CaseSummaryDao.CalendarCaseRow loadCaseRowForEvent(com.shale.core.model.CalendarEvent event, int tenantId) { return event == null || event.caseId() == null || caseSummaryDao == null ? null : caseSummaryDao.findActiveForCalendar(tenantId, event.caseId()); }
    private CalendarFeedDao.CalendarTaskCardRow loadTaskRowForEvent(com.shale.core.model.CalendarEvent event, int tenantId) { if (event == null || event.taskId() == null) return null; List<CalendarFeedDao.CalendarTaskCardRow> rows = calendarFeedDao.listTaskCardRows(tenantId, List.of(event.taskId())); return rows.isEmpty() ? null : rows.getFirst(); }
    private Node createRelatedCaseNode(CaseSummaryDao.CalendarCaseRow row) { if (row == null) return null; var summary = row.summary(); return caseCardFactory.create(new CaseCardFactory.CaseCardModel(Math.toIntExact(summary.caseId()), summary.caseName(), null, null, summary.responsibleAttorneyName(), summary.responsibleAttorneyColor(), row.nonEngagementLetterSent()), CaseCardFactory.Variant.MINI); }
    private Node createRelatedTaskNode(CalendarFeedDao.CalendarTaskCardRow row) { if (row == null) return null; return taskCardFactory.create(new TaskCardFactory.TaskCardModel(row.taskId(), row.caseId() == null ? null : row.caseId().longValue(), row.caseName(), null, null, null, row.caseResponsibleAttorney(), row.caseResponsibleAttorneyColor(), row.caseNonEngagementLetterSent(), row.title(), row.description(), row.createdByDisplayName(), null, null, row.priorityColorHex(), row.dueAt(), row.completedAt(), List.of()), TaskCardFactory.Variant.MINI); }
    private String saveEditedEvent(com.shale.core.model.CalendarEvent existing, NewCalendarEventDialog.CreateCalendarEventInput input) { LocalDateTime startsAt = input.allDay() ? input.date().atStartOfDay() : input.date().atTime(input.startTime()); LocalDateTime endsAt = input.allDay() ? null : startsAt.plusMinutes(input.durationMinutes()); try { calendarService.updateEvent(new com.shale.core.model.CalendarEvent(existing.calendarEventId(), existing.shaleClientId(), input.calendarEventTypeId(), input.caseId(), existing.taskId(), input.title(), input.description(), startsAt, endsAt, input.allDay(), existing.sourceType(), existing.sourceField(), existing.sourceId(), input.assignedToUserId(), existing.completed(), existing.cancelled(), appState == null ? null : appState.getUserId(), existing.createdAt(), existing.updatedAt())); showError(null); loadCurrentRange(); return null; } catch (RuntimeException ex) { return "Could not save event. Please check values and try again."; } }
    private List<NewCalendarEventDialog.CaseOption> caseOptionsForPicker(Integer selectedCaseId) {
        if (Platform.isFxApplicationThread()) throw new IllegalStateException("Calendar case options must load off the JavaFX Application Thread");
        long started = PerfLog.start();
        int tenantId = appState == null || appState.getShaleClientId() == null ? 0 : appState.getShaleClientId();
        List<NewCalendarEventDialog.CaseOption> options = (caseSummaryDao == null ? List.<CaseSummaryDao.CalendarCaseRow>of()
                : caseSummaryDao.listActiveForCalendar(tenantId)).stream()
                .map(row -> new NewCalendarEventDialog.CaseOption(Math.toIntExact(row.summary().caseId()), safe(row.summary().caseName()),
                        row.summary().responsibleAttorneyName(), row.summary().responsibleAttorneyColor(), row.nonEngagementLetterSent()))
                .toList();
        PerfLog.logDone("DAO", "calendar case-picker options rows=" + options.size()
                + " dbRoundTrips=" + (caseSummaryDao == null ? 0 : 1) + " fxThread=false", started);
        return options;
    }
    private List<NewCalendarEventDialog.AssignedUserOption> assignedUserOptionsForPicker(int tenantId, Integer selectedUserId) {
        if (caseTaskService == null) return List.of();
        Map<Integer, String> names = new LinkedHashMap<>();
        java.util.Map<Integer, String> colors = new LinkedHashMap<>();
        caseTaskService.loadAssignableUsers(tenantId).forEach(u -> { names.putIfAbsent(u.id(), safe(u.displayName())); colors.putIfAbsent(u.id(), u.color()); });
        if (selectedUserId != null && selectedUserId > 0) names.putIfAbsent(selectedUserId, "User #" + selectedUserId);
        Integer currentUserId = currentUserId();
        return names.entrySet().stream()
                .map(e -> new NewCalendarEventDialog.AssignedUserOption(e.getKey(), e.getValue(), colors.get(e.getKey())))
                .sorted(Comparator.comparing((NewCalendarEventDialog.AssignedUserOption o) -> !Objects.equals(o.userId(), currentUserId))
                        .thenComparing(o -> safe(o.displayName()).toLowerCase(Locale.ROOT))
                        .thenComparing(o -> o.userId() == null ? Integer.MAX_VALUE : o.userId()))
                .toList();
    }
    private int resolveDurationMinutes(com.shale.core.model.CalendarEvent event) { if (event == null || event.endsAt() == null || event.startsAt() == null || !event.endsAt().isAfter(event.startsAt())) return 60; long minutes = java.time.Duration.between(event.startsAt(), event.endsAt()).toMinutes(); long roundedUp = ((minutes + 29) / 30) * 30; if (roundedUp < 30) roundedUp = 30; if (roundedUp > 8 * 60) roundedUp = 8 * 60; return (int) roundedUp; }
    private String deleteEvent(Integer calendarEventId, int tenantId) { try { calendarService.deleteCalendarEvent(calendarEventId, tenantId); showError(null); loadCurrentRange(); return null; } catch (RuntimeException ex) { return "Could not delete event. Please try again."; } }
    private static LocalDate weekStartFor(LocalDate date) { return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)); }
    private static LocalDate workWeekStartFor(LocalDate date) { return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    private LocalDate currentRangeStart() { return switch (selectedViewMode()) { case VIEW_FIVE_DAY -> workWeekStartFor(selectedDate); case VIEW_DAY -> selectedDate; case VIEW_MONTH -> selectedDate.withDayOfMonth(1); default -> weekStartFor(selectedDate); }; }
    private LocalDate currentRangeEndInclusive() { LocalDate start = currentRangeStart(); return switch (selectedViewMode()) { case VIEW_FIVE_DAY -> start.plusDays(4); case VIEW_DAY -> start; case VIEW_MONTH -> YearMonth.from(selectedDate).atEndOfMonth(); default -> start.plusDays(6); }; }
    private LocalDate shiftSelectedDate(int direction) { return switch (selectedViewMode()) { case VIEW_FIVE_DAY -> workWeekStartFor(selectedDate).plusWeeks(direction); case VIEW_DAY -> selectedDate.plusDays(direction); case VIEW_MONTH -> selectedDate.plusMonths(direction); default -> weekStartFor(selectedDate).plusWeeks(direction); }; }
    private String currentRangeLabel() { return switch (selectedViewMode()) { case VIEW_MONTH -> MONTH_RANGE_FORMAT.format(selectedDate); case VIEW_DAY -> WEEK_RANGE_FORMAT.format(selectedDate); default -> WEEK_RANGE_FORMAT.format(currentRangeStart()) + " - " + WEEK_RANGE_FORMAT.format(currentRangeEndInclusive()); }; }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String formatHourLabel(int hour24) { int hour12 = hour24 % 12; if (hour12 == 0) hour12 = 12; return hour12 + (hour24 < 12 ? " AM" : " PM"); }
    private void setLoading(boolean loading) { calendarLoadingLabel.setVisible(loading); calendarLoadingLabel.setManaged(loading); }
    private void showError(String text) { boolean has = text != null && !text.isBlank(); calendarErrorLabel.setText(has ? text : ""); calendarErrorLabel.setVisible(has); calendarErrorLabel.setManaged(has); }
    private record EventTypeFilterOption(String matchKey, String displayName) { boolean isAll() { return safe(matchKey).isBlank(); } }
}
