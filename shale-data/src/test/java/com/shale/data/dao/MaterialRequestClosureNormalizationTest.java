package com.shale.data.dao;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

final class MaterialRequestClosureNormalizationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 27, 15, 30);

    @Test
    void activeRequestChangedToCancelledGetsCompleteClosureMetadata() {
        var closure = MaterialRequestDao.normalizeClosure("cancelled", null, null, null,
                null, null, null, 42, NOW);

        assertEquals(NOW, closure.closedAt());
        assertEquals(42, closure.closedByUserId());
        assertEquals("Status changed to cancelled.", closure.closureReason());
    }

    @Test
    void alreadyClosedRequestChangedToCancelledPreservesExistingClosure() {
        LocalDateTime original = NOW.minusDays(5);
        var closure = MaterialRequestDao.normalizeClosure("cancelled", NOW, 99, "new reason",
                original, 7, "Original reason", 42, NOW);

        assertEquals(original, closure.closedAt());
        assertEquals(7, closure.closedByUserId());
        assertEquals("Original reason", closure.closureReason());
    }

    @Test
    void cancelledRequestMovedToActiveClearsAllClosureFields() {
        var closure = MaterialRequestDao.normalizeClosure("requested", NOW.minusDays(1), 7, "Cancelled",
                NOW.minusDays(1), 7, "Cancelled", 42, NOW);

        assertNull(closure.closedAt());
        assertNull(closure.closedByUserId());
        assertNull(closure.closureReason());
    }

    @Test
    void ordinaryEditsPreserveValidClosureState() {
        var active = MaterialRequestDao.normalizeClosure("follow_up_due", null, null, null,
                null, null, null, 42, NOW);
        LocalDateTime original = NOW.minusHours(3);
        var closed = MaterialRequestDao.normalizeClosure("closed", null, null, null,
                original, 7, "Completed", 42, NOW);

        assertEquals(new MaterialRequestDao.ClosureValues(null, null, null), active);
        assertEquals(new MaterialRequestDao.ClosureValues(original, 7, "Completed"), closed);
    }

    @Test
    void terminalDetectionUsesStableSystemKeyNotCustomizableDisplayName() {
        var renamedCancelled = MaterialRequestDao.normalizeClosure("cancelled", null, null, null,
                null, null, null, 42, NOW);
        var displayTextThatLooksTerminal = MaterialRequestDao.normalizeClosure("voided by client", NOW, 42, "reason",
                NOW, 42, "reason", 42, NOW);

        assertNotNull(renamedCancelled.closedAt(), "A renamed lookup still supplies the stable cancelled SystemKey.");
        assertNull(displayTextThatLooksTerminal.closedAt(), "Display text alone cannot define terminal semantics.");
    }

    @Test
    void mutationBoundaryBindsNormalizedClosureAndKeepsConcurrencyAuditAndCaseTouching() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/MaterialRequestDao.java"));

        assertTrue(source.contains("setTs(ps,i++,closure.closedAt())"));
        assertTrue(source.contains("setInt(ps,i++,closure.closedByUserId())"));
        assertTrue(source.contains("ps.setString(i++,closure.closureReason())"));
        assertFalse(source.contains("setTs(ps,i++,c.closedAt())"));
        assertTrue(source.contains("AND RowVer=?"));
        assertTrue(source.contains("ps.setBytes(i++,c.rowVer())"));
        assertTrue(source.contains("touchCase(con,c.caseId(),c.shaleClientId())"));
        assertTrue(source.contains("EntityActionAuditEvent.Action.UPDATED"));
        assertTrue(source.contains("LOWER(LTRIM(RTRIM(SystemKey)))"));
        assertTrue(source.contains("LOWER(LTRIM(RTRIM(Name)))"));
    }
}
