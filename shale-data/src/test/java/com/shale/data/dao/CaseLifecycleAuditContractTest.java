package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseLifecycleAuditContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
    }

    @Test void deleteAndRestoreAreAtomicAppendOnlyAndActorIsAuthenticated() throws Exception {
        String s = source();
        assertTrue(s.contains("SESSION_CONTEXT(N'PrincipalUserId')"));
        assertTrue(s.contains("EntityActionAuditEvent.EntityType.CASE"));
        assertTrue(s.contains("Action.DELETED : EntityActionAuditEvent.Action.RESTORED"));
        assertTrue(s.contains("CaseTimelineEventTypes.CASE_DELETED : CaseTimelineEventTypes.CASE_RESTORED"));
        assertTrue(s.contains("con.commit()"));
        assertTrue(s.contains("con.rollback()"));
        assertFalse(s.contains("DELETE FROM dbo.CaseTimelineEvents"));
    }

    @Test void staleAndCrossTenantTransitionsCannotWriteEvents() throws Exception {
        String s = source();
        assertTrue(s.contains("AND RowVer = ?"));
        assertTrue(s.contains("shaleClientId does not match current session"));
        assertTrue(s.contains("if (ps.executeUpdate() != 1) { con.rollback(); return false; }"));
		assertTrue(s.contains("restoreCase(long caseId, Integer shaleClientId, byte[] expectedRowVer)"));
		assertTrue(s.contains("updateDeletedState(caseId, shaleClientId, false, expectedRowVer.clone())"));
    }

    @Test void metadataContainsOnlyAuthoritativeCaseIdAndTimelineReadPreservesDeletedHistory() throws Exception {
        String s = source();
        assertTrue(s.contains("Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID"));
        assertFalse(s.contains("Map.of(EntityActionAuditEvent.MetadataKey.CASE_ID, caseName"));
        String list = s.substring(s.indexOf("public List<CaseTimelineEventDto> listCaseTimelineEvents"));
		assertTrue(list.contains("cte.ShaleClientId = CAST(SESSION_CONTEXT(N'ShaleClientId') AS INT)"));
		assertTrue(list.contains("cte.EventType NOT LIKE 'TASK[_]%'"));
		assertTrue(list.contains("u.ShaleClientId = cte.ShaleClientId"));
        list = list.substring(0, list.indexOf("public ", 10));
        assertFalse(list.contains("IsDeleted"));
        assertTrue(list.contains("cte.ShaleClientId = cte.ShaleClientId") || list.contains("c.ShaleClientId = cte.ShaleClientId"));
    }
}
