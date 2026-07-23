package com.shale.ui.component.factory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

import com.shale.ui.util.ColorUtil;

public final class DueProximityStyles {
    public static final String COMPLETED_COLOR = "#16a34a";
    public static final String OVERDUE_COLOR = "#7f1d1d";
    public static final String DUE_WITHIN_ONE_DAY_COLOR = "#dc2626";
    public static final String DUE_WITHIN_ONE_WEEK_COLOR = "#f97316";
    public static final String DUE_WITHIN_TWO_WEEKS_COLOR = "#eab308";
    public static final String NEUTRAL_RAIL_COLOR = "#CBD5E1";
    public static final String DEFAULT_SURFACE = "rgba(248,250,252,0.96)";
    public static final String HOVER_SURFACE = "rgba(255,255,255,0.985)";
    public static final String CARD_RADIUS = "14";

    public record Presentation(String dueColorCss, String washCss, boolean urgent) {}

    private DueProximityStyles() {}

    public static String accentColor(LocalDateTime dueAt, LocalDateTime completedAt) {
        return accentColor(dueAt, completedAt, Clock.systemDefaultZone());
    }

    public static String accentColor(LocalDateTime dueAt, LocalDateTime completedAt, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (completedAt != null) return COMPLETED_COLOR;
        if (dueAt == null) return null;
        LocalDateTime now = LocalDateTime.now(clock);
        if (dueAt.isBefore(now)) return OVERDUE_COLOR;
        if (!dueAt.isAfter(now.plusDays(1))) return DUE_WITHIN_ONE_DAY_COLOR;
        if (!dueAt.isAfter(now.plusWeeks(1))) return DUE_WITHIN_ONE_WEEK_COLOR;
        if (!dueAt.isAfter(now.plusWeeks(2))) return DUE_WITHIN_TWO_WEEKS_COLOR;
        return null;
    }

    public static Presentation presentation(LocalDateTime dueAt, LocalDateTime completedAt, boolean hovered) {
        return presentation(dueAt, completedAt, hovered, Clock.systemDefaultZone());
    }

    public static Presentation presentation(LocalDateTime dueAt, LocalDateTime completedAt, boolean hovered, Clock clock) {
        String accent = accentColor(dueAt, completedAt, clock);
        String surface = hovered ? HOVER_SURFACE : DEFAULT_SURFACE;
        String dueColor = (accent == null || accent.isBlank()) ? NEUTRAL_RAIL_COLOR : accent;
        double leadingOpacity = accent == null || accent.isBlank() ? (hovered ? 0.24 : 0.22) : (hovered ? 0.18 : 0.14);
        double fadeOpacity = accent == null || accent.isBlank() ? (hovered ? 0.15 : 0.13) : (hovered ? 0.10 : 0.08);
        String wash = "linear-gradient(to right, "
                + ColorUtil.toCssRgba(dueColor, leadingOpacity) + " 0%, "
                + ColorUtil.toCssRgba(dueColor, fadeOpacity) + " 24%, "
                + surface + " 62%, "
                + surface + " 100%)";
        return new Presentation(dueColor, wash, accent != null && !accent.isBlank());
    }
}
