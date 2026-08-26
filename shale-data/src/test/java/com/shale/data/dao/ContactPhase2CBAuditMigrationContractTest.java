package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ContactPhase2CBAuditMigrationContractTest {
 @Test void successorAddsExactlyThreeContactPointTokensAndKeepsGuards()throws Exception{
  String predecessor=Files.readString(AuditEntityTypeMigrationChain.PHASE_2B);
  String sql=Files.readString(AuditEntityTypeMigrationChain.PHASE_2C_B);
  Set<String> before=AuditEntityTypeMigrationChain.declaredAllowlist(predecessor),after=AuditEntityTypeMigrationChain.declaredAllowlist(sql);
  var additions=new java.util.HashSet<>(after);additions.removeAll(before);
  assertEquals(Set.of("CONTACT_PHONE_NUMBER","CONTACT_EMAIL_ADDRESS","CONTACT_ADDRESS"),additions);
  assertTrue(sql.contains("DECLARE @OperatorVerifiedAllTenantVisibility bit = 0"));
  assertTrue(sql.contains("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL"));
  assertTrue(sql.contains("WITH CHECK ADD CONSTRAINT"));
  assertFalse(sql.contains("security_policy_id"));assertFalse(sql.contains("predicate_id"));
 }
}
