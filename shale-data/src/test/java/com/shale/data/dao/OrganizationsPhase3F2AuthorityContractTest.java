package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

/** Architectural backstop for structured Organization write authority and operational verification. */
final class OrganizationsPhase3F2AuthorityContractTest {
 @Test void scalarContactWritesExistOnlyInsideAggregateOwner() throws Exception {
  Path root=Path.of("src/main/java");
  try(var files=Files.walk(root)){
   var offenders=files.filter(p->p.toString().endsWith(".java"))
    .filter(p->!p.getFileName().toString().equals("OrganizationTypeMutationDao.java"))
    .filter(p->{try{String s=Files.readString(p);return s.matches("(?is).*(INSERT\\s+(?:INTO\\s+)?dbo\\.Organizations\\s*\\([^)]*(?:Phone|Fax|Email|Website|Address1).*|UPDATE\\s+dbo\\.Organizations\\s+SET[^;]*(?:Phone|Fax|Email|Website|Address1)\\s*=).*");}catch(Exception e){throw new RuntimeException(e);}}).toList();
   assertTrue(offenders.isEmpty(),"Only the aggregate owner may write Organization compatibility contact columns: "+offenders);
  }
 }
 @Test void verificationIsReadOnlyGuardedAndCoversRequiredFindings() throws Exception {
  String sql=Files.readString(Path.of("..","docs/sql/verification/2026-09-10_organizations_phase3f2_compatibility_verification.sql"));
  assertTrue(sql.contains("@ExpectedDatabase"));assertTrue(sql.contains("FindingCount"));
  for(String required:new String[]{"voice","Fax","email","address","website","Multiple active","Invalid voice/Fax","Duplicate active","Cross-tenant or orphan","Invalid ordering","Blank active","Deleted rows still primary"})assertTrue(sql.contains(required),required);
  assertFalse(sql.matches("(?is).*(INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP|TRUNCATE)\\s+(?:TABLE\\s+|dbo\\.).*"),"verification must contain no DML or DDL");
  assertFalse(sql.toUpperCase().contains("DISABLE SECURITY POLICY"));
 }
}
