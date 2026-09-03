package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shale.core.dto.CaseTimelineEventDto;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** User-visible regression coverage for Details timeline events returned by persistence. */
class CaseDetailsTimelineCoverageTest {
    @Test
    void overviewDetailsEventIsRenderedOnceWithItsPersistedActorAndDescription() {
        CaseTimelineEventDto event = new CaseTimelineEventDto(1, 42, 7, "CASE_NAME_CHANGED",
                LocalDateTime.of(2026, 9, 2, 12, 0), 9, "Case name changed",
                "from Old name to New name", "Alex Smith");

        assertEquals("Alex Smith Case name changed from Old name to New name.",
                CaseController.timelineDescription(event));
    }

    @Test
    void redactedLongDetailsDoNotInventSensitiveTimelineContent() {
        CaseTimelineEventDto event = new CaseTimelineEventDto(2, 42, 7, "DESCRIPTION_CHANGED",
                LocalDateTime.of(2026, 9, 2, 12, 0), 9, "Description updated", null, "Alex Smith");

        assertEquals("Alex Smith Description updated.", CaseController.timelineDescription(event));
    }
}
