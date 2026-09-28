package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

/** Guards the phase where SOL/TCN definitions are ordinary overlays while mapping rows remain historical. */
final class CaseDateTypeLifecycleCutoverContractTest {
    private static final String DAO = read("src/main/java/com/shale/data/dao/CaseDateDao.java");
    private static final String FORM = read("src/main/java/com/shale/data/dao/FormConfigurationDao.java");
    private static final String OVERVIEW = read("src/main/java/com/shale/data/dao/CaseOverviewConfigurationDao.java");
    private static String read(String p){try{return Files.readString(Path.of(p));}catch(Exception e){throw new ExceptionInInitializerError(e);}}

    @Test void visibilityAndSelectionNoLongerDependOnSolOrTcnMappings() {
        String selector=DAO.substring(DAO.indexOf("public List<EffectiveCaseDateTypeDto> listEffectiveCaseDateTypes"),DAO.indexOf("public int resolveEffectiveCaseDateTypeId"));
        assertAll(
            () -> assertTrue(selector.contains("'intake','statute_of_limitations','tort_notice_deadline'")),
            () -> assertFalse(selector.contains("CaseDateTypeSemanticRoleMappings")),
            () -> assertFalse(FORM.contains("CaseDateTypeSemanticRoleMappings")),
            () -> assertFalse(OVERVIEW.substring(OVERVIEW.indexOf("private static void validateSelections")).contains("CaseDateTypeSemanticRoleMappings")));
    }

    @Test void overlayPrecedenceSupportsInactiveMaskAndDeletedReset() {
        assertAll(
            () -> assertTrue(DAO.contains("t.ShaleClientId = ? AND t.IsDeleted = 0 THEN 0")),
            () -> assertTrue(DAO.contains("WHERE rn = 1 AND IsDeleted = 0 AND IsActive = 1")),
            () -> assertTrue(DAO.contains("candidate.IsDeleted=0")),
            () -> assertTrue(DAO.contains("AND t.IsActive=1")));
    }

    @Test void ordinaryOverridesUseTenantConcurrencyAndTransactionalAuditWithoutChangingGlobalIds() {
        assertAll(
            () -> assertTrue(DAO.contains("requireOrdinaryOverrideKey")),
            () -> assertTrue(DAO.contains("statute_of_limitations\",\"tort_notice_deadline")),
            () -> assertTrue(DAO.contains("WHERE Id=? AND ShaleClientId=? AND RowVer=?")),
            () -> assertTrue(DAO.contains("EntityType.CASE_DATE_TYPE")),
            () -> assertTrue(DAO.contains("catch(Exception e){con.rollback()")),
            () -> assertFalse(DAO.contains("UPDATE dbo.CaseDateTypes SET ShaleClientId")),
            () -> assertFalse(DAO.contains("UPDATE dbo.CaseDates SET CaseDateTypeId")));
    }

    @Test void onlyIntakeIsOperativeProtectedAdministrationAndSingleton() {
        String singleton=DAO.substring(DAO.indexOf("private static void requireProtectedSingletonAvailable"),DAO.indexOf("static String occurrenceSql"));
        assertAll(
            () -> assertTrue(DAO.contains("WHERE r.RoleKey='INTAKE'")),
            () -> assertTrue(DAO.contains("Only Intake is an operative protected Case Date role.")),
            () -> assertTrue(singleton.contains("m.SemanticRoleKey='INTAKE'")),
            () -> assertFalse(singleton.contains("STATUTE_OF_LIMITATIONS")),
            () -> assertFalse(singleton.contains("TORT_NOTICE_DEADLINE")));
    }

    @Test void historicalOccurrencesRemainReadableAndOrdinaryDeadlinesRemainDeterministic() {
        assertAll(
            () -> assertTrue(DAO.contains("COALESCE(eff.Name, st.Name) AS TypeName")),
            () -> assertTrue(DAO.contains("requireHistoricalType")),
            () -> assertTrue(DAO.contains("ORDER BY cd.StartsAt ASC,cd.Id ASC")),
            () -> assertFalse(DAO.contains("DELETE FROM dbo.CaseDates")));
    }
}
