package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
final class CaseDateSemanticRoleAdministrationContractTest {
 private static String source(String p)throws Exception{return Files.readString(Path.of(p));}
 @Test void mutationsAreTenantOwnedConcurrentAtomicAndAudited()throws Exception{String s=source("src/main/java/com/shale/data/dao/CaseDateDao.java");assertTrue(s.contains("ShaleClientId=? AND IsActive=1 AND IsDeleted=0"));assertTrue(s.contains("ShaleClientId=? AND RowVer=?"));assertTrue(s.contains("CASE_DATE_ROLE_MAPPING"));assertTrue(s.contains("SEMANTIC_ROLE"));assertTrue(s.contains("CASE_DATE_TYPE_ID"));assertTrue(s.contains("con.rollback()"));assertFalse(s.contains("Name=?"));}
 @Test void lifecycleIsProtectedAndHistoryIsNotRewritten()throws Exception{String s=source("src/main/java/com/shale/data/dao/CaseDateDao.java");assertTrue(s.contains("requireNotActivelyMapped"));assertTrue(s.contains("Change or reset the "));assertFalse(s.contains("UPDATE dbo.CaseDates SET CaseDateTypeId")&&s.contains("SemanticRoleMappings SET CaseDateTypeId"));}
 @Test void migrationAddsStableAuditVocabulary()throws Exception{String s=source("../docs/sql/2026-08-10_case_date_semantic_role_admin_phase2.sql");assertTrue(s.contains("'CASE_DATE_ROLE_MAPPING'"));assertTrue(s.contains("BEGIN TRANSACTION"));}
}
