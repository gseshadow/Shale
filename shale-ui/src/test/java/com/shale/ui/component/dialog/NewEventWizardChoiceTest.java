package com.shale.ui.component.dialog;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.model.CalendarEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewEventWizardChoiceTest {
    @Test void activeSameNamedTypesRemainDistinctAndCarryStableSourceAndId() {
        var general = new CalendarEventType(11, 7, "hearing", "Hearing", "#111111", 1, true, null, null);
        var inactiveGeneral = new CalendarEventType(12, 7, "old", "Old", null, 2, false, null, null);
        var caseType = type(21, "Hearing", true, false);
        var inactiveCase = type(22, "Old case", false, false);
        var deletedCase = type(23, "Deleted case", true, true);

        var choices = NewEventWizard.choices(List.of(general, inactiveGeneral), List.of(caseType, inactiveCase, deletedCase));

        assertEquals(2, choices.size());
        assertEquals(List.of("Hearing", "Hearing"), choices.stream().map(NewEventWizard.TypeChoice::name).toList());
        assertEquals(NewEventWizard.SourceKind.GENERAL_EVENT, choices.get(0).sourceKind());
        assertEquals(11, choices.get(0).authoritativeTypeId());
        assertEquals("General Event", choices.get(0).groupLabel());
        assertEquals(NewEventWizard.SourceKind.CASE_EVENT, choices.get(1).sourceKind());
        assertEquals(21, choices.get(1).authoritativeTypeId());
        assertEquals("Case Event", choices.get(1).groupLabel());
        assertNotEquals(choices.get(0), choices.get(1), "presentation names must not establish identity");
    }

    private static EffectiveCaseDateTypeDto type(int id, String name, boolean active, boolean deleted) {
        return new EffectiveCaseDateTypeDto(id, 7, null, name, null, "OTHER", null, true, id, active, deleted,
                EffectiveCaseDateTypeDto.Origin.TENANT_CREATED, new byte[]{1});
    }
}
