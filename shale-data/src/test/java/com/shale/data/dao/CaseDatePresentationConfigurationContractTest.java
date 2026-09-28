package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.dto.CaseDatePresentationSelectionDto;
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

    @Test void administrationLoadIsAlsoAdminAuthorized()throws Exception{
        String s=read("shale-data/src/main/java/com/shale/data/dao/CaseDatePresentationConfigurationDao.java");
        String get=s.substring(s.indexOf("public CaseDatePresentationConfigurationDto get"),s.indexOf("public CaseDatePresentationConfigurationDto replace"));
        assertTrue(get.contains("verifySession(con,tenant,actor,true)"),
                "historical configuration reads are administrator-only, not merely UI-hidden");
    }

    @Test void resolverMatchesGlobalOrTenantStoredTypeWithoutRewritingAndKeepsHistoryReadable()throws Exception{
        String s=read("shale-data/src/main/java/com/shale/data/dao/CaseDatePresentationConfigurationDao.java");
        assertAll(
          ()->assertTrue(s.contains("stored.ShaleClientId=? OR stored.ShaleClientId IS NULL")),
          ()->assertTrue(s.contains("LOWER(LTRIM(RTRIM(stored.SystemKey)))=SUBSTRING(s.SelectionIdentity,8,160)")),
          ()->assertTrue(s.contains("ORDER BY cd.StartsAt,cd.Id")),
          ()->assertTrue(s.contains("NOT EXISTS(SELECT 1 FROM dbo.CaseOverviewConfigurations"), "missing parent inherits the firm Overview default"),
          ()->assertTrue(s.contains("JOIN dbo.CaseOverviewConfigurations o"), "an existing parent, including one with zero children, overrides the default"),
          ()->assertTrue(s.contains("resolveForCases(Collection"), "collection reads must be set based"),
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
          ()->assertTrue(s.contains("Mirror CaseOverviewConfigurationDao.defaults")),
          ()->assertTrue(s.contains("ROW_NUMBER() OVER(PARTITION BY ShaleClientId ORDER BY OriginalSortOrder)-1 CompactSortOrder")),
          ()->assertFalse(s.contains("Every tenant must receive the five established Overview defaults.")),
          ()->assertTrue(s.contains("A required protected Case Date mapping is missing or ambiguous.")),
          ()->assertTrue(s.contains("A tenant Case Date overlay family is ambiguous.")),
          ()->assertFalse(s.contains("UPDATE dbo.CaseDates")),
          ()->assertFalse(s.contains("UPDATE dbo.CaseDateTypeSemanticRoleMappings")));
    }

    @Test void builtInOnlyTenantOmitsUnavailableOptionalOverviewTypesButRetainsRequiredDefaults(){
        var effective=List.of(type(1,"intake"),type(2,"statute_of_limitations"),type(3,"tort_notice_deadline"));
        assertEquals(List.of("intake","statute_of_limitations","tort_notice_deadline"),
                CaseOverviewConfigurationDao.defaults(effective).stream().map(EffectiveCaseDateTypeDto::systemKey).toList(),
                "a built-in-only tenant must preserve its actual three-date uncustomized Overview rather than fail on optional types");
    }

    @Test void firmOverviewDefaultsPreserveSelectionOrderHistoricalPresentationAndExplicitEmpty(){
        var current=type(11,"intake");
        var historical=new EffectiveCaseDateTypeDto(12,7,null,"Former deadline",null,"OTHER","#654321",false,2,false,true,
                EffectiveCaseDateTypeDto.Origin.TENANT_CREATED,new byte[]{2});
        var selections=List.of(
                new CaseDatePresentationSelectionDto("TYPE:12",0,historical,true),
                new CaseDatePresentationSelectionDto("SYSTEM:intake",1,current,false));
        assertEquals(List.of(historical,current),CaseOverviewConfigurationDao.selectionTypes(selections),
                "Overview inheritance must use each selection's tenant-effective or historical presentation in saved order");
        assertEquals(List.of(),CaseOverviewConfigurationDao.selectionTypes(List.of()),
                "an explicitly empty firm Overview selection must remain empty");
    }

    @Test void servicePortDefaultsFailClosedAndProductionAdapterDelegates()throws Exception{
        String port=read("shale-core/src/main/java/com/shale/core/service/CaseServicePort.java");
        String adapter=read("shale-data/src/main/java/com/shale/data/service/adapter/CaseServiceAdapter.java");
        assertAll(
          ()->assertTrue(port.contains("Case Date presentation configuration is unavailable.")),
          ()->assertTrue(adapter.contains("requireCaseDatePresentationConfigurationDao().get(t,a,p)")),
          ()->assertTrue(adapter.contains("requireCaseDatePresentationConfigurationDao().replace(c)")),
          ()->assertTrue(adapter.contains("requireCaseDatePresentationConfigurationDao().resolve(caseId,t,a,p)")),
          ()->assertTrue(adapter.contains("resolveForCases(ids,t,a,p)")));
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

    @Test void failurePreflightReportsRollbackStateAndEveryIneligibleCandidateReason()throws Exception{
        String preflight=read("docs/sql/2026-09-24_case_date_presentation_configuration_phase1_preflight.sql");
        assertAll(
          ()->assertTrue(preflight.contains("ROLLED_BACK_OR_NEVER_APPLIED")),
          ()->assertTrue(preflight.contains("OBJECTS_PRESENT_REVIEW_ROWS")),
          ()->assertTrue(preflight.contains("TypeOwnerTenantId")),
          ()->assertTrue(preflight.contains("GLOBAL_NOT_RUNTIME_VISIBLE")),
          ()->assertTrue(preflight.contains("TENANT_RESET_MARKER")),
          ()->assertTrue(preflight.contains("INACTIVE_EFFECTIVE_WINNER")),
          ()->assertTrue(preflight.contains("SHADOWED_OR_AMBIGUOUS_TENANT_ROW")),
          ()->assertFalse(preflight.matches("(?is).*(INSERT|UPDATE|DELETE|MERGE|ALTER|CREATE|DROP)\\s+dbo\\..*"),
                  "preflight may use temp tables but must never mutate a durable dbo object"));
    }

    private static EffectiveCaseDateTypeDto type(int id,String key){return new EffectiveCaseDateTypeDto(id,null,key,key,null,"OTHER","#123456",false,id,true,false,EffectiveCaseDateTypeDto.Origin.GLOBAL,new byte[]{1});}
}
