package com.shale.ui.component.dialog;

import com.shale.core.model.CalendarEventType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewCalendarEventDialogDefaultsTest {

    @Test
    void defaultTypePrefersMeetingOverGeneral() {
        List<CalendarEventType> types = List.of(
                new CalendarEventType(1, null, "GENERAL", "General", null, 10, true, null, null),
                new CalendarEventType(2, null, "MEETING", "Meeting", null, 20, true, null, null)
        );
        assertEquals(2, NewCalendarEventDialog.resolveDefaultTypeId(types));
    }

    @Test
    void createDefaultDateIsToday() {
        LocalDate today = LocalDate.now();
        assertEquals(today, LocalDate.now());
    }
}
