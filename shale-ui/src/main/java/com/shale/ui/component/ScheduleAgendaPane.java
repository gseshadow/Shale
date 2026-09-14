package com.shale.ui.component;

import com.shale.core.model.CalendarFeedCategory;
import com.shale.core.model.CalendarFeedClickTarget;
import com.shale.core.model.CalendarFeedItem;
import com.shale.core.model.CalendarFeedSourceFilter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

public final class ScheduleAgendaPane {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    public record ClickHandlers(Consumer<Integer> onEvent, Consumer<Long> onTask, Consumer<Integer> onCase,
                                BiConsumer<Integer, Long> onCaseDates) {}

    private final VBox agendaBox;
    private final Label statusLabel;
    private final ClickHandlers handlers;

    public ScheduleAgendaPane(VBox agendaBox, Label statusLabel, ClickHandlers handlers) {
        this.agendaBox = Objects.requireNonNull(agendaBox, "agendaBox");
        this.statusLabel = statusLabel;
        this.handlers = handlers == null ? new ClickHandlers(null, null, null, null) : handlers;
    }

    public void showMessage(String message) {
        agendaBox.getChildren().clear();
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setVisible(true);
            statusLabel.setManaged(true);
        }
    }

    public void render(List<CalendarFeedItem> items, CalendarFeedSourceFilter filter, String allDisabledMessage, String emptyMessage, String noUpcomingMessage) {
        agendaBox.getChildren().clear();
        CalendarFeedSourceFilter activeFilter = filter == null ? CalendarFeedSourceFilter.caseCalendarDefaults() : filter;
        if (!activeFilter.hasAnyEnabled()) {
            showMessage(allDisabledMessage);
            return;
        }
        List<CalendarFeedItem> visible = (items == null ? List.<CalendarFeedItem>of() : items).stream().filter(activeFilter::matches).toList();
        if (visible.isEmpty()) {
            showMessage(emptyMessage);
            return;
        }
        if (statusLabel != null) {
            statusLabel.setVisible(false);
            statusLabel.setManaged(false);
        }
        LocalDate today = LocalDate.now();
        List<CalendarFeedItem> upcoming = visible.stream().filter(i -> !itemDate(i).isBefore(today)).sorted(upcomingComparator()).toList();
        List<CalendarFeedItem> past = visible.stream().filter(i -> itemDate(i).isBefore(today)).sorted(pastComparator()).toList();
        if (upcoming.isEmpty()) agendaBox.getChildren().add(sectionMessage("Upcoming", noUpcomingMessage));
        else appendSection("Upcoming", upcoming, false);
        if (!past.isEmpty()) appendSection("Past", past, true);
    }

    public static Comparator<CalendarFeedItem> upcomingComparator() {
        return Comparator.comparing(ScheduleAgendaPane::itemDate)
                .thenComparing(i -> !i.allDay())
                .thenComparing(CalendarFeedItem::startsAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(i -> safe(i.key()));
    }

    public static Comparator<CalendarFeedItem> pastComparator() {
        return Comparator.comparing(ScheduleAgendaPane::itemDate, Comparator.reverseOrder())
                .thenComparing(i -> !i.allDay())
                .thenComparing(CalendarFeedItem::startsAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(i -> safe(i.key()));
    }

    private void appendSection(String title, List<CalendarFeedItem> items, boolean past) {
        Label section = new Label(title);
        section.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        agendaBox.getChildren().add(section);
        LocalDate current = null;
        for (CalendarFeedItem item : items) {
            LocalDate date = itemDate(item);
            if (!Objects.equals(current, date)) {
                current = date;
                Label heading = new Label(formatDateHeading(date));
                heading.setStyle("-fx-font-weight: 700; -fx-opacity: 0.78;");
                agendaBox.getChildren().add(heading);
            }
            agendaBox.getChildren().add(createRow(item, past));
        }
    }

    private Node createRow(CalendarFeedItem item, boolean past) {
        Label time = new Label(item.allDay() ? "All day" : item.startsAt().format(TIME_FORMAT));
        time.setMinWidth(72);
        Label title = new Label(safe(item.title()).replaceFirst("\\s+—\\s+.*$", ""));
        title.setWrapText(true);
        title.setStyle("-fx-font-weight: 700;");
        Label meta = new Label(CalendarFeedCategory.classify(item).name().replace('_', ' ') + " • " + safe(item.displayTypeName()));
        meta.setStyle("-fx-opacity: 0.68; -fx-font-size: 11px;");
        VBox text = new VBox(2, title, meta);
        HBox row = new HBox(10, time, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 10, 8, 10));
        row.setStyle("-fx-background-color: rgba(255,255,255,0.86); -fx-background-radius: 10; -fx-border-color: rgba(31,41,55,0.12); -fx-border-radius: 10;" + (past ? " -fx-opacity: 0.78;" : ""));
        configureClick(row, item);
        return row;
    }

    private void configureClick(Node row, CalendarFeedItem item) {
        CalendarFeedClickTarget target = CalendarFeedClickTarget.resolve(item);
        if (!target.actionable()) return;
        row.setCursor(Cursor.HAND);
        Runnable activate = () -> {
            switch (target.kind()) {
                case CALENDAR_EVENT -> { if (handlers.onEvent() != null) handlers.onEvent().accept(Math.toIntExact(target.id())); }
                case TASK -> { if (handlers.onTask() != null) handlers.onTask().accept(target.id()); }
                case CASE -> { if (handlers.onCase() != null) handlers.onCase().accept(Math.toIntExact(target.id())); }
                case CASE_DATES -> { if (handlers.onCaseDates() != null) handlers.onCaseDates().accept(target.caseId(), target.id()); }
                case NONE -> { }
            }
        };
        row.setFocusTraversable(true);
        row.setOnMouseClicked(e -> { if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && e.isStillSincePress()) { activate.run(); e.consume(); } });
        row.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ENTER || e.getCode() == javafx.scene.input.KeyCode.SPACE) { activate.run(); e.consume(); } });
    }

    public static LocalDate itemDate(CalendarFeedItem item) {
        return item == null || item.startsAt() == null ? LocalDate.MAX : item.startsAt().toLocalDate();
    }

    private static String formatDateHeading(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "Today — " + date.format(DATE_FORMAT);
        if (date.equals(today.plusDays(1))) return "Tomorrow — " + date.format(DATE_FORMAT);
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " — " + date.format(DATE_FORMAT);
    }

    private static Node sectionMessage(String title, String message) {
        VBox box = new VBox(4);
        Label heading = new Label(title);
        heading.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Label body = new Label(message);
        body.setStyle("-fx-opacity: 0.72;");
        box.getChildren().addAll(heading, body);
        return box;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
