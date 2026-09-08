package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import java.nio.file.*;
import java.util.List;
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
  assertEquals(1,count(s,"CaseTimelineWriter.append"),"only Intake By, not layout, writes timeline");
  assertTrue(s.contains("EntityType.CASE_OVERVIEW_CONFIGURATION"));
  assertTrue(s.contains("Objects.equals(before.userId,c.intakeTakenByUserId())"));
  assertTrue(s.contains("ISNULL(IsRemoved,0)=0"));
 }
 @Test void migrationDefinesParentChildUniquenessOrderingRlsAndRerunGuards() throws Exception {
  String s=Files.readString(Path.of("../docs/sql/2026-09-08_case_overview_configuration_phase1.sql"));
  assertTrue(s.contains("SET XACT_ABORT ON")); assertTrue(s.contains("BEGIN TRY")); assertTrue(s.contains("BEGIN TRANSACTION"));
  assertTrue(s.contains("UQ_CaseOverviewConfigurations_TenantCase")); assertTrue(s.contains("UQ_CaseOverviewDateSelections_ConfigType"));
  assertTrue(s.contains("UQ_CaseOverviewDateSelections_ConfigOrder")); assertTrue(s.contains("sec.fn_FilterByTenant(ShaleClientId)"));
  assertFalse(s.contains("UPDATE dbo.Cases")); assertFalse(s.contains("UPDATE dbo.CaseDates"));
 }
 @Test void productionServiceAdapterExposesTheThreeDaoOperations() throws Exception {
  String s=Files.readString(Path.of("src/main/java/com/shale/data/service/adapter/CaseServiceAdapter.java"));
  assertTrue(s.contains("requireOverviewConfigurationDao().get(caseId,tenant,actor)"));
  assertTrue(s.contains("requireOverviewConfigurationDao().replace(c)"));
  assertTrue(s.contains("requireOverviewConfigurationDao().updateIntakeTakenBy(c)"));
 }
 @Test void auditAllowlistSuccessorIsTransactionalAndAddsSafeEntityVocabulary() throws Exception {
  String s=Files.readString(Path.of("../docs/sql/2026-09-08_entity_action_audit_case_overview_configuration.sql"));
  assertTrue(s.contains("SET XACT_ABORT ON")); assertTrue(s.contains("('CASE_OVERVIEW_CONFIGURATION')"));
  assertTrue(s.contains("BEGIN TRANSACTION")); assertTrue(s.contains("IF XACT_STATE() <> 0 ROLLBACK"));
 }
 private static EffectiveCaseDateTypeDto type(int id,String key){return new EffectiveCaseDateTypeDto(id,7,key,key,null,"OTHER","#123456",false,id,true,false,EffectiveCaseDateTypeDto.Origin.TENANT_CREATED,new byte[]{1});}
 private static int count(String text,String token){int n=0,p=0;while((p=text.indexOf(token,p))>=0){n++;p+=token.length();}return n;}
}
