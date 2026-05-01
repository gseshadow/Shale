package com.shale.ui.controller;

import com.shale.core.model.CalendarFeedItem;
import com.shale.data.dao.CalendarFeedDao;
import com.shale.data.dao.CaseDao;
import com.shale.ui.component.dialog.NewCalendarEventDialog;
import com.shale.ui.component.factory.CalendarEventCardFactory;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.TaskCardFactory;
import com.shale.ui.services.CalendarService;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.state.AppState;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class CalendarController {
    private static final DateTimeFormatter WEEK_RANGE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DAY_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_RANGE_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final String VIEW_WEEK = "Week";
    private static final String VIEW_FIVE_DAY = "5 Day";
    private static final String VIEW_DAY = "Day";
    private static final String VIEW_MONTH = "Month";
    private static final double HALF_HOUR_HEIGHT = 34.0;
    private static final CaseFilterOption ALL_CASES_OPTION = new CaseFilterOption(null, "All cases");
    private static final EventTypeFilterOption ALL_TYPES_OPTION = new EventTypeFilterOption("", "All types");

    @FXML private ChoiceBox<String> viewModeChoice;
    @FXML private Label weekRangeLabel;
    @FXML private Label calendarLoadingLabel;
    @FXML private Label calendarErrorLabel;
    @FXML private HBox weekBoard;
    @FXML private TextField searchTextField;
    @FXML private ComboBox<CaseFilterOption> caseFilterCombo;
    @FXML private ComboBox<EventTypeFilterOption> eventTypeFilterCombo;

    private AppState appState;
    private CalendarService calendarService;
    private CalendarFeedDao calendarFeedDao;
    private Consumer<Integer> onOpenCase;
    private Consumer<Long> onOpenTask;
    private CaseTaskService caseTaskService;
    private CaseDao caseDao;
    private int loadGeneration;
    private LocalDate selectedDate;
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(300));
    private List<CalendarFeedItem> loadedItems = List.of();
    private String searchText = "";
    private Integer selectedCaseId;
    private String selectedEventTypeKey = "";

    private final CalendarEventCardFactory calendarEventCardFactory = new CalendarEventCardFactory();
    private CaseCardFactory caseCardFactory = new CaseCardFactory(id -> {});
    private TaskCardFactory taskCardFactory = new TaskCardFactory(id -> {}, id -> {}, id -> {}, id -> {});
    private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "calendar-feed-loader"); t.setDaemon(true); return t; });

    public void init(AppState appState, CalendarService calendarService, CalendarFeedDao calendarFeedDao, CaseTaskService caseTaskService, CaseDao caseDao, Consumer<Integer> onOpenCase, Consumer<Long> onOpenTask) {
        this.appState = appState; this.calendarService = calendarService; this.calendarFeedDao = calendarFeedDao;
        this.caseTaskService = caseTaskService;
        this.caseDao = caseDao;
        this.onOpenCase = onOpenCase == null ? id -> {} : onOpenCase; this.onOpenTask = onOpenTask == null ? id -> {} : onOpenTask;
        this.caseCardFactory = new CaseCardFactory(this.onOpenCase);
        this.taskCardFactory = new TaskCardFactory(this.onOpenTask, id -> {}, this.onOpenCase, id -> {});
    }

    @FXML private void initialize() {
        viewModeChoice.getItems().setAll(VIEW_WEEK, VIEW_FIVE_DAY, VIEW_DAY, VIEW_MONTH);
        viewModeChoice.setValue(VIEW_WEEK);
        selectedDate = LocalDate.now();
        viewModeChoice.valueProperty().addListener((obs, o, n) -> { if (!Objects.equals(o, n)) loadCurrentRange(); });
        configureFilters();
        renderCurrentShell();
        Platform.runLater(this::loadCurrentRange);
    }
    private void configureFilters() {
        caseFilterCombo.setButtonCell(new ListCell<>() { @Override protected void updateItem(CaseFilterOption item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? "All cases" : item.displayName()); }});
        caseFilterCombo.setCellFactory(v -> new ListCell<>() { @Override protected void updateItem(CaseFilterOption item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? "" : item.displayName()); }});
        caseFilterCombo.valueProperty().addListener((obs, o, n) -> {
            selectedCaseId = (n == null || n.isAll()) ? null : n.caseId();
            applyFiltersAndRender();
        });
        eventTypeFilterCombo.setButtonCell(new ListCell<>() { @Override protected void updateItem(EventTypeFilterOption item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? "All types" : item.displayName()); }});
        eventTypeFilterCombo.setCellFactory(v -> new ListCell<>() { @Override protected void updateItem(EventTypeFilterOption item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? "" : item.displayName()); }});
        eventTypeFilterCombo.valueProperty().addListener((obs, o, n) -> {
            selectedEventTypeKey = (n == null || n.isAll()) ? "" : safe(n.matchKey());
            applyFiltersAndRender();
        });
        searchTextField.textProperty().addListener((obs, o, n) -> { searchDebounce.stop(); searchDebounce.setOnFinished(evt -> { searchText = safe(n).trim(); applyFiltersAndRender(); }); searchDebounce.playFromStart(); });
        caseFilterCombo.getItems().setAll(ALL_CASES_OPTION);
        caseFilterCombo.setValue(ALL_CASES_OPTION);
        eventTypeFilterCombo.getItems().setAll(ALL_TYPES_OPTION);
        eventTypeFilterCombo.setValue(ALL_TYPES_OPTION);
    }

    @FXML private void onToday() { selectedDate = LocalDate.now(); loadCurrentRange(); }
    @FXML private void onPreviousWeek() { selectedDate = shiftSelectedDate(-1); loadCurrentRange(); }
    @FXML private void onNextWeek() { selectedDate = shiftSelectedDate(1); loadCurrentRange(); }
    @FXML private void onClearFilters() {
        searchTextField.clear();
        selectedCaseId = null;
        selectedEventTypeKey = "";
        caseFilterCombo.setValue(ALL_CASES_OPTION);
        eventTypeFilterCombo.setValue(ALL_TYPES_OPTION);
        applyFiltersAndRender();
    }

    @FXML private void onNewEvent() {
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0 || calendarService == null) { showError("Calendar is unavailable because no tenant is selected."); return; }
        LocalDate defaultDate = currentRangeStart();
        var result = NewCalendarEventDialog.showAndWait(weekBoard.getScene() == null ? null : weekBoard.getScene().getWindow(), calendarService.listEffectiveEventTypes(tenantId), defaultDate, caseOptionsForPicker(null), assignedUserOptionsForPicker(tenantId, null));
        if (result.isEmpty()) return;
        var input = result.get();
        LocalDateTime startsAt = input.allDay() ? input.date().atStartOfDay() : input.date().atTime(input.startTime());
        LocalDateTime endsAt = input.allDay() ? null : startsAt.plusMinutes(input.durationMinutes());
        try {
            calendarService.createEvent(new com.shale.core.model.CalendarEvent(null, tenantId, input.calendarEventTypeId(), input.caseId(), null, input.title(), input.description(), startsAt, endsAt, input.allDay(), "MANUAL", null, null, input.assignedToUserId(), false, false, appState == null ? null : appState.getUserId(), null, null));
            showError(null); loadCurrentRange();
        } catch (RuntimeException ex) { showError("Could not save event. Please check values and try again."); }
    }

    private void loadCurrentRange() {
        loadGeneration++; int current = loadGeneration; renderCurrentShell(); setLoading(true); showError(null);
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0 || calendarService == null) { setLoading(false); showError("Calendar is unavailable because no tenant is selected."); return; }
        LocalDateTime start = currentRangeStart().atStartOfDay(); LocalDateTime end = currentRangeEndInclusive().plusDays(1).atStartOfDay();
        dbExec.submit(() -> {
            try {
                List<CalendarFeedItem> items = calendarService.listCalendarFeed(tenantId, start, end);
                Platform.runLater(() -> { if (current != loadGeneration) return; setLoading(false); loadedItems = items == null ? List.of() : items; refreshFilterOptions(); applyFiltersAndRender(); });
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
        CaseFilterOption activeCaseFilter = caseFilterCombo == null ? null : caseFilterCombo.getValue();
        EventTypeFilterOption activeTypeFilter = eventTypeFilterCombo == null ? null : eventTypeFilterCombo.getValue();
        Integer activeCaseId = (activeCaseFilter == null || activeCaseFilter.isAll()) ? null : activeCaseFilter.caseId();
        String activeTypeKey = (activeTypeFilter == null || activeTypeFilter.isAll()) ? "" : safe(activeTypeFilter.matchKey());
        return items.stream().filter(Objects::nonNull).filter(item -> {
            if (activeCaseId != null && !Objects.equals(item.caseId(), activeCaseId)) return false;
            if (!activeTypeKey.isBlank() && !eventTypeMatches(item, activeTypeKey)) return false;
            if (search.isBlank()) return true;
            return containsIgnoreCase(item.title(), search) || containsIgnoreCase(item.relatedDisplayName(), search)
                    || containsIgnoreCase(item.displayTypeName(), search) || containsIgnoreCase(item.calendarEventTypeSystemKey(), search);
        }).toList();
    }
    private static boolean eventTypeMatches(CalendarFeedItem item, String matchKey) {
        return safe(item.calendarEventTypeSystemKey()).equalsIgnoreCase(matchKey) || safe(item.displayTypeName()).equalsIgnoreCase(matchKey);
    }
    private static boolean containsIgnoreCase(String value, String loweredNeedle) { return safe(value).toLowerCase(Locale.ROOT).contains(loweredNeedle); }
    private void refreshFilterOptions() {
        Map<Integer, String> cases = new HashMap<>();
        for (CalendarFeedItem i : loadedItems) if (i != null && i.caseId() != null) cases.putIfAbsent(i.caseId(), safe(i.relatedDisplayName()).isBlank() ? ("Case #" + i.caseId()) : i.relatedDisplayName());
        List<CaseFilterOption> caseOptions = cases.entrySet().stream()
                .map(e -> new CaseFilterOption(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(o -> safe(o.displayName()).toLowerCase(Locale.ROOT)))
                .toList();
        List<CaseFilterOption> allCaseOptions = new ArrayList<>();
        allCaseOptions.add(ALL_CASES_OPTION);
        allCaseOptions.addAll(caseOptions);
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
        switch (safe(viewModeChoice.getValue())) {
            case VIEW_DAY -> renderDay(items);
            case VIEW_MONTH -> renderMonth(items);
            case VIEW_FIVE_DAY -> renderWeekLike(items, true);
            default -> renderWeekLike(items, false);
        }
    }

    private void renderWeekLike(List<CalendarFeedItem> items, boolean fiveDay) {
        weekBoard.getChildren().clear();
        LocalDate start = fiveDay ? workWeekStartFor(selectedDate) : weekStartFor(selectedDate);
        int dayCount = fiveDay ? 5 : 7;
        Map<LocalDate, List<CalendarFeedItem>> grouped = groupAndSort(items, start, dayCount);
        LocalDate today = LocalDate.now(); LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < dayCount; i++) {
            LocalDate day = start.plusDays(i); VBox lane = new VBox(6); lane.getStyleClass().add("calendar-day-lane"); lane.setPadding(new Insets(8));
            VBox header = new VBox(2); Label dayName = new Label(day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())); Label date = new Label(DAY_DATE_FORMAT.format(day));
            header.getChildren().addAll(dayName, date, new Label(grouped.getOrDefault(day, List.of()).size() + " items"));
            ScrollPane laneScroll = new ScrollPane(buildDayTimeline(grouped.getOrDefault(day, List.of()), today, now, day)); laneScroll.setFitToWidth(true); laneScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            lane.getChildren().addAll(header, laneScroll); VBox.setVgrow(laneScroll, Priority.ALWAYS); HBox.setHgrow(lane, Priority.ALWAYS); weekBoard.getChildren().add(lane);
        }
    }

    private void renderDay(List<CalendarFeedItem> items) { weekBoard.getChildren().clear(); VBox lane = new VBox(8); lane.getStyleClass().add("calendar-day-lane"); lane.setPadding(new Insets(8)); lane.getChildren().add(buildDayTimeline(groupAndSort(items, selectedDate, 1).getOrDefault(selectedDate, List.of()), LocalDate.now(), LocalDateTime.now(), selectedDate)); HBox.setHgrow(lane, Priority.ALWAYS); weekBoard.getChildren().add(lane); }

    private void renderMonth(List<CalendarFeedItem> items) {
        weekBoard.getChildren().clear(); LocalDate monthStart = selectedDate.withDayOfMonth(1); LocalDate gridStart = weekStartFor(monthStart);
        Map<LocalDate, List<CalendarFeedItem>> grouped = groupAndSort(items, gridStart, 42); GridPane grid = new GridPane(); grid.setHgap(6); grid.setVgap(6);
        for (int i = 0; i < 42; i++) {
            LocalDate day = gridStart.plusDays(i); VBox cell = new VBox(2); cell.getStyleClass().add("calendar-day-lane"); cell.setPadding(new Insets(6));
            cell.getChildren().add(new Label(String.valueOf(day.getDayOfMonth()))); List<CalendarFeedItem> dayItems = grouped.getOrDefault(day, List.of());
            for (int j = 0; j < Math.min(3, dayItems.size()); j++) { Node bubble = calendarEventCardFactory.createAllDayBubble(dayItems.get(j)); configureCalendarCardClick(bubble, dayItems.get(j)); cell.getChildren().add(bubble); }
            if (dayItems.size() > 3) cell.getChildren().add(new Label("+" + (dayItems.size() - 3) + " more"));
            grid.add(cell, i % 7, i / 7);
        }
        weekBoard.getChildren().add(grid);
    }

    private VBox buildDayTimeline(List<CalendarFeedItem> dayItems, LocalDate today, LocalDateTime now, LocalDate day) {
        if (dayItems.isEmpty()) { VBox empty = new VBox(); empty.getChildren().add(new Label("No items match filters.")); return empty; }
        VBox root = new VBox(8); List<CalendarFeedItem> allDayItems = dayItems.stream().filter(CalendarFeedItem::allDay).toList(); List<CalendarFeedItem> timedItems = dayItems.stream().filter(i -> !i.allDay()).toList();
        VBox allDaySection = new VBox(4); allDaySection.getChildren().add(new Label("All day")); if (allDayItems.isEmpty()) allDaySection.getChildren().add(new Label("No all-day items")); else for (CalendarFeedItem i : allDayItems) { Node b = calendarEventCardFactory.createAllDayBubble(i); configureCalendarCardClick(b, i); allDaySection.getChildren().add(b); }
        GridPane timedGrid = new GridPane();
        Map<Integer, List<CalendarFeedItem>> timedBySlot = new HashMap<>(); Map<CalendarFeedItem, Integer> spanByItem = new HashMap<>();
        for (CalendarFeedItem item : timedItems) { LocalDateTime start = item.startsAt() == null ? day.atStartOfDay() : item.startsAt(); int slot = Math.max(0, Math.min(47, (int)((start.getHour()*60.0 + start.getMinute())/30.0))); timedBySlot.computeIfAbsent(slot, k -> new ArrayList<>()).add(item); spanByItem.put(item, 1); }
        for (int slot = 0; slot < 48; slot++) { RowConstraints rc = new RowConstraints(); rc.setPrefHeight(HALF_HOUR_HEIGHT); timedGrid.getRowConstraints().add(rc); Label hour = new Label(slot % 2 == 0 ? formatHourLabel(slot / 2) : ""); VBox box = new VBox(4); box.setPrefHeight(HALF_HOUR_HEIGHT); timedGrid.add(hour, 0, slot); timedGrid.add(box, 1, slot); for (CalendarFeedItem item : timedBySlot.getOrDefault(slot, List.of())) { Node c = calendarEventCardFactory.create(item, today, now); configureCalendarCardClick(c, item); box.getChildren().add(c); } }
        root.getChildren().addAll(allDaySection, timedGrid); return root;
    }

    private Map<LocalDate, List<CalendarFeedItem>> groupAndSort(List<CalendarFeedItem> items, LocalDate start, int dayCount) {
        Map<LocalDate, List<CalendarFeedItem>> grouped = new LinkedHashMap<>(); for (int i = 0; i < dayCount; i++) grouped.put(start.plusDays(i), new ArrayList<>());
        for (CalendarFeedItem item : items) { if (item == null || item.startsAt() == null) continue; LocalDate date = item.startsAt().toLocalDate(); if (grouped.containsKey(date)) grouped.get(date).add(item); }
        Comparator<CalendarFeedItem> cmp = Comparator.comparing((CalendarFeedItem i) -> !i.allDay()).thenComparing(CalendarFeedItem::startsAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(i -> safe(i.displayTypeName())).thenComparing(i -> safe(i.title()));
        grouped.values().forEach(list -> list.sort(cmp)); return grouped;
    }

    private void configureCalendarCardClick(Node card, CalendarFeedItem item) {
        if (item == null || card == null) return;
        if (isManualEvent(item)) { Integer eventId = parseEventId(item.key()); if (eventId == null || eventId <= 0) return; card.setCursor(Cursor.HAND); card.setOnMouseClicked(evt -> openEditEventDialog(eventId)); return; }
        if (item.taskId() != null) { card.setCursor(Cursor.HAND); card.setOnMouseClicked(evt -> onOpenTask.accept(item.taskId().longValue())); return; }
        if (item.caseId() != null) { card.setCursor(Cursor.HAND); card.setOnMouseClicked(evt -> onOpenCase.accept(item.caseId())); }
    }

    private void openEditEventDialog(int eventId) { Integer tenantId = appState == null ? null : appState.getShaleClientId(); if (tenantId == null || tenantId <= 0 || calendarService == null) return; var event = calendarService.getEventById(eventId, tenantId); if (event == null) { showError("Could not load event for editing."); return; } var initial = new NewCalendarEventDialog.CreateCalendarEventInput(event.title(), event.calendarEventTypeId(), event.startsAt().toLocalDate(), event.allDay(), event.allDay() ? null : event.startsAt().toLocalTime(), resolveDurationMinutes(event), event.description(), event.caseId(), event.assignedToUserId()); Node rc = buildRelatedCaseNodeForEvent(event); Node rt = buildRelatedTaskNodeForEvent(event); NewCalendarEventDialog.showEditDialog(weekBoard.getScene() == null ? null : weekBoard.getScene().getWindow(), calendarService.listEffectiveEventTypes(tenantId), initial, input -> saveEditedEvent(event, input), () -> deleteEvent(event.calendarEventId(), tenantId), rc, rt, caseOptionsForPicker(event.caseId()), assignedUserOptionsForPicker(tenantId, event.assignedToUserId())); }
    private Node buildRelatedCaseNodeForEvent(com.shale.core.model.CalendarEvent event) { Integer tenantId = appState == null ? null : appState.getShaleClientId(); if (event == null || tenantId == null || event.caseId() == null) return null; List<CalendarFeedDao.CalendarCaseCardRow> rows = calendarFeedDao.listCaseCardRows(tenantId, List.of(event.caseId())); if (rows.isEmpty()) return null; var row = rows.getFirst(); return caseCardFactory.create(new CaseCardFactory.CaseCardModel(row.caseId(), row.caseName(), null, null, row.responsibleAttorney(), row.responsibleAttorneyColor(), row.nonEngagementLetterSent()), CaseCardFactory.Variant.MINI); }
    private Node buildRelatedTaskNodeForEvent(com.shale.core.model.CalendarEvent event) { Integer tenantId = appState == null ? null : appState.getShaleClientId(); if (event == null || tenantId == null || event.taskId() == null) return null; List<CalendarFeedDao.CalendarTaskCardRow> rows = calendarFeedDao.listTaskCardRows(tenantId, List.of(event.taskId())); if (rows.isEmpty()) return null; var row = rows.getFirst(); return taskCardFactory.create(new TaskCardFactory.TaskCardModel(row.taskId(), row.caseId() == null ? null : row.caseId().longValue(), row.caseName(), row.caseResponsibleAttorney(), row.caseResponsibleAttorneyColor(), row.caseNonEngagementLetterSent(), row.title(), null, row.createdByDisplayName(), row.priorityColorHex(), row.dueAt(), row.completedAt(), List.of()), TaskCardFactory.Variant.MINI); }
    private String saveEditedEvent(com.shale.core.model.CalendarEvent existing, NewCalendarEventDialog.CreateCalendarEventInput input) { LocalDateTime startsAt = input.allDay() ? input.date().atStartOfDay() : input.date().atTime(input.startTime()); LocalDateTime endsAt = input.allDay() ? null : startsAt.plusMinutes(input.durationMinutes()); try { calendarService.updateEvent(new com.shale.core.model.CalendarEvent(existing.calendarEventId(), existing.shaleClientId(), input.calendarEventTypeId(), input.caseId(), existing.taskId(), input.title(), input.description(), startsAt, endsAt, input.allDay(), existing.sourceType(), existing.sourceField(), existing.sourceId(), input.assignedToUserId(), existing.completed(), existing.cancelled(), existing.createdByUserId(), existing.createdAt(), existing.updatedAt())); showError(null); loadCurrentRange(); return null; } catch (RuntimeException ex) { return "Could not save event. Please check values and try again."; } }
    private List<NewCalendarEventDialog.CaseOption> caseOptionsForPicker(Integer selectedCaseId) {
        Map<Integer, String> names = new LinkedHashMap<>();
        if (caseDao != null) {
            caseDao.searchCasesByName("").forEach(c -> names.putIfAbsent(Math.toIntExact(c.id()), c.name()));
            if (selectedCaseId != null && selectedCaseId > 0 && !names.containsKey(selectedCaseId)) {
                var row = caseDao.getCaseRow(selectedCaseId.longValue());
                if (row != null) names.put(selectedCaseId, row.name());
            }
        }
        return names.entrySet().stream().map(e -> new NewCalendarEventDialog.CaseOption(e.getKey(), e.getValue())).sorted(Comparator.comparing(o -> safe(o.displayName()).toLowerCase(Locale.ROOT))).toList();
    }
    private List<NewCalendarEventDialog.AssignedUserOption> assignedUserOptionsForPicker(int tenantId, Integer selectedUserId) {
        if (caseTaskService == null) return List.of();
        Map<Integer, String> names = new LinkedHashMap<>();
        java.util.Map<Integer, String> colors = new LinkedHashMap<>();
        caseTaskService.loadAssignableUsers(tenantId).forEach(u -> { names.putIfAbsent(u.id(), safe(u.displayName())); colors.putIfAbsent(u.id(), u.color()); });
        if (selectedUserId != null && selectedUserId > 0) names.putIfAbsent(selectedUserId, "User #" + selectedUserId);
        return names.entrySet().stream().map(e -> new NewCalendarEventDialog.AssignedUserOption(e.getKey(), e.getValue(), colors.get(e.getKey()))).toList();
    }
    private int resolveDurationMinutes(com.shale.core.model.CalendarEvent event) { if (event == null || event.endsAt() == null || event.startsAt() == null || !event.endsAt().isAfter(event.startsAt())) return 60; long minutes = java.time.Duration.between(event.startsAt(), event.endsAt()).toMinutes(); long roundedUp = ((minutes + 29) / 30) * 30; if (roundedUp < 30) roundedUp = 30; if (roundedUp > 8 * 60) roundedUp = 8 * 60; return (int) roundedUp; }
    private String deleteEvent(Integer calendarEventId, int tenantId) { try { calendarService.deleteCalendarEvent(calendarEventId, tenantId); showError(null); loadCurrentRange(); return null; } catch (RuntimeException ex) { return "Could not delete event. Please try again."; } }
    private static boolean isManualEvent(CalendarFeedItem item) { String sourceType = safe(item.sourceType()).trim().toUpperCase(Locale.ROOT); return "MANUAL".equals(sourceType) || "CALENDAR_EVENT".equals(sourceType); }
    private static Integer parseEventId(String key) { if (key == null || !key.startsWith("EVENT:")) return null; try { return Integer.parseInt(key.substring("EVENT:".length())); } catch (NumberFormatException ex) { return null; } }
    private static LocalDate weekStartFor(LocalDate date) { return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)); }
    private static LocalDate workWeekStartFor(LocalDate date) { return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    private LocalDate currentRangeStart() { return switch (safe(viewModeChoice.getValue())) { case VIEW_FIVE_DAY -> workWeekStartFor(selectedDate); case VIEW_DAY -> selectedDate; case VIEW_MONTH -> selectedDate.withDayOfMonth(1); default -> weekStartFor(selectedDate); }; }
    private LocalDate currentRangeEndInclusive() { LocalDate start = currentRangeStart(); return switch (safe(viewModeChoice.getValue())) { case VIEW_FIVE_DAY -> start.plusDays(4); case VIEW_DAY -> start; case VIEW_MONTH -> YearMonth.from(selectedDate).atEndOfMonth(); default -> start.plusDays(6); }; }
    private LocalDate shiftSelectedDate(int direction) { return switch (safe(viewModeChoice.getValue())) { case VIEW_FIVE_DAY -> workWeekStartFor(selectedDate).plusWeeks(direction); case VIEW_DAY -> selectedDate.plusDays(direction); case VIEW_MONTH -> selectedDate.plusMonths(direction); default -> weekStartFor(selectedDate).plusWeeks(direction); }; }
    private String currentRangeLabel() { return switch (safe(viewModeChoice.getValue())) { case VIEW_MONTH -> MONTH_RANGE_FORMAT.format(selectedDate); case VIEW_DAY -> WEEK_RANGE_FORMAT.format(selectedDate); default -> WEEK_RANGE_FORMAT.format(currentRangeStart()) + " - " + WEEK_RANGE_FORMAT.format(currentRangeEndInclusive()); }; }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String formatHourLabel(int hour24) { int hour12 = hour24 % 12; if (hour12 == 0) hour12 = 12; return hour12 + (hour24 < 12 ? " AM" : " PM"); }
    private void setLoading(boolean loading) { calendarLoadingLabel.setVisible(loading); calendarLoadingLabel.setManaged(loading); }
    private void showError(String text) { boolean has = text != null && !text.isBlank(); calendarErrorLabel.setText(has ? text : ""); calendarErrorLabel.setVisible(has); calendarErrorLabel.setManaged(has); }
    private record CaseFilterOption(Integer caseId, String displayName) { boolean isAll() { return caseId == null; } }
    private record EventTypeFilterOption(String matchKey, String displayName) { boolean isAll() { return safe(matchKey).isBlank(); } }
}
