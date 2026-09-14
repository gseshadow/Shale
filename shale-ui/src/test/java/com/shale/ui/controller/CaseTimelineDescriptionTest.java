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

    @Test
    void overviewEventsRenderActorReadableSentencesWithoutDescriptionContent() {
        var changedNumber = new CaseTimelineEventDto(1, 42, 7, "CASE_NUMBER_CHANGED", LocalDateTime.now(), 9,
                "changed Case Number", "from N-1 to N-2", "Jane Smith");
        var changedName = new CaseTimelineEventDto(2, 42, 7, "CASE_NAME_CHANGED", LocalDateTime.now(), 9,
                "changed Case Name", "from Old name to New name", "Jane Smith");
        var changedDescription = new CaseTimelineEventDto(3, 42, 7, "DESCRIPTION_CHANGED", LocalDateTime.now(), 9,
                "updated Description", null, "Jane Smith");

        assertEquals("Jane Smith changed Case Number from N-1 to N-2.", CaseController.timelineDescription(changedNumber));
        assertEquals("Jane Smith changed Case Name from Old name to New name.", CaseController.timelineDescription(changedName));
        assertEquals("Jane Smith updated Description.", CaseController.timelineDescription(changedDescription));
    }
}
