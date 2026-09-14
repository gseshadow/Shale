package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeDurationInputTest {
    @Test void standardChoicesContainEveryHalfHourInTwelveHourDisplay() {
        List<String> choices = TimeDurationInput.standardTimes();
        assertEquals(48, choices.size());
        assertEquals("12:00 AM", choices.getFirst());
        assertEquals("12:30 AM", choices.get(1));
        assertEquals("12:00 PM", choices.get(24));
        assertEquals("11:30 PM", choices.getLast());
        assertEquals(48, choices.stream().distinct().count());
    }

    @Test void parsesEstablishedCustomFormatsAndNormalizesDisplay() {
        assertEquals(LocalTime.of(9, 0), TimeDurationInput.parse("9"));
        assertEquals(LocalTime.of(9, 15), TimeDurationInput.parse("9:15"));
        assertEquals(LocalTime.of(9, 0), TimeDurationInput.parse("9 AM"));
        assertEquals(LocalTime.of(9, 15), TimeDurationInput.parse("9:15 am"));
        assertEquals(LocalTime.of(14, 15), TimeDurationInput.parse("14:15"));
        assertEquals("2:15 PM", TimeDurationInput.format(LocalTime.of(14, 15)));
    }

    @Test void rejectsInvalidAndAmbiguousMixedValues() {
        for (String invalid : List.of("", "noonish", "9:75", "14:15 PM", "25:00"))
            assertThrows(IllegalArgumentException.class, () -> TimeDurationInput.parse(invalid), invalid);
    }

    @Test void sameDayCrossMidnightAndMultiDayCalculationsPreserveLocalSemantics() {
        LocalDate first = LocalDate.of(2026, 8, 18);
        assertEquals(LocalDateTime.of(2026, 8, 18, 10, 0),
                TimeDurationInput.calculateEnd(first, null, LocalTime.of(9, 0), 60));
        assertEquals(LocalDateTime.of(2026, 8, 19, 0, 30),
                TimeDurationInput.calculateEnd(first, first.plusDays(1), LocalTime.of(23, 30), 60));
        assertEquals(LocalDateTime.of(2026, 8, 21, 10, 15),
                TimeDurationInput.calculateEnd(first, first.plusDays(3), LocalTime.of(9, 0), 75));
    }

    @Test void timestampPrepopulationRoundTripsExactTimedValues() {
        assertRoundTrip(LocalDateTime.of(2026, 8, 18, 9, 17), LocalDateTime.of(2026, 8, 18, 10, 4));
        assertRoundTrip(LocalDateTime.of(2026, 8, 18, 23, 45), LocalDateTime.of(2026, 8, 19, 0, 7));
        assertRoundTrip(LocalDateTime.of(2026, 8, 18, 9, 17), LocalDateTime.of(2026, 8, 21, 10, 4));
    }

    private static void assertRoundTrip(LocalDateTime start, LocalDateTime end) {
        var value = TimeDurationInput.fromTimestamps(start, end);
        assertEquals(start.toLocalTime(), value.startTime());
        assertEquals(end, TimeDurationInput.calculateEnd(start.toLocalDate(), end.toLocalDate(),
                value.startTime(), value.durationMinutes()));
    }
}
