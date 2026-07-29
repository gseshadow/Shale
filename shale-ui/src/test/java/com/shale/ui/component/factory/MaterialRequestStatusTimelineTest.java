package com.shale.ui.component.factory;

import com.shale.core.dto.MaterialRequestStatusHistoryDto;
import com.shale.core.dto.RequestStatusDto;
import com.shale.ui.component.StatusTimeline;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class MaterialRequestStatusTimelineTest {
    @Test
    void repeatedHistoryIsChronologicalAndNeverReorderedByLookupSortOrder() {
        var items = MaterialRequestCardFactory.requestStatusItems(List.of(
                occurrence(30, "A", "A", 3), occurrence(10, "A", "A", 1), occurrence(20, "B", "B", 2)),
                List.of(status(2, "B", "Status B", "#222222", 1, true, false),
                        status(1, "A", "Status A", "#111111", 999, true, false),
                        status(3, "NEVER", "Never held", "#333333", 0, true, false)));

        assertEquals(List.of("Status A", "Status B", "Status A"), items.stream().map(StatusTimeline.Item::name).toList());
        assertEquals(List.of(StatusTimeline.State.COMPLETED, StatusTimeline.State.COMPLETED, StatusTimeline.State.CURRENT), items.stream().map(StatusTimeline.Item::state).toList());
        assertFalse(items.stream().anyMatch(i -> "Never held".equals(i.name()) || i.state() == StatusTimeline.State.FUTURE));
        assertEquals("Status A", items.getLast().name());
    }

    @Test
    void inactiveDeletedAndUnknownOccurrencesRemainVisibleFromStoredFallback() {
        var items = MaterialRequestCardFactory.requestStatusItems(List.of(
                occurrence(1, "INACTIVE", "Saved inactive", 1), occurrence(2, "LEGACY", "Legacy hold", 2)),
                List.of(status(1, "INACTIVE", "Inactive decorated", "#123456", 10, false, true)));

        assertEquals(List.of("Inactive decorated", "Legacy hold"), items.stream().map(StatusTimeline.Item::name).toList());
        assertEquals("#123456", items.getFirst().color());
        assertEquals(MaterialRequestCardFactory.NEUTRAL_STATUS_COLOR, items.getLast().color());
    }

    @Test
    void oneInitialOccurrenceIsOnlyCurrentNodeAndTooltipUsesPersistedMetadata() {
        var items = MaterialRequestCardFactory.requestStatusItems(List.of(occurrence(1, "A", "A", 1)), List.of());
        assertEquals(1, items.size());
        assertEquals(StatusTimeline.State.CURRENT, items.getFirst().state());
        assertTrue(items.getFirst().tooltip().contains("Changed: Jan 1, 2026 9:00 AM"));
        assertTrue(items.getFirst().tooltip().contains("By: Actor"));
    }

    private static MaterialRequestStatusHistoryDto occurrence(long id,String key,String stored,int day){
        return new MaterialRequestStatusHistoryDto(id,7,6502,44,key,stored,9,"Actor", LocalDateTime.of(2026,1,day,9,0));
    }
    private static RequestStatusDto status(int id,String key,String name,String color,int order,boolean active,boolean deleted){
        return new RequestStatusDto(id,7,key,name,color,order,active,deleted,new byte[]{1});
    }
}
