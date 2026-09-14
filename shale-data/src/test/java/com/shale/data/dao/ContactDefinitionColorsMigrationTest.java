package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  assertFalse(s.contains("predicate_id"));
  assertFalse(s.contains("predicate_object_id"));
  assertTrue(s.contains("target_object_id"));
  assertTrue(s.contains("predicate_type_desc"));
  assertTrue(s.contains("predicate_definition"));
  assertTrue(s.contains("FROM sys.security_policies sp"));
  assertTrue(s.contains("sp.name AS PolicyName"));
  assertTrue(s.contains("sp.is_enabled"));

  Pattern rlsTargets = Pattern.compile(
      "WHERE\\s+spr\\.target_object_id\\s+IN\\s*\\((.*?)\\)\\s*ORDER BY",
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  Matcher targetClause = rlsTargets.matcher(s.replace("\r\n", "\n").replace('\r', '\n'));
  assertTrue(targetClause.find(), "RLS target filter is missing");
  Matcher objectId = Pattern.compile("OBJECT_ID\\(N'dbo\\.([A-Za-z]+)'\\)", Pattern.CASE_INSENSITIVE)
      .matcher(targetClause.group(1));
  Set<String> tables = new HashSet<>();
  while (objectId.find()) tables.add(objectId.group(1));
  assertEquals(Set.of("ContactTypes", "Specialties", "CredentialDefinitions"), tables);
  assertFalse(targetClause.find(), "RLS target filter must be unique");
 }
 @Test void javaColorContractNormalizesAndRejectsMissingOrInvalid(){
  assertEquals("#A1B2C3",ContactMutationDao.normalizeColor(" #a1b2c3 "));
  assertThrows(IllegalArgumentException.class,()->ContactMutationDao.normalizeColor(null));
  assertThrows(IllegalArgumentException.class,()->ContactMutationDao.normalizeColor("A1B2C3"));
 }
}
