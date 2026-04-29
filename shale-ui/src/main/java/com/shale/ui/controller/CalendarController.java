package com.shale.ui.controller;

import com.shale.core.model.CalendarFeedItem;
import com.shale.data.dao.CalendarFeedDao;
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
import javafx.scene.layout.VBox;
import javafx.scene.Node;

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

    @FXML private Button todayButton;
    @FXML private Button prevWeekButton;
    @FXML private Button nextWeekButton;
    @FXML private ChoiceBox<String> viewModeChoice;
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
        Map<Integer, CaseCardFactory.CaseCardModel> caseModels = loadCaseModels(items);
        Map<Long, TaskCardFactory.TaskCardModel> taskModels = loadTaskModels(items);

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

            VBox dayItemsContainer = new VBox(4);
            dayItemsContainer.getStyleClass().add("calendar-day-items");

            if (dayItems.isEmpty()) {
                Label empty = new Label("No items");
                empty.getStyleClass().add("lane-empty-state");
                dayItemsContainer.getChildren().add(empty);
            } else {
                for (CalendarFeedItem item : dayItems) {
                    if (Boolean.getBoolean("shale.debug.calendar.colors")) {
                        System.out.println("[CALENDAR COLOR] key=" + item.key()
                                + " type=" + item.calendarEventTypeSystemKey()
                                + " colorHex=" + item.colorHex());
                    }
                    dayItemsContainer.getChildren().add(calendarEventCardFactory.create(item, today, now, buildRelatedCard(item, caseModels, taskModels)));
                }
            }
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



    private Node buildRelatedCard(CalendarFeedItem item, Map<Integer, CaseCardFactory.CaseCardModel> caseModels, Map<Long, TaskCardFactory.TaskCardModel> taskModels) {
        if (item.taskId() != null) {
            TaskCardFactory.TaskCardModel model = taskModels.get(item.taskId().longValue());
            if (model != null) return taskCardFactory.create(model, TaskCardFactory.Variant.MINI);
        }
        if (item.caseId() != null) {
            CaseCardFactory.CaseCardModel model = caseModels.get(item.caseId());
            if (model != null) return caseCardFactory.create(model, CaseCardFactory.Variant.MINI);
        }
        return null;
    }

    private Map<Integer, CaseCardFactory.CaseCardModel> loadCaseModels(List<CalendarFeedItem> items) {
        Map<Integer, CaseCardFactory.CaseCardModel> out = new HashMap<>();
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0 || calendarFeedDao == null) return out;
        List<Integer> caseIds = items.stream().map(CalendarFeedItem::caseId).filter(Objects::nonNull).distinct().toList();
        for (CalendarFeedDao.CalendarCaseCardRow row : calendarFeedDao.listCaseCardRows(tenantId, caseIds)) {
            out.put(row.caseId(), new CaseCardFactory.CaseCardModel(row.caseId(), row.caseName(), null, null, row.responsibleAttorney(), row.responsibleAttorneyColor(), row.nonEngagementLetterSent()));
        }
        return out;
    }

    private Map<Long, TaskCardFactory.TaskCardModel> loadTaskModels(List<CalendarFeedItem> items) {
        Map<Long, TaskCardFactory.TaskCardModel> out = new HashMap<>();
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0 || calendarFeedDao == null) return out;
        List<Integer> taskIds = items.stream().map(CalendarFeedItem::taskId).filter(Objects::nonNull).distinct().toList();
        for (CalendarFeedDao.CalendarTaskCardRow row : calendarFeedDao.listTaskCardRows(tenantId, taskIds)) {
            out.put(row.taskId(), new TaskCardFactory.TaskCardModel(row.taskId(), row.caseId() == null ? null : row.caseId().longValue(), row.caseName(), row.caseResponsibleAttorney(), row.caseResponsibleAttorneyColor(), row.caseNonEngagementLetterSent(), row.title(), null, row.createdByDisplayName(), row.priorityColorHex(), row.dueAt(), row.completedAt(), List.of()));
        }
        return out;
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
