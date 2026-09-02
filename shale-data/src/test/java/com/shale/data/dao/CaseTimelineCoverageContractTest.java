package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Protects user-visible Case Timeline coverage and explicit exclusions. */
class CaseTimelineCoverageContractTest {
    private static final String CASE_DAO = read("src/main/java/com/shale/data/dao/CaseDao.java");
    private static final String DATE_DAO = read("src/main/java/com/shale/data/dao/CaseDateDao.java");
    private static final String REQUEST_DAO = read("src/main/java/com/shale/data/dao/MaterialRequestDao.java");

    @Test
    void caseDatesWriteExactlyAtMeaningfulCreateUpdateRemoveRestoreSeams() {
        assertTrue(DATE_DAO.contains("CaseTimelineWriter.CASE_DATE_CREATED"));
        assertTrue(DATE_DAO.contains("CaseTimelineWriter.CASE_DATE_UPDATED"));
        assertTrue(DATE_DAO.contains("CaseTimelineWriter.CASE_DATE_REMOVED"));
        assertTrue(DATE_DAO.contains("CaseTimelineWriter.CASE_DATE_RESTORED"));
        assertTrue(DATE_DAO.indexOf("if(before.typeId==c.caseDateTypeId()")
                < DATE_DAO.indexOf("CaseTimelineWriter.CASE_DATE_UPDATED", DATE_DAO.indexOf("public CaseDateDto updateCaseDate")));
    }

    @Test
    void materialRequestsCoverMeaningfulMutationsWithoutCopyingFreeFormContent() {
        for (String event : new String[]{"MATERIAL_REQUEST_CREATED", "MATERIAL_REQUEST_UPDATED",
                "MATERIAL_REQUEST_STATUS_CHANGED", "MATERIAL_REQUEST_REMOVED", "MATERIAL_REQUEST_NOTE_ADDED"}) {
            assertTrue(REQUEST_DAO.contains("CaseTimelineWriter." + event));
        }
        assertTrue(REQUEST_DAO.contains("if(!meaningful&&!explicitScheduleChange){con.rollback();return prior;}"));
        assertFalse(REQUEST_DAO.contains("CaseTimelineWriter.append(con,c.caseId(),c.shaleClientId(),c.actorUserId(),CaseTimelineWriter.MATERIAL_REQUEST_NOTE_ADDED,\"added a note to a Material Request\",body"));
    }

    @Test
    void linksCoverVisibleOperationsAndSuppressNoOps() {
        for (String event : new String[]{"CASE_LINK_CREATED", "CASE_LINK_UPDATED", "CASE_LINK_REMOVED",
                "CASE_LINK_PRIMARY_CHANGED", "CASE_LINKS_REORDERED", "CASE_LINK_SHARE_ADDED",
                "CASE_LINK_SHARE_UPDATED", "CASE_LINK_SHARE_REMOVED"}) {
            assertTrue(CASE_DAO.contains("CaseTimelineWriter." + event));
        }
        assertTrue(CASE_DAO.contains("sameCaseLinkValues(existing"));
        assertTrue(CASE_DAO.contains("if (Objects.equals(previousPrimary, caseLinkId))"));
        assertTrue(CASE_DAO.contains("if (currentOrder.equals(ids))"));
    }

    @Test
    void partiesRemainOnTheirExistingPathAndAreNotDuplicatedByNewWriter() {
        assertEquals(0, count(method("public long addCaseParty", "private void insertCasePartyWithValidation"), "CaseTimelineWriter"));
        assertEquals(0, count(method("public void updateCaseParty", "public void removeCaseParty"), "CaseTimelineWriter"));
        assertEquals(0, count(method("public void removeCaseParty", "private void normalizeCasePartyRelationshipPrimaries"), "CaseTimelineWriter"));
    }

    @Test
    void timelineReadExcludesTasksAndDoesNotProjectManualCaseUpdates() {
        String list = method("public List<CaseTimelineEventDto> listCaseTimelineEvents", "public boolean markMedicalRecordsRequested");
        assertTrue(list.contains("cte.EventType NOT LIKE 'TASK[_]%'"));
        assertFalse(list.contains("CaseUpdates"));
        assertFalse(list.contains("TaskTimelineEvents"));
    }

    private static String method(String start, String end) {
        int from = CASE_DAO.indexOf(start);
        int to = CASE_DAO.indexOf(end, from + start.length());
        return CASE_DAO.substring(from, to);
    }

    private static int count(String value, String needle) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }

    private static String read(String file) {
        try { return Files.readString(Path.of(file)); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
