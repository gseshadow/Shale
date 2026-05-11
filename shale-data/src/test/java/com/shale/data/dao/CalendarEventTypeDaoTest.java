package com.shale.data.dao;

import com.shale.core.model.CalendarEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalendarEventTypeDaoTest {

    @Test
    void resolveEffectiveForTenantPrefersTenantRowAndKeepsGlobalFallback() {
        CalendarEventType globalGeneral = type(1, null, "GENERAL", "General", 10);
        CalendarEventType tenantGeneral = type(2, 77, "GENERAL", "General (Tenant)", 10);
        CalendarEventType globalCourt = type(3, null, "COURT", "Court", 20);
        CalendarEventType otherTenant = type(4, 88, "GENERAL", "Other Tenant", 5);

        List<CalendarEventType> effective = CalendarEventTypeDao.resolveEffectiveForTenant(
                List.of(globalGeneral, tenantGeneral, globalCourt, otherTenant),
                77);

        assertEquals(List.of(2, 3), effective.stream().map(CalendarEventType::calendarEventTypeId).toList());
    }

    private static CalendarEventType type(int id, Integer tenantId, String systemKey, String name, int sortOrder) {
        return new CalendarEventType(id, tenantId, systemKey, name, null, sortOrder, true, null, null);
    }
}
