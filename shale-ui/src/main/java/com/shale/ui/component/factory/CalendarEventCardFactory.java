package com.shale.ui.component.factory;

import com.shale.core.model.CalendarFeedItem;
import com.shale.ui.util.ColorUtil;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

public final class CalendarEventCardFactory {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    public Node create(CalendarFeedItem item, LocalDate today, LocalDateTime now) {
        Objects.requireNonNull(item, "item");

        HBox card = new HBox(0);
        card.getStyleClass().add("calendar-event-card");
        Region accentBar = buildAccentBar(item.colorHex());
        if (accentBar != null) {
            card.getChildren().add(accentBar);
        }

        VBox content = new VBox(3);

        LocalDate itemDate = item.startsAt() == null ? null : item.startsAt().toLocalDate();
        if (item.startsAt() != null && item.startsAt().isBefore(now)) {
            card.getStyleClass().add("calendar-event-card-past");
        }
        if (itemDate != null && itemDate.equals(today)) {
            card.getStyleClass().add("calendar-event-card-today");
        }

        HBox badges = new HBox(4);
        badges.getStyleClass().add("calendar-event-card-badges");

        Label typeBadge = new Label(resolveType(item));
        typeBadge.getStyleClass().addAll("calendar-event-badge", "calendar-event-type-badge");

        Label categoryBadge = new Label(resolveCategory(item));
        categoryBadge.getStyleClass().addAll("calendar-event-badge", "calendar-event-category-badge");

        badges.getChildren().addAll(typeBadge, categoryBadge);

        Label time = new Label(resolveTime(item));
        time.getStyleClass().add("calendar-event-time");

        Label title = new Label(safe(item.title()));
        title.getStyleClass().add("calendar-event-title");
        title.setWrapText(true);

        Label relatedSummary = new Label(resolveRelatedSummary(item));
        relatedSummary.getStyleClass().add("calendar-event-related");
        if (!relatedSummary.getText().isBlank()) {
            content.getChildren().add(relatedSummary);
        }
        content.getChildren().addAll(time, title, badges);
        card.getChildren().add(content);

        return card;
    }

    public Node createAllDayBubble(CalendarFeedItem item) {
        HBox card = new HBox(6);
        card.getStyleClass().addAll("calendar-event-card", "calendar-all-day-bubble");
        Region accentBar = buildAccentBar(item.colorHex());
        if (accentBar != null) card.getChildren().add(accentBar);
        Label title = new Label(safe(item.title()));
        title.getStyleClass().add("calendar-all-day-title");
        title.setMaxWidth(Double.MAX_VALUE);
        Label badge = new Label(resolveType(item) + " · " + resolveCategory(item));
        badge.getStyleClass().add("calendar-all-day-meta");
        card.getChildren().addAll(title, badge);
        HBox.setHgrow(title, Priority.ALWAYS);
        return card;
    }

    private static Region buildAccentBar(String colorHex) {
        String normalized = ColorUtil.normalizeStoredColor(colorHex);
        if (normalized == null) {
            return null;
        }
        String accent = "#" + normalized.substring(0, 6);
        Region accentBar = new Region();
        accentBar.setMinWidth(5);
        accentBar.setPrefWidth(5);
        accentBar.setMaxWidth(5);
        accentBar.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 6 0 0 6;");
        return accentBar;
    }

    private static String resolveType(CalendarFeedItem item) {
        String sourceType = normalize(item.sourceType());
        String sourceField = normalize(item.sourceField());

        if ("CASE".equals(sourceType) || "CASE_FIELD".equals(sourceType)) return "Case";
        if ("TASK".equals(sourceType) || "TASK_FIELD".equals(sourceType)) return "Task";

        if ("PROJECTED".equals(sourceType)) {
            if ("DUEAT".equals(sourceField) || item.taskId() != null) return "Task";
            if (item.caseId() != null) return "Case";
        }

        if ("MANUAL".equals(sourceType) || "CALENDAR_EVENT".equals(sourceType)) return "Event";
        return "Other";
    }

    private static String resolveCategory(CalendarFeedItem item) {
        if (!safe(item.displayTypeName()).isBlank()) return item.displayTypeName();
        if (!safe(item.calendarEventTypeSystemKey()).isBlank()) {
            return item.calendarEventTypeSystemKey().replace('_', ' ').trim();
        }
        return "Event";
    }

    private static String resolveTime(CalendarFeedItem item) {
        if (item.allDay()) return "All day";
        if (item.startsAt() == null) return "Time TBD";
        return TIME_FORMAT.format(item.startsAt());
    }
    private static String resolveRelatedSummary(CalendarFeedItem item) {
        if (item.taskId() != null) return "Task #" + item.taskId();
        if (item.caseId() != null) return "Case #" + item.caseId();
        return "";
    }



    private static String normalize(String value) {
        return safe(value).trim().toUpperCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
