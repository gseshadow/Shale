package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
class TaskPolicyConfigurationContractTest {
 private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();
 @Test void mutationIsTenantAdminConcurrentAndTransactionallyAudited()throws Exception{String s=Files.readString(ROOT.resolve("shale-data/src/main/java/com/shale/data/dao/TaskPolicyConfigurationDao.java"));assertAll(
  ()->assertTrue(s.contains("verifyContext(con,c.shaleClientId())")),()->assertTrue(s.contains("validateAdmin(con,c.shaleClientId(),c.actorUserId())")),
  ()->assertTrue(s.contains("ISNULL(is_admin,0)=1")),()->assertTrue(s.contains("AND RowVer=?")),
  ()->assertTrue(s.contains("con.setAutoCommit(false)")),()->assertTrue(s.contains("audits.append(con")),
  ()->assertTrue(s.indexOf("audits.append(con")<s.indexOf("con.commit()")),()->assertTrue(s.contains("con.rollback()")),
  ()->assertTrue(s.contains("PREVIOUS_POLICY")),()->assertTrue(s.contains("RESULTING_POLICY")));}
 @Test void migrationAndVerificationProtectPolicyAndAudit()throws Exception{String m=Files.readString(ROOT.resolve("docs/sql/2026-10-05_task_due_date_policy.sql"));String a=Files.readString(ROOT.resolve("docs/sql/2026-10-05_task_policy_entity_action_audit.sql"));String v=Files.readString(ROOT.resolve("docs/sql/2026-10-05_task_due_date_policy_verify.sql"));assertAll(()->assertTrue(m.contains("UQ_TaskPolicyConfigurations_Tenant")),()->assertTrue(m.contains("AFTER INSERT")),()->assertTrue(m.contains("AFTER UPDATE")),()->assertTrue(a.contains("TASK_POLICY_CONFIGURATION")),()->assertTrue(v.contains("PREVIOUS_POLICY")),()->assertTrue(v.contains("RESULTING_POLICY")));}
 @Test void auditVocabularyAllowsOnlyUpdatesWithSafeTransitions(){var event=EntityActionAuditEvent.now(7,3,EntityActionAuditEvent.EntityType.TASK_POLICY_CONFIGURATION,9,EntityActionAuditEvent.Action.UPDATED,null,null,Map.of(EntityActionAuditEvent.MetadataKey.PREVIOUS_POLICY,"WARN",EntityActionAuditEvent.MetadataKey.RESULTING_POLICY,"REQUIRED"));assertEquals("WARN",event.metadata().get(EntityActionAuditEvent.MetadataKey.PREVIOUS_POLICY));assertThrows(IllegalArgumentException.class,()->EntityActionAuditEvent.now(7,3,EntityActionAuditEvent.EntityType.TASK_POLICY_CONFIGURATION,9,EntityActionAuditEvent.Action.CREATED,null,null,Map.of()));}
}
