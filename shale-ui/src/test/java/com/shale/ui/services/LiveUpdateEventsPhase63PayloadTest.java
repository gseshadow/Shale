package com.shale.ui.services;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class LiveUpdateEventsPhase63PayloadTest {
    @Test
    void caseLinkPayloadContainsOnlyStableIdentifiersAndChange() {
        String payload = LiveUpdateEvents.caseLinkPatch(101L, 202L, 303L, 404, LiveUpdateEvents.CHANGE_UPDATED);
        assertTrue(payload.contains("\"caseId\":101"));
        assertTrue(payload.contains("\"caseLinkId\":202"));
        assertTrue(payload.contains("\"externalLinkId\":303"));
        assertTrue(payload.contains("\"linkTypeId\":404"));
        assertTrue(payload.contains("\"change\":\"UPDATED\""));
        assertSensitiveFieldsAbsent(payload);
    }

    @Test
    void sharePayloadContainsContactIdButNoContactPiiOrNotes() {
        String payload = LiveUpdateEvents.caseLinkSharePatch(101L, 202L, 505L, 606, LiveUpdateEvents.CHANGE_SHARED);
        assertTrue(payload.contains("\"contactId\":606"));
        assertTrue(payload.contains("\"caseLinkShareId\":505"));
        assertSensitiveFieldsAbsent(payload);
    }

    @Test
    void linkTypeAndEntityActivityPayloadsStayInvalidationOnly() {
        assertSensitiveFieldsAbsent(LiveUpdateEvents.linkTypePatch(42, LiveUpdateEvents.CHANGE_ACTIVATED));
        assertSensitiveFieldsAbsent(LiveUpdateEvents.auditActivityPatch(88L));
    }

    private static void assertSensitiveFieldsAbsent(String payload) {
        String lower = payload.toLowerCase();
        for (String forbidden : new String[] {"url", "title", "displayname", "description", "notes", "email", "phone", "rowver", "metadata", "sql", "exception", "command", "dto"}) {
            assertFalse(lower.contains(forbidden), () -> "payload must not contain " + forbidden + ": " + payload);
        }
    }
}
