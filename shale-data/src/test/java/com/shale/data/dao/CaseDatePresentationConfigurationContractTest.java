package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Protects the tenant default foundation before any card/Overview reader cutover. */
final class CaseDatePresentationConfigurationContractTest {
    private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();
    private static String read(String path)throws Exception{return Files.readString(ROOT.resolve(path)).replace("\r\n","\n");}

    @Test void stableIdentitiesNormalizeAndExplicitEmptyIsAuthoritative(){
        assertEquals("SYSTEM:statute_of_limitations",CaseDatePresentationConfigurationDao.normalizeIdentity(" system:Statute_Of_Limitations "));
        assertEquals("TYPE:42",CaseDatePresentationConfigurationDao.normalizeIdentity("type:042"));
        assertEquals(List.of(),CaseDatePresentationConfigurationDao.normalizeDistinct(List.of()));
        assertThrows(IllegalArgumentException.class,()->CaseDatePresentationConfigurationDao.normalizeDistinct(List.of("SYSTEM:intake","system:INTAKE")));
        assertThrows(IllegalArgumentException.class,()->CaseDatePresentationConfigurationDao.normalizeIdentity("TYPE:0"));
    }

    @Test void daoEnforcesTenantAdminConcurrencyOrderingAuditAndRollback()throws Exception{
        String s=read("shale-data/src/main/java/com/shale/data/dao/CaseDatePresentationConfigurationDao.java");
        assertAll(
          ()->assertTrue(s.contains("SESSION_CONTEXT(N'ShaleClientId')")),
          ()->assertTrue(s.contains("SESSION_CONTEXT(N'PrincipalUserId')")),
          ()->assertTrue(s.contains("ISNULL(is_admin,0)=1")),
          ()->assertTrue(s.contains("WITH(UPDLOCK,HOLDLOCK)")),
          ()->assertTrue(s.contains("RowVer=?")),
          ()->assertTrue(s.contains("ORDER BY s.SortOrder,s.Id")),
          ()->assertTrue(s.contains("EntityType.CASE_DATE_PRESENTATION_CONFIGURATION")),
          ()->assertTrue(s.contains("audits.append(con")),
          ()->assertTrue(s.indexOf("audits.append(con")<s.indexOf("con.commit()")),
          ()->assertTrue(s.contains("con.rollback()")));
    }

    @Test void resolverMatchesGlobalOrTenantStoredTypeWithoutRewritingAndKeepsHistoryReadable()throws Exception{
        String s=read("shale-data/src/main/java/com/shale/data/dao/CaseDatePresentationConfigurationDao.java");
        assertAll(
          ()->assertTrue(s.contains("stored.ShaleClientId=c.ShaleClientId OR stored.ShaleClientId IS NULL")),
          ()->assertTrue(s.contains("LOWER(LTRIM(RTRIM(stored.SystemKey)))=SUBSTRING(s.SelectionIdentity,8,160)")),
          ()->assertTrue(s.contains("ORDER BY cd.StartsAt,cd.Id")),
          ()->assertFalse(s.contains("UPDATE dbo.CaseDates")),
          ()->assertTrue(s.contains("CASE WHEN t.IsActive=1 AND t.IsDeleted=0 THEN 0 ELSE 1 END Historical")),
          ()->assertTrue(s.contains("not active and tenant-effective")));
    }

    @Test void migrationSeedsActualCardAndOverviewOrderWithStrictRls()throws Exception{
        String s=read("docs/sql/2026-09-24_case_date_presentation_configuration_phase1.sql");
        assertAll(
          ()->assertTrue(s.contains("sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseDatePresentationConfigurations")),
          ()->assertTrue(s.contains("sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseDatePresentationSelections")),
          ()->assertTrue(s.contains("UQ_CaseDatePresentationConfigurations_TenantPurpose")),
          ()->assertTrue(s.contains("UQ_CaseDatePresentationSelections_ConfigOrder")),
          ()->assertTrue(s.contains("WHEN 'INTAKE' THEN 0 WHEN 'STATUTE_OF_LIMITATIONS' THEN 1 ELSE 2")),
          ()->assertTrue(s.contains("('SYSTEM:date_of_injury',0),('SYSTEM:date_of_medical_negligence',1),('SYSTEM:intake',2),('SYSTEM:statute_of_limitations',3),('SYSTEM:tort_notice_deadline',4)")),
          ()->assertFalse(s.contains("UPDATE dbo.CaseDates")),
          ()->assertFalse(s.contains("UPDATE dbo.CaseDateTypeSemanticRoleMappings")));
    }

    @Test void servicePortDefaultsFailClosedAndProductionAdapterDelegates()throws Exception{
        String port=read("shale-core/src/main/java/com/shale/core/service/CaseServicePort.java");
        String adapter=read("shale-data/src/main/java/com/shale/data/service/adapter/CaseServiceAdapter.java");
        assertAll(
          ()->assertTrue(port.contains("Case Date presentation configuration is unavailable.")),
          ()->assertTrue(adapter.contains("requireCaseDatePresentationConfigurationDao().get(t,a,p)")),
          ()->assertTrue(adapter.contains("requireCaseDatePresentationConfigurationDao().replace(c)")),
          ()->assertTrue(adapter.contains("requireCaseDatePresentationConfigurationDao().resolve(caseId,t,a,p)")));
    }

    @Test void verificationIsRowLevelAndAuditVocabularyIsForwardOnly()throws Exception{
        String verify=read("docs/sql/2026-09-24_case_date_presentation_configuration_phase1_verify.sql");
        String audit=read("docs/sql/2026-09-24_case_date_presentation_audit_allowlist_phase1.sql");
        assertAll(
          ()->assertTrue(verify.contains("SelectionIdentity,s.SortOrder")),
          ()->assertTrue(verify.contains("CaseDateId,cd.CaseDateTypeId")),
          ()->assertTrue(verify.contains("StoredTypeOwner")),
          ()->assertFalse(verify.matches("(?is).*(INSERT|UPDATE|DELETE|MERGE|ALTER|CREATE|DROP)\\s+dbo\\..*")),
          ()->assertTrue(audit.contains("''CASE_DATE_PRESENTATION_CONFIGURATION''")),
          ()->assertTrue(audit.contains("BEGIN TRANSACTION")),
          ()->assertTrue(audit.contains("XACT_STATE()<>0 ROLLBACK")));
    }
}
