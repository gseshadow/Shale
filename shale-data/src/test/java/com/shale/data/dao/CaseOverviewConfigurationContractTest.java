package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CaseOverviewConfigurationContractTest {
 @Test void uncustomizedDefaultsPreserveExistingOverviewOrder() {
  var all=List.of(type(1,"tort_notice_deadline"),type(2,"intake"),type(3,"date_of_injury"),type(4,"statute_of_limitations"),type(5,"date_of_medical_negligence"));
  assertEquals(List.of("date_of_injury","date_of_medical_negligence","intake","statute_of_limitations","tort_notice_deadline"),CaseOverviewConfigurationDao.defaults(all).stream().map(EffectiveCaseDateTypeDto::systemKey).toList());
 }
 @Test void duplicatesAreRejectedButExplicitEmptySelectionIsValid() {
  assertDoesNotThrow(()->CaseOverviewConfigurationDao.rejectDuplicates(List.of()));
  assertThrows(IllegalArgumentException.class,()->CaseOverviewConfigurationDao.rejectDuplicates(List.of(3,3)));
 }
 @Test void daoOwnsAuthorizationTenantConcurrencyAtomicTimelineAndAuditContracts() throws Exception {
  String s=Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseOverviewConfigurationDao.java"));
  assertTrue(s.contains("ISNULL(is_admin,0)=1")); assertTrue(s.contains("SESSION_CONTEXT(N'ShaleClientId')"));
  assertTrue(s.contains("RowVer=?")); assertTrue(s.contains("con.setAutoCommit(false)")); assertTrue(s.contains("con.rollback()"));
  assertTrue(s.contains("CaseDateTypeSemanticRoleMappings")); assertTrue(s.contains("is not effective for this tenant"));
  assertTrue(s.contains("INTAKE_TAKEN_BY_CHANGED")); assertTrue(s.contains("CaseTimelineWriter.append"));
  assertEquals(2,count(s,"CaseTimelineWriter.append"),"individual and combined Intake By paths each own one conditional timeline append");
  assertTrue(s.contains("EntityType.CASE_OVERVIEW_CONFIGURATION"));
  assertTrue(s.contains("Objects.equals(before.userId,c.intakeTakenByUserId())"));
  assertTrue(s.contains("ISNULL(IsRemoved,0)=0"));
 }
 @Test void migrationDefinesParentChildUniquenessOrderingRlsAndRerunGuards() throws Exception {
  String s=Files.readString(Path.of("../docs/sql/2026-09-08_case_overview_configuration_phase1.sql"));
  assertTrue(s.contains("SET XACT_ABORT ON")); assertTrue(s.contains("BEGIN TRY")); assertTrue(s.contains("BEGIN TRANSACTION"));
  assertTrue(s.contains("UQ_CaseOverviewConfigurations_TenantCase")); assertTrue(s.contains("UQ_CaseOverviewDateSelections_ConfigType"));
  assertTrue(s.contains("UQ_CaseOverviewDateSelections_ConfigOrder")); assertTrue(s.contains("sec.fn_FilterByTenant(ShaleClientId)"));
  assertTrue(s.contains("ShaleClientId int NOT NULL, CaseId int NOT NULL"));
  assertFalse(s.contains("CaseId bigint NOT NULL"));
  assertFalse(s.contains("UPDATE dbo.Cases")); assertFalse(s.contains("UPDATE dbo.CaseDates"));
 }
 @Test void productionServiceAdapterExposesTheThreeDaoOperations() throws Exception {
  String s=Files.readString(Path.of("src/main/java/com/shale/data/service/adapter/CaseServiceAdapter.java"));
  assertTrue(s.contains("requireOverviewConfigurationDao().get(caseId,tenant,actor)"));
  assertTrue(s.contains("requireOverviewConfigurationDao().replace(c)"));
  assertTrue(s.contains("requireOverviewConfigurationDao().updateIntakeTakenBy(c)"));
  assertTrue(s.contains("requireOverviewConfigurationDao().update(c)"));
 }
 @Test void combinedSaveOwnsOneTransactionAndPreservesAtomicTimelineRules() throws Exception {
  String s=Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseOverviewConfigurationDao.java"));
  String combined=s.substring(s.indexOf("public CaseOverviewMutationResult update("),s.indexOf("public CaseOverviewDateConfigurationDto replace("));
  assertEquals(1,count(combined,"con.setAutoCommit(false)"));assertEquals(1,count(combined,"con.commit()"));assertTrue(combined.contains("con.rollback()"));
  assertEquals(1,count(combined,"CaseTimelineWriter.append"),"only a true Intake By change writes one timeline row");
  assertTrue(combined.contains("if(!c.layoutChanged()&&!c.intakeTakenByChanged())"));assertTrue(combined.contains("updateCaseOnce"));
  assertTrue(combined.contains("retained"),"configured inactive type identities remain valid while retained");
 }
 @Test void overviewCaseMutationsUseOnlyDeployedModificationColumnsAndConcurrencyPredicate() throws Exception {
  String source=Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseOverviewConfigurationDao.java"));
  var matcher=Pattern.compile("UPDATE dbo\\.Cases SET ([^\\\"]+) WHERE").matcher(source);
  int caseUpdates=0;
  while(matcher.find()) {
   caseUpdates++;
   Set<String> columns=Pattern.compile(",").splitAsStream(matcher.group(1)).map(v->v.substring(0,v.indexOf('='))).collect(java.util.stream.Collectors.toSet());
   assertTrue(Set.of("IntakeTakenByUserId","UpdatedAt").containsAll(columns),"Cases mutation referenced undeployed modification columns: "+columns);
   assertTrue(columns.contains("UpdatedAt"),"every Overview Cases mutation must advance RowVer through UpdatedAt");
  }
  assertEquals(4,caseUpdates,"the combined, standalone Intake By, and layout-only Cases mutation variants must all be inspected");
  assertTrue(source.contains("UPDATE dbo.Cases SET IntakeTakenByUserId=?,UpdatedAt=SYSDATETIME() WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0 AND RowVer=?"));
  assertTrue(source.contains("UPDATE dbo.Cases SET UpdatedAt=SYSDATETIME() WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0 AND RowVer=?"));
  assertTrue(source.contains("UPDATE dbo.Cases SET UpdatedAt=SYSDATETIME() WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0"));
 }
 @Test void overviewUserCandidatesExcludeRemovedUsers() throws Exception {
  String s=Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
  int start=s.indexOf("public List<UserRow> listUsersForTenant");int end=s.indexOf("public List<CaseUserTeamRow>",start);
  assertTrue(s.substring(start,end).contains("u.IsRemoved = 0"));
 }
 @Test void auditAllowlistSuccessorIsTransactionalAndAddsSafeEntityVocabulary() throws Exception {
  String s=Files.readString(Path.of("../docs/sql/2026-09-08_entity_action_audit_case_overview_configuration.sql"));
  assertTrue(s.contains("SET XACT_ABORT ON")); assertTrue(s.contains("('CASE_OVERVIEW_CONFIGURATION')"));
  assertTrue(s.contains("BEGIN TRANSACTION")); assertTrue(s.contains("IF XACT_STATE() <> 0 ROLLBACK"));
 }
 private static EffectiveCaseDateTypeDto type(int id,String key){return new EffectiveCaseDateTypeDto(id,7,key,key,null,"OTHER","#123456",false,id,true,false,EffectiveCaseDateTypeDto.Origin.TENANT_CREATED,new byte[]{1});}
 private static int count(String text,String token){int n=0,p=0;while((p=text.indexOf(token,p))>=0){n++;p+=token.length();}return n;}
}
