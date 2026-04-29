package com.shale.ui.controller;

import com.shale.core.model.CalendarFeedItem;
import com.shale.ui.services.CalendarService;
import com.shale.ui.state.AppState;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CalendarController {
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    @FXML private Label calendarTitleLabel;
    @FXML private Label calendarSubtitleLabel;
    @FXML private Label calendarLoadingLabel;
    @FXML private Label calendarEmptyStateLabel;
    @FXML private VBox upcomingListContainer;

    private AppState appState;
    private CalendarService calendarService;
    private int loadGeneration;

    private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "calendar-feed-loader");
        t.setDaemon(true);
        return t;
    });

    public void init(AppState appState, CalendarService calendarService) {
        this.appState = appState;
        this.calendarService = calendarService;
    }

    @FXML
    private void initialize() {
        calendarTitleLabel.setText("Calendar");
        calendarSubtitleLabel.setText("Calendar events and case/task deadlines will appear here.");
        setLoading(true);
        Platform.runLater(this::loadUpcoming);
    }

    private void loadUpcoming() {
        loadGeneration++;
        int current = loadGeneration;
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0 || calendarService == null) {
            setLoading(false);
            showEmpty("Calendar is unavailable because no tenant is selected.");
            return;
        }

        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusDays(30);
        dbExec.submit(() -> {
            final List<CalendarFeedItem> items;
            try {
                items = calendarService.listCalendarFeed(tenantId, start, end);
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    if (current != loadGeneration) return;
                    renderItems(List.of());
                });
                return;
            }
            Platform.runLater(() -> {
                if (current != loadGeneration) return;
                renderItems(items);
            });
        });
    }

    private void renderItems(List<CalendarFeedItem> items) {
        upcomingListContainer.getChildren().clear();
        setLoading(false);
        if (items == null || items.isEmpty()) {
            showEmpty("No upcoming calendar items.");
            return;
        }
        calendarEmptyStateLabel.setVisible(false);
        calendarEmptyStateLabel.setManaged(false);
        for (CalendarFeedItem item : items) {
            Label row = new Label(formatRow(item));
            row.getStyleClass().add("lane-empty-state");
            row.setWrapText(true);
            upcomingListContainer.getChildren().add(row);
        }
    }

    private String formatRow(CalendarFeedItem item) {
        String when = item.allDay()
                ? item.startsAt().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                : item.startsAt().format(DISPLAY_FORMAT);
        return when + " • " + item.displayTypeName() + " • " + item.title();
    }

    private void setLoading(boolean loading) {
        calendarLoadingLabel.setVisible(loading);
        calendarLoadingLabel.setManaged(loading);
    }

    private void showEmpty(String text) {
        calendarEmptyStateLabel.setText(text);
        calendarEmptyStateLabel.setVisible(true);
        calendarEmptyStateLabel.setManaged(true);
    }
}
