package com.shale.ui.component;

import com.shale.ui.util.ControlStyles;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Shared editable time and exact hours/minutes input for scheduling forms. */
public final class TimeDurationInput extends HBox {
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final DateTimeFormatter TWELVE_HOUR = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("h[:mm] a").parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .toFormatter(Locale.US).withResolverStyle(ResolverStyle.SMART);
    private static final DateTimeFormatter TWENTY_FOUR_HOUR = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 1, 2, java.time.format.SignStyle.NOT_NEGATIVE)
            .optionalStart().appendLiteral(':').appendValue(ChronoField.MINUTE_OF_HOUR, 2).optionalEnd()
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .toFormatter(Locale.US).withResolverStyle(ResolverStyle.STRICT);
    private static final List<String> STANDARD_TIMES = IntStream.range(0, 48)
            .mapToObj(i -> LocalTime.MIDNIGHT.plusMinutes(i * 30L).format(DISPLAY)).toList();

    private final ComboBox<String> startTime = new ComboBox<>();
    private final ComboBox<Integer> hours = new ComboBox<>();
    private final ComboBox<Integer> minutes = new ComboBox<>();

    public TimeDurationInput() {
        setSpacing(8);
        setAlignment(Pos.BOTTOM_LEFT);
        startTime.setEditable(true);
        startTime.getItems().setAll(STANDARD_TIMES);
        startTime.setValue(format(LocalTime.of(9, 0)));
        startTime.setPromptText("Enter or select a time");
        startTime.setAccessibleText("Start Time");
        hours.getItems().setAll(IntStream.rangeClosed(0, 23).boxed().toList());
        minutes.getItems().setAll(IntStream.rangeClosed(0, 59).boxed().toList());
        hours.setValue(1);
        minutes.setValue(0);
        hours.setAccessibleText("Duration Hours");
        minutes.setAccessibleText("Duration Minutes");
        ControlStyles.formControl(startTime);
        ControlStyles.formControl(hours);
        ControlStyles.formControl(minutes);
        VBox timeBox = labeled("Start Time", startTime);
        VBox hoursBox = labeled("Hours", hours);
        VBox minutesBox = labeled("Minutes", minutes);
        timeBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(timeBox, Priority.ALWAYS);
        hoursBox.setMinWidth(90);
        minutesBox.setMinWidth(90);
        getChildren().setAll(timeBox, hoursBox, minutesBox);
    }

    private static VBox labeled(String text, ComboBox<?> control) {
        Label label = new Label(text);
        label.setLabelFor(control);
        label.setAccessibleText(text);
        control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, label, control);
    }

    public LocalTime commitTime() {
        String value = startTime.getEditor().getText();
        LocalTime parsed = parse(value);
        String normalized = format(parsed);
        startTime.setValue(normalized);
        startTime.getEditor().setText(normalized);
        return parsed;
    }

    public int durationMinutes() {
        Integer h = hours.getValue();
        Integer m = minutes.getValue();
        if (h == null || m == null) throw new IllegalArgumentException("Hours and Minutes are required for a timed event.");
        int total = h * 60 + m;
        if (total == 0) throw new IllegalArgumentException("Duration must be greater than 0 Hours and 0 Minutes.");
        return total;
    }

    public void setTimedValue(LocalTime time, int totalMinutes) {
        LocalTime safeTime = time == null ? LocalTime.of(9, 0) : time;
        int safeDuration = totalMinutes <= 0 ? 60 : Math.min(totalMinutes, 23 * 60 + 59);
        startTime.setValue(format(safeTime));
        startTime.getEditor().setText(format(safeTime));
        hours.setValue(safeDuration / 60);
        minutes.setValue(safeDuration % 60);
    }

    public void setTimedControlsDisabled(boolean disabled) {
        startTime.setDisable(disabled);
        hours.setDisable(disabled);
        minutes.setDisable(disabled);
    }

    public static LocalTime parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Start Time is required for a timed event.");
        String input = value.strip().replaceAll("\\s+", " ");
        boolean hasMeridiem = input.toUpperCase(Locale.US).matches(".*\\b(AM|PM)$");
        try {
            LocalTime parsed = LocalTime.parse(input, hasMeridiem ? TWELVE_HOUR : TWENTY_FOUR_HOUR);
            return parsed;
        } catch (DateTimeParseException | NumberFormatException ex) {
            throw new IllegalArgumentException("Start Time must be a valid time such as 9:15 AM or 14:15.");
        }
    }

    public static String format(LocalTime time) { return time.format(DISPLAY); }
    public static List<String> standardTimes() { return STANDARD_TIMES; }

    public static LocalDateTime calculateEnd(LocalDate startDate, LocalDate endDate, LocalTime startTime, int durationMinutes) {
        LocalDate selectedEnd = endDate == null ? startDate : endDate;
        LocalDateTime candidate = selectedEnd.atTime(startTime).plusMinutes(durationMinutes);
        long wrappedDays = (startTime.toSecondOfDay() / 60L + durationMinutes) / (24 * 60L);
        return candidate.minusDays(wrappedDays);
    }

    public static TimedValue fromTimestamps(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt == null) return new TimedValue(LocalTime.of(9, 0), 60);
        if (endsAt == null || !endsAt.isAfter(startsAt)) return new TimedValue(startsAt.toLocalTime(), 60);
        long minutes = Duration.between(startsAt, endsAt).toMinutes();
        int subDayMinutes = (int) Math.floorMod(minutes, 24 * 60);
        if (subDayMinutes == 0) subDayMinutes = 60;
        return new TimedValue(startsAt.toLocalTime(), subDayMinutes);
    }

    public record TimedValue(LocalTime startTime, int durationMinutes) {}

    public ComboBox<String> startTimeControl() { return startTime; }
    public ComboBox<Integer> hoursControl() { return hours; }
    public ComboBox<Integer> minutesControl() { return minutes; }
}
