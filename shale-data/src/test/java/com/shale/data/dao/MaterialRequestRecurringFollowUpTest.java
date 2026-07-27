package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

final class MaterialRequestRecurringFollowUpTest {
    private static final LocalDateTime NOW=LocalDateTime.of(2026,7,27,12,0);

    @Test void settingAndChangingIntervalRecalculatesFromMutationTime(){
        assertEquals(NOW.plusDays(7),MaterialRequestDao.normalizeCreateSchedule(null,7,NOW));
        var existing=new MaterialRequestDao.ScheduleSnapshot(7,NOW.plusDays(7),"requested");
        assertEquals(NOW.plusDays(14),MaterialRequestDao.normalizeUpdateSchedule(existing.nextFollowUpAt(),14,existing,"requested",NOW));
    }
    @Test void removingManagedIntervalClearsButOneTimeScheduleIsPreserved(){
        LocalDateTime oneTime=NOW.plusDays(2);
        assertEquals(oneTime,MaterialRequestDao.normalizeUpdateSchedule(oneTime,null,new MaterialRequestDao.ScheduleSnapshot(null,oneTime,"requested"),"requested",NOW));
        assertNull(MaterialRequestDao.normalizeUpdateSchedule(oneTime,null,new MaterialRequestDao.ScheduleSnapshot(7,oneTime,"requested"),"requested",NOW));
    }
    @Test void unchangedIntervalPreservesScheduleAndReopenResumesFromReopenTime(){
        LocalDateTime scheduled=NOW.plusDays(3);
        assertEquals(scheduled,MaterialRequestDao.normalizeUpdateSchedule(scheduled,3,new MaterialRequestDao.ScheduleSnapshot(3,scheduled,"requested"),"requested",NOW));
        assertEquals(NOW.plusDays(3),MaterialRequestDao.normalizeUpdateSchedule(scheduled,3,new MaterialRequestDao.ScheduleSnapshot(3,scheduled,"closed"),"requested",NOW));
    }
    @Test void supportedRangeIsEnforced(){
        assertThrows(IllegalArgumentException.class,()->MaterialRequestDao.validateInterval(0));
        assertThrows(IllegalArgumentException.class,()->MaterialRequestDao.validateInterval(366));
        assertDoesNotThrow(()->MaterialRequestDao.validateInterval(null));
        assertDoesNotThrow(()->MaterialRequestDao.validateInterval(365));
    }
}
