package com.shale.ui.controller;

import com.shale.core.model.CalendarFeedItem;
import com.shale.data.dao.CalendarFeedDao;
import com.shale.ui.component.dialog.NewCalendarEventDialog;
import com.shale.ui.component.factory.CalendarEventCardFactory;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.TaskCardFactory;
import com.shale.ui.services.CalendarService;
import com.shale.ui.state.AppState;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.scene.Cursor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CalendarController {
    private static final DateTimeFormatter WEEK_RANGE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DAY_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d");
    private static final String VIEW_WEEK = "Week";
    private static final int DAY_START_HOUR = 0;
    private static final int DAY_END_HOUR = 24;
    private static final double HALF_HOUR_HEIGHT = 34.0;

    @FXML private Button todayButton;
    @FXML private Button prevWeekButton;
    @FXML private Button nextWeekButton;
    @FXML private ChoiceBox<String> viewModeChoice;
    @FXML private Button newEventButton;
    @FXML private Label weekRangeLabel;
    @FXML private Label calendarLoadingLabel;
    @FXML private Label calendarErrorLabel;
    @FXML private HBox weekBoard;

    private AppState appState;
    private CalendarService calendarService;
    private CalendarFeedDao calendarFeedDao;
    private Consumer<Integer> onOpenCase;
    private Consumer<Long> onOpenTask;
    private int loadGeneration;
    private LocalDate selectedWeekStart;

    private final CalendarEventCardFactory calendarEventCardFactory = new CalendarEventCardFactory();
    private CaseCardFactory caseCardFactory = new CaseCardFactory(id -> {});
    private TaskCardFactory taskCardFactory = new TaskCardFactory(id -> {}, id -> {}, id -> {}, id -> {});

    private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "calendar-feed-loader");
        t.setDaemon(true);
        return t;
    });

    public void init(AppState appState, CalendarService calendarService, CalendarFeedDao calendarFeedDao, Consumer<Integer> onOpenCase, Consumer<Long> onOpenTask) {
        this.appState = appState;
        this.calendarService = calendarService;
        this.calendarFeedDao = calendarFeedDao;
        this.onOpenCase = onOpenCase == null ? id -> {} : onOpenCase;
        this.onOpenTask = onOpenTask == null ? id -> {} : onOpenTask;
        this.caseCardFactory = new CaseCardFactory(this.onOpenCase);
        this.taskCardFactory = new TaskCardFactory(this.onOpenTask, id -> {}, this.onOpenCase, id -> {});
    }

    @FXML
    private void initialize() {
        viewModeChoice.getItems().setAll(VIEW_WEEK);
        viewModeChoice.setValue(VIEW_WEEK);
        viewModeChoice.setDisable(true);
        selectedWeekStart = weekStartFor(LocalDate.now());
        renderWeekShell();
        Platform.runLater(this::loadSelectedWeek);
    }

    @FXML
    private void onToday() {
        selectedWeekStart = weekStartFor(LocalDate.now());
        loadSelectedWeek();
    }

    @FXML
    private void onPreviousWeek() {
        selectedWeekStart = selectedWeekStart.minusWeeks(1);
        loadSelectedWeek();
    }


    @FXML
    private void onNewEvent() {
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0 || calendarService == null) {
            showError("Calendar is unavailable because no tenant is selected.");
            return;
        }

        LocalDate defaultDate = LocalDate.now();
        if (selectedWeekStart != null) {
            LocalDate weekEnd = selectedWeekStart.plusDays(6);
            if (defaultDate.isBefore(selectedWeekStart) || defaultDate.isAfter(weekEnd)) {
                defaultDate = selectedWeekStart;
            }
        }

        var result = NewCalendarEventDialog.showAndWait(
                weekBoard.getScene() == null ? null : weekBoard.getScene().getWindow(),
                calendarService.listEffectiveEventTypes(tenantId),
                defaultDate);

        if (result.isEmpty()) return;

        var input = result.get();
        LocalDateTime startsAt = input.allDay() ? input.date().atStartOfDay() : input.date().atTime(input.startTime());
        LocalDateTime endsAt = input.allDay() ? null : startsAt.plusMinutes(input.durationMinutes());

        try {
            calendarService.createEvent(new com.shale.core.model.CalendarEvent(
                    null,
                    tenantId,
                    input.calendarEventTypeId(),
                    null,
                    null,
                    input.title(),
                    input.description(),
                    startsAt,
                    endsAt,
                    input.allDay(),
                    "MANUAL",
                    null,
                    null,
                    null,
                    false,
                    false,
                    appState == null ? null : appState.getUserId(),
                    null,
                    null));
            showError(null);
            loadSelectedWeek();
        } catch (RuntimeException ex) {
            showError("Could not save event. Please check values and try again.");
        }
    }

    @FXML
    private void onNextWeek() {
        selectedWeekStart = selectedWeekStart.plusWeeks(1);
        loadSelectedWeek();
    }

    private void loadSelectedWeek() {
        loadGeneration++;
        int current = loadGeneration;
        renderWeekShell();
        setLoading(true);
        showError(null);

        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0 || calendarService == null) {
            setLoading(false);
            showError("Calendar is unavailable because no tenant is selected.");
            return;
        }

        LocalDateTime start = selectedWeekStart.atStartOfDay();
        LocalDateTime end = selectedWeekStart.plusDays(7).atStartOfDay();
        dbExec.submit(() -> {
            try {
                List<CalendarFeedItem> items = calendarService.listCalendarFeed(tenantId, start, end);
                Platform.runLater(() -> {
                    if (current != loadGeneration) return;
                    setLoading(false);
                    renderWeek(items == null ? List.of() : items);
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    if (current != loadGeneration) return;
                    setLoading(false);
                    showError("Could not load calendar for this week.");
                    renderWeek(List.of());
                });
            }
        });
    }

    private void renderWeekShell() {
        LocalDate weekEnd = selectedWeekStart.plusDays(6);
        weekRangeLabel.setText(WEEK_RANGE_FORMAT.format(selectedWeekStart) + " - " + WEEK_RANGE_FORMAT.format(weekEnd));
        renderWeek(List.of());
    }

    private void renderWeek(List<CalendarFeedItem> items) {
        weekBoard.getChildren().clear();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        Map<LocalDate, List<CalendarFeedItem>> grouped = groupAndSort(items);

        for (int i = 0; i < 7; i++) {
            LocalDate day = selectedWeekStart.plusDays(i);
            List<CalendarFeedItem> dayItems = grouped.getOrDefault(day, List.of());
            VBox lane = new VBox(6);
            lane.getStyleClass().add("calendar-day-lane");
            lane.setPadding(new Insets(8));
            lane.setFillWidth(true);
            lane.setMinWidth(0);
            lane.setPrefWidth(0);
            lane.setMaxWidth(Double.MAX_VALUE);

            if (day.equals(today)) lane.getStyleClass().add("calendar-day-lane-today");
            if (day.isBefore(today)) lane.getStyleClass().add("calendar-day-lane-past");

            VBox header = new VBox(2);
            header.getStyleClass().add("calendar-day-header");
            Label dayName = new Label(day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault()));
            Label date = new Label(DAY_DATE_FORMAT.format(day));
            Label count = new Label(dayItems.size() + (dayItems.size() == 1 ? " item" : " items"));
            header.getChildren().addAll(dayName, date, count);

            VBox dayItemsContainer = buildDayTimeline(dayItems, today, now, day);
            ScrollPane laneScroll = new ScrollPane(dayItemsContainer);
            laneScroll.getStyleClass().add("calendar-day-scroll");
            laneScroll.setFitToWidth(true);
            laneScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            laneScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            lane.getChildren().addAll(header, laneScroll);
            VBox.setVgrow(laneScroll, Priority.ALWAYS);
            HBox.setHgrow(lane, Priority.ALWAYS);
            weekBoard.getChildren().add(lane);
        }
    }

    private VBox buildDayTimeline(List<CalendarFeedItem> dayItems,
                                  LocalDate today,
                                  LocalDateTime now,
                                  LocalDate day) {
        VBox root = new VBox(8);
        root.getStyleClass().add("calendar-day-items");

        List<CalendarFeedItem> allDayItems = dayItems.stream().filter(CalendarFeedItem::allDay).toList();
        List<CalendarFeedItem> timedItems = dayItems.stream().filter(i -> !i.allDay()).toList();

        VBox allDaySection = new VBox(4);
        allDaySection.getStyleClass().add("calendar-all-day-section");
        Label allDayLabel = new Label("All day");
        allDayLabel.getStyleClass().add("calendar-all-day-label");
        allDaySection.getChildren().add(allDayLabel);
        if (allDayItems.isEmpty()) {
            Label none = new Label("No all-day items");
            none.getStyleClass().add("lane-empty-state");
            allDaySection.getChildren().add(none);
        } else {
            for (CalendarFeedItem item : allDayItems) {
                Node card = buildAllDayBubbleNode(item, today, now);
                allDaySection.getChildren().add(card);
            }
        }

        Map<Integer, List<CalendarFeedItem>> timedBySlot = new HashMap<>();
        for (CalendarFeedItem item : timedItems) {
            LocalDateTime start = item.startsAt() == null ? day.atStartOfDay() : item.startsAt();
            double startMinutes = Math.max(0, Math.min(24 * 60, start.getHour() * 60.0 + start.getMinute()));
            double snappedStart = Math.floor(startMinutes / 30.0) * 30.0;
            int slot = Math.max(0, Math.min(47, (int) (snappedStart / 30.0)));
            timedBySlot.computeIfAbsent(slot, k -> new ArrayList<>()).add(item);
        }

        VBox timedGrid = new VBox(0);
        timedGrid.getStyleClass().add("calendar-timed-stack");
        for (int slot = 0; slot < 48; slot++) {
            int hour = slot / 2;
            boolean hourStart = slot % 2 == 0;
            HBox row = new HBox(6);
            row.getStyleClass().add("calendar-time-row");
            Label hourLabel = new Label(hourStart ? formatHourLabel(hour) : "");
            hourLabel.getStyleClass().add("calendar-hour-label");
            hourLabel.setMinWidth(62);
            hourLabel.setPrefWidth(62);
            hourLabel.setMaxWidth(62);
            VBox slotBox = new VBox(4);
            slotBox.getStyleClass().add("calendar-time-slot");
            slotBox.setMinHeight(HALF_HOUR_HEIGHT);
            slotBox.setPrefHeight(HALF_HOUR_HEIGHT);
            HBox.setHgrow(slotBox, Priority.ALWAYS);
            List<CalendarFeedItem> slotItems = timedBySlot.getOrDefault(slot, List.of());
            for (CalendarFeedItem item : slotItems) {
                Node card = buildEventCardNode(item, today, now);
                if (card instanceof Region regionCard) {
                    regionCard.setMinHeight(28);
                    regionCard.setPrefHeight(34);
                    regionCard.setMaxWidth(Double.MAX_VALUE);
                }
                slotBox.getChildren().add(card);
            }
            row.getChildren().addAll(hourLabel, slotBox);
            timedGrid.getChildren().add(row);
        }
        root.getChildren().addAll(allDaySection, timedGrid);
        return root;
    }

    private Node buildEventCardNode(CalendarFeedItem item, LocalDate today, LocalDateTime now) {
        if (Boolean.getBoolean("shale.debug.calendar.colors")) {
            System.out.println("[CALENDAR COLOR] key=" + item.key()
                    + " type=" + item.calendarEventTypeSystemKey()
                    + " colorHex=" + item.colorHex());
        }
        Node card = calendarEventCardFactory.create(item, today, now);
        configureCalendarCardClick(card, item);
        return card;
    }

    private Node buildAllDayBubbleNode(CalendarFeedItem item, LocalDate today, LocalDateTime now) {
        Node bubble = calendarEventCardFactory.createAllDayBubble(item);
        configureCalendarCardClick(bubble, item);
        return bubble;
    }

    private static String formatHourLabel(int hour24) {
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        return hour12 + (hour24 < 12 ? " AM" : " PM");
    }




    private Map<LocalDate, List<CalendarFeedItem>> groupAndSort(List<CalendarFeedItem> items) {
        Map<LocalDate, List<CalendarFeedItem>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) grouped.put(selectedWeekStart.plusDays(i), new ArrayList<>());
        for (CalendarFeedItem item : items) {
            if (item == null || item.startsAt() == null) continue;
            LocalDate date = item.startsAt().toLocalDate();
            if (grouped.containsKey(date)) grouped.get(date).add(item);
        }
        Comparator<CalendarFeedItem> cmp = Comparator
                .comparing((CalendarFeedItem i) -> !i.allDay())
                .thenComparing(CalendarFeedItem::startsAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(i -> safe(i.displayTypeName()))
                .thenComparing(i -> safe(i.title()));
        grouped.values().forEach(list -> list.sort(cmp));
        return grouped;
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private void configureCalendarCardClick(Node card, CalendarFeedItem item) {
        if (item == null || card == null) return;
        if (isManualEvent(item)) {
            Integer eventId = parseEventId(item.key());
            if (eventId == null || eventId <= 0) return;
            card.setCursor(Cursor.HAND);
            card.setOnMouseClicked(evt -> openEditEventDialog(eventId));
            return;
        }
        if (item.taskId() != null) {
            card.setCursor(Cursor.HAND);
            card.setOnMouseClicked(evt -> onOpenTask.accept(item.taskId().longValue()));
            return;
        }
        if (item.caseId() != null) {
            card.setCursor(Cursor.HAND);
            card.setOnMouseClicked(evt -> onOpenCase.accept(item.caseId()));
        }
    }

    private void openEditEventDialog(int eventId) {
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0 || calendarService == null) return;
        var event = calendarService.getEventById(eventId, tenantId);
        if (event == null) { showError("Could not load event for editing."); return; }
        var initial = new NewCalendarEventDialog.CreateCalendarEventInput(
                event.title(), event.calendarEventTypeId(), event.startsAt().toLocalDate(), event.allDay(),
                event.allDay() ? null : event.startsAt().toLocalTime(),
	resolveDurationMinutes(event), event.description());
        Node relatedCaseNode = buildRelatedCaseNodeForEvent(event);
        Node relatedTaskNode = buildRelatedTaskNodeForEvent(event);
        NewCalendarEventDialog.showEditDialog(
                weekBoard.getScene() == null ? null : weekBoard.getScene().getWindow(),
                calendarService.listEffectiveEventTypes(tenantId),
                initial,
                input -> saveEditedEvent(event, input),
                () -> deleteEvent(event.calendarEventId(), tenantId),
                relatedCaseNode,
                relatedTaskNode
        );
    }

    private Node buildRelatedCaseNodeForEvent(com.shale.core.model.CalendarEvent event) {
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (event == null || tenantId == null || event.caseId() == null) return null;
        List<CalendarFeedDao.CalendarCaseCardRow> rows = calendarFeedDao.listCaseCardRows(tenantId, List.of(event.caseId()));
        if (rows.isEmpty()) return null;
        CalendarFeedDao.CalendarCaseCardRow row = rows.getFirst();
        return caseCardFactory.create(new CaseCardFactory.CaseCardModel(row.caseId(), row.caseName(), null, null, row.responsibleAttorney(), row.responsibleAttorneyColor(), row.nonEngagementLetterSent()), CaseCardFactory.Variant.MINI);
    }
    private Node buildRelatedTaskNodeForEvent(com.shale.core.model.CalendarEvent event) {
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (event == null || tenantId == null || event.taskId() == null) return null;
        List<CalendarFeedDao.CalendarTaskCardRow> rows = calendarFeedDao.listTaskCardRows(tenantId, List.of(event.taskId()));
        if (rows.isEmpty()) return null;
        CalendarFeedDao.CalendarTaskCardRow row = rows.getFirst();
        return taskCardFactory.create(new TaskCardFactory.TaskCardModel(row.taskId(), row.caseId() == null ? null : row.caseId().longValue(), row.caseName(), row.caseResponsibleAttorney(), row.caseResponsibleAttorneyColor(), row.caseNonEngagementLetterSent(), row.title(), null, row.createdByDisplayName(), row.priorityColorHex(), row.dueAt(), row.completedAt(), List.of()), TaskCardFactory.Variant.MINI);
    }

    private String saveEditedEvent(com.shale.core.model.CalendarEvent existing, NewCalendarEventDialog.CreateCalendarEventInput input) {
        LocalDateTime startsAt = input.allDay() ? input.date().atStartOfDay() : input.date().atTime(input.startTime());
        LocalDateTime endsAt = input.allDay() ? null : startsAt.plusMinutes(input.durationMinutes());
        try {
            calendarService.updateEvent(new com.shale.core.model.CalendarEvent(existing.calendarEventId(), existing.shaleClientId(), input.calendarEventTypeId(), existing.caseId(), existing.taskId(), input.title(), input.description(), startsAt, endsAt, input.allDay(), existing.sourceType(), existing.sourceField(), existing.sourceId(), existing.assignedToUserId(), existing.completed(), existing.cancelled(), existing.createdByUserId(), existing.createdAt(), existing.updatedAt()));
            showError(null);
            loadSelectedWeek();
            return null;
        } catch (RuntimeException ex) {
            return "Could not save event. Please check values and try again.";
        }
    }


    private int resolveDurationMinutes(com.shale.core.model.CalendarEvent event) {
        if (event == null || event.endsAt() == null || event.startsAt() == null || !event.endsAt().isAfter(event.startsAt())) {
            return 60;
        }
        long minutes = java.time.Duration.between(event.startsAt(), event.endsAt()).toMinutes();
        long roundedUp = ((minutes + 29) / 30) * 30;
        if (roundedUp < 30) roundedUp = 30;
        if (roundedUp > 8 * 60) roundedUp = 8 * 60;
        return (int) roundedUp;
    }

    private String deleteEvent(Integer calendarEventId, int tenantId) {
        try {
            calendarService.deleteCalendarEvent(calendarEventId, tenantId);
            showError(null);
            loadSelectedWeek();
            return null;
        } catch (RuntimeException ex) {
            return "Could not delete event. Please try again.";
        }
    }
    private static boolean isManualEvent(CalendarFeedItem item) {
        String sourceType = safe(item.sourceType()).trim().toUpperCase(Locale.ROOT);
        return "MANUAL".equals(sourceType) || "CALENDAR_EVENT".equals(sourceType);
    }
    private static Integer parseEventId(String key) {
        if (key == null || !key.startsWith("EVENT:")) return null;
        try { return Integer.parseInt(key.substring("EVENT:".length())); } catch (NumberFormatException ex) { return null; }
    }

    private static LocalDate weekStartFor(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    private void setLoading(boolean loading) {
        calendarLoadingLabel.setVisible(loading);
        calendarLoadingLabel.setManaged(loading);
    }

    private void showError(String text) {
        boolean has = text != null && !text.isBlank();
        calendarErrorLabel.setText(has ? text : "");
        calendarErrorLabel.setVisible(has);
        calendarErrorLabel.setManaged(has);
    }
}
