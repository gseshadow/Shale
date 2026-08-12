package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MaterialRequestActivityAndRecipientsTest {
    private static final LocalDateTime NOW=LocalDateTime.of(2026,7,29,12,0);

    @Test void meaningfulActivityRestartsAnActiveManagedInterval() {
        var old=new MaterialRequestDao.ScheduleSnapshot(7,NOW.minusDays(1),"requested");
        assertEquals(NOW.plusDays(7),MaterialRequestDao.normalizeUpdateSchedule(old.nextFollowUpAt(),7,old,"requested",NOW,true,false));
    }
    @Test void noOpPreservesScheduleAndTerminalActivityStopsIt() {
        var old=new MaterialRequestDao.ScheduleSnapshot(7,NOW.plusDays(1),"requested");
        assertEquals(old.nextFollowUpAt(),MaterialRequestDao.normalizeUpdateSchedule(old.nextFollowUpAt(),7,old,"requested",NOW,false,false));
        assertNull(MaterialRequestDao.normalizeUpdateSchedule(old.nextFollowUpAt(),7,old,"closed",NOW,true,false));
        assertNull(MaterialRequestDao.normalizeUpdateSchedule(old.nextFollowUpAt(),7,old,"cancelled",NOW,true,false));
    }
    @Test void explicitDateWinsAndReopenUsesActivityTime() {
        var explicit=NOW.plusDays(2);var old=new MaterialRequestDao.ScheduleSnapshot(7,NOW.minusDays(1),"requested");
        assertEquals(explicit,MaterialRequestDao.normalizeUpdateSchedule(explicit,7,old,"requested",NOW,true,true));
        assertEquals(NOW.plusDays(7),MaterialRequestDao.normalizeUpdateSchedule(old.nextFollowUpAt(),7,new MaterialRequestDao.ScheduleSnapshot(7,old.nextFollowUpAt(),"closed"),"requested",NOW,true,false));
    }
    @Test void responsibleUsersAreStableIdDeduplicatedForEveryCandidateType() {
        var due=new MaterialRequestDao.MaterialRequestDueNotificationCandidate(1,2,3,"x",20,10,NOW.toLocalDate(),"requested");
        var follow=new MaterialRequestDao.MaterialRequestFollowUpNotificationCandidate(1,2,3,"x",20,10,NOW,7);
        assertEquals(Set.of(10,20),due.recipientUserIds());assertEquals(Set.of(10,20),follow.recipientUserIds());
        assertEquals(Set.of(10),new MaterialRequestDao.MaterialRequestDueNotificationCandidate(1,2,3,"x",10,10,NOW.toLocalDate(),"requested").recipientUserIds());
        assertTrue(new MaterialRequestDao.MaterialRequestFollowUpNotificationCandidate(1,2,3,"x",null,null,NOW,null).recipientUserIds().isEmpty());
    }
}
