package com.shale.ui.component.factory;

import com.shale.core.model.CalendarFeedItem;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class CalendarEventCardFactory {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    public Node create(CalendarFeedItem item, LocalDate today, LocalDateTime now) {
        Objects.requireNonNull(item, "item");

        VBox card = new VBox(3);
        card.getStyleClass().add("calendar-event-card");

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

        card.getChildren().addAll(badges, time, title);

        String relatedHint = resolveRelatedHint(item);
        if (!relatedHint.isBlank()) {
            Label related = new Label(relatedHint);
            related.getStyleClass().add("calendar-event-related");
            card.getChildren().add(related);
        }

        return card;
    }

    private static String resolveType(CalendarFeedItem item) {
        if ("CASE_FIELD".equalsIgnoreCase(safe(item.sourceType()))) return "Case";
        if ("TASK_FIELD".equalsIgnoreCase(safe(item.sourceType()))) return "Task";
        if ("MANUAL".equalsIgnoreCase(safe(item.sourceType()))) return "Event";
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

    private static String resolveRelatedHint(CalendarFeedItem item) {
        if (item.caseId() != null) return "Case #" + item.caseId();
        if (item.taskId() != null) return "Task #" + item.taskId();
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
