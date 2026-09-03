package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class CaseTeamRoleDefinitionContractTest {
 private static final String DAO=read("src/main/java/com/shale/data/dao/CaseTeamRoleDefinitionDao.java");
 private static final String SQL=read("../docs/sql/2026-09-03_case_team_role_definitions_phase1.sql");
 private static String read(String p){try{return Files.readString(Path.of(p));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @Test void migrationSeedsEveryPersistedCaseTeamRoleWithoutChangingAssignments(){for(String key:new String[]{"responsible_attorney","prelitigation_staff","attorney","legal_assistant","paralegal","law_clerk","co_counsel"})assertTrue(SQL.contains(key));assertTrue(SQL.contains("LegacyRoleId"));assertFalse(SQL.contains("UPDATE dbo.CaseUsers"));}
 @Test void schemaHasOverlayLifecycleConcurrencyUniquenessAndRls(){for(String token:new String[]{"ShaleClientId int NULL","SystemKey varchar(64)","IsProtected","IsActive","IsDeleted","RowVer rowversion","UX_CaseTeamRoleDefinitions_GlobalSystemKey","UX_CaseTeamRoleDefinitions_TenantSystemKey","fn_FilterByTenantOrGlobal"})assertTrue(SQL.contains(token),token);}
 @Test void effectiveReadMasksInactiveAndFallsBackOnlyAfterDeletedOverride(){assertTrue(DAO.contains("CASE WHEN d.ShaleClientId=? THEN 0 ELSE 1 END"));assertTrue(DAO.contains("d.ShaleClientId IS NULL OR d.IsDeleted=0"));assertFalse(DAO.contains("IsActive=1"));assertTrue(DAO.contains("d.SystemKey IS NULL"));}
 @Test void mutationsEnforceTenantAdminNamesConcurrencyAndTransactionalAudit(){for(String token:new String[]{"SESSION_CONTEXT(N'ShaleClientId')","SESSION_CONTEXT(N'PrincipalUserId')","is_admin,0)=1","LOWER(LTRIM(RTRIM(d.Name)))","ShaleClientId=? AND RowVer=?","c.rollback()","EntityType.CASE_TEAM_ROLE","Action.OVERRIDE_RESET"})assertTrue(DAO.contains(token),token);}
}
