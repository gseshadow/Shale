package com.shale.ui.component.factory;

import com.shale.core.model.CalendarFeedItem;
import com.shale.ui.util.ColorUtil;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

public final class CalendarEventCardFactory {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    public Node create(CalendarFeedItem item, LocalDate today, LocalDateTime now, Node relatedNode) {
        Objects.requireNonNull(item, "item");

        VBox card = new VBox(3);
        card.getStyleClass().add("calendar-event-card");
        applyTypeAccent(card, item.colorHex());

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

        if (relatedNode != null) {
            relatedNode.getStyleClass().add("calendar-event-related");
            card.getChildren().add(relatedNode);
        }

        return card;
    }

    private static void applyTypeAccent(VBox card, String colorHex) {
        String normalized = ColorUtil.normalizeStoredColor(colorHex);
        if (normalized == null) {
            return;
        }
        String accent = "#" + normalized.substring(0, 6);
        card.setStyle("-fx-border-width: 1 1 1 4; -fx-border-color: rgba(20, 42, 74, 0.12) rgba(20, 42, 74, 0.12) rgba(20, 42, 74, 0.12) " + accent + ";");
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



    private static String normalize(String value) {
        return safe(value).trim().toUpperCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
