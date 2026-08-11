package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

final class CaseCalendarFoundationMigrationTest {
 @Test void migrationIsAdditiveIdempotentTenantSafeAndDoesNotGuessLinks() throws Exception {
  String sql=Files.readString(Path.of("../docs/sql/2026-08-11_case_date_calendar_link_foundation_step1.sql"));
  assertAll(
   ()->assertTrue(sql.contains("COL_LENGTH(N'dbo.CalendarEvents',N'CaseDateId') IS NULL")),
   ()->assertTrue(sql.contains("FOREIGN KEY(ShaleClientId,CaseDateId) REFERENCES dbo.CaseDates(ShaleClientId,Id)")),
   ()->assertTrue(sql.contains("UNIQUE INDEX UX_CalendarEvents_ActiveCaseDateLink")),
   ()->assertTrue(sql.contains("UNIQUE INDEX UX_CalendarCaseDateTypeMappings_EventType")),
   ()->assertTrue(sql.contains("UNIQUE INDEX UX_CalendarCaseDateTypeMappings_DateType")),
   ()->assertTrue(sql.contains("CaseDateToCalendar")),()->assertTrue(sql.contains("CalendarToCaseDate")),
   ()->assertTrue(sql.contains("sec.fn_FilterByTenant(ShaleClientId)")),
   ()->assertFalse(sql.matches("(?is).*UPDATE\\s+(dbo\\.)?(CalendarEvents|CaseDates)\\s+SET.*")),
   ()->assertFalse(sql.contains("INSERT dbo.CalendarCaseDateTypeMappings")));
 }
}
