package com.shale.ui.controller.support;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import org.junit.jupiter.api.Test;

final class FieldConfirmationSelectionTest {
    @Test void requiredAndConfirmationRemainIndependentInAllFourCombinations() {
        var base = new NewIntakeDatesConfiguration.Selection(type(), false);
        var optionalPending = NewIntakeDatesConfiguration.withConfirmation(base, true, 8);
        var requiredOnly = NewIntakeDatesConfiguration.withRequired(base, true);
        var requiredPending = NewIntakeDatesConfiguration.withRequired(optionalPending, true);
        assertAll(
                () -> assertFalse(base.required() || base.requiresConfirmation(), "optional/unconfirmed must remain valid"),
                () -> assertTrue(optionalPending.requiresConfirmation() && !optionalPending.required(), "optional values may require confirmation"),
                () -> assertTrue(requiredOnly.required() && !requiredOnly.requiresConfirmation(), "required does not imply confirmation"),
                () -> assertTrue(requiredPending.required() && requiredPending.requiresConfirmation(), "both policies may be enabled"));
    }

    @Test void disablingConfirmationClearsRoleWithoutChangingRequired() {
        var selected = new NewIntakeDatesConfiguration.Selection(type(), true, true, 44, null);
        var disabled = NewIntakeDatesConfiguration.withConfirmation(selected, false, 44);
        assertTrue(disabled.required());
        assertFalse(disabled.requiresConfirmation());
        assertNull(disabled.roleDefinitionId());
    }

    private static EffectiveCaseDateTypeDto type() {
        return new EffectiveCaseDateTypeDto(12, 7, "STATUTE_OF_LIMITATIONS", "SOL", null, "DEADLINE", "#123456", false, 1, true, false, EffectiveCaseDateTypeDto.Origin.TENANT_CREATED, new byte[]{1});
    }
}
