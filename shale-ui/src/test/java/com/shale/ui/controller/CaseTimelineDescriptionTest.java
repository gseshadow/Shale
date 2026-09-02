package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shale.core.dto.CaseTimelineEventDto;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CaseTimelineDescriptionTest {
    @Test
    void statusHistoryIsRenderedAsAReadableAttributedSentence() {
        var event = new CaseTimelineEventDto(1, 42, 7, "STATUS_CHANGED", LocalDateTime.now(), 9,
                "Status changed", "from Intake to Active", "Jane Smith");
        assertEquals("Jane Smith changed Status from Intake to Active.", CaseController.timelineDescription(event));
    }

    @Test
    void incompleteHistoricalEventHasGracefulFallbacks() {
        var event = new CaseTimelineEventDto(1, 42, 7, "", null, null, "", "", "");
        assertEquals("System Case activity.", CaseController.timelineDescription(event));
    }
}
