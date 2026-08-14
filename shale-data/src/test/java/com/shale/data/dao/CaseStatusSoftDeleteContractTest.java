package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class CaseStatusSoftDeleteContractTest {
 private static String source(String path)throws Exception{return Files.readString(Path.of(path)).replace("\r\n","\n");}
 @Test void removalIsTenantScopedAtomicAuditedAndNeverDeletesHistory()throws Exception{
  String s=source("src/main/java/com/shale/data/dao/CaseDao.java");
  String m=s.substring(s.indexOf("private void mutateCaseStatusLifecycle"),s.indexOf("public void reorderCaseStatuses"));
  assertTrue(m.contains("validateAdminActorForTenant")); assertTrue(m.contains("SET IsActive=?,IsDeleted=?"));
  assertTrue(m.contains("ShaleClientId=?")); assertTrue(m.contains("CASE_STATUS")); assertTrue(m.contains("DEACTIVATED")); assertTrue(m.contains("RESTORED"));
  assertTrue(m.contains("con.rollback()")); assertFalse(m.matches("(?s).*DELETE\\s+FROM\\s+dbo\\.Statuses.*")); assertFalse(m.contains("CaseStatuses"));
 }
 @Test void selectorsExcludeInactiveButHistoryAndCurrentJoinsRetainDefinitions()throws Exception{
  String s=source("src/main/java/com/shale/data/dao/CaseDao.java");
  String selectors=s.substring(s.indexOf("public List<StatusRow> listStatusesForTenant"),s.indexOf("public List<CaseStatusDto> listTenantCaseStatuses"));
  assertTrue(selectors.contains("removeIf(status -> !status.active() || status.deleted())"));
  String history=s.substring(s.indexOf("private List<CaseStatusHistoryDto> listCaseStatusHistory"),s.indexOf("public List<CaseStatusHistoryDto>", s.indexOf("private List<CaseStatusHistoryDto>")));
  assertTrue(history.contains("INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId")); assertFalse(history.contains("s.IsActive")); assertFalse(history.contains("s.IsDeleted"));
 }
 @Test void migrationAddsOnlyLifecycleColumnsAndDoesNotTouchCaseStatuses()throws Exception{
  String sql=source("../docs/sql/2026-08-14_statuses_soft_delete_and_audit.sql");
  assertTrue(sql.contains("ADD IsActive"));assertTrue(sql.contains("ADD IsDeleted"));assertTrue(sql.contains("'CASE_STATUS'"));
  assertFalse(sql.matches("(?s).*DELETE\\s+FROM\\s+dbo\\.Statuses.*"));assertFalse(sql.matches("(?s).*(UPDATE|DELETE|INSERT)\\s+(INTO\\s+)?dbo\\.CaseStatuses.*"));
 }
}
