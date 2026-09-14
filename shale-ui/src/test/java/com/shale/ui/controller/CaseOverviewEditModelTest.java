package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseOverviewEditModelTest {
    @Test void baselineShownRowsKeepExactResolvedOverviewOrder() {
        var model = model(List.of(t(5, 50, "Tort"), t(1, 10, "Injury"), t(3, 30, "Intake")), List.of(3, 1));
        assertEquals(List.of(3, 1), ids(model.shownTypes()), "shown rows must follow the baseline DTO, not definition order");
        assertEquals(List.of(5), ids(model.availableTypes()));
    }

    @Test void movingUpAndDownVisiblySwapsAdjacentShownRows() {
        var model = model(List.of(t(1, 10, "One"), t(2, 20, "Two"), t(3, 30, "Three")), List.of(1, 2, 3));
        assertTrue(model.moveUp(3));
        assertEquals(List.of(1, 3, 2), ids(model.shownTypes()), "Move Up must change rendered row order");
        assertTrue(model.moveDown(1));
        assertEquals(List.of(3, 1, 2), ids(model.shownTypes()), "Move Down must change rendered row order");
    }

    @Test void movementIndexesAndButtonStatesIgnoreAvailableRows() {
        var model = model(List.of(t(1, 1, "Available first"), t(2, 2, "Shown first"), t(3, 3, "Available middle"), t(4, 4, "Shown last")), List.of(2, 4));
        assertFalse(model.canMoveUp(2), "first shown row cannot move up");
        assertTrue(model.canMoveDown(2));
        assertTrue(model.canMoveUp(4));
        assertFalse(model.canMoveDown(4), "last shown row cannot move down");
        assertTrue(model.moveDown(2));
        assertEquals(List.of(4, 2), ids(model.shownTypes()));
    }

    @Test void addingAppendsOnceAndRemovingReturnsToStableAvailableOrder() {
        var model = model(List.of(t(3, 30, "Zulu"), t(1, 10, "Alpha"), t(2, 20, "Beta")), List.of(2));
        assertEquals(List.of(1, 3), ids(model.availableTypes()));
        model.select(1);
        model.select(1);
        assertEquals(List.of(2, 1), ids(model.shownTypes()), "new selections append exactly once");
        model.remove(2);
        assertEquals(List.of(1), ids(model.shownTypes()));
        assertEquals(List.of(2, 3), ids(model.availableTypes()), "removed rows return to available definition order");
    }

    @Test void submittedIdsAlwaysEqualVisibleShownRowOrder() {
        var model = model(List.of(t(1, 1, "One"), t(2, 2, "Two"), t(3, 3, "Three")), List.of(2, 1));
        model.select(3);
        model.moveUp(3);
        assertEquals(ids(model.shownTypes()), model.selectedIds());
    }

    @Test void intakeClearAndFailedSaveRemainStagedAndDoubleSaveIsPrevented() {
        var model = new CaseOverviewEditModel(List.of(t(1, 1, "One")), List.of(t(1, 1, "One")), 9);
        model.setIntakeUserId(null);
        assertTrue(model.intakeChanged());
        assertTrue(model.beginSave());
        assertFalse(model.beginSave());
        model.saveFailed();
        assertNull(model.intakeUserId());
        assertTrue(model.beginSave());
    }

    @Test void historicalInactiveConfiguredTypeIsShownUntilExplicitRemoval() {
        var historical = inactive(8, "Historical");
        var model = new CaseOverviewEditModel(List.of(t(1, 1, "Active")), List.of(historical, t(1, 1, "Active")), null);
        assertEquals(List.of(8, 1), ids(model.shownTypes()));
        model.remove(8);
        assertEquals(List.of(1), ids(model.shownTypes()));
        assertFalse(ids(model.availableTypes()).contains(8), "inactive historical types cannot become available choices");
    }

    private static CaseOverviewEditModel model(List<EffectiveCaseDateTypeDto> available, List<Integer> selected) {
        return new CaseOverviewEditModel(available, selected.stream().map(id -> available.stream().filter(t -> t.id() == id).findFirst().orElseThrow()).toList(), null);
    }

    private static List<Integer> ids(List<EffectiveCaseDateTypeDto> types) { return types.stream().map(EffectiveCaseDateTypeDto::id).toList(); }
    private static EffectiveCaseDateTypeDto t(int id, int order, String name) { return new EffectiveCaseDateTypeDto(id, 7, "k" + id, name, null, "OTHER", "#123456", false, order, true, false, EffectiveCaseDateTypeDto.Origin.TENANT_CREATED, new byte[]{1}); }
    private static EffectiveCaseDateTypeDto inactive(int id, String name) { return new EffectiveCaseDateTypeDto(id, 7, "k" + id, name, null, "OTHER", "#123456", false, id, false, true, EffectiveCaseDateTypeDto.Origin.TENANT_CREATED, new byte[]{1}); }
}
