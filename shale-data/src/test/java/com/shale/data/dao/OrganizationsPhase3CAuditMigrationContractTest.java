package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class OrganizationsPhase3CAuditMigrationContractTest {
 @Test void successorIsRefusalDefaultTransactionalAndAddsOnlyStructuredOrganizationVocabulary()throws Exception{
  String sql=Files.readString(AuditEntityTypeMigrationChain.ORGANIZATIONS_PHASE_3C);Set<String> before=AuditEntityTypeMigrationChain.declaredAllowlist(Files.readString(AuditEntityTypeMigrationChain.ORGANIZATIONS));Set<String> after=AuditEntityTypeMigrationChain.declaredAllowlist(sql);Set<String> additions=new HashSet<>(after);additions.removeAll(before);
  assertEquals(Set.of("ORGANIZATION","ORGANIZATION_PHONE","ORGANIZATION_EMAIL","ORGANIZATION_ADDRESS","ORGANIZATION_WEBSITE"),additions);
  assertTrue(after.containsAll(before));assertTrue(sql.contains("@OperatorVerifiedAllTenantVisibility bit=0"));assertTrue(sql.contains("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL"));assertTrue(sql.contains("BEGIN TRANSACTION"));assertTrue(sql.contains("XACT_STATE() <> 0 ROLLBACK TRANSACTION"));assertFalse(sql.toUpperCase(Locale.ROOT).contains("DISABLE SECURITY POLICY"));
 }
 @Test void runtimeAuditVocabularyRejectsSensitiveValuesAndAllowsChildLifecycle(){for(var type:List.of(EntityActionAuditEvent.EntityType.ORGANIZATION_PHONE,EntityActionAuditEvent.EntityType.ORGANIZATION_EMAIL,EntityActionAuditEvent.EntityType.ORGANIZATION_ADDRESS,EntityActionAuditEvent.EntityType.ORGANIZATION_WEBSITE))for(var action:List.of(EntityActionAuditEvent.Action.CREATED,EntityActionAuditEvent.Action.UPDATED,EntityActionAuditEvent.Action.REMOVED,EntityActionAuditEvent.Action.RESTORED,EntityActionAuditEvent.Action.REORDERED))assertDoesNotThrow(()->EntityActionAuditEvent.now(7,9,type,10,action,EntityActionAuditEvent.EntityType.ORGANIZATION,4L,Map.of(EntityActionAuditEvent.MetadataKey.ORGANIZATION_ID,4)));}
}
