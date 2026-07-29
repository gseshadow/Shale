package com.shale.ui.component.factory;

import com.shale.core.dto.RequestStatusDto;
import com.shale.ui.component.StatusTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class MaterialRequestStatusTimelineTest {
    @Test
    void effectiveStatusesAreFilteredOrderedAndResolvedByEstablishedLookupIdentity() {
        var items = MaterialRequestCardFactory.requestStatusItems("REVIEW", "Review", List.of(
                status(3, "DONE", "Tenant Complete", "#334455", 30, true, false),
                status(2, "REVIEW", "Tenant Review", "#223344", 20, true, false),
                status(9, null, "Deleted custom", "#999999", 5, true, true),
                status(8, null, "Inactive custom", "#888888", 4, false, false),
                status(1, "START", "Tenant Start", "#112233", 10, true, false)));

        assertEquals(List.of("Tenant Start", "Tenant Review", "Tenant Complete"), items.stream().map(StatusTimeline.Item::name).toList());
        assertEquals(List.of("#112233", "#223344", "#334455"), items.stream().map(StatusTimeline.Item::color).toList());
        assertEquals(List.of(StatusTimeline.State.COMPLETED, StatusTimeline.State.CURRENT, StatusTimeline.State.FUTURE),
                items.stream().map(StatusTimeline.Item::state).toList());
        assertEquals("REVIEW", items.get(1).identity());
    }

    @Test
    void unresolvedStoredStatusRemainsVisibleAndIsNotAssignedToAWorkflowStep() {
        var items = MaterialRequestCardFactory.requestStatusItems("LEGACY_HOLD", "Legacy Hold",
                List.of(status(1, "START", "Start", null, 10, true, false)));

        assertEquals(2, items.size());
        assertEquals(StatusTimeline.State.FUTURE, items.get(0).state());
        assertEquals("Legacy Hold", items.get(1).name());
        assertEquals(StatusTimeline.State.CURRENT, items.get(1).state());
        assertTrue(items.get(1).tooltip().contains("not in the effective"));
    }

    @Test
    void equalSortOrderUsesStableLookupIdRatherThanAlphabeticalName() {
        var items = MaterialRequestCardFactory.requestStatusItems("A", "A", List.of(
                status(20, "Z", "Alpha", null, 10, true, false),
                status(10, "A", "Zulu", null, 10, true, false)));
        assertEquals(List.of("Zulu", "Alpha"), items.stream().map(StatusTimeline.Item::name).toList());
    }

    private static RequestStatusDto status(int id, String key, String name, String color, int order, boolean active, boolean deleted) {
        return new RequestStatusDto(id, 7, key, name, color, order, active, deleted, new byte[]{1});
    }
}
