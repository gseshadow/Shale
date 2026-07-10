package com.shale.ui.services;

import com.shale.core.model.CalendarEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarServiceManualTypeFilterTest {

    @Test
    void projectedOnlyEventTypesAreNotSelectableForManualEvents() {
        assertFalse(CalendarService.isSelectableManualType(type("TASK_DUE")));
        assertFalse(CalendarService.isSelectableManualType(type("TASK_DUE_DATE")));
        assertFalse(CalendarService.isSelectableManualType(type("CASE_DEADLINE")));
        assertFalse(CalendarService.isSelectableManualType(type("STATUTE_OF_LIMITATIONS")));
        assertFalse(CalendarService.isSelectableManualType(type("TORT_NOTICE_DEADLINE")));
        assertFalse(CalendarService.isSelectableManualType(type("DISCOVERY_DEADLINE")));
        assertFalse(CalendarService.isSelectableManualType(type("CASE_DATE")));
        assertFalse(CalendarService.isSelectableManualType(type("PROJECTED_CUSTOM")));

        assertTrue(CalendarService.isSelectableManualType(type("DEADLINE")));
        assertTrue(CalendarService.isSelectableManualType(type("MEETING")));
        assertTrue(CalendarService.isSelectableManualType(type(null)));
    }

    private static CalendarEventType type(String systemKey) {
        return new CalendarEventType(1, null, systemKey, systemKey == null ? "Custom" : systemKey, null, 1, true, null, null);
    }
}
