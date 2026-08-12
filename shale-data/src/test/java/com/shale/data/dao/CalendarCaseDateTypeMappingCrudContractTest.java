package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CalendarCaseDateTypeMappingCrudContractTest {
    private static final String DAO=read("src/main/java/com/shale/data/dao/CalendarCaseDateTypeMappingDao.java");
    private static final String PORT=read("../shale-core/src/main/java/com/shale/core/service/CalendarCaseDateTypeMappingServicePort.java");
    private static final String ADAPTER=read("src/main/java/com/shale/data/service/adapter/CalendarCaseDateTypeMappingServiceAdapter.java");
    private static String read(String path){try{return Files.readString(Path.of(path));}catch(Exception e){throw new ExceptionInInitializerError(e);}}

    @Test void contractExposesCompleteCrudAndProductionDelegation(){
        for(String method:new String[]{"listMappings","createMapping","updateMapping","setMappingActive","deleteMapping"}){
            assertTrue(PORT.contains(method)); assertTrue(ADAPTER.contains("dao."+method+"("));
        }
        assertFalse(PORT.contains("shaleClientId")); assertFalse(PORT.contains("actorUserId"));
    }
    @Test void tenantAndActorComeOnlyFromAuthenticatedSession(){
        assertTrue(DAO.contains("SESSION_CONTEXT(N'ShaleClientId')")); assertTrue(DAO.contains("SESSION_CONTEXT(N'PrincipalUserId')"));
        assertTrue(DAO.contains("COALESCE(is_admin,0)=1")); assertTrue(DAO.contains("ShaleClientId=? AND Id=?"));
        assertTrue(DAO.contains("Calendar/case-date type mapping was not found."));
    }
    @Test void referencesPermitOnlyGlobalOrCurrentTenantActiveTypes(){
        assertTrue(DAO.contains("CalendarEventTypeId=? AND (ShaleClientId IS NULL OR ShaleClientId=?) AND IsActive=1"));
        assertTrue(DAO.contains("Id=? AND (ShaleClientId IS NULL OR ShaleClientId=?) AND IsActive=1 AND IsDeleted=0"));
    }
    @Test void mutationsEnforceDirectionCardinalityAndOptimisticConcurrency(){
        assertTrue(DAO.contains("At least one synchronization direction must be enabled.")); assertTrue(DAO.contains("WITH (UPDLOCK,HOLDLOCK)"));
        assertTrue(DAO.contains("CalendarEventTypeId=? OR CaseDateTypeId=?")); assertTrue(DAO.contains("RowVer=?"));
        assertTrue(DAO.contains("getErrorCode()==2601")); assertTrue(DAO.contains("getErrorCode()==2627"));
    }
    @Test void everyMutationIsTransactionalReloadedAndAuditedWithoutSensitiveMetadata(){
        assertTrue(DAO.contains("con.setAutoCommit(false)")); assertTrue(DAO.contains("con.commit()")); assertTrue(DAO.contains("rollback(con)"));
        assertTrue(DAO.contains("return requireRow(con"));
        for(String action:new String[]{"CREATED","UPDATED","ACTIVATED","DEACTIVATED","DELETED"})assertTrue(DAO.contains("Action."+action));
        assertTrue(DAO.contains("CALENDAR_EVENT_TYPE_ID")); assertTrue(DAO.contains("CASE_DATE_TYPE_ID"));
        assertFalse(DAO.toLowerCase().contains("metadata.put(entityactionauditevent.metadatakey.description"));
        assertTrue(DAO.indexOf("audit(con") < DAO.lastIndexOf("con.commit()"));
    }
}
