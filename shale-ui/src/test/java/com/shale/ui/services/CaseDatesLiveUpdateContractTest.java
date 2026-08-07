package com.shale.ui.services;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CaseDatesLiveUpdateContractTest {
    @Test void payloadIsIdentifierOnlyAndPhiSafe() {
        String payload = LiveUpdateEvents.caseDatesPatch(41, LiveUpdateEvents.CHANGE_UPDATED);
        assertEquals("{\"caseId\":41,\"change\":\"UPDATED\"}", payload);
        String lower = payload.toLowerCase();
        for (String forbidden : new String[]{"startsat", "endsat", "datevalue", "name", "description", "notes", "rowver"})
            assertFalse(lower.contains(forbidden), forbidden);
    }

    @Test void controllerUsesReadOnlyGuardedAuthoritativeRefreshAndOnePublisherPath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        assertTrue(source.contains("LiveUpdateEvents.ENTITY_CASE_DATES.equals(entityType)"));
        assertTrue(source.contains("event.shaleClientId() != tenantId"));
        assertTrue(source.contains("event.entityId() != caseId.longValue()"));
        assertTrue(source.contains("mine.equals(event.clientInstanceId())"));
        assertTrue(source.contains("rememberCaseDateEvent(event.eventId())"));
        assertTrue(source.contains("caseDatesStale = true;"));
        assertTrue(source.contains("loadCaseDatesAsync();"));
        assertTrue(source.contains("loadCompatibilityDatesAsync(activeCaseId);"));
        assertTrue(source.contains("caseDateEditorOpen || caseDateMutationInFlight.get() || compatibilityDates.isSaving()"));
        String receiver = source.substring(source.indexOf("if (LiveUpdateEvents.ENTITY_CASE_DATES.equals(entityType)"), source.indexOf("if (!LiveUpdateEvents.ENTITY_CASE_LINK.equals(entityType)"));
        assertFalse(receiver.contains("caseService.createCaseDate"));
        assertFalse(receiver.contains("caseService.updateCaseDate"));
        assertFalse(receiver.contains("caseService.deleteCaseDate"));
        assertFalse(receiver.contains("caseService.restoreCaseDate"));
        assertFalse(receiver.contains("mutateMigratedCompatibilityDates"));
    }
}
