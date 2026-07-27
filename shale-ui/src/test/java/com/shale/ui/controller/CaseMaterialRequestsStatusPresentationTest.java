package com.shale.ui.controller;

import com.shale.core.dto.RequestStatusDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class CaseMaterialRequestsStatusPresentationTest {
    @Test
    void systemKeyResolvesToEffectiveDisplayNameAndColor() {
        var presentation = CaseMaterialRequestsTabController.resolveRequestStatusPresentation(
                List.of(status("FOLLOW_UP_DUE", "Follow-up Due", "#F59E0B")), "FOLLOW_UP_DUE");

        assertEquals("Follow-up Due", presentation.name());
        assertEquals("#F59E0B", presentation.color());
    }

    @Test
    void matchingIsCaseInsensitiveAndTrimsSavedAndLookupValues() {
        var presentation = CaseMaterialRequestsTabController.resolveRequestStatusPresentation(
                List.of(status(" FOLLOW_UP_DUE ", " Follow-up Due ", "#F59E0B")), "  follow_up_due  ");

        assertEquals("Follow-up Due", presentation.name());
        assertEquals("#F59E0B", presentation.color());
    }

    @Test
    void savedDisplayNameAlsoMatchesCaseInsensitivelyAndTrimmed() {
        var presentation = CaseMaterialRequestsTabController.resolveRequestStatusPresentation(
                List.of(status("FOLLOW_UP_DUE", "Follow-up Due", "#F59E0B")), "  fOlLoW-Up DuE ");

        assertEquals("Follow-up Due", presentation.name());
        assertEquals("#F59E0B", presentation.color());
    }

    @Test
    void tenantRenamedEffectiveStatusSuppliesBothTenantNameAndColor() {
        var presentation = CaseMaterialRequestsTabController.resolveRequestStatusPresentation(
                List.of(status("FOLLOW_UP_DUE", "Client Reminder Needed", "#7C3AED")), "follow_up_due");

        assertEquals("Client Reminder Needed", presentation.name());
        assertEquals("#7C3AED", presentation.color());
    }

    @Test
    void unmatchedLegacyUnderscoreStatusGetsReadableFallbackWithoutColor() {
        var presentation = CaseMaterialRequestsTabController.resolveRequestStatusPresentation(
                List.of(status("OPEN", "Open", "#22C55E")), "  AWAITING_OLD_RECORDS  ");

        assertEquals("Awaiting Old Records", presentation.name());
        assertNull(presentation.color());
    }

    private static RequestStatusDto status(String key, String name, String color) {
        return new RequestStatusDto(1, 42, key, name, color, 1, true, false, null);
    }
}
