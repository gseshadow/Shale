package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class ContactDefinitionColorsMigrationTest {
 private static String read(String name)throws Exception{return Files.readString(Path.of("../docs/sql/"+name));}
 @Test void migrationIsGuardedRerunnableAndBackfillsAllDefinitions()throws Exception{
  String s=read("2026-08-25_contacts_phase2a_definition_colors.sql");
  for(String table:new String[]{"ContactTypes","Specialties","CredentialDefinitions"})assertTrue(s.contains("N'"+table+"'"));
  assertTrue(s.contains("@OperatorVerifiedAllTenantVisibility bit = 0"));
  assertTrue(s.contains("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL"));
  assertTrue(s.contains("BEGIN TRY")); assertTrue(s.contains("BEGIN TRANSACTION")); assertTrue(s.contains("ROLLBACK TRANSACTION"));
  assertTrue(s.contains("SET Color=N''#6C757D'' WHERE Color IS NULL OR NULLIF"));
  assertTrue(s.contains("ALTER COLUMN Color nvarchar(20) NOT NULL"));
  assertTrue(s.contains("DEFAULT(N''#6C757D'')")); assertTrue(s.contains("Color=UPPER(Color)"));
 }
 @Test void verificationCoversColumnsValidityLifecycleAssignmentsExpertAndRls()throws Exception{
  String s=read("2026-08-25_contacts_phase2a_definition_colors_verify.sql");
  assertTrue(s.contains("InvalidOrMissingColor")); assertTrue(s.contains("ConstraintType"));
  assertTrue(s.contains("LifecycleState")); assertTrue(s.contains("SystemKey=N'expert'"));
  assertTrue(s.contains("AssignmentCount")); assertTrue(s.contains("security_predicates"));
 }
 @Test void javaColorContractNormalizesAndRejectsMissingOrInvalid(){
  assertEquals("#A1B2C3",ContactMutationDao.normalizeColor(" #a1b2c3 "));
  assertThrows(IllegalArgumentException.class,()->ContactMutationDao.normalizeColor(null));
  assertThrows(IllegalArgumentException.class,()->ContactMutationDao.normalizeColor("A1B2C3"));
 }
}
