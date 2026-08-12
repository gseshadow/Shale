package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarCaseDatesRefreshContractTest {
    @Test
    void calendarRefreshesOnlySafeRelevantCaseDatesInvalidations() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));

        assertTrue(source.contains("LiveUpdateEvents.ENTITY_CASE_DATES.equals(event.entityType())"));
        assertTrue(source.contains("event.shaleClientId() != tenantId"));
        assertTrue(source.contains("localInstance.equals(event.clientInstanceId())"));
        assertTrue(source.contains("rememberCaseDatesEvent(event.eventId())"));
        assertTrue(source.contains("caseDatesRefreshQueued.compareAndSet(false, true)"));
        assertTrue(source.contains("loadCurrentRange(false); // load generation discards any older in-flight response"));
    }
}
