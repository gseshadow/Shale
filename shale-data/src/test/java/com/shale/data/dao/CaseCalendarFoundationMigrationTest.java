package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;

final class CaseCalendarFoundationMigrationTest {
 private static String sql;
 @BeforeAll static void load() throws Exception { sql=Files.readString(Path.of("../docs/sql/2026-08-11_case_date_calendar_link_foundation_step1.sql")); }

 @Test void installsCompleteStrictTenantRlsAndSelectsPolicyDeterministically(){
  assertAll(
   ()->assertTrue(sql.contains("COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter')>1"),"ambiguous policy must fail"),
   ()->assertFalse(sql.contains("SELECT TOP(1) @Policy")),
   ()->assertTrue(sql.contains("(N'FILTER',NULL")),
   ()->assertTrue(sql.contains("(N'BLOCK',N'AFTER INSERT'")),
   ()->assertTrue(sql.contains("(N'BLOCK',N'BEFORE UPDATE'")),
   ()->assertTrue(sql.contains("(N'BLOCK',N'AFTER UPDATE'")),
   ()->assertTrue(sql.contains("N'BLOCK PREDICATE '")),
   ()->assertTrue(sql.contains("competing or incompatible security predicate")));
 }

 @Test void validatesAuditUsersByAuthoritativeTenantIdsWithoutInventingLifecycleRules(){
  String trigger=sql.substring(sql.indexOf("CREATE TRIGGER dbo.TR_CalendarCaseDateTypeMappings_Tenant"));
  assertAll(
   ()->assertTrue(trigger.contains("cu.id=i.CreatedByUserId")),
   ()->assertTrue(trigger.contains("uu.id=i.UpdatedByUserId")),
   ()->assertTrue(trigger.contains("cu.ShaleClientId<>i.ShaleClientId")),
   ()->assertTrue(trigger.contains("uu.ShaleClientId<>i.ShaleClientId")),
   ()->assertFalse(trigger.contains("is_deleted")),
   ()->assertFalse(trigger.contains("name=")));
 }

 @Test void rerunRetainsCorrectObjectsAndRejectsSameNamedIncompatibleObjects(){
  assertAll(
   ()->assertTrue(sql.contains("expected UNIQUE (ShaleClientId,Id) without a filter")),
   ()->assertTrue(sql.contains("expected UNIQUE (ShaleClientId,CaseDateId) WHERE CaseDateId IS NOT NULL")),
   ()->assertTrue(sql.contains("expected the non-cascading composite tenant/CaseDate foreign key")),
   ()->assertTrue(sql.contains("named active mapping unique index exists with an incompatible definition")),
   ()->assertTrue(sql.contains("named mapping foreign key exists with incompatible columns or cascade actions")),
   ()->assertTrue(sql.contains("trigger")&&sql.contains("incompatible tenant-validation definition")));
 }

 @Test void compatiblePartialTableIsCompletedButIncompatibleShapeIsNotReinterpreted(){
  assertAll(
   ()->assertTrue(sql.contains("IF OBJECT_ID(N'dbo.CalendarCaseDateTypeMappings',N'U') IS NULL")),
   ()->assertTrue(sql.contains("ADD CONSTRAINT DF_CalendarCaseDateTypeMappings_CDToCal")),
   ()->assertTrue(sql.contains("ADD CONSTRAINT PK_CalendarCaseDateTypeMappings PRIMARY KEY(Id)")),
   ()->assertTrue(sql.contains("missing or incompatible required columns; no destructive repair was attempted")),
   ()->assertFalse(sql.matches("(?is).*DROP\\s+TABLE\\s+dbo\\.CalendarCaseDateTypeMappings.*")),
   ()->assertFalse(sql.matches("(?is).*TRUNCATE\\s+TABLE\\s+dbo\\.CalendarCaseDateTypeMappings.*")));
 }

 @Test void validatesPrerequisitesAndPreservesExplicitCardinalityWithoutHistoricalPairing(){
  assertAll(
   ()->assertTrue(sql.contains("@PrerequisiteColumns")),
   ()->assertTrue(sql.contains("(N'Users',N'id',N'int',4,0)")),
   ()->assertTrue(sql.contains("(N'CalendarEventTypes',N'ShaleClientId',N'int',4,1)")),
   ()->assertTrue(sql.contains("Existing dbo.CalendarEvents.CaseDateId must be nullable bigint")),
   ()->assertTrue(sql.contains("delete_referential_action=0 AND fk.update_referential_action=0")),
   ()->assertTrue(sql.contains("CREATE UNIQUE INDEX UX_CalendarEvents_ActiveCaseDateLink")),
   ()->assertFalse(sql.contains("INSERT dbo.CalendarCaseDateTypeMappings")),
   ()->assertFalse(sql.matches("(?is).*UPDATE\\s+(dbo\\.)?(CalendarEvents|CaseDates)\\s+SET.*")));
 }
}
