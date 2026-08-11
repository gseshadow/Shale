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
   ()->assertTrue(trigger.contains("cu.ShaleClientId IS NULL")),
   ()->assertTrue(trigger.contains("uu.ShaleClientId IS NULL")),
   ()->assertFalse(trigger.contains("is_deleted")),
   ()->assertFalse(trigger.contains("name=")));
 }

 @Test void acceptsTheVerifiedRealPreMigrationUsersTenantShape(){
  assertAll(
   ()->assertTrue(sql.contains("(N'Users',N'id',N'int',4,0)")),
   ()->assertTrue(sql.contains("(N'Users',N'ShaleClientId',N'int',4,1)")),
   ()->assertFalse(sql.contains("(N'Users',N'ShaleClientId',N'int',4,0)")));
 }

 @Test void rejectsNullTenantAndCrossTenantAuditUsersDuringPreflightAndWrites(){
  String preflight=sql.substring(sql.indexOf("IF EXISTS(SELECT 1 FROM dbo.CalendarCaseDateTypeMappings"),sql.indexOf("DECLARE @FkName"));
  String trigger=sql.substring(sql.indexOf("CREATE TRIGGER dbo.TR_CalendarCaseDateTypeMappings_Tenant"),sql.indexOf("/* Reject competing"));
  assertAll(
   ()->assertTrue(preflight.contains("cu.ShaleClientId IS NULL OR cu.ShaleClientId<>i.ShaleClientId")),
   ()->assertTrue(preflight.contains("uu.ShaleClientId IS NULL OR uu.ShaleClientId<>i.ShaleClientId")),
   ()->assertTrue(trigger.contains("cu.ShaleClientId IS NULL OR cu.ShaleClientId<>i.ShaleClientId")),
   ()->assertTrue(trigger.contains("uu.ShaleClientId IS NULL OR uu.ShaleClientId<>i.ShaleClientId")));
 }

 @Test void normalizesFormattingWhenValidatingStoredPredicateAndTriggerDefinitions(){
  assertAll(
   ()->assertTrue(sql.contains("p.predicate_definition,N'[',N''")),
   ()->assertTrue(sql.contains("N']',N''")),
   ()->assertTrue(sql.contains("N'(',N''")),
   ()->assertTrue(sql.contains("N')',N''")),
   ()->assertTrue(sql.contains("SET @TriggerDefinition=REPLACE(@TriggerDefinition,N' ',N'')")),
   ()->assertTrue(sql.contains("N'sec.fn_filterbytenantshaleclientid'")));
 }

 @Test void transactionMakesFirstInstallAtomicAndLateFailureRollsBackEarlierDdl(){
  assertAll(
   ()->assertTrue(sql.contains("SET XACT_ABORT ON")),
   ()->assertTrue(sql.contains("BEGIN TRY\nBEGIN TRANSACTION;")),
   ()->assertTrue(sql.contains("COMMIT;")),
   ()->assertTrue(sql.contains("BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK; THROW; END CATCH")),
   ()->assertTrue(sql.indexOf("ALTER TABLE dbo.CalendarEvents ADD CaseDateId")<sql.indexOf("ALTER SECURITY POLICY")));
 }

 @Test void firstRunAndRerunPathsCoverExpectedFoundationObjects(){
  assertAll(
   ()->assertTrue(sql.contains("ALTER TABLE dbo.CalendarEvents ADD CaseDateId bigint NULL")),
   ()->assertTrue(sql.contains("ALTER TABLE dbo.CalendarEvents ADD RowVer rowversion NOT NULL")),
   ()->assertTrue(sql.contains("CREATE UNIQUE INDEX UX_CaseDates_ShaleClientId_Id")),
   ()->assertTrue(sql.contains("CREATE UNIQUE INDEX UX_CalendarEvents_ActiveCaseDateLink")),
   ()->assertTrue(sql.contains("FK_CalendarEvents_CaseDate_Tenant")),
   ()->assertTrue(sql.contains("CREATE TABLE dbo.CalendarCaseDateTypeMappings")),
   ()->assertTrue(sql.contains("CREATE TRIGGER dbo.TR_CalendarCaseDateTypeMappings_Tenant")),
   ()->assertTrue(sql.contains("IF NOT EXISTS(SELECT 1 FROM sys.security_predicates")));
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
